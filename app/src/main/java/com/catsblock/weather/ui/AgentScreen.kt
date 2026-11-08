
@file:OptIn(ExperimentalLayoutApi::class)

package com.catsblock.weather.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbDownOffAlt
import androidx.compose.material.icons.outlined.ThumbUpOffAlt
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.weather.data.AgentCard
import com.catsblock.weather.data.AgentDay
import com.catsblock.weather.data.AgentLimits
import com.catsblock.weather.data.AgentMessages
import com.catsblock.weather.data.AgentStep
import com.catsblock.weather.data.AppSettings
import com.catsblock.weather.data.FeedbackRating
import com.catsblock.weather.data.FeedbackReason
import com.catsblock.weather.data.StepStatus
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

private const val HIDDEN_STEP = "limiter"
private const val IdleDelayMs = 5000L
private const val TypeStepMs = 55L
private const val EraseStepMs = 28L
private const val HoldMs = 1800L
private const val PauseMs = 450L

private data class FeedbackTarget(val messageId: Long, val rating: FeedbackRating)

private enum class BarState { WorkersLimit, Checking, Unavailable, Limited, Input }

private fun stepLabel(id: String): String = tr(
    when (id) {
        "region" -> "Checking region"
        "validate" -> "Validating message"
        "locate" -> "Locating place"
        "weather" -> "Fetching weather data"
        "model" -> "Thinking"
        "format" -> "Preparing reply"
        else -> "Working"
    }
)

private fun liveLabel(steps: List<AgentStep>): String {
    val running = steps.lastOrNull { it.status == StepStatus.Run && it.id != HIDDEN_STEP }
    return if (running != null) stepLabel(running.id) else tr("Thinking")
}

@Composable
fun AgentScreen(vm: WeatherViewModel) {
    val scheme = MaterialTheme.colorScheme
    val chat = vm.agent
    val messages by chat.messages.collectAsStateWithLifecycle()
    val city by vm.city.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val blockedUntil by chat.blockedUntil.collectAsStateWithLifecycle()
    val gate by chat.gate.collectAsStateWithLifecycle()
    val adminRequested by chat.adminRequested.collectAsStateWithLifecycle()
    val limited = blockedUntil > 0L
    val canSend = text.isNotBlank() && gate == AgentGate.Open && !limited
    val submit = {
        if (canSend && chat.send(text, city, settings)) text = ""
    }
    var feedbackTarget by remember { mutableStateOf<FeedbackTarget?>(null) }
    val listState = rememberLazyListState()
    val bottomInset = LocalBottomInset.current
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val bottom = if (imeBottom > bottomInset) imeBottom else bottomInset

    if (adminRequested) AdminPanel(onDismiss = { chat.adminRequested.value = false })

    feedbackTarget?.let { target ->
        FeedbackDialog(
            rating = target.rating,
            onDismiss = { feedbackTarget = null },
            onSubmit = { reasons, done ->
                chat.submitFeedback(target.messageId, target.rating, reasons, settings.language.code) { sent ->
                    if (sent) feedbackTarget = null
                    done(sent)
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        chat.refreshAvailability()
    }
    LaunchedEffect(blockedUntil) {
        if (blockedUntil > 0L) {
            val wait = blockedUntil - System.currentTimeMillis()
            if (wait > 0L) delay(wait)
            chat.clearBlock()
        }
    }

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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        AgentBubbleRow(
                            message,
                            settings,
                            { rating -> feedbackTarget = FeedbackTarget(message.id, rating) },
                            Modifier.animateItem()
                        )
                    }
                }
            }
            val barModifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom)
            val barState = when {
                gate == AgentGate.WorkersLimit -> BarState.WorkersLimit
                gate == AgentGate.Checking -> BarState.Checking
                gate == AgentGate.Unavailable -> BarState.Unavailable
                limited -> BarState.Limited
                else -> BarState.Input
            }
            AnimatedContent(
                targetState = barState,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 }) togetherWith fadeOut(tween(120))
                },
                label = "agent-bar"
            ) { state ->
                when (state) {
                    BarState.WorkersLimit -> AgentWorkersLimitBar(barModifier)
                    BarState.Checking -> AgentCheckingBar(barModifier)
                    BarState.Unavailable -> AgentUnavailableBar(barModifier)
                    BarState.Limited -> AgentLimitBar(blockedUntil, barModifier)
                    BarState.Input -> AgentInputBar(
                        text = text,
                        onTextChange = { text = clampAgentInput(it) },
                        canSend = canSend,
                        sending = false,
                        onSend = { submit() },
                        modifier = barModifier
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentHeader() {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "agent-header")
    val corner by transition.animateFloat(
        initialValue = 50f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "corner"
    )
    Row(
        Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(percent = corner.toInt()))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Stars, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            tr("Agent"),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
            color = scheme.onBackground,
            maxLines = 1
        )
    }
}

