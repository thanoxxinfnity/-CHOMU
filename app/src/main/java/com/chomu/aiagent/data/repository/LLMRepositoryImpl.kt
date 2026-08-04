package com.chomu.aiagent.data.repository

import com.chomu.aiagent.data.local.ChatDao
import com.chomu.aiagent.data.local.ChatEntity
import com.chomu.aiagent.data.remote.GeminiApiService
import com.chomu.aiagent.data.remote.NvidiaApiService
import com.chomu.aiagent.data.remote.dto.*
import com.chomu.aiagent.domain.model.ApiConfig
import com.chomu.aiagent.domain.model.ApiProvider
import com.chomu.aiagent.domain.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LLMRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val geminiApiService: GeminiApiService,
    private val httpClient: OkHttpClient
) : LLMRepository {

    private var nvidiaService: NvidiaApiService? = null
    private var currentNvidiaBaseUrl: String = ""

    private fun getNvidiaService(baseUrl: String): NvidiaApiService {
        if (nvidiaService == null || currentNvidiaBaseUrl != baseUrl) {
            currentNvidiaBaseUrl = baseUrl
            nvidiaService = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NvidiaApiService::class.java)
        }
        return nvidiaService!!
    }

    override suspend fun sendMessage(
        userMessage: String,
        history: List<Message>,
        config: ApiConfig
    ): Result<String> = runCatching {
        when (config.provider) {
            ApiProvider.GEMINI -> sendGeminiMessage(userMessage, history, config)
            ApiProvider.NVIDIA_NIM -> sendNvidiaMessage(userMessage, history, config)
        }
    }

    private suspend fun sendGeminiMessage(
        userMessage: String,
        history: List<Message>,
        config: ApiConfig
    ): String {
        val contents = mutableListOf<GeminiContent>()
        history.takeLast(20).forEach { msg ->
            contents.add(GeminiContent(
                role = if (msg.isUser) "user" else "model",
                parts = listOf(GeminiPart(msg.content))
            ))
        }
        contents.add(GeminiContent("user", listOf(GeminiPart(userMessage))))

        val request = GeminiRequest(
            contents = contents,
            systemInstruction = GeminiSystemInstruction(listOf(GeminiPart(config.systemPrompt))),
            generationConfig = GeminiGenerationConfig(
                temperature = config.temperature,
                maxOutputTokens = config.maxTokens
            )
        )

        val response = geminiApiService.generateContent(
            model = config.geminiModel,
            apiKey = config.geminiApiKey,
            request = request
        )

        if (!response.isSuccessful) {
            throw Exception("Gemini API error ${response.code()}: ${response.errorBody()?.string()}")
        }

        return response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("Empty response from Gemini")
    }

    private suspend fun sendNvidiaMessage(
        userMessage: String,
        history: List<Message>,
        config: ApiConfig
    ): String {
        val messages = mutableListOf<NvidiaMessage>()
        messages.add(NvidiaMessage("system", config.systemPrompt))
        history.takeLast(20).forEach { msg ->
            messages.add(NvidiaMessage(
                role = if (msg.isUser) "user" else "assistant",
                content = msg.content
            ))
        }
        messages.add(NvidiaMessage("user", userMessage))

        val request = NvidiaRequest(
            model = config.nvidiaModel,
            messages = messages,
            temperature = config.temperature,
            maxTokens = config.maxTokens
        )

        val service = getNvidiaService(config.nvidiaBaseUrl)
        val response = service.chatCompletions(
            bearerToken = "Bearer ${config.nvidiaApiKey}",
            request = request
        )

        if (!response.isSuccessful) {
            throw Exception("NVIDIA NIM error ${response.code()}: ${response.errorBody()?.string()}")
        }

        return response.body()?.choices?.firstOrNull()?.message?.content
            ?: throw Exception("Empty response from NVIDIA NIM")
    }

    override suspend fun fetchGeminiModels(apiKey: String): Result<List<String>> = runCatching {
        val response = geminiApiService.listModels(apiKey)
        if (!response.isSuccessful) throw Exception("Failed to fetch Gemini models")
        response.body()?.models
            ?.map { it.name.removePrefix("models/") }
            ?.filter { it.contains("gemini") }
            ?: listOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-2.0-flash")
    }

    override suspend fun fetchNvidiaModels(apiKey: String, baseUrl: String): Result<List<String>> = runCatching {
        val service = getNvidiaService(baseUrl)
        val response = service.listModels("Bearer $apiKey")
        if (!response.isSuccessful) throw Exception("Failed to fetch NVIDIA models")
        response.body()?.data?.map { it.id } ?: listOf(
            "meta/llama-3.1-70b-instruct",
            "mistralai/mistral-7b-instruct-v0.3",
            "nvidia/nemotron-4-340b-instruct"
        )
    }

    override fun getMessagesFlow(sessionId: String): Flow<List<Message>> =
        chatDao.getMessagesForSession(sessionId).map { entities ->
            entities.map { it.toMessage() }
        }

    override suspend fun saveMessage(message: Message, sessionId: String) {
        chatDao.insertMessage(message.toEntity(sessionId))
    }

    override suspend fun clearHistory(sessionId: String) {
        chatDao.clearSession(sessionId)
    }

    private fun ChatEntity.toMessage() = Message(
        id = id, content = content, isUser = isUser,
        timestamp = timestamp, isError = isError, automationLog = automationLog
    )

    private fun Message.toEntity(sessionId: String) = ChatEntity(
        id = id, content = content, isUser = isUser,
        timestamp = timestamp, isError = isError, automationLog = automationLog,
        sessionId = sessionId
    )
}
