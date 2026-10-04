package com.phoenix.ai

import android.content.Context

class PhoenixConfig(context: Context) {
    private val prefs = context.getSharedPreferences("phoenix_config", Context.MODE_PRIVATE)

    fun apiBase(): String {
        val saved = prefs.getString("api_base", "")?.trim().orEmpty()
        return (saved.ifBlank { BuildConfig.DEFAULT_API_BASE }).trimEnd('/')
    }

    fun accessToken(): String =
        prefs.getString("access_token", "")?.trim().orEmpty()

    fun save(apiBase: String, accessToken: String) {
        prefs.edit()
            .putString("api_base", apiBase.trim().trimEnd('/'))
            .putString("access_token", accessToken.trim())
            .apply()
    }
}
