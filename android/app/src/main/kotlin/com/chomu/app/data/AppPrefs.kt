package com.chomu.app.data

import android.content.Context

class AppPrefs(context: Context) {
    private val p = context.getSharedPreferences("chomu_prefs", Context.MODE_PRIVATE)

    var nvidiaApiKey: String
        get() = p.getString("nvidia_api_key", "") ?: ""
        set(v) { p.edit().putString("nvidia_api_key", v).apply() }

    var nvidiaModel: String
        get() = p.getString("nvidia_model", "meta/llama-3.1-70b-instruct") ?: "meta/llama-3.1-70b-instruct"
        set(v) { p.edit().putString("nvidia_model", v).apply() }

    var companionName: String
        get() = p.getString("companion_name", "Mia") ?: "Mia"
        set(v) { p.edit().putString("companion_name", v).apply() }

    var vrmUrl: String
        get() = p.getString("vrm_url", "") ?: ""
        set(v) { p.edit().putString("vrm_url", v).apply() }
}
