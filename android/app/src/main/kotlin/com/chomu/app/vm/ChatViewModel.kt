package com.chomu.app.vm

import android.app.Application
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.WebView
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chomu.app.api.NvidiaClient
import com.chomu.app.data.AppPrefs
import kotlinx.coroutines.launch
import java.util.Locale

data class ChatMessage(val role: String, val content: String)

enum class CompanionState { IDLE, THINKING, SPEAKING, LISTENING, ERROR }

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = AppPrefs(app)
    private val nvidia = NvidiaClient()

    val messages = mutableStateListOf<ChatMessage>()
    val state = mutableStateOf(CompanionState.IDLE)
    val isLoading = mutableStateOf(false)

    var webView: WebView? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.05f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {
                        state.value = CompanionState.IDLE
                        js("CompanionBridge.setEmotion('neutral')")
                    }
                    override fun onError(id: String?) {
                        state.value = CompanionState.IDLE
                    }
                })
                ttsReady = true
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || isLoading.value) return
        val key = prefs.nvidiaApiKey
        if (key.isBlank()) {
            messages += ChatMessage("assistant", "Settings mein NVIDIA API key daalo pehle!")
            return
        }
        messages += ChatMessage("user", text)
        callAi(key)
    }

    fun sendCameraFrame(base64: String) {
        val key = prefs.nvidiaApiKey
        if (key.isBlank() || isLoading.value) return
        isLoading.value = true
        state.value = CompanionState.THINKING
        viewModelScope.launch {
            try {
                val reply = nvidia.vision(key, base64, "What do you see? Reply in 1-2 short sentences, speaking to me directly as a companion.")
                messages += ChatMessage("user", "[Camera]")
                messages += ChatMessage("assistant", reply)
                speak(reply)
            } catch (e: Exception) {
                isLoading.value = false
                state.value = CompanionState.IDLE
            }
        }
    }

    private fun callAi(key: String) {
        isLoading.value = true
        state.value = CompanionState.THINKING
        js("CompanionBridge.setEmotion('thinking')")
        viewModelScope.launch {
            try {
                val history = buildHistory(key)
                val reply = nvidia.chat(key, prefs.nvidiaModel, history)
                messages += ChatMessage("assistant", reply)
                speak(reply)
            } catch (e: Exception) {
                val err = "Oops! ${e.message?.take(60) ?: "API error"}"
                messages += ChatMessage("assistant", err)
                state.value = CompanionState.ERROR
                js("CompanionBridge.setEmotion('sad')")
                isLoading.value = false
            }
        }
    }

    private fun buildHistory(key: String): List<NvidiaClient.Msg> {
        val name = prefs.companionName
        val sys = NvidiaClient.Msg("system",
            "You are $name, a warm and expressive AI companion. " +
            "Keep all responses SHORT — max 2 sentences. Be playful and direct.")
        val hist = messages.takeLast(12).map { NvidiaClient.Msg(it.role, it.content) }
        return listOf(sys) + hist
    }

    fun speak(text: String) {
        if (!ttsReady) {
            state.value = CompanionState.IDLE
            isLoading.value = false
            return
        }
        state.value = CompanionState.SPEAKING
        isLoading.value = false
        js("CompanionBridge.setEmotion('happy')")
        val params = android.os.Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "chomu_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        tts?.stop()
        state.value = CompanionState.IDLE
        js("CompanionBridge.setEmotion('neutral')")
    }

    fun js(script: String) {
        webView?.post { webView?.evaluateJavascript("javascript:$script", null) }
    }

    fun loadVrmModel(url: String) {
        js("CompanionBridge.loadModel('$url')")
    }

    override fun onCleared() {
        tts?.shutdown()
        super.onCleared()
    }
}
