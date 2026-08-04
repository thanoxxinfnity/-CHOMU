package com.chomu.aiagent.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chomu.aiagent.data.repository.AppSettings
import com.chomu.aiagent.data.repository.LLMRepository
import com.chomu.aiagent.domain.model.*
import com.chomu.aiagent.service.FloatingBubbleService
import com.chomu.aiagent.ui.components.VoiceManager
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val agentState: AgentState = AgentState.IDLE,
    val operationMode: OperationMode = OperationMode.CONVERSATIONAL,
    val inputText: String = "",
    val error: String? = null,
    val automationLog: List<String> = emptyList(),
    val isVoiceListening: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    application: Application,
    private val repository: LLMRepository,
    private val appSettings: AppSettings,
    private val voiceManager: VoiceManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val gson = Gson()

    init {
        viewModelScope.launch {
            repository.getMessagesFlow().collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
        // Apply saved voice preference
        voiceManager.setGender(appSettings.getVoiceGender())
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text, error = null) }
    }

    fun sendMessage(text: String = _uiState.value.inputText) {
        if (text.isBlank()) return
        val config = appSettings.getApiConfig()

        when {
            config.provider == ApiProvider.GEMINI && config.geminiApiKey.isBlank() -> {
                _uiState.update { it.copy(error = "Please set your Gemini API key in Settings") }
                return
            }
            config.provider == ApiProvider.NVIDIA_NIM && config.nvidiaApiKey.isBlank() -> {
                _uiState.update { it.copy(error = "Please set your NVIDIA NIM API key in Settings") }
                return
            }
        }

        val userMsg = Message(content = text, isUser = true)
        voiceManager.stop()
        _uiState.update { it.copy(inputText = "", isLoading = true, agentState = AgentState.TALKING, error = null) }

        viewModelScope.launch {
            repository.saveMessage(userMsg)

            val isTaskCommand = detectTaskIntent(text)
            val mode = if (isTaskCommand) OperationMode.TASK_AUTOMATION else OperationMode.CONVERSATIONAL
            _uiState.update { it.copy(
                operationMode = mode,
                agentState = if (isTaskCommand) AgentState.WORKING else AgentState.TALKING
            )}

            val adjustedConfig = if (isTaskCommand) config.copy(systemPrompt = AUTOMATION_SYSTEM_PROMPT) else config

            repository.sendMessage(
                userMessage = text,
                history = _uiState.value.messages.takeLast(20),
                config = adjustedConfig
            ).onSuccess { response ->
                val agentMsg = if (isTaskCommand) processAutomationResponse(response)
                              else Message(content = response, isUser = false)
                repository.saveMessage(agentMsg)
                _uiState.update { it.copy(isLoading = false, agentState = AgentState.IDLE, operationMode = OperationMode.CONVERSATIONAL) }
                // Auto-speak AI response if voice enabled
                if (appSettings.isVoiceEnabled() && !agentMsg.isError) {
                    voiceManager.speak(agentMsg.content)
                }
            }.onFailure { err ->
                val errMsg = Message(content = "Error: ${err.message}", isUser = false, isError = true)
                repository.saveMessage(errMsg)
                _uiState.update { it.copy(isLoading = false, agentState = AgentState.IDLE, error = err.message) }
            }
        }
    }

    fun replayMessage(content: String) {
        voiceManager.speak(content)
    }

    private fun processAutomationResponse(raw: String): Message {
        val jsonStr = extractJson(raw)
        return try {
            val action = gson.fromJson(jsonStr, AutomationAction::class.java)
            val logEntry = buildString {
                append("🤖 ${action.thought}\n▶ ${action.action}")
                action.targetId?.let { append(" → $it") }
                action.textInput?.let { append("\n📝 \"$it\"") }
            }
            addAutomationLog(logEntry)
            if (!action.isFinished) dispatchAction(action)
            Message(
                content = if (action.isFinished) "✅ Task completed: ${action.thought}" else "⚙️ ${action.thought}",
                isUser = false,
                automationLog = logEntry
            )
        } catch (e: JsonSyntaxException) {
            Message(content = raw, isUser = false)
        }
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start != -1 && end != -1 && end > start) text.substring(start, end + 1) else text
    }

    private fun addAutomationLog(entry: String) {
        _uiState.update { it.copy(automationLog = (it.automationLog + entry).takeLast(20)) }
    }

    private fun dispatchAction(action: AutomationAction) {
        if (!isAccessibilityServiceEnabled()) {
            _uiState.update {
                it.copy(error = "Enable CHOMU Accessibility Service in Settings > Accessibility to use automation")
            }
            return
        }
        val intent = Intent("com.chomu.aiagent.AUTOMATION_ACTION").apply {
            putExtra("action_type", action.action)
            putExtra("target_id", action.targetId)
            putExtra("text_input", action.textInput)
            putExtra("scroll_direction", action.scrollDirection)
            setPackage(getApplication<Application>().packageName)
        }
        getApplication<Application>().sendBroadcast(intent)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val app = getApplication<Application>()
        val serviceId = "${app.packageName}/.service.AgentAutomationService"
        return try {
            val enabledServices = Settings.Secure.getString(
                app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                if (colonSplitter.next().equals(serviceId, ignoreCase = true)) return true
            }
            false
        } catch (_: Exception) { false }
    }

    /**
     * Smarter intent detection using scoring.
     * Returns true only when there's a clear automation command, not just a conversational question.
     */
    private fun detectTaskIntent(text: String): Boolean {
        val lower = text.trim().lowercase()

        // Pure conversation signals — bail out immediately
        val conversationStarters = listOf(
            "what ", "how ", "why ", "who ", "when ", "where ", "which ",
            "tell me", "explain", "describe", "what is", "what are",
            "can you ", "could you ", "would you ", "should i",
            "hi ", "hello", "hey ", "thanks", "thank you", "good ",
            "ok ", "okay ", "yes", "no ", "nah", "hmm", "lol",
            "bro", "yar", "kya", "mujhe batao", "bata", "samjhao"
        )
        if (conversationStarters.any { lower.startsWith(it) }) return false
        // Question ending without clear action verb = conversation
        if (lower.endsWith("?") && !lower.contains("kaise") &&
            automationVerbs.none { lower.startsWith(it) }) return false

        // App names are strong automation signals
        val hasAppName = knownApps.any { lower.contains(it) }

        // Automation verb at start of sentence is the strongest signal
        val hasActionVerb = automationVerbs.any { lower.startsWith(it) } ||
            automationPhrases.any { lower.contains(it) }

        return hasActionVerb || hasAppName
    }

    fun setVoiceListening(active: Boolean) {
        _uiState.update { it.copy(
            isVoiceListening = active,
            agentState = if (active) AgentState.LISTENING else AgentState.IDLE
        )}
    }

    fun newChat() {
        voiceManager.stop()
        viewModelScope.launch {
            repository.clearHistory()
            _uiState.update { it.copy(
                automationLog = emptyList(),
                error = null,
                agentState = AgentState.IDLE,
                operationMode = OperationMode.CONVERSATIONAL
            )}
        }
    }

    fun stopTask() {
        voiceManager.stop()
        _uiState.update { it.copy(agentState = AgentState.IDLE, operationMode = OperationMode.CONVERSATIONAL, isLoading = false) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _uiState.update { it.copy(automationLog = emptyList()) }
        }
    }

    fun startFloatingBubble() {
        val intent = Intent(getApplication(), FloatingBubbleService::class.java)
        getApplication<Application>().startForegroundService(intent)
    }

    fun stopFloatingBubble() {
        val intent = Intent(getApplication(), FloatingBubbleService::class.java)
        getApplication<Application>().stopService(intent)
    }
}

