package com.chomu.aiagent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chomu.aiagent.data.repository.AppSettings
import com.chomu.aiagent.data.repository.LLMRepository
import com.chomu.aiagent.domain.model.ApiConfig
import com.chomu.aiagent.domain.model.ApiProvider
import com.chomu.aiagent.ui.components.VoiceGender
import com.chomu.aiagent.ui.components.VoiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiProvider: ApiProvider = ApiProvider.GEMINI,
    val geminiApiKey: String = "",
    val nvidiaApiKey: String = "",
    val geminiModel: String = "gemini-1.5-flash",
    val nvidiaModel: String = "meta/llama-3.1-70b-instruct",
    val nvidiaBaseUrl: String = "https://integrate.api.nvidia.com/v1/",
    val availableGeminiModels: List<String> = defaultGeminiModels,
    val availableNvidiaModels: List<String> = defaultNvidiaModels,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val systemPrompt: String = "",
    val voiceGender: VoiceGender = VoiceGender.SWARA_FEMALE,
    val voiceEnabled: Boolean = true,
    val isFetchingModels: Boolean = false,
    val savedSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettings: AppSettings,
    private val repository: LLMRepository,
    private val voiceManager: VoiceManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init { loadSettings() }

    private fun loadSettings() {
        val cfg = appSettings.getApiConfig()
        _state.update { it.copy(
            apiProvider = cfg.provider,
            geminiApiKey = cfg.geminiApiKey,
            nvidiaApiKey = cfg.nvidiaApiKey,
            geminiModel = cfg.geminiModel,
            nvidiaModel = cfg.nvidiaModel,
            nvidiaBaseUrl = cfg.nvidiaBaseUrl,
            temperature = cfg.temperature,
            maxTokens = cfg.maxTokens,
            systemPrompt = cfg.systemPrompt,
            voiceGender = appSettings.getVoiceGender(),
            voiceEnabled = appSettings.isVoiceEnabled()
        )}
    }

    fun saveSettings() {
        val s = _state.value
        appSettings.saveApiConfig(ApiConfig(
            provider = s.apiProvider,
            geminiApiKey = s.geminiApiKey,
            nvidiaApiKey = s.nvidiaApiKey,
            geminiModel = s.geminiModel,
            nvidiaModel = s.nvidiaModel,
            nvidiaBaseUrl = s.nvidiaBaseUrl,
            temperature = s.temperature,
            maxTokens = s.maxTokens,
            systemPrompt = s.systemPrompt
        ))
        appSettings.saveVoiceGender(s.voiceGender)
        appSettings.saveVoiceEnabled(s.voiceEnabled)
        voiceManager.setGender(s.voiceGender)
        _state.update { it.copy(savedSuccess = true, error = null) }
    }

    fun fetchModels() {
        val s = _state.value
        _state.update { it.copy(isFetchingModels = true, error = null) }
        viewModelScope.launch {
            when (s.apiProvider) {
                ApiProvider.GEMINI ->
                    repository.fetchGeminiModels(s.geminiApiKey)
                        .onSuccess { models -> _state.update { it.copy(availableGeminiModels = models, isFetchingModels = false) } }
                        .onFailure { e -> _state.update { it.copy(error = e.message, isFetchingModels = false) } }

                ApiProvider.NVIDIA_NIM ->
                    repository.fetchNvidiaModels(s.nvidiaApiKey, s.nvidiaBaseUrl)
                        .onSuccess { models -> _state.update { it.copy(availableNvidiaModels = models, isFetchingModels = false) } }
                        .onFailure { e -> _state.update { it.copy(error = e.message, isFetchingModels = false) } }
            }
        }
    }

    fun dismissSavedBanner() {
        _state.update { it.copy(savedSuccess = false) }
    }

    fun update(block: SettingsUiState.() -> SettingsUiState) = _state.update(block)
}

val defaultGeminiModels = listOf(
    "gemini-1.5-flash", "gemini-1.5-pro",
    "gemini-2.0-flash", "gemini-2.0-flash-lite",
    "gemini-1.0-pro"
)

val defaultNvidiaModels = listOf(
    "meta/llama-3.1-70b-instruct",
    "meta/llama-3.1-8b-instruct",
    "mistralai/mistral-7b-instruct-v0.3",
    "nvidia/nemotron-4-340b-instruct",
    "microsoft/phi-3-medium-128k-instruct"
)
