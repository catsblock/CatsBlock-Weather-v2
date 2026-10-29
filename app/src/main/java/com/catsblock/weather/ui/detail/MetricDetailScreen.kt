package com.catsblock.weather.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsblock.weather.data.Hour
import com.catsblock.weather.data.PressureUnit
import com.catsblock.weather.data.WeatherBundle
import com.catsblock.weather.data.WindUnit
import com.catsblock.weather.data.aqiAdvice
import com.catsblock.weather.data.aqiStatus
import com.catsblock.weather.data.compassLabel
import com.catsblock.weather.data.humidityDescription
import com.catsblock.weather.data.precipLabel
import com.catsblock.weather.data.precipToday
import com.catsblock.weather.data.uvAdvice
import com.catsblock.weather.data.uvLevel
import com.catsblock.weather.data.windDescription
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.beaufort
import com.catsblock.weather.ui.cloudAdvice
import com.catsblock.weather.ui.cloudBandLabel
import com.catsblock.weather.ui.dateLabel
import com.catsblock.weather.ui.dayLabel
import com.catsblock.weather.ui.f1
import com.catsblock.weather.ui.glass.snowIntensity
import com.catsblock.weather.ui.hourLabel
import com.catsblock.weather.ui.pressureFormat
import com.catsblock.weather.ui.pressureShort
import com.catsblock.weather.ui.pressureValue
import com.catsblock.weather.ui.tempText
import com.catsblock.weather.ui.toUnit
import com.catsblock.weather.ui.visibilityShort
import com.catsblock.weather.ui.weekdayLabel
import com.catsblock.weather.ui.windShort
import com.catsblock.weather.ui.windText
import com.catsblock.weather.ui.windValue
import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class MetricKind { Precip, Wind, Air, Uv, Humidity, Pressure, Cloud, Visibility }

private class DayData(val date: LocalDate, val hours: List<Hour>)

private class Ctx(
    val today: LocalDate,
    val nowHour: Int,
    val windUnit: WindUnit,
    val pressureUnit: PressureUnit,
    val fahrenheit: Boolean,
    val bundle: WeatherBundle
)

private class TabSpec(
    val label: String,
    val outlookLabel: String,
    val headline: String,
    val sub: String?,
    val accent: Color,
    val floating: Boolean,
    val leadLabel: String,
    val dates: List<LocalDate>,
    val pills: List<Pill>,
    val body: @Composable (Int) -> Unit
)

private val Blue = Color(0xFF64B5F6)
private val IceBlue = Color(0xFFB3E5FC)
private val Cyan = Color(0xFF4DD0E1)
private val Purple = Color(0xFFB39DDB)
private val Green = Color(0xFF66BB6A)
private val Amber = Color(0xFFE5A22B)
private val Orange = Color(0xFFE06C0F)
private val Red = Color(0xFFD13B3B)
private val Violet = Color(0xFF8E4EC6)
private val Maroon = Color(0xFF8A2A47)
private val WindBlue = Color(0xFF90B4F0)
private val CloudGrey = Color(0xFFB0BEC5)
private val PressureViolet = Color(0xFFB39DDB)

private val UvColors = listOf(Color(0xFF3E8E41), Color(0xFFE5A22B), Color(0xFFE06C0F), Color(0xFFC62F2F), Color(0xFF7B2CA3))
private val AqiColors = listOf(Color(0xFF66BB6A), Color(0xFFE6C229), Color(0xFFEF8F2B), Color(0xFFE5484D), Color(0xFF8E4EC6), Color(0xFF8A2A47))
private val VisibilityColors = listOf(Color(0xFFD13B3B), Color(0xFFE0701A), Color(0xFFE6C229), Color(0xFF66BB6A), Color(0xFF4DD0E1))
private val ComfortColors = listOf(Color(0xFF4FC3D6), Color(0xFF4CAF50), Color(0xFFE6A817), Color(0xFFE0701A), Color(0xFFD13B3B))

private val UvBreaks = listOf(3.0, 6.0, 8.0, 11.0)
private val AqiBreaks = listOf(50.0, 100.0, 150.0, 200.0, 300.0)
private val ComfortBreaks = listOf(15.0, 24.0, 28.0, 31.0)
private val VisibilityBreaks = listOf(1.0, 4.0, 10.0, 20.0)

private fun band(value: Double, breaks: List<Double>): Int = breaks.count { value >= it }

private fun groupDays(bundle: WeatherBundle, today: LocalDate): List<DayData> =
    bundle.hours
        .groupBy { it.time.toLocalDate() }
        .entries
        .filter { !it.key.isBefore(today) }
        .sortedBy { it.key }
        .take(7)
        .map { entry -> DayData(entry.key, entry.value.sortedBy { it.time }) }

