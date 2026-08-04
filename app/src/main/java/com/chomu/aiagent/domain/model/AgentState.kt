package com.chomu.aiagent.domain.model

enum class AgentState {
    IDLE,
    LISTENING,
    TALKING,
    WORKING
}

enum class OperationMode {
    CONVERSATIONAL,
    TASK_AUTOMATION
}

enum class ApiProvider {
    GEMINI,
    NVIDIA_NIM
}

data class AutomationAction(
    val mode: String = "TASK",
    val thought: String = "",
    val action: String = "",
    val targetId: String? = null,
    val textInput: String? = null,
    val scrollDirection: String? = null,
    val isFinished: Boolean = false
)
