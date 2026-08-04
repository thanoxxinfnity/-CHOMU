package com.chomu.aiagent.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.chomu.aiagent.domain.model.ApiConfig
import com.chomu.aiagent.domain.model.ApiProvider
import com.chomu.aiagent.domain.model.DEFAULT_SYSTEM_PROMPT
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettings @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "chomu_secure_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getApiConfig(): ApiConfig = ApiConfig(
        provider = ApiProvider.valueOf(prefs.getString("provider", "GEMINI")!!),
        geminiApiKey = prefs.getString("gemini_key", "") ?: "",
        nvidiaApiKey = prefs.getString("nvidia_key", "") ?: "",
        geminiModel = prefs.getString("gemini_model", "gemini-1.5-flash") ?: "gemini-1.5-flash",
        nvidiaModel = prefs.getString("nvidia_model", "meta/llama-3.1-70b-instruct") ?: "meta/llama-3.1-70b-instruct",
        nvidiaBaseUrl = prefs.getString("nvidia_url", "https://integrate.api.nvidia.com/v1/") ?: "https://integrate.api.nvidia.com/v1/",
        temperature = prefs.getFloat("temperature", 0.7f),
        maxTokens = prefs.getInt("max_tokens", 2048),
        systemPrompt = prefs.getString("system_prompt", "") ?.ifBlank { DEFAULT_SYSTEM_PROMPT } ?: DEFAULT_SYSTEM_PROMPT
    )

    fun saveApiConfig(config: ApiConfig) {
        prefs.edit().apply {
            putString("provider", config.provider.name)
            putString("gemini_key", config.geminiApiKey)
            putString("nvidia_key", config.nvidiaApiKey)
            putString("gemini_model", config.geminiModel)
            putString("nvidia_model", config.nvidiaModel)
            putString("nvidia_url", config.nvidiaBaseUrl)
            putFloat("temperature", config.temperature)
            putInt("max_tokens", config.maxTokens)
            putString("system_prompt", config.systemPrompt)
        }.apply()
    }
}