private fun DayData.series(select: (Hour) -> Double?): List<Float?> =
    List(24) { h -> hours.firstOrNull { it.time.hour == h }?.let(select)?.toFloat() }

private fun DayData.focus(ctx: Ctx): Hour? =
    if (date == ctx.today) hours.firstOrNull { it.time.hour == ctx.nowHour } ?: hours.lastOrNull()
    else hours.firstOrNull { it.time.hour == 12 } ?: hours.firstOrNull()

private fun DayData.marker(ctx: Ctx): Int? = if (date == ctx.today) ctx.nowHour else null

private fun bottomLabel(index: Int, day: DayData, lead: String): String =
    if (index == 0) lead else weekdayLabel(day.date.atStartOfDay())

private fun dewPoint(tempC: Double, humidity: Int): Double {
    val a = 17.62
    val b = 243.12
    val g = ln(humidity.coerceIn(1, 100) / 100.0) + a * tempC / (b + tempC)
    return b * g / (a - g)
}

private fun wetBulb(tempC: Double, humidity: Int): Double {
    val rh = humidity.coerceIn(1, 100).toDouble()
    return tempC * atan(0.151977 * sqrt(rh + 8.313659)) + atan(tempC + rh) - atan(rh - 1.676331) +
        0.00391838 * rh.pow(1.5) * atan(0.023101 * rh) - 4.686035
}

private fun List<Float?>.peak(): Float = filterNotNull().maxOrNull() ?: 0f

@Composable
fun MetricDetailScreen(
    kind: MetricKind,
    bundle: WeatherBundle,
    windUnit: WindUnit,
    pressureUnit: PressureUnit,
    fahrenheit: Boolean,
    onBack: () -> Unit
) {
    val now = bundle.current.now
    val ctx = remember(bundle, windUnit, pressureUnit, fahrenheit) {
        Ctx(now.toLocalDate(), now.hour, windUnit, pressureUnit, fahrenheit, bundle)
    }
    val tabs = remember(kind, ctx) { buildTabs(kind, groupDays(bundle, ctx.today), ctx) }
    if (tabs.isEmpty() || tabs.first().pills.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var dayIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = tabs[tabIndex.coerceIn(0, tabs.lastIndex)]
    val count = tab.pills.size
    val safe = dayIndex.coerceIn(0, count - 1)
    val select: (Int) -> Unit = { dayIndex = it.coerceIn(0, count - 1) }
    val date = tab.dates[safe]
    val lead = if (safe == 0) tab.leadLabel else dayLabel(date, ctx.today)

    DetailDialog(
        title = titleFor(kind),
        onBack = onBack,
        tabs = tabs.map { it.label },
        selectedTab = tabIndex.coerceIn(0, tabs.lastIndex),
        onTab = { tabIndex = it },
        outlook = {
            OutlookCard(tab.outlookLabel, tab.headline, tab.sub, 0) {
                PillChart(tab.pills, safe, select, tab.accent, floating = tab.floating)
            }
        },
        navigator = {
            DateNavigator(
                label = "$lead, ${dateLabel(date)}",
                canPrevious = safe > 0,
                canNext = safe < count - 1,
                onPrevious = { select(safe - 1) },
                onNext = { select(safe + 1) }
            )
        },
        body = {
            AnimatedContent(
                targetState = tabIndex.coerceIn(0, tabs.lastIndex) * 100 + safe,
                transitionSpec = {
                    val dir = if (targetState >= initialState) 1 else -1
                    (slideInHorizontally { it / 5 * dir } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 5 * dir } + fadeOut())
                },
                label = "day-body"
            ) { state ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    tabs[state / 100].body(state % 100)
                }
            }
        }
    )
}

private fun titleFor(kind: MetricKind): String = tr(
    when (kind) {
        MetricKind.Precip -> "Precipitation forecast"
        MetricKind.Wind -> "Wind forecast"
        MetricKind.Air -> "Air quality"
        MetricKind.Uv -> "UV index"
        MetricKind.Humidity -> "Humidity"
        MetricKind.Pressure -> "Pressure forecast"
        MetricKind.Cloud -> "Cloud cover"
        MetricKind.Visibility -> "Visibility"
    }
)

