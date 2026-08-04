package com.chomu.aiagent.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chomu.aiagent.data.repository.AppSettings
import com.chomu.aiagent.data.repository.LLMRepository
import com.chomu.aiagent.domain.model.*
import com.chomu.aiagent.service.FloatingBubbleService
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
    private val appSettings: AppSettings
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
            }.onFailure { err ->
                val errMsg = Message(content = "Error: ${err.message}", isUser = false, isError = true)
                repository.saveMessage(errMsg)
                _uiState.update { it.copy(isLoading = false, agentState = AgentState.IDLE, error = err.message) }
            }
        }
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
        val intent = Intent("com.chomu.aiagent.AUTOMATION_ACTION").apply {
            putExtra("action_type", action.action)
            putExtra("target_id", action.targetId)
            putExtra("text_input", action.textInput)
            putExtra("scroll_direction", action.scrollDirection)
            setPackage(getApplication<Application>().packageName)
        }
        getApplication<Application>().sendBroadcast(intent)
    }

    private fun detectTaskIntent(text: String): Boolean {
        val keywords = listOf(
            "open ", "launch ", "send message", "call ", "search for", "play ",
            "book ", "navigate to", "set alarm", "take screenshot", "turn on",
            "turn off", "click on", "scroll ", "type ", "swipe ", "go to ",
            "download ", "install ", "close ", "switch to"
        )
        val lower = text.lowercase()
        return keywords.any { lower.contains(it) }
    }

    fun setVoiceListening(active: Boolean) {
        _uiState.update { it.copy(
            isVoiceListening = active,
            agentState = if (active) AgentState.LISTENING else AgentState.IDLE
        )}
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

private const val AUTOMATION_SYSTEM_PROMPT = """You are CHOMU, an Android phone automation agent.
Respond ONLY with valid JSON, no other text whatsoever:
{"mode":"TASK","thought":"what you're doing","action":"ACTION_TYPE","target_id":"id_or_null","text_input":"text_or_null","scroll_direction":"up/down/null","is_finished":false}

Actions: CLICK, LONG_CLICK, SET_TEXT, SCROLL, GLOBAL_BACK, GLOBAL_HOME, GLOBAL_RECENTS, TAKE_SCREENSHOT, FINISH_TASK
One action per response. Think step by step."""
