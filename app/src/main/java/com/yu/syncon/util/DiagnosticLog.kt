package com.yu.syncon.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small local-only ring buffer for support diagnostics. Never contains visited content. */
object DiagnosticLog {
    private const val PREFS = "syncon_diagnostics"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 200

    @Synchronized
    fun record(context: Context, event: String, detail: String? = null) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = try { JSONArray(prefs.getString(KEY_EVENTS, "[]")) } catch (_: Exception) { JSONArray() }
        val events = mutableListOf<JSONObject>()
        for (index in 0 until existing.length()) events += existing.optJSONObject(index) ?: continue
        events += JSONObject().apply {
            put("timestampUtc", System.currentTimeMillis())
            put("event", event)
            if (!detail.isNullOrBlank()) put("detail", detail.take(500))
        }
        val trimmed = events.takeLast(MAX_EVENTS)
        prefs.edit().putString(KEY_EVENTS, JSONArray(trimmed).toString()).apply()
    }

    fun export(context: Context): JSONArray {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EVENTS, "[]")
        return try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    }
}