private fun buildTabs(kind: MetricKind, all: List<DayData>, ctx: Ctx): List<TabSpec> = when (kind) {
    MetricKind.Precip -> listOfNotNull(precipTab(all, ctx))
    MetricKind.Wind -> listOfNotNull(windTab(all, ctx))
    MetricKind.Uv -> listOfNotNull(uvTab(all, ctx))
    MetricKind.Air -> listOfNotNull(airTab(all.filter { d -> d.hours.any { it.aqi != null } }, ctx))
    MetricKind.Humidity -> listOfNotNull(humidityTab(all, ctx), comfortTab(all, ctx))
    MetricKind.Pressure -> listOfNotNull(pressureTab(all, ctx))
    MetricKind.Cloud -> listOfNotNull(cloudTab(all.filter { d -> d.hours.any { it.cloud >= 0 } }, ctx))
    MetricKind.Visibility -> listOfNotNull(visibilityTab(all, ctx))
}

private fun precipTab(days: List<DayData>, ctx: Ctx): TabSpec? {
    if (days.isEmpty()) return null
    val totals = days.map { d -> d.hours.sumOf { it.precip } }
    val maxTotal = max(totals.maxOrNull() ?: 0.0, 1.0)
    val chances = days.map { d -> ctx.bundle.days.firstOrNull { it.date == d.date }?.rainChance }
    val lead = tr("Today")
    val pills = days.mapIndexed { i, d ->
        val raw = (totals[i] / maxTotal).toFloat().coerceIn(0f, 1f)
        Pill(
            top = chances[i]?.let { "$it%" } ?: f1(totals[i]),
            bottom = bottomLabel(i, d, lead),
            low = 0f,
            high = if (totals[i] > 0.0) max(raw, 0.08f) else 0f
        )
    }
    return TabSpec(
        label = tr("Precipitation"),
        outlookLabel = tr("Rain outlook"),
        headline = "${f1(totals.sum())} mm ${tr("total")}",
        sub = null,
        accent = Blue,
        floating = false,
        leadLabel = lead,
        dates = days.map { it.date },
        pills = pills,
        body = { i -> PrecipBody(days[i], totals[i], chances[i], ctx) }
    )
}

