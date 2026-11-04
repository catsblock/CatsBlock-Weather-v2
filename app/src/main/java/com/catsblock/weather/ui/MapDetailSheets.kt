package com.catsblock.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.MunicipalityMeta
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

internal class MapDetail(val type: String, val data: JSONObject)

private val Dash = "—"

private fun formatTime(raw: String?): String {
    if (raw.isNullOrBlank()) return Dash
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(I18n.lang.locale)
    val zone = ZoneId.systemDefault()
    runCatching { return formatter.format(OffsetDateTime.parse(raw).atZoneSameInstant(zone)) }
    runCatching { return formatter.format(Instant.parse(raw).atZone(zone)) }
    runCatching {
        val local = LocalDateTime.parse(raw.trim().replace(' ', 'T'))
        return formatter.format(local.atZone(zone))
    }
    return raw
}

private fun count(value: Long): String = NumberFormat.getInstance(I18n.lang.locale).format(value)

private fun decimal(value: Double, digits: Int = 1): String =
    NumberFormat.getInstance(I18n.lang.locale).apply { maximumFractionDigits = digits; minimumFractionDigits = 0 }.format(value)

private fun usd(value: Double): String {
    val format = NumberFormat.getCurrencyInstance(I18n.lang.locale)
    format.currency = Currency.getInstance("USD")
    format.maximumFractionDigits = 0
    return format.format(value)
}

private fun area(hectares: Double): String =
    if (hectares >= 100.0) decimal(hectares / 100.0) + " km²" else decimal(hectares) + " ha"

private fun levelColor(level: String): Color = when (level) {
    "danger", "severe" -> Color(0xFFE53935)
    "alert" -> Color(0xFFFF6D00)
    "moderate", "warning" -> Color(0xFFFFD600)
    else -> Color(0xFF9E9E9E)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapDetailSheet(detail: MapDetail, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val data = detail.data
    val lat = data.optDouble("lat", Double.NaN)
    val lon = data.optDouble("lon", Double.NaN)
    val meta by produceState<JSONObject?>(null, detail.type, lat, lon) {
        value = if (detail.type == "sample" || lat.isNaN() || lon.isNaN()) null else withContext(Dispatchers.IO) {
            runCatching { MunicipalityMeta.details(lat, lon) }.getOrNull()
        }
    }
    val loading = detail.type != "sample" && meta == null && !lat.isNaN()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (detail.type) {
                "alert" -> AlertBody(data)
                "fire" -> FireBody(data)
                "sample" -> MapReadingBody(data)
                else -> Header(tr("Municipality details"), null, Color.Unspecified)
            }
            if (detail.type != "sample") MunicipalityBlock(meta, loading)
        }
    }
}

