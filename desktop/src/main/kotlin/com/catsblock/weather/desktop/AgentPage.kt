package com.catsblock.weather.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Suggestions = listOf(
    "What will the weather be like in New York?",
    "Is it going to rain in my city?",
    "What time does the rain start?",
    "What day will the temperature drop?"
)

private const val RecheckMs = 60_000L

@Composable
fun AgentGlyph(tint: Color, size: androidx.compose.ui.unit.Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val bubble = Path().apply {
            moveTo(4f * u, 5f * u); lineTo(20f * u, 5f * u); lineTo(20f * u, 16f * u)
            lineTo(11f * u, 16f * u); lineTo(6f * u, 20f * u); lineTo(6f * u, 16f * u); lineTo(4f * u, 16f * u); close()
        }
        drawPath(bubble, tint, style = Stroke(width = 2f * u, cap = StrokeCap.Round))
        drawLine(tint, Offset(8f * u, 9.5f * u), Offset(16f * u, 9.5f * u), 2f * u, StrokeCap.Round)
        drawLine(tint, Offset(8f * u, 12.5f * u), Offset(13f * u, 12.5f * u), 2f * u, StrokeCap.Round)
    }
}

private fun stepLabel(id: String): String = ta(
    when (id) {
        "region" -> "Checking region"
        "limiter" -> "Checking request limit"
        "validate" -> "Validating message"
        "locate" -> "Locating place"
        "weather" -> "Fetching weather data"
        "model" -> "Thinking"
        "format" -> "Preparing reply"
        else -> "Working"
    }
)

private fun liveLabel(steps: List<AgentStep>): String {
    val running = steps.lastOrNull { it.status == StepStatus.Run }
    return if (running != null) stepLabel(running.id) else ta("Working")
}

