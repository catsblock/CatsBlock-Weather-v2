package com.catsblock.weather.data

import android.content.Context
import android.net.Uri

object MapFeeds {

    fun handles(uri: Uri): Boolean {
        val name = uri.lastPathSegment ?: return false
        return name.startsWith("__") && name.endsWith(".json")
    }

    fun serve(context: Context, uri: Uri): ByteArray? {
        fun number(name: String): Double? = uri.getQueryParameter(name)?.toDoubleOrNull()
        return when (uri.lastPathSegment) {
            "__fires.json" -> FireHotspots.json(context, number("day")?.toLong())
            "__alerts.json" -> AlertFeed.json(number("at")?.toLong())
            "__alertrange.json" -> AlertFeed.range()
            "__drought.json" -> DroughtFeed.json(uri.getQueryParameter("d"))
            "__firedetail.json" -> {
                val query = FireDetails.Query(
                    lat = number("lat") ?: return null,
                    lon = number("lon") ?: return null,
                    size = number("size") ?: 0.25,
                    hotspots = number("n")?.toInt() ?: 1,
                    frp = number("frp") ?: 0.0,
                    firstMinute = number("first")?.toLong() ?: 0L,
                    lastMinute = number("last")?.toLong() ?: 0L,
                    brightness = number("bright") ?: 0.0,
                    confidence = number("conf") ?: 0.0,
                    night = number("night")?.toInt() ?: 0
                )
                FireDetails.build(query).toString().toByteArray(Charsets.UTF_8)
            }
            else -> null
        }
    }
}
