package com.phoenix.ai

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MatchSummary(
    val matchId: String,
    val home: String,
    val away: String,
    val kickoff: String,
    val competition: String,
    val status: String,
    val venue: String
)

data class ScorePath(
    val score: String,
    val probability: Double
)

data class MatchAnalysis(
    val matchId: String,
    val home: String,
    val away: String,
    val homeWin: Double,
    val draw: Double,
    val awayWin: Double,
    val xgHome: Double,
    val xgAway: Double,
    val confidence: Double,
    val scores: List<ScorePath>,
    val tailRisks: List<String>,
    val explanation: List<String>,
    val evidence: List<String>
)

class PhoenixApi(private val config: PhoenixConfig) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun today(onResult: (Result<List<MatchSummary>>) -> Unit) {
        async(onResult) {
            val json = request("GET", "/v1/matches/today")
            val rows = json.optJSONArray("matches") ?: JSONArray()
            buildList {
                for (i in 0 until rows.length()) {
                    val item = rows.getJSONObject(i)
                    add(
                        MatchSummary(
                            matchId = item.optString("match_id"),
                            home = item.optString("home"),
                            away = item.optString("away"),
                            kickoff = item.optString("kickoff"),
                            competition = item.optString("competition"),
                            status = item.optString("status"),
                            venue = item.optString("venue")
                        )
                    )
                }
            }
        }
    }

    fun analyze(matchId: String, onResult: (Result<MatchAnalysis>) -> Unit) {
        async(onResult) {
            val json = request("GET", "/v1/matches/$matchId/analysis")
            val p = json.getJSONObject("prediction")
            val scoresJson = p.optJSONArray("scorelines") ?: JSONArray()
            val scores = buildList {
                for (i in 0 until scoresJson.length()) {
                    val row = scoresJson.getJSONObject(i)
                    add(ScorePath(row.optString("score"), row.optDouble("probability")))
                }
            }
            val risks = jsonArrayToStrings(p.optJSONArray("tail_risks"))
            val explanation = jsonArrayToStrings(p.optJSONArray("explanation"))
            val evidenceObj = json.optJSONObject("evidence") ?: JSONObject()
            val evidence = buildList {
                val keys = evidenceObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    add("$key: ${evidenceObj.opt(key)}")
                }
            }

            MatchAnalysis(
                matchId = p.optString("match_id"),
                home = p.optString("home"),
                away = p.optString("away"),
                homeWin = p.optDouble("home_win"),
                draw = p.optDouble("draw"),
                awayWin = p.optDouble("away_win"),
                xgHome = p.optDouble("expected_goals_home"),
                xgAway = p.optDouble("expected_goals_away"),
                confidence = p.optDouble("confidence"),
                scores = scores,
                tailRisks = risks,
                explanation = explanation,
                evidence = evidence
            )
        }
    }

    fun chat(message: String, context: JSONObject?, onResult: (Result<String>) -> Unit) {
        async(onResult) {
            val payload = JSONObject()
                .put("message", message)
                .put("context", context ?: JSONObject())
            val json = request("POST", "/v1/chat", payload)
            json.optString("reply")
        }
    }

    fun health(onResult: (Result<String>) -> Unit) {
        async(onResult) {
            val json = request("GET", "/health")
            "${json.optString("service")} · ${json.optString("version")}"
        }
    }

    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val base = config.apiBase()
        require(base.startsWith("http://") || base.startsWith("https://")) {
            "请先在“设置”里填写 Phoenix API 地址"
        }

        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            config.accessToken().takeIf { it.isNotBlank() }?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
            if (body != null) {
                doOutput = true
                outputStream.bufferedWriter().use { writer ->
                    writer.write(body.toString())
                }
            }
        }

        try {
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (code !in 200..299) {
                val detail = runCatching {
                    JSONObject(text).optString("detail")
                }.getOrNull().orEmpty()
                error("HTTP $code ${detail.ifBlank { text.take(180) }}")
            }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun jsonArrayToStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) add(array.optString(i))
        }
    }

    private fun <T> async(onResult: (Result<T>) -> Unit, work: () -> T) {
        Thread {
            val result = runCatching(work)
            mainHandler.post { onResult(result) }
        }.start()
    }
}
