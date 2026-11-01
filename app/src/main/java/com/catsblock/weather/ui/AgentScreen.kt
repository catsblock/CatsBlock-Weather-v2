package com.catsblock.weather.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.weather.data.AgentCard
import com.catsblock.weather.data.AgentDay
import com.catsblock.weather.data.AgentLimits
import com.catsblock.weather.data.AppSettings
import com.catsblock.weather.data.wmoCondition
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.components.ExpressiveLoader
import kotlinx.coroutines.delay
import java.time.LocalDate

private val Suggestions = listOf(
    "What will the weather be like in New York?",
    "Is it going to rain in my city?",
    "What time does the rain start?",
    "What day will the temperature drop?"
)

private const val IdleDelayMs = 5000L
private const val TypeStepMs = 55L
private const val EraseStepMs = 28L
private const val HoldMs = 1800L
private const val PauseMs = 450L

@Composable
fun AgentScreen(vm: WeatherViewModel) {
    val scheme = MaterialTheme.colorScheme
    val chat = vm.agent
    val messages by chat.messages.collectAsStateWithLifecycle()
    val sending by chat.sending.collectAsStateWithLifecycle()
    val city by vm.city.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val canSend = text.isNotBlank() && !sending
    val submit = {
        if (canSend && chat.send(text, city, settings)) text = ""
    }
    val listState = rememberLazyListState()
    val bottomInset = LocalBottomInset.current
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val bottom = if (imeBottom > bottomInset) imeBottom else bottomInset

    LaunchedEffect(messages.size, messages.lastOrNull()?.pending) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Box(
        Modifier.fillMaxSize().background(scheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight()) {
            AgentHeader()
            if (messages.isEmpty()) {
                AgentEmptyState(Modifier.weight(1f).fillMaxWidth())
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        AgentBubbleRow(message, settings, Modifier.animateItem())
                    }
                }
            }
            AgentInputBar(
                text = text,
                onTextChange = { text = clampAgentInput(it) },
                canSend = canSend,
                onSend = { submit() },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom)
            )
        }
    }
}

@Composable
private fun AgentHeader() {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "agent-header")
    val corner by transition.animateFloat(
        initialValue = 50f,
        targetValue = 32f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "corner"
    )
    Row(
        Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(percent = corner.toInt()))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Stars, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            tr("Agent"),
            style = MaterialTheme.typography.headlineMedium,
            color = scheme.onBackground
        )
    }
}