@Composable
private fun AgentLimitBar(blockedUntil: Long, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    var remainingSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(blockedUntil) {
        while (true) {
            val remaining = ((blockedUntil - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            remainingSeconds = remaining
            if (remaining == 0L) break
            delay(1000L)
        }
    }

    val minutes = remainingSeconds / 60L
    val seconds = remainingSeconds % 60L
    val countdown = if (minutes > 0L) {
        "%d:%02d".format(minutes, seconds)
    } else {
        "%ds".format(seconds)
    }

    Box(
        modifier
            .fillMaxWidth()
            .background(scheme.secondaryContainer, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                tr(AgentMessages.LIMIT),
                color = scheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Text(
                countdown,
                modifier = Modifier.padding(top = 4.dp),
                color = scheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AgentUnavailableBar(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .background(scheme.errorContainer, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            tr(AgentMessages.UNAVAILABLE),
            color = scheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AgentWorkersLimitBar(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .background(scheme.errorContainer, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            tr(AgentMessages.WORKERS_LIMIT),
            color = scheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AgentCheckingBar(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveLoader(size = 40.dp, contained = true)
        Spacer(Modifier.width(12.dp))
        Text(
            tr("Checking Agent availability"),
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
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
    val corner by transition.animateFloat(
        initialValue = 50f,
        targetValue = 32f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "emptyCorner"
    )
    val enter = remember { MutableTransitionState(false).apply { targetState = true } }
    Column(
        modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visibleState = enter,
            enter = fadeIn(tween(420)) + scaleIn(spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow), initialScale = 0.7f)
        ) {
        Box(
            Modifier
                .size(120.dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                }
                .clip(RoundedCornerShape(percent = corner.toInt()))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Stars, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(64.dp))
        }
        }
        Spacer(Modifier.height(24.dp))
        AnimatedVisibility(
            visibleState = enter,
            enter = fadeIn(tween(420, delayMillis = 180)) + slideInVertically(tween(420, delayMillis = 180)) { it / 2 }
        ) {
            Text(
                tr("Ask the Agent about the weather"),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AgentBubbleRow(
    message: AgentMessage,
    settings: AppSettings,
    onFeedback: (FeedbackRating) -> Unit,
    modifier: Modifier
) {
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
            if (message.fromUser) {
                UserBubble(message.text, message.error)
            } else {
                Column(horizontalAlignment = Alignment.Start) {
                    AgentBubble(message, settings)
                    if (!message.pending && !message.notice && message.text.isNotEmpty()) {
                        MessageActions(message, onFeedback)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String, error: String?) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 300.dp)) {
        Surface(
            color = scheme.primary,
            contentColor = scheme.onPrimary,
            shape = RoundedCornerShape(28.dp, 28.dp, 8.dp, 28.dp)
        ) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
        }
        if (error != null) {
            Text(
                tr(error),
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.error,
                textAlign = TextAlign.End
            )
        }
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
        shape = RoundedCornerShape(28.dp, 28.dp, 28.dp, 8.dp),
        modifier = Modifier.widthIn(max = 360.dp)
    ) {
        AnimatedContent(
            targetState = message.pending,
            transitionSpec = { fadeIn(tween(240, delayMillis = 80)) togetherWith fadeOut(tween(80)) },
            label = "bubble-state"
        ) { pending ->
            Column(
                Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (pending) {
                    WorkingIndicator(message.steps, content)
                } else {
                    if (message.text.isNotEmpty()) {
                        Text(
                            if (message.notice) tr(message.text) else message.text,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, lineHeight = 24.sp),
                            color = content
                        )
                    }
                    message.cards.forEachIndexed { index, card ->
                        StaggerIn(index) {
                            when (card) {
                                is AgentCard.Current -> CurrentWeatherCard(card, settings, content)
                                is AgentCard.Days -> DaysCard(card, settings, content)
                                is AgentCard.Metric -> MetricCard(card, settings, content)
                                is AgentCard.MapView -> AgentMapCard(card, settings, content)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkingIndicator(steps: List<AgentStep>, content: Color) {
    Row(
        Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveLoader(size = 44.dp, contained = true)
        Spacer(Modifier.width(12.dp))
        AnimatedContent(
            targetState = liveLabel(steps),
            transitionSpec = {
                (fadeIn(tween(200)) + slideInVertically(tween(240)) { it / 2 }) togetherWith
                    (fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 2 })
            },
            label = "working-label"
        ) { label ->
            Text(
                label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun CardPanel(content: Color, body: @Composable () -> Unit) {
    Surface(
        color = content.copy(alpha = 0.1f),
        contentColor = content,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { body() }
    }
}

@Composable
internal fun CardTitle(label: String, place: String, content: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.72f))
        if (place.isNotEmpty()) {
            Text(
                place,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
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
internal fun Stat(label: String, value: String, content: Color, modifier: Modifier) {
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
    sending: Boolean,
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
            shape = RoundedCornerShape(36.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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
                AnimatedVisibility(
                    visible = !empty,
                    enter = fadeIn(tween(160)) + expandHorizontally(tween(200)),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(tween(160))
                ) {
                    Text(
                        "$count/${AgentLimits.MAX_CHARS}",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (count >= AgentLimits.MAX_CHARS) scheme.error else scheme.onSurfaceVariant
                    )
                }
                SendButton(canSend, sending, onSend)
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
private fun SendButton(enabled: Boolean, sending: Boolean, onClick: () -> Unit) {
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
    val corner by animateFloatAsState(
        if (enabled) 32f else 50f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "sendCorner"
    )
    Box(
        Modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(percent = corner.toInt()))
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (sending) {
            CircularProgressIndicator(Modifier.size(22.dp), color = scheme.primary, strokeWidth = 2.5.dp)
        } else {
            Icon(Icons.Rounded.ArrowUpward, contentDescription = tr("Send"), tint = tint, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun StaggerIn(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(90L + index * 90L)
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 6 } + expandVertically(tween(300))
    ) { content() }
}

private fun copyText(context: Context, text: String): Boolean {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
    return runCatching { manager.setPrimaryClip(ClipData.newPlainText(tr("Agent"), text)) }.isSuccess
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, tr("Share")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
    }
}

@Composable
private fun MessageActions(message: AgentMessage, onFeedback: (FeedbackRating) -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600L)
            copied = false
        }
    }
    val rating = message.rating
    val rated = rating != null
    val canChange = message.feedbackCount < FEEDBACK_MAX_SUBMISSIONS
    val idle = scheme.onSurfaceVariant
    Row(
        Modifier.padding(start = 4.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionIcon(
            index = 0,
            icon = if (rating == FeedbackRating.Good) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUpOffAlt,
            description = tr("Good response"),
            tint = when {
                rating == FeedbackRating.Good -> scheme.primary
                else -> idle
            },
            enabled = canChange && rating != FeedbackRating.Good,
            selected = rating == FeedbackRating.Good
        ) { onFeedback(FeedbackRating.Good) }
        ActionIcon(
            index = 1,
            icon = if (rating == FeedbackRating.Bad) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDownOffAlt,
            description = tr("Bad response"),
            tint = when {
                rating == FeedbackRating.Bad -> scheme.error
                else -> idle
            },
            enabled = canChange && rating != FeedbackRating.Bad,
            selected = rating == FeedbackRating.Bad
        ) { onFeedback(FeedbackRating.Bad) }
        ActionIcon(
            index = 2,
            icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
            description = tr(if (copied) "Copied" else "Copy"),
            tint = if (copied) scheme.primary else idle
        ) {
            if (copyText(context, message.text)) copied = true
        }
        ActionIcon(
            index = 3,
            icon = Icons.Rounded.Share,
            description = tr("Share"),
            tint = idle
        ) { shareText(context, message.text) }
        AnimatedVisibility(
            visible = rated,
            modifier = Modifier.weight(1f, fill = false),
            enter = fadeIn(tween(300)) + expandHorizontally(tween(300)),
            exit = fadeOut(tween(150))
        ) {
            Text(
                tr("Thank you for your feedback."),
                modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = idle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActionIcon(
    index: Int,
    icon: ImageVector,
    description: String,
    tint: Color,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(120L + index * 70L)
        shown = true
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed) 0.82f else 1f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "actionPress"
    )
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            pop.animateTo(1.35f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow))
        }
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.4f)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interaction,
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer {
                    scaleX = press * pop.value
                    scaleY = press * pop.value
                }
        ) {
            Crossfade(targetState = icon, animationSpec = tween(180), label = "actionIcon") { current ->
                Icon(current, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun FeedbackDialog(
    rating: FeedbackRating,
    onDismiss: () -> Unit,
    onSubmit: (Set<FeedbackReason>, (Boolean) -> Unit) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val good = rating == FeedbackRating.Good
    val reasons = remember(rating) { FeedbackReason.forRating(rating) }
    var selected by remember { mutableStateOf(emptySet<FeedbackReason>()) }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        icon = {
            Icon(
                if (good) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbDown,
                contentDescription = null,
                tint = if (good) scheme.primary else scheme.error
            )
        },
        title = { Text(tr(if (good) "Why was this response good?" else "Why was this response bad?")) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(tr("Select all that apply."), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    reasons.forEachIndexed { index, reason ->
                        ReasonChip(reason, index, reason in selected, !sending) {
                            selected = if (reason in selected) selected - reason else selected + reason
                            failed = false
                        }
                    }
                }
                Text(
                    tr("Your question and this reply will be sent to the administrators."),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant
                )
                AnimatedVisibility(visible = failed, enter = fadeIn(tween(200)) + expandVertically(tween(200))) {
                    Text(tr("Feedback could not be sent. Try again."), style = MaterialTheme.typography.labelLarge, color = scheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!sending && selected.isNotEmpty()) {
                        sending = true
                        failed = false
                        onSubmit(selected) { sent ->
                            sending = false
                            if (!sent) failed = true
                        }
                    }
                },
                enabled = selected.isNotEmpty()
            ) {
                AnimatedContent(targetState = sending, label = "submit-state") { busy ->
                    if (busy) {
                        ExpressiveLoader(size = 28.dp, color = scheme.onPrimary)
                    } else {
                        Text(tr("Submit"))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sending) { Text(tr("Cancel")) }
        }
    )
}

@Composable
private fun ReasonChip(reason: FeedbackReason, index: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.8f)
    ) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            label = { Text(tr(reason.label)) },
            leadingIcon = if (selected) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else {
                null
            },
            modifier = Modifier.animateContentSize()
        )
    }
}
