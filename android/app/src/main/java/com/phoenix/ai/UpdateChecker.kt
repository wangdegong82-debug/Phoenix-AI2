package com.phoenix.ai

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PhoenixUpdate(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

object UpdateChecker {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/wangdegong82-debug/Phoenix-AI2/releases/latest"

    fun check(
        currentVersion: String,
        onResult: (Result<PhoenixUpdate?>) -> Unit
    ) {
        Thread {
            val result = runCatching {
                val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "PhoenixAI-Android")
                }

                try {
                    if (connection.responseCode !in 200..299) {
                        error("GitHub API HTTP ${connection.responseCode}")
                    }

                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val tag = json.optString("tag_name").removePrefix("v")
                    val notes = json.optString("body")

                    var apkUrl = ""
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url")
                                break
                            }
                        }
                    }

                    if (apkUrl.isBlank() || compareVersions(tag, currentVersion) <= 0) {
                        null
                    } else {
                        PhoenixUpdate(
                            versionName = tag,
                            downloadUrl = apkUrl,
                            releaseNotes = notes.ifBlank { "Phoenix AI 新版本已发布。" }
                        )
                    }
                } finally {
                    connection.disconnect()
                }
            }

            Handler(Looper.getMainLooper()).post {
                onResult(result)
            }
        }.start()
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.removePrefix("v").split(".")
        val right = b.removePrefix("v").split(".")
        val size = maxOf(left.size, right.size)

        for (i in 0 until size) {
            val l = left.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            val r = right.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            if (l != r) return l.compareTo(r)
        }
        return 0
    }
}
