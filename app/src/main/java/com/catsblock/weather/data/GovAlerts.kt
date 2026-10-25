package com.catsblock.weather.data

import android.content.Context
import org.json.JSONObject
import java.time.OffsetDateTime

internal object GovAlertParser {

    private fun clean(list: List<GovAlert>): List<GovAlert> =
        list.filter { it.event.isNotBlank() || it.headline.isNotBlank() }
            .distinctBy { it.event + "|" + it.headline }
            .take(5)

    fun weatherApi(root: JSONObject): List<GovAlert> {
        val array = root.optJSONObject("alerts")?.optJSONArray("alert") ?: return emptyList()
        val out = ArrayList<GovAlert>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val ends = runCatching { OffsetDateTime.parse(o.optString("expires")).toEpochSecond() }.getOrDefault(0L)
            out += GovAlert(
                event = o.optString("event").trim(),
                headline = o.optString("headline").trim(),
                description = o.optString("desc").trim(),
                sender = "",
                severity = o.optString("severity").trim(),
                endsEpoch = ends
            )
        }
        return clean(out)
    }

    fun visual(root: JSONObject): List<GovAlert> {
        val array = root.optJSONArray("alerts") ?: return emptyList()
        val out = ArrayList<GovAlert>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            out += GovAlert(
                event = o.optString("event").trim(),
                headline = o.optString("headline").trim(),
                description = o.optString("description").trim(),
                sender = "",
                severity = "",
                endsEpoch = o.optLong("endsEpoch", 0L)
            )
        }
        return clean(out)
    }

    fun openWeather(root: JSONObject): List<GovAlert> {
        val array = root.optJSONArray("alerts") ?: return emptyList()
        val out = ArrayList<GovAlert>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            out += GovAlert(
                event = o.optString("event").trim(),
                headline = o.optString("event").trim(),
                description = o.optString("description").trim(),
                sender = o.optString("sender_name").trim(),
                severity = "",
                endsEpoch = o.optLong("end", 0L)
            )
        }
        return clean(out)
    }
}

object GovAlertStore {

    private const val FILE = "gov_alert"
    private const val MAX_AGE_MILLIS = 12 * 60 * 60 * 1000L

    fun save(context: Context, city: City, alerts: List<GovAlert>) {
        val first = alerts.firstOrNull()
        val text = first?.let { it.event.ifBlank { it.headline } }.orEmpty()
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString("city", city.id)
            .putString("text", text)
            .putLong("ends", first?.endsEpoch ?: 0L)
            .putLong("savedAt", System.currentTimeMillis())
            .apply()
    }

    fun current(context: Context, city: City): String? {
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (prefs.getString("city", null) != city.id) return null
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("savedAt", 0L) > MAX_AGE_MILLIS) return null
        val ends = prefs.getLong("ends", 0L)
        if (ends > 0L && ends * 1000L < now) return null
        return prefs.getString("text", null)?.takeIf { it.isNotBlank() }
    }
}
