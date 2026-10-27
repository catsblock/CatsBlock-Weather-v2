package com.catsblock.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catsblock.weather.data.CloudBand
import com.catsblock.weather.data.Hour
import com.catsblock.weather.data.cloudBand
import com.catsblock.weather.i18n.tr
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

private const val CHART_END_HOUR = 20

fun cloudBandLabel(percent: Int): String = tr(
    when (cloudBand(percent)) {
        CloudBand.Clear -> "Clear sky"
        CloudBand.Few -> "Few clouds"
        CloudBand.Partly -> "Partly cloudy"
        CloudBand.Mostly -> "Predominantly cloudy"
        CloudBand.Overcast -> "Overcast"
    }
)

fun cloudAdvice(percent: Int): String = tr(
    when (cloudBand(percent)) {
        CloudBand.Clear -> "Almost no clouds. Direct sunlight is strong, so sun protection matters even when it feels cool."
        CloudBand.Few -> "Only thin, scattered clouds. Sunlight stays bright with soft, brief shade."
        CloudBand.Partly -> "Sun and clouds alternate. Light will shift between bright and soft throughout the day."
        CloudBand.Mostly -> "Clouds cover most of the sky and filter the sun. Light is soft, with only short bright breaks."
        CloudBand.Overcast -> "Dense cloud cover blocking most direct sun. Natural light will be soft and even throughout the day."
    }
)

fun cloudHistory(hours: List<Hour>, today: LocalDate, limit: Int = 7): List<HistoryDay> =
    hours
        .filter { it.cloud >= 0 && !it.time.toLocalDate().isBefore(today) }
        .groupBy { it.time.toLocalDate() }
        .entries
        .take(limit)
        .map { (date, list) ->
            HistoryDay(
                date = date,
                low = list.minOf { it.cloud },
                high = list.maxOf { it.cloud },
                avg = list.map { it.cloud }.average().roundToInt()
            )
        }

private fun chartEnd(now: LocalDateTime): LocalDateTime =
    if (now.hour < CHART_END_HOUR) now.toLocalDate().atTime(CHART_END_HOUR, 0)
    else now.toLocalDate().plusDays(1).atTime(CHART_END_HOUR, 0)

private fun cloudSeries(hours: List<Hour>, now: LocalDateTime): FloatArray {
    val start = now.withMinute(0).withSecond(0).withNano(0)
    val end = chartEnd(now)
    return hours
        .filter { it.cloud >= 0 && !it.time.isBefore(start) && !it.time.isAfter(end) }
        .map { it.cloud.toFloat() }
        .toFloatArray()
}

private fun Modifier.cloudChart(values: FloatArray, line: Color, fillTop: Color, fillBottom: Color): Modifier =
    drawWithCache {
        val count = values.size
        if (count < 2) {
            onDrawBehind { }
        } else {
            val pad = 4.dp.toPx()
            val w = size.width
            val h = size.height
            val xs = FloatArray(count) { w * it / (count - 1) }
            val ys = FloatArray(count) { pad + (h - 2f * pad) * (1f - values[it].coerceIn(0f, 100f) / 100f) }
            val path = Path().apply {
                moveTo(xs[0], ys[0])
                for (i in 1 until count) {
                    val mid = (xs[i - 1] + xs[i]) / 2f
                    cubicTo(mid, ys[i - 1], mid, ys[i], xs[i], ys[i])
                }
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            val fillBrush = Brush.verticalGradient(listOf(fillTop, fillBottom), 0f, h)
            val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val dot = Offset(xs[0], ys[0])
            val dotRadius = 3.5f.dp.toPx()
            onDrawBehind {
                drawPath(fill, fillBrush)
                drawPath(path, line, style = stroke)
                drawCircle(Color.White, dotRadius, dot)
            }
        }
    }

@Composable
fun CloudCoverCard(
    percent: Int,
    hours: List<Hour>,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val series = remember(hours, now) { cloudSeries(hours, now) }
    val end = remember(now) { chartEnd(now) }
    GlassCard(modifier.height(176.dp), onClick, Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CardHeader(Icons.Rounded.Cloud, tr("Cloud cover"))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 44.sp,
                        lineHeight = 52.sp
                    ),
                    maxLines = 1
                )
                Text(
                    cloudBandLabel(percent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            if (series.size >= 2) {
                Column(Modifier.weight(1f)) {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .cloudChart(
                                series,
                                Color.White.copy(alpha = 0.95f),
                                Color.White.copy(alpha = 0.30f),
                                Color.White.copy(alpha = 0.02f)
                            )
                    )
                    Text(
                        "${tr("Until")} ${clockLabel(end)}",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
