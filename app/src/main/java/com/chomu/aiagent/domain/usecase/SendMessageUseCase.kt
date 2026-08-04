package com.chomu.aiagent.domain.usecase

import com.chomu.aiagent.data.repository.AppSettings
import com.chomu.aiagent.data.repository.LLMRepository
import com.chomu.aiagent.domain.model.Message
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val repository: LLMRepository,
    private val appSettings: AppSettings
) {
    suspend operator fun invoke(
        userMessage: String,
        history: List<Message>
    ): Result<String> {
        val config = appSettings.getApiConfig()
        return repository.sendMessage(userMessage, history, config)
    }
}
