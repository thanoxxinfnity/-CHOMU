package com.chomu.aiagent.ui.components

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class VoiceGender { MADHUR_MALE, SWARA_FEMALE }

@Singleton
class VoiceManager @Inject constructor(@ApplicationContext private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var lastSpokenText = ""
    var currentGender: VoiceGender = VoiceGender.SWARA_FEMALE
    private var pendingText: String? = null

    init { initTts() }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                applyVoiceConfig()
                pendingText?.let { speak(it) }
                pendingText = null
            } else {
                Log.e("VoiceManager", "TTS init failed with status $status")
            }
        }
    }

    private fun applyVoiceConfig() {
        val t = tts ?: return
        val locale = Locale("hi", "IN")
        val langResult = t.isLanguageAvailable(locale)
        if (langResult >= TextToSpeech.LANG_AVAILABLE) {
            t.language = locale
        }

        // Try to find matching named voice (Microsoft voices on Samsung/some devices)
        val hiVoices = t.voices?.filter { v ->
            v.locale.language == "hi" && !v.isNetworkConnectionRequired
        } ?: emptyList()

        val selected = when (currentGender) {
            VoiceGender.MADHUR_MALE ->
                hiVoices.firstOrNull { "madhur" in it.name.lowercase() }
                    ?: hiVoices.firstOrNull { "male" in it.name.lowercase() }
                    ?: hiVoices.firstOrNull()
            VoiceGender.SWARA_FEMALE ->
                hiVoices.firstOrNull { "swara" in it.name.lowercase() }
                    ?: hiVoices.firstOrNull { "female" in it.name.lowercase() }
                    ?: hiVoices.drop(1).firstOrNull()
                    ?: hiVoices.firstOrNull()
        }
        selected?.let { t.voice = it }

        // Pitch simulation when specific voice not found
        t.setPitch(if (currentGender == VoiceGender.MADHUR_MALE) 0.82f else 1.18f)
        t.setSpeechRate(0.92f)
    }

    fun setGender(gender: VoiceGender) {
        currentGender = gender
        if (isReady) applyVoiceConfig()
    }

    fun speak(text: String) {
        lastSpokenText = text
        if (!isReady) {
            pendingText = text
            return
        }
        // Strip markdown symbols before speaking
        val clean = text.replace(Regex("[*#`~_]"), "").trim()
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "chomu_${System.currentTimeMillis()}")
    }

    fun replay() {
        if (lastSpokenText.isNotBlank()) speak(lastSpokenText)
    }

    fun stop() { tts?.stop() }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