@Composable
private fun MapReadingBody(data: JSONObject) {
    val layerId = data.optString("layer")
    val layer = MapLayer.entries.firstOrNull { it.id == layerId }
    val title = tr(layer?.label ?: "Weather map")
    val value = data.optDouble("value", Double.NaN)
    val unit = data.optString("unit")
    val lat = data.optDouble("lat", Double.NaN)
    val lon = data.optDouble("lon", Double.NaN)
    val readingLabel = data.optString("readingLabel").ifBlank { title }
    val status = data.optString("status")
    val secondary = data.optDouble("secondary", Double.NaN)
    val secondaryLabel = data.optString("secondaryLabel")
    val secondaryUnit = data.optString("secondaryUnit")

    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    if (status.isNotBlank()) {
        Text(status, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (value.isNaN()) {
        Text(tr("Details unavailable"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(
            readingLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            decimal(value) + (if (unit.isBlank()) "" else " $unit"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium
        )
        if (!secondary.isNaN() && secondaryLabel.isNotBlank()) {
            Field(secondaryLabel, decimal(secondary) + (if (secondaryUnit.isBlank()) "" else " $secondaryUnit"))
        }
    }
    if (!lat.isNaN() && !lon.isNaN()) {
        HorizontalDivider()
        Field(
            tr("Coordinates"),
            String.format(Locale.US, "%.3f, %.3f", lat, lon)
        )
    }
    data.optString("source").takeIf { it.isNotBlank() }?.let { Note(tr("Source") + ": " + it) }
}

@Composable
private fun Header(title: String, chip: String?, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        if (chip != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(color, RoundedCornerShape(2.dp)))
                Text(chip, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun Block(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        content()
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { Dash }, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AlertBody(d: JSONObject) {
    val level = d.optString("level")
    val label = when (level) {
        "danger" -> tr("Danger")
        "alert" -> tr("Alert")
        else -> tr("Warning")
    }
    Header(d.optString("event").ifBlank { d.optString("headline") }, label, levelColor(level))
    Block(null) {
        d.optString("severity").takeIf { it.isNotBlank() }?.let { Field(tr("Severity"), tr(it)) }
        d.optString("urgency").takeIf { it.isNotBlank() }?.let { Field(tr("Urgency"), tr(it)) }
        d.optString("certainty").takeIf { it.isNotBlank() }?.let { Field(tr("Certainty"), tr(it)) }
        Field(tr("Effective"), formatTime(d.optString("effective")))
        Field(tr("Expires"), formatTime(d.optString("expires")))
        Field(tr("Issued by"), d.optString("sender"))
        Field(tr("Affected area"), d.optString("area"))
    }
    d.optString("description").takeIf { it.isNotBlank() }?.let { Block(tr("Description")) { Text(it, style = MaterialTheme.typography.bodyMedium) } }
    d.optString("instruction").takeIf { it.isNotBlank() }?.let { Block(tr("Instructions")) { Text(it, style = MaterialTheme.typography.bodyMedium) } }
    Note(tr("Source") + ": " + d.optString("source"))
}

@Composable
private fun FireBody(d: JSONObject) {
    val modeled = HashSet<String>().apply {
        val list = d.optJSONArray("modeled")
        if (list != null) for (i in 0 until list.length()) add(list.getString(i))
    }
    fun mark(key: String, text: String) = if (key in modeled) "≈ $text" else text
    val status = d.optString("status")
    val statusLabel = when (status) {
        "controlled" -> tr("Controlled")
        "contained" -> tr("Contained")
        else -> tr("Active")
    }
    val title = d.optString("name").ifBlank { tr("Wildfire details") }
    Header(title, statusLabel, when (status) {
        "controlled" -> Color(0xFF43A047)
        "contained" -> Color(0xFFFFB300)
        else -> Color(0xFFE53935)
    })
    Block(null) {
        Field(tr("Detection date"), formatTime(d.optString("detected")))
        Field(tr("Report time"), formatTime(d.optString("reported")))
        Field(tr("Last updated"), formatTime(d.optString("updated")))
        Field(tr("Estimated damage"), mark("cost", usd(d.optDouble("costUsd", 0.0))))
        Field(tr("Affected area"), mark("area", area(d.optDouble("areaHa", 0.0))))
        Field(
            tr("Status"),
            if (d.has("containment")) statusLabel + " · " + tr("Containment") + " " + decimal(d.optDouble("containment"), 0) + "%" else statusLabel
        )
        Field(
            tr("Cause"),
            when (d.optString("cause")) {
                "human" -> tr("Human-caused")
                "natural" -> tr("Natural (lightning)")
                "undetermined" -> tr("Undetermined")
                else -> tr("Under investigation")
            }
        )
    }
    Block(tr("Fire behavior")) {
        Field(
            tr("Fire behavior"),
            when (d.optString("behavior")) {
                "smoldering" -> tr("Smoldering")
                "creeping" -> tr("Creeping")
                "intense" -> tr("Intense burning")
                "extreme" -> tr("Extreme behavior")
                else -> tr("Active spread")
            }
        )
        Field(tr("Fire radiative power"), decimal(d.optDouble("frp", 0.0)) + " MW")
        if (d.optDouble("brightness", 0.0) > 0) Field(tr("Brightness"), decimal(d.optDouble("brightness"), 0) + " K")
        Field(tr("Hotspots detected"), count(d.optLong("hotspots", 0L)))
        if (d.optDouble("confidence", 0.0) > 0) Field(tr("Detection confidence"), decimal(d.optDouble("confidence"), 0) + "%")
        Field(tr("Satellite pass"), if (d.optBoolean("night")) tr("Night-time") else tr("Daytime"))
        val fuelText = d.optString("fuelText")
        val fuel = fuelText.ifBlank {
            when (d.optString("fuelKey")) {
                "boreal" -> tr("Boreal forest")
                "tropical" -> tr("Tropical forest")
                "savanna" -> tr("Savanna")
                "grass" -> tr("Grassland")
                "shrub" -> tr("Shrubland")
                else -> tr("Temperate forest")
            }
        }
        Field(tr("Fuel type"), mark("fuel", fuel))
    }
    if (modeled.isNotEmpty()) Note(tr("Values marked ≈ are modeled estimates, not official figures."))
    Note(tr("Source") + ": NASA FIRMS · NIFC")
}

@Composable
private fun MunicipalityBlock(meta: JSONObject?, loading: Boolean) {
    Block(tr("Municipality")) {
        if (meta == null) {
            Text(
                if (loading) tr("Loading details…") else tr("Details unavailable"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Block
        }
        val name = meta.optString("name")
        if (name.isBlank()) {
            Text(tr("Details unavailable"), style = MaterialTheme.typography.bodyMedium)
            return@Block
        }
        Field(tr("Municipality"), name)
        HorizontalDivider()
        Field(tr("Region"), meta.optString("region"))
        Field(tr("Country"), meta.optString("country"))
        if (meta.has("population")) Field(tr("Population"), count(meta.optLong("population")))
        if (meta.optDouble("areaKm2", 0.0) > 0) Field(tr("Area"), decimal(meta.optDouble("areaKm2")) + " km²")
        if (meta.has("elevation")) Field(tr("Elevation"), decimal(meta.optDouble("elevation"), 0) + " m")
        Field(
            tr("Coordinates"),
            String.format(Locale.US, "%.4f, %.4f", meta.optDouble("lat", meta.optDouble("queryLat")), meta.optDouble("lon", meta.optDouble("queryLon")))
        )
        meta.optString("timezone").takeIf { it.isNotBlank() }?.let { Field(tr("Time zone"), it) }
    }
}
