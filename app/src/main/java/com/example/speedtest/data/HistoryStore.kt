package com.example.speedtest.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Локальное хранилище истории замеров в SharedPreferences (JSON вручную,
 * без сторонних библиотек). Хранит последние [MAX] записей.
 */
class HistoryStore(context: Context) {

    private companion object {
        const val PREFS = "speedtest_history"
        const val KEY = "results"
        const val MAX = 50
    }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<TestResult> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TestResult(
                    timestamp = o.getLong("t"),
                    pingMs = o.getDouble("p"),
                    jitterMs = o.getDouble("j"),
                    downloadMbps = o.getDouble("d"),
                    uploadMbps = o.getDouble("u"),
                    networkType = o.optString("n", "—")
                )
            }.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(result: TestResult): List<TestResult> {
        val current = load().toMutableList()
        current.add(0, result)
        val trimmed = current.take(MAX)
        save(trimmed)
        return trimmed
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun save(list: List<TestResult>) {
        val arr = JSONArray()
        list.forEach { r ->
            val o = JSONObject()
            o.put("t", r.timestamp)
            o.put("p", r.pingMs)
            o.put("j", r.jitterMs)
            o.put("d", r.downloadMbps)
            o.put("u", r.uploadMbps)
            o.put("n", r.networkType)
            arr.put(o)
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}