@Composable
private fun PrecipBody(day: DayData, total: Double, chance: Int?, ctx: Ctx) {
    val series = day.series { it.precip }
    CardGroup(
        listOf(
            {
                GaugeRow(
                    label = tr("Precipitation"),
                    value = "${f1(total)} mm",
                    sub = chance?.let { "$it% ${tr("chance")}" } ?: precipLabel(total)
                ) { ArcGauge((chance ?: 0) / 100f, Blue, center = chance?.let { "$it%" }) }
            },
            { AdviceRow(Icons.Rounded.Umbrella, Blue, precipToday(total)) }
        ),
        baseIndex = 1
    )
    ChartCard(tr("Hourly forecast"), 3) {
        HourlyChart(
            lines = listOf(ChartLine(series, Blue, tr("Precipitation"))),
            style = HourlyStyle.Bars,
            yMin = 0f,
            yMax = max(3f, series.peak() * 1.25f),
            thresholds = listOf(Threshold(2.5f, "2.5 mm")),
            nowHour = day.marker(ctx),
            format = { _, v -> "${f1(v.toDouble())} mm" }
        )
    }
    if (total > 0.0) {
        val snow = day.hours.filter { snowIntensity(it.kind) > 0f }.sumOf { it.precip }
        val rain = (total - snow).coerceAtLeast(0.0)
        ChartCard(tr("Breakdown by type"), 4) {
            Text("${f1(total)} mm", color = MaterialTheme.colorScheme.onSurface, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(12.dp))
            StackedBar(listOf(rain.toFloat() to Blue, snow.toFloat() to IceBlue))
            Spacer(Modifier.size(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (rain > 0.0) Legend(Blue, tr("Rain"), "${f1(rain)} mm")
                if (snow > 0.0) Legend(IceBlue, tr("Snow"), "${f1(snow)} mm")
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = scheme.onSurface, fontWeight = FontWeight.Medium)
            Text(value, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun windTab(days: List<DayData>, ctx: Ctx): TabSpec? {
    if (days.isEmpty()) return null
    val peaks = days.map { d -> d.hours.maxOfOrNull { it.windSpeed } ?: 0.0 }
    val means = days.map { d -> d.hours.map { it.windSpeed }.average().takeIf { !it.isNaN() } ?: 0.0 }
    val weekMax = max(peaks.maxOrNull() ?: 0.0, 10.0)
    val unit = ctx.windUnit
    val lead = tr("Now")
    val pills = days.mapIndexed { i, d ->
        Pill(
            top = "Bft ${beaufort(peaks[i])}",
            bottom = bottomLabel(i, d, lead),
            low = 0f,
            high = max((peaks[i] / weekMax).toFloat().coerceIn(0f, 1f), 0.08f)
        )
    }
    val low = windValue(means.minOrNull() ?: 0.0, unit).roundToInt()
    val high = windValue(peaks.maxOrNull() ?: 0.0, unit).roundToInt()
    return TabSpec(
        label = tr("Wind"),
        outlookLabel = tr("Wind outlook"),
        headline = "$low–$high ${unit.label}",
        sub = windDescription(peaks.maxOrNull() ?: 0.0),
        accent = WindBlue,
        floating = false,
        leadLabel = lead,
        dates = days.map { it.date },
        pills = pills,
        body = { i -> WindBody(days[i], ctx) }
    )
}

@Composable
private fun WindBody(day: DayData, ctx: Ctx) {
    val unit = ctx.windUnit
    val f = day.focus(ctx)
    val speed = f?.windSpeed ?: 0.0
    val dir = f?.windDir ?: 0.0
    val letters = listOf(compassLabel(0.0), compassLabel(90.0), compassLabel(180.0), compassLabel(270.0))
    val series = day.series { windValue(it.windSpeed, unit) }
    val dirs = day.series { it.windDir }
    val moderate = windValue(20.0, unit).toFloat()
    CardGroup(
        listOf({
            GaugeRow(
                label = tr("Wind"),
                value = windText(speed, unit),
                sub = "${windShort(speed)} · ${tr("From")} ${compassLabel(dir)}"
            ) { WindCompass(dir, letters) }
        }),
        baseIndex = 1
    )
    ChartCard(tr("Hourly wind"), 2) {
        HourlyChart(
            lines = listOf(ChartLine(series, WindBlue, tr("Wind"))),
            style = HourlyStyle.Line,
            yMin = 0f,
            yMax = max(moderate * 1.3f, series.peak() * 1.25f),
            thresholds = listOf(Threshold(moderate, "${tr("Moderate")} (${moderate.roundToInt()} ${unit.label})")),
            nowHour = day.marker(ctx),
            format = { _, v -> "${v.roundToInt()} ${unit.label}" },
            arrows = dirs
        )
    }
}

private fun uvTab(days: List<DayData>, ctx: Ctx): TabSpec? {
    if (days.isEmpty()) return null
    val peaks = days.map { d -> d.hours.maxOfOrNull { it.uv } ?: 0.0 }
    val lead = tr("Now")
    val pills = days.mapIndexed { i, d ->
        Pill(
            top = peaks[i].roundToInt().toString(),
            bottom = bottomLabel(i, d, lead),
            low = 0f,
            high = max((peaks[i] / 12.0).toFloat().coerceIn(0f, 1f), 0.08f),
            tint = UvColors[band(peaks[i], UvBreaks)]
        )
    }
    return TabSpec(
        label = tr("UV index"),
        outlookLabel = tr("UV outlook"),
        headline = "${(peaks.minOrNull() ?: 0.0).roundToInt()}–${(peaks.maxOrNull() ?: 0.0).roundToInt()}",
        sub = null,
        accent = UvColors[1],
        floating = false,
        leadLabel = lead,
        dates = days.map { it.date },
        pills = pills,
        body = { i -> UvBody(days[i], ctx) }
    )
}

@Composable
private fun UvBody(day: DayData, ctx: Ctx) {
    val f = day.focus(ctx)
    val peakHour = day.hours.maxByOrNull { it.uv }
    val peak = peakHour?.uv ?: 0.0
    val uv = if (day.date == ctx.today) f?.uv ?: 0.0 else peak
    val tint = UvColors[band(uv, UvBreaks)]
    val series = day.series { it.uv }
    CardGroup(
        listOf(
            {
                val scheme = MaterialTheme.colorScheme
                Text(tr("UV index"), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(4.dp))
                Text(
                    uvLevel(uv),
                    color = scheme.onSurface,
                    fontSize = 40.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (peakHour != null) {
                    Text(
                        "${tr("Peak around")} ${hourLabel(peakHour.time)} · ${peak.roundToInt()}",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.size(18.dp))
                SegmentBar(UvColors, piecewiseFraction(uv, UvBreaks))
            },
            { AdviceRow(Icons.Rounded.Shield, tint, uvAdvice(uv)) }
        ),
        baseIndex = 1
    )
    ChartCard(tr("Hourly UV index"), 3) {
        HourlyChart(
            lines = listOf(ChartLine(series, tint, tr("UV index"))),
            style = HourlyStyle.Line,
            yMin = 0f,
            yMax = max(12f, series.peak() + 1f),
            thresholds = UvBreaks.map { Threshold(it.toFloat(), "${uvLevel(it)} (${it.roundToInt()}.0)") },
            nowHour = day.marker(ctx),
            format = { _, v -> "UV ${f1(v.toDouble())}" },
            bandColors = listOf(0f to UvColors[0], 3f to UvColors[1], 6f to UvColors[2], 8f to UvColors[3], 11f to UvColors[4])
        )
    }
}

private fun airTab(days: List<DayData>, ctx: Ctx): TabSpec? {
    if (days.isEmpty()) return null
    val avgs = days.map { d -> d.hours.mapNotNull { it.aqi }.average() }
    val lead = tr("Now")
    val pills = days.mapIndexed { i, d ->
        Pill(
            top = "AQI ${avgs[i].roundToInt()}",
            bottom = bottomLabel(i, d, lead),
            low = 0f,
            high = max((avgs[i] / 200.0).toFloat().coerceIn(0f, 1f), 0.1f),
            tint = AqiColors[band(avgs[i], AqiBreaks)]
        )
    }
    val first = days.first().focus(ctx)?.aqi ?: avgs.first()
    return TabSpec(
        label = tr("Air quality"),
        outlookLabel = tr("Air quality outlook"),
        headline = "AQI ${first.roundToInt()}",
        sub = aqiStatus(first),
        accent = AqiColors[0],
        floating = false,
        leadLabel = lead,
        dates = days.map { it.date },
        pills = pills,
        body = { i -> AirBody(days[i], avgs[i], ctx) }
    )
}

@Composable
private fun AirBody(day: DayData, avg: Double, ctx: Ctx) {
    val f = day.focus(ctx)
    val aqi = f?.aqi ?: avg
    val tint = AqiColors[band(aqi, AqiBreaks)]
    val series = day.series { it.aqi }
    CardGroup(
        listOf(
            {
                GaugeRow(
                    label = tr("Air quality"),
                    value = "AQI ${aqi.roundToInt()}",
                    sub = aqiStatus(aqi)
                ) { ArcGauge((aqi / 300.0).toFloat(), tint, segments = AqiColors.take(5)) }
            },
            { AdviceRow(Icons.Rounded.Shield, tint, aqiAdvice(aqi), tr("Based on the US Air Quality Index (AQI)")) }
        ),
        baseIndex = 1
    )
    ChartCard(tr("Hourly AQI"), 3) {
        HourlyChart(
            lines = listOf(ChartLine(series, tint, "AQI")),
            style = HourlyStyle.Line,
            yMin = 0f,
            yMax = max(120f, series.peak() * 1.25f),
            thresholds = listOf(Threshold(50f, "${aqiStatus(25.0)} (50)"), Threshold(100f, "${aqiStatus(75.0)} (100)")),
            nowHour = day.marker(ctx),
            format = { _, v -> "AQI ${v.roundToInt()}" },
            bandColors = listOf(0f to AqiColors[0], 50f to AqiColors[1], 100f to AqiColors[2], 150f to AqiColors[3])
        )
    }
    ChartCard(tr("AQI scale"), 4) {
        SegmentBar(AqiColors, piecewiseFraction(aqi, AqiBreaks))
    }
}

private fun humidityTab(days: List<DayData>, ctx: Ctx): TabSpec? {
    if (days.isEmpty()) return null
    val lows = days.map { d -> d.hours.minOfOrNull { it.humidity } ?: 0 }
    val highs = days.map { d -> d.hours.maxOfOrNull { it.humidity } ?: 0 }
    val lead = tr("Today")
    val pills = days.mapIndexed { i, d ->
        Pill(
            top = highs[i].toString(),
            bottom = bottomLabel(i, d, lead),
            low = lows[i] / 100f,
            high = max(highs[i] / 100f, lows[i] / 100f + 0.12f).coerceAtMost(1f)
        )
    }
    return TabSpec(
        label = tr("Humidity"),
        outlookLabel = tr("Humidity outlook"),
        headline = "${lows.minOrNull() ?: 0}%–${highs.maxOrNull() ?: 0}%",
        sub = tr("Variation this week"),
        accent = Cyan,
        floating = true,
        leadLabel = lead,
        dates = days.map { it.date },
        pills = pills,
        body = { i -> HumidityBody(days[i], ctx) }
    )
}

@Composable
private fun HumidityBody(day: DayData, ctx: Ctx) {
    val f = day.focus(ctx)
    val h = f?.humidity ?: day.hours.map { it.humidity }.average().roundToInt()
    val dew = f?.let { dewPoint(it.temp, it.humidity) }
    val humiditySeries = day.series { it.humidity.toDouble() }
    val dewSeries = day.series { dewPoint(it.temp, it.humidity) }
    val dewMin = dewSeries.filterNotNull().minOrNull() ?: 0f
    val dewMax = dewSeries.filterNotNull().maxOrNull() ?: 1f
    CardGroup(
        listOf(
            {
                GaugeRow(
                    label = tr("Humidity"),
                    value = "$h%",
                    sub = dew?.let { "${tr("Dew point")} ${tempText(it, ctx.fahrenheit)}" }
                ) { WaterGauge(h / 100f, Cyan) }
            },
            { AdviceRow(Icons.Rounded.Shield, Green, humidityDescription(h)) }
        ),
        baseIndex = 1
    )
    ChartCard(tr("Humidity and dew point"), 3) {
        HourlyChart(
            lines = listOf(
                ChartLine(humiditySeries, Cyan, tr("Humidity")),
                ChartLine(dewSeries, Purple, tr("Dew point"), (dewMin - 20f)..(dewMax + 15f))
            ),
            style = HourlyStyle.Line,
            yMin = 0f,
            yMax = 100f,
            thresholds = listOf(Threshold(50f, "50%")),
            nowHour = day.marker(ctx),
            format = { index, v -> if (index == 0) "${v.roundToInt()}%" else tempText(v.toDouble(), ctx.fahrenheit) }
        )
    }
}

private fun comfortTab(days: List<DayData>, ctx: Ctx): TabSpec? {
    if (days.isEmpty()) return null
    val peaks = days.map { d -> d.hours.maxOfOrNull { wetBulb(it.temp, it.humidity) } ?: 0.0 }
    val lead = tr("Now")
    val pills = days.mapIndexed { i, d ->
        Pill(
            top = tempText(peaks[i], ctx.fahrenheit),
            bottom = bottomLabel(i, d, lead),
            low = 0f,
            high = max((peaks[i] / 35.0).toFloat().coerceIn(0f, 1f), 0.08f),
            tint = ComfortColors[band(peaks[i], ComfortBreaks)]
        )
    }
    return TabSpec(
        label = tr("Cooling comfort"),
        outlookLabel = tr("Cooling comfort outlook"),
        headline = tempText(peaks.maxOrNull() ?: 0.0, ctx.fahrenheit),
        sub = tr("Peak this week"),
        accent = ComfortColors[1],
        floating = false,
        leadLabel = lead,
        dates = days.map { it.date },
        pills = pills,
        body = { i -> ComfortBody(days[i], ctx) }
    )
}

private fun comfortLabel(index: Int): String = tr(listOf("Cool", "Good", "Reduced", "Difficult", "Dangerous")[index])

private fun comfortAdvice(index: Int): String = tr(
    listOf(
        "Cool conditions. Sweat evaporates quickly, so layers help keep you warm.",
        "Normal cooling conditions. Comfortable for outdoor activities. Stay hydrated as usual.",
        "Cooling is reduced. Take breaks in the shade and drink water regularly.",
        "Cooling is difficult. Limit strenuous activity outdoors and rest often.",
        "Cooling is dangerously low. Avoid strenuous outdoor activity and stay in cool places."
    )[index]
)

@Composable
private fun ComfortBody(day: DayData, ctx: Ctx) {
    val f = day.focus(ctx)
    val tw = f?.let { wetBulb(it.temp, it.humidity) } ?: 0.0
    val idx = band(tw, ComfortBreaks)
    val series = day.series { wetBulb(it.temp, it.humidity).toUnit(ctx.fahrenheit) }
    val reduced = 24.0.toUnit(ctx.fahrenheit).toFloat()
    val difficult = 28.0.toUnit(ctx.fahrenheit).toFloat()
    CardGroup(
        listOf(
            {
                val scheme = MaterialTheme.colorScheme
                Text(tr("Cooling comfort"), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(4.dp))
                Text(comfortLabel(idx), color = scheme.onSurface, fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${tr("Wet-bulb")} ${tempText(tw, ctx.fahrenheit)}",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.size(18.dp))
                SegmentBar(ComfortColors, piecewiseFraction(tw, ComfortBreaks))
            },
            { AdviceRow(Icons.Rounded.Info, ComfortColors[idx], comfortAdvice(idx)) }
        ),
        baseIndex = 1
    )
    ChartCard(tr("Cooling comfort by hour"), 3) {
        HourlyChart(
            lines = listOf(ChartLine(series, ComfortColors[idx], tr("Wet-bulb"))),
            style = HourlyStyle.Line,
            yMin = 0f.toDouble().toUnit(ctx.fahrenheit).toFloat(),
            yMax = 36.0.toUnit(ctx.fahrenheit).toFloat(),
            thresholds = listOf(Threshold(reduced, comfortLabel(2)), Threshold(difficult, comfortLabel(3))),
            nowHour = day.marker(ctx),
            format = { _, v -> tempText(if (ctx.fahrenheit) (v - 32.0) * 5.0 / 9.0 else v.toDouble(), ctx.fahrenheit) }
        )
    }
}

private fun pressureTab(days: List<DayData>, ctx: Ctx): TabSpec? {
    if (days.isEmpty()) return null
    val unit = ctx.pressureUnit
    val avgs = days.map { d -> d.hours.map { it.pressure }.average() }
    val lo = avgs.minOrNull() ?: 1013.0
    val hi = avgs.maxOrNull() ?: 1013.0
    val span = max(hi - lo, 0.0001)
    val lead = tr("Now")
    val pills = days.mapIndexed { i, d ->
        Pill(
            top = pressureFormat(pressureValue(avgs[i], unit), unit),
            bottom = bottomLabel(i, d, lead),
            low = 0f,
            high = (0.10 + 0.90 * ((avgs[i] - lo) / span)).toFloat().coerceIn(0f, 1f)
        )
    }
    return TabSpec(
        label = tr("Pressure"),
        outlookLabel = tr("Pressure outlook"),
        headline = "${pressureFormat(pressureValue(lo, unit), unit)}–${pressureFormat(pressureValue(hi, unit), unit)} ${unit.label}",
        sub = null,
        accent = PressureViolet,
        floating = false,
        leadLabel = lead,
        dates = days.map { it.date },
        pills = pills,
        body = { i -> PressureBody(days[i], ctx) }
    )
}

@Composable
private fun PressureBody(day: DayData, ctx: Ctx) {
    val unit = ctx.pressureUnit
    val f = day.focus(ctx)
    val hpa = f?.pressure ?: day.hours.map { it.pressure }.average()
    val reference = if (day.date == ctx.today) {
        day.hours.firstOrNull { it.time.hour == (ctx.nowHour - 3).coerceAtLeast(0) }?.pressure
    } else {
        day.hours.firstOrNull()?.pressure
    }
    val delta = hpa - (reference ?: hpa)
    val trendText = tr(if (delta > 0.6) "Rising" else if (delta < -0.6) "Falling" else "Steady")
    val trendIcon = if (delta > 0.6) Icons.AutoMirrored.Rounded.TrendingUp else if (delta < -0.6) Icons.AutoMirrored.Rounded.TrendingDown else Icons.AutoMirrored.Rounded.TrendingFlat
    val trendTint = if (delta > 0.6) Green else if (delta < -0.6) Orange else MaterialTheme.colorScheme.onSurfaceVariant
    val series = day.series { pressureValue(it.pressure, unit) }
    val data = series.filterNotNull()
    val pad = (2.0 * unit.factor).toFloat()
    val yMin = (data.minOrNull() ?: 0f) - pad
    val yMax = max((data.maxOrNull() ?: 1f) + pad, yMin + pad * 2f)
    val standard = pressureValue(1013.0, unit).toFloat()
    CardGroup(
        listOf(
            {
                GaugeRow(
                    label = tr("Atmospheric pressure"),
                    value = "${pressureFormat(pressureValue(hpa, unit), unit)} ${unit.label}",
                    sub = trendText,
                    subIcon = trendIcon,
                    subTint = trendTint
                ) { ArcGauge(((hpa - 980.0) / 70.0).toFloat(), Green) }
            },
            { AdviceRow(Icons.Rounded.Info, PressureViolet, pressureShort(hpa)) }
        ),
        baseIndex = 1
    )
    ChartCard(tr("Hourly pressure"), 3) {
        HourlyChart(
            lines = listOf(ChartLine(series, PressureViolet, tr("Pressure"))),
            style = HourlyStyle.Line,
            yMin = min(yMin, standard - pad),
            yMax = max(yMax, standard + pad),
            thresholds = listOf(Threshold(standard, "${tr("Standard")} (${pressureFormat(standard.toDouble(), unit)})")),
            nowHour = day.marker(ctx),
            format = { _, v -> "${pressureFormat(v.toDouble(), unit)} ${unit.label}" }
        )
    }
}

private fun cloudTab(days: List<DayData>, ctx: Ctx): TabSpec? {
    if (days.isEmpty()) return null
    val avgs = days.map { d -> d.hours.filter { it.cloud >= 0 }.map { it.cloud }.average() }
    val lead = tr("Today")
    val pills = days.mapIndexed { i, d ->
        Pill(
            top = "${avgs[i].roundToInt()}%",
            bottom = bottomLabel(i, d, lead),
            low = 0f,
            high = max((avgs[i] / 100.0).toFloat().coerceIn(0f, 1f), 0.08f)
        )
    }
    val overall = avgs.average().roundToInt()
    return TabSpec(
        label = tr("Cloud cover"),
        outlookLabel = tr("Cloud cover outlook"),
        headline = "$overall%",
        sub = cloudBandLabel(overall),
        accent = CloudGrey,
        floating = false,
        leadLabel = lead,
        dates = days.map { it.date },
        pills = pills,
        body = { i -> CloudBody(days[i], avgs[i].roundToInt(), ctx) }
    )
}

@Composable
private fun CloudBody(day: DayData, avg: Int, ctx: Ctx) {
    val f = day.focus(ctx)
    val value = f?.cloud?.takeIf { it >= 0 } ?: avg
    val series = day.series { if (it.cloud >= 0) it.cloud.toDouble() else null }
    CardGroup(
        listOf(
            {
                GaugeRow(
                    label = tr("Cloud cover"),
                    value = "$value%",
                    sub = cloudBandLabel(value)
                ) { ArcGauge(value / 100f, CloudGrey, center = "$value%") }
            },
            { AdviceRow(Icons.Rounded.Cloud, CloudGrey, cloudAdvice(value)) }
        ),
        baseIndex = 1
    )
    ChartCard(tr("Hourly cloud cover"), 3) {
        HourlyChart(
            lines = listOf(ChartLine(series, CloudGrey, tr("Cloud cover"))),
            style = HourlyStyle.Line,
            yMin = 0f,
            yMax = 100f,
            thresholds = listOf(Threshold(50f, "50%")),
            nowHour = day.marker(ctx),
            format = { _, v -> "${v.roundToInt()}%" }
        )
    }
}

private fun visibilityText(km: Double): String = if (km >= 10.0) km.roundToInt().toString() else f1(km)

private fun visibilityLabel(index: Int): String = tr(listOf("Very poor", "Poor", "Moderate", "Good", "Excellent")[index])

private fun visibilityTab(days: List<DayData>, ctx: Ctx): TabSpec? {
    if (days.isEmpty()) return null
    val avgs = days.map { d -> d.hours.map { it.visibility / 1000.0 }.average().takeIf { !it.isNaN() } ?: 0.0 }
    val lead = tr("Now")
    val pills = days.mapIndexed { i, d ->
        Pill(
            top = visibilityText(avgs[i]),
            bottom = bottomLabel(i, d, lead),
            low = 0f,
            high = max((avgs[i] / 20.0).toFloat().coerceIn(0f, 1f), 0.08f),
            tint = VisibilityColors[band(avgs[i], VisibilityBreaks)]
        )
    }
    val first = days.first().focus(ctx)?.let { it.visibility / 1000.0 } ?: avgs.first()
    return TabSpec(
        label = tr("Visibility"),
        outlookLabel = tr("Visibility outlook"),
        headline = "${visibilityText(first)} km",
        sub = visibilityLabel(band(first, VisibilityBreaks)),
        accent = VisibilityColors[3],
        floating = false,
        leadLabel = lead,
        dates = days.map { it.date },
        pills = pills,
        body = { i -> VisibilityBody(days[i], avgs[i], ctx) }
    )
}

@Composable
private fun VisibilityBody(day: DayData, avg: Double, ctx: Ctx) {
    val f = day.focus(ctx)
    val km = f?.let { it.visibility / 1000.0 } ?: avg
    val idx = band(km, VisibilityBreaks)
    val tint = VisibilityColors[idx]
    val series = day.series { it.visibility / 1000.0 }
    CardGroup(
        listOf(
            {
                GaugeRow(
                    label = tr("Visibility"),
                    value = "${visibilityText(km)} km",
                    sub = visibilityLabel(idx)
                ) { VisibilityGauge((km / 20.0).toFloat(), tint) }
            },
            { AdviceRow(Icons.Rounded.Visibility, tint, visibilityShort(km)) }
        ),
        baseIndex = 1
    )
    ChartCard(tr("Hourly visibility"), 3) {
        HourlyChart(
            lines = listOf(ChartLine(series, tint, tr("Visibility"))),
            style = HourlyStyle.Line,
            yMin = 0f,
            yMax = max(12f, series.peak() * 1.2f),
            thresholds = listOf(Threshold(1f, "1 km"), Threshold(10f, "10 km")),
            nowHour = day.marker(ctx),
            format = { _, v -> "${visibilityText(v.toDouble())} km" },
            bandColors = listOf(
                0f to VisibilityColors[0],
                1f to VisibilityColors[1],
                4f to VisibilityColors[2],
                10f to VisibilityColors[3],
                20f to VisibilityColors[4]
            )
        )
    }
    ChartCard(tr("Visibility scale"), 4) {
        SegmentBar(VisibilityColors, piecewiseFraction(km, VisibilityBreaks))
    }
}