private val automationVerbs = listOf(
    "open ", "launch ", "close ", "start ",
    "send ", "call ", "video call", "dial ",
    "play ", "pause ", "stop ", "skip ", "next ",
    "set alarm", "set a timer", "set reminder", "remind me",
    "take screenshot", "capture ",
    "turn on", "turn off", "enable ", "disable ",
    "click ", "tap ", "press ", "swipe ", "scroll ",
    "type ", "search for", "search on", "look up",
    "navigate to", "go to ", "directions to",
    "download ", "install ", "uninstall ",
    "switch to", "share ", "forward ", "delete ",
    "increase ", "decrease ", "volume ", "mute ", "unmute ",
    "book ", "order ", "buy ", "purchase "
)

private val automationPhrases = listOf(
    "send message", "send a message", "voice message",
    "open settings", "open camera", "open maps",
    "set an alarm", "wake me up", "my favorite",
    "for me on", "on my phone", "from my phone"
)

private val knownApps = listOf(
    "whatsapp", "instagram", "youtube", "twitter", "facebook",
    "gmail", "google maps", "spotify", "netflix", "telegram",
    "snapchat", "linkedin", "tiktok", "uber", "ola",
    "swiggy", "zomato", "amazon", "flipkart", "paytm",
    "phonepe", "gpay", "chrome", "settings", "camera",
    "gallery", "contacts", "dialer", "calculator", "clock"
)

private const val AUTOMATION_SYSTEM_PROMPT = """You are CHOMU, an Android phone automation agent.
Respond ONLY with valid JSON, no other text whatsoever:
{"mode":"TASK","thought":"what you're doing","action":"ACTION_TYPE","target_id":"id_or_null","text_input":"text_or_null","scroll_direction":"up/down/null","is_finished":false}

Actions: CLICK, LONG_CLICK, SET_TEXT, SCROLL, GLOBAL_BACK, GLOBAL_HOME, GLOBAL_RECENTS, TAKE_SCREENSHOT, FINISH_TASK
One action per response. Think step by step."""