@Composable
fun AgentPage(chat: AgentChat, settings: DesktopSettings, onOpenMap: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val messages by chat.messages.collectAsState()
    val gate by chat.gate.collectAsState()
    val blockedUntil by chat.blockedUntil.collectAsState()
    val sending by chat.sending.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        chat.refreshAvailability()
        while (true) {
            delay(RecheckMs)
            if (chat.gate.value != AgentGate.Open) chat.refreshAvailability()
        }
    }
    LaunchedEffect(messages.size, messages.lastOrNull()?.steps?.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 880.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(ta("Agent"), style = MaterialTheme.typography.headlineMedium)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    EmptyState(gate == AgentGate.Open && blockedUntil <= System.currentTimeMillis() && settings.hasCity) { text ->
                        chat.send(text, settings.city, settings)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(messages, key = { it.id }) { message -> MessageRow(message, settings, onOpenMap) }
                    }
                }
            }
            when {
                !settings.hasCity -> Notice(ta("Choose a city to begin"), scheme.secondaryContainer, scheme.onSecondaryContainer)
                gate == AgentGate.Checking -> Notice(ta("Checking Agent availability"), scheme.secondaryContainer, scheme.onSecondaryContainer)
                gate == AgentGate.Unavailable -> Notice(ta(AgentMessages.UNAVAILABLE), scheme.errorContainer, scheme.onErrorContainer)
                gate == AgentGate.WorkersLimit -> Notice(ta(AgentMessages.WORKERS_LIMIT), scheme.errorContainer, scheme.onErrorContainer)
                blockedUntil > System.currentTimeMillis() -> LimitBar(blockedUntil) { chat.clearBlock() }
                else -> InputBar(sending) { text -> chat.send(text, settings.city, settings) }
            }
            Text(
                ta("AI may provide uninformed or incorrect answers. Always check the weather forecast to ensure that rain, storms, and other weather conditions are expected."),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EmptyState(enabled: Boolean, onPick: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(ta("Ask the Agent about the weather"), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Suggestions.forEach { key ->
            val label = ta(key)
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().clip(SmallShape)
                    .clickable(enabled = enabled) { onPick(label) }.padding(vertical = 8.dp, horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun Notice(text: String, background: Color, content: Color) {
    Box(Modifier.fillMaxWidth().clip(CardShape).background(background).padding(horizontal = 20.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
        Text(text, color = content, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LimitBar(blockedUntil: Long, onExpired: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var seconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(blockedUntil) {
        while (true) {
            val left = ((blockedUntil - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            seconds = left
            if (left == 0L) {
                onExpired()
                break
            }
            delay(1000L)
        }
    }
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val rest = seconds % 60L
    val countdown = if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
    Box(Modifier.fillMaxWidth().clip(CardShape).background(scheme.secondaryContainer).padding(horizontal = 20.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(ta(AgentMessages.LIMIT), color = scheme.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Text(countdown, Modifier.padding(top = 4.dp), color = scheme.onSecondaryContainer, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InputBar(sending: Boolean, onSend: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    fun submit() {
        if (text.isBlank()) return
        if (onSend(text)) text = ""
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = clampAgentInput(it) },
            modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                    submit()
                    true
                } else false
            },
            singleLine = true,
            shape = SmallShape,
            placeholder = { Text(ta("Ask the Agent about the weather"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingText = { Text("${text.codePointCount(0, text.length)}/${AgentLimits.MAX_CHARS}") }
        )
        Button(onClick = ::submit, enabled = text.isNotBlank(), shape = SmallShape) {
            if (sending) {
                CircularProgressIndicator(Modifier.size(16.dp).padding(end = 0.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.size(8.dp))
            }
            Text(ta("Send"))
        }
    }
}

@Composable
private fun MessageRow(message: AgentMessage, settings: DesktopSettings, onOpenMap: () -> Unit) {
    if (message.fromUser) UserBubble(message) else AgentBubble(message, settings, onOpenMap)
}

@Composable
private fun UserBubble(message: AgentMessage) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.widthIn(max = 560.dp).clip(CardShape).background(scheme.primaryContainer).padding(horizontal = 16.dp, vertical = 12.dp)) {
            SelectionContainer { Text(message.text, color = scheme.onPrimaryContainer, style = MaterialTheme.typography.bodyLarge) }
        }
        message.error?.let { Text(ta(it), color = scheme.error, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End) }
    }
}

@Composable
private fun AgentBubble(message: AgentMessage, settings: DesktopSettings, onOpenMap: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container = if (message.notice) scheme.secondaryContainer else scheme.surfaceContainerHigh
    val content = if (message.notice) scheme.onSecondaryContainer else scheme.onSurface
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (message.pending) {
            Row(Modifier.clip(CardShape).background(container).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = content)
                Text(liveLabel(message.steps), color = content, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            if (message.text.isNotEmpty()) {
                Box(Modifier.widthIn(max = 640.dp).clip(CardShape).background(container).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    SelectionContainer {
                        Text(if (message.notice) ta(message.text) else message.text, color = content, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            message.cards.forEach { card -> CardView(card, settings, onOpenMap) }
        }
    }
}

@Composable
private fun CardPanel(body: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().clip(CardShape).background(scheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { body() }
}

@Composable
private fun CardTitle(label: String, place: String) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, fontWeight = FontWeight.SemiBold)
        if (place.isNotBlank()) Text(place, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CardView(card: AgentCard, settings: DesktopSettings, onOpenMap: () -> Unit) {
    when (card) {
        is AgentCard.Current -> CurrentCard(card, settings)
        is AgentCard.Days -> DaysCard(card, settings)
        is AgentCard.Series -> SeriesCard(card, settings)
        is AgentCard.MapView -> CardPanel {
            CardTitle(ta("Weather map"), card.place)
            TextButton(onClick = onOpenMap) { Text(ta("Weather map")) }
        }
    }
}

@Composable
private fun CurrentCard(card: AgentCard.Current, settings: DesktopSettings) {
    val scheme = MaterialTheme.colorScheme
    val kind = kindOf(card.code, card.windKmh)
    CardPanel {
        CardTitle(ta("Current weather"), card.place)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WeatherIcon(kind, card.isDay, 56.dp)
            Column {
                Text(formatTemp(card.temp, settings.fahrenheit), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = scheme.onSurface)
                Text(kind.label(card.isDay), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(ta("Feels like"), formatTemp(card.feels, settings.fahrenheit), Modifier.weight(1f))
            Stat(ta("Humidity"), "${card.humidity}%", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(ta("Wind"), formatWind(card.windKmh, settings.windUnit), Modifier.weight(1f))
            Stat(ta("Precipitation"), formatPrecip(card.precipMm, settings.precipUnit), Modifier.weight(1f))
        }
    }
}

@Composable
private fun DaysCard(card: AgentCard.Days, settings: DesktopSettings) {
    val scheme = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    CardPanel {
        CardTitle(ta(if (card.history) "Past days" else "Forecast"), card.place)
        card.days.forEach { day ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(dayText(day.date, today), Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                WeatherIcon(kindOf(day.code), true, 30.dp)
                Text("${day.rainChance}%", Modifier.weight(0.8f).padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
                Text(
                    formatTemp(day.max, settings.fahrenheit) + " / " + formatTemp(day.min, settings.fahrenheit),
                    Modifier.weight(1.4f), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, textAlign = TextAlign.End, maxLines = 1
                )
            }
        }
    }
}

private fun dayText(date: LocalDate, today: LocalDate): String =
    if (date == today) t("Today") else date.format(DateTimeFormatter.ofPattern("EEE, d MMM", uiLocale()))

private fun metricText(metric: AgentMetric, value: Double, settings: DesktopSettings): String = when (metric) {
    AgentMetric.Humidity, AgentMetric.Clouds -> "${value.toInt()}%"
    AgentMetric.Wind -> formatWind(value, settings.windUnit)
    AgentMetric.Pressure -> formatPressure(value, settings.pressureUnit)
    AgentMetric.Uv -> "%.1f".format(uiLocale(), value)
    AgentMetric.Rain -> formatPrecip(value, settings.precipUnit)
    AgentMetric.Temperature -> formatTemp(value, settings.fahrenheit)
}

@Composable
private fun SeriesCard(card: AgentCard.Series, settings: DesktopSettings) {
    val scheme = MaterialTheme.colorScheme
    CardPanel {
        CardTitle(ta(card.metric.title), card.place)
        Text(metricText(card.metric, card.now, settings), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = scheme.onSurface)
        SeriesChart(card, scheme.primary, scheme.outlineVariant, scheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(ta("3 days ago"), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text(ta("Next 24 hours"), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(ta("Lowest"), metricText(card.metric, card.min, settings), Modifier.weight(1f))
            Stat(ta("Average"), metricText(card.metric, card.avg, settings), Modifier.weight(1f))
            Stat(ta("Highest"), metricText(card.metric, card.max, settings), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SeriesChart(card: AgentCard.Series, line: Color, grid: Color, marker: Color) {
    val values = (card.past + card.now + card.ahead).filter { it.isFinite() }
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        if (values.size < 2) return@Canvas
        val hi = values.max()
        val lo = if (card.metric.zeroBased) minOf(0.0, values.min()) else values.min()
        val span = (hi - lo).takeIf { it > 1e-9 } ?: 1.0
        val pad = 6f
        fun x(i: Int) = pad + (size.width - 2 * pad) * i / (values.size - 1)
        fun y(v: Double) = pad + (size.height - 2 * pad) * (1f - ((v - lo) / span).toFloat())
        drawLine(grid, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        drawPath(path, line, style = Stroke(width = 3f, cap = StrokeCap.Round))
        val nowIndex = card.past.count { it.isFinite() }
        drawLine(marker, Offset(x(nowIndex), 0f), Offset(x(nowIndex), size.height), 1.5f)
        drawCircle(line, 6f, Offset(x(nowIndex), y(card.now)))
    }
}