@Composable
private fun AgentEmptyState(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "agent-empty")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Column(
        modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(96.dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                }
                .clip(CircleShape)
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Stars, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            tr("Ask the Agent about the weather"),
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AgentBubbleRow(message: AgentMessage, settings: AppSettings, modifier: Modifier) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        modifier = modifier,
        enter = fadeIn(tween(220)) +
            scaleIn(
                spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                initialScale = 0.86f,
                transformOrigin = TransformOrigin(if (message.fromUser) 1f else 0f, 1f)
            ) +
            slideInVertically(tween(260)) { it / 5 }
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
        ) {
            if (message.fromUser) UserBubble(message.text) else AgentBubble(message, settings)
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.primary,
        contentColor = scheme.onPrimary,
        shape = RoundedCornerShape(24.dp, 24.dp, 6.dp, 24.dp),
        modifier = Modifier.widthIn(max = 300.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun AgentBubble(message: AgentMessage, settings: AppSettings) {
    val scheme = MaterialTheme.colorScheme
    val container = lerp(scheme.primary, Color.Black, 0.74f)
    val content = lerp(scheme.primary, Color.White, 0.9f)
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(24.dp, 24.dp, 24.dp, 6.dp),
        modifier = Modifier.widthIn(max = 340.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (message.pending) {
                ExpressiveLoader(size = 32.dp, color = content)
            } else {
                if (message.text.isNotEmpty()) {
                    Text(
                        if (message.notice) tr(message.text) else message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = content
                    )
                }
                message.cards.forEach { card ->
                    when (card) {
                        is AgentCard.Current -> CurrentWeatherCard(card, settings, content)
                        is AgentCard.Days -> DaysCard(card, settings, content)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardPanel(content: Color, body: @Composable () -> Unit) {
    Surface(
        color = content.copy(alpha = 0.1f),
        contentColor = content,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { body() }
    }
}

@Composable
private fun CardTitle(label: String, place: String, content: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.72f))
        if (place.isNotEmpty()) {
            Text(
                place,
                style = MaterialTheme.typography.titleSmall,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CurrentWeatherCard(card: AgentCard.Current, settings: AppSettings, content: Color) {
    val kind = wmoCondition(card.code, card.windKmh)
    CardPanel(content) {
        CardTitle(tr("Current weather"), card.place, content)
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(kind, card.isDay, Modifier.size(56.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    tempText(card.temp, settings.fahrenheit),
                    style = MaterialTheme.typography.headlineMedium,
                    color = content
                )
                Text(kind.label(card.isDay), style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.8f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(tr("Feels like"), tempText(card.feels, settings.fahrenheit), content, Modifier.weight(1f))
            Stat(tr("Humidity"), "${card.humidity}%", content, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(tr("Wind"), windText(card.windKmh, settings.windUnit), content, Modifier.weight(1f))
            Stat(tr("Precipitation"), precipText(card.precipMm, settings.precipUnit), content, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, content: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleSmall, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DaysCard(card: AgentCard.Days, settings: AppSettings, content: Color) {
    val today = remember { LocalDate.now() }
    CardPanel(content) {
        CardTitle(tr(if (card.history) "Past days" else "Forecast"), card.place, content)
        card.days.forEach { day -> DayRow(day, today, settings, content) }
    }
}

@Composable
private fun DayRow(day: AgentDay, today: LocalDate, settings: AppSettings, content: Color) {
    val kind = wmoCondition(day.code)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            dayLabel(day.date, today),
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        WeatherIcon(kind, true, Modifier.size(30.dp))
        Text(
            "${day.rainChance}%",
            modifier = Modifier.weight(0.8f).padding(start = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = content.copy(alpha = 0.75f),
            maxLines = 1
        )
        Text(
            tempText(day.max, settings.fahrenheit) + " / " + tempText(day.min, settings.fahrenheit),
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.titleSmall,
            color = content,
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}

@Composable
private fun AgentInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    canSend: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var idleTick by remember { mutableIntStateOf(0) }
    var ghost by remember { mutableStateOf<String?>(null) }
    val empty = text.isEmpty()

    LaunchedEffect(empty, idleTick) {
        ghost = null
        if (!empty) return@LaunchedEffect
        delay(IdleDelayMs)
        var last = -1
        while (true) {
            var next = Suggestions.indices.random()
            while (Suggestions.size > 1 && next == last) next = Suggestions.indices.random()
            last = next
            val phrase = tr(Suggestions[next])
            ghost = ""
            for (i in 1..phrase.length) {
                ghost = phrase.take(i)
                delay(TypeStepMs)
            }
            delay(HoldMs)
            for (i in phrase.length - 1 downTo 0) {
                ghost = phrase.take(i)
                delay(EraseStepMs)
            }
            delay(PauseMs)
        }
    }

    val count = text.codePointCount(0, text.length)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = scheme.surfaceContainerHigh,
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f).padding(vertical = 6.dp), contentAlignment = Alignment.CenterStart) {
                    if (empty) {
                        Text(
                            ghost ?: tr("Ask the Agent about the weather"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            idleTick++
                            onTextChange(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(onSend = { onSend() })
                    )
                }
                if (!empty) {
                    Text(
                        "$count/${AgentLimits.MAX_CHARS}",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (count >= AgentLimits.MAX_CHARS) scheme.error else scheme.onSurfaceVariant
                    )
                }
                SendButton(canSend, onSend)
            }
        }
        Text(
            tr("AI may provide uninformed or incorrect answers. Always check the weather forecast to ensure that rain, storms, and other weather conditions are expected."),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (enabled) scheme.primary else scheme.onSurface.copy(alpha = 0.12f),
        tween(200),
        label = "sendContainer"
    )
    val tint by animateColorAsState(
        if (enabled) scheme.onPrimary else scheme.onSurface.copy(alpha = 0.38f),
        tween(200),
        label = "sendTint"
    )
    val scale by animateFloatAsState(
        if (enabled) 1f else 0.92f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "sendScale"
    )
    Box(
        Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.ArrowUpward, contentDescription = tr("Send"), tint = tint, modifier = Modifier.size(24.dp))
    }
}
