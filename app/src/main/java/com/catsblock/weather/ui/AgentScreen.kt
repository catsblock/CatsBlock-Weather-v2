package com.catsblock.weather.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.CircularProgressIndicator
import androidx.lifecycle.Lifecycle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
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

private const val IdleDelayMs = 5000L
private const val TypeStepMs = 55L
private const val EraseStepMs = 28L
private const val HoldMs = 1800L
private const val PauseMs = 450L

private val FailureTint = Color(0xFFFF8A80)

private enum class ProcessState { Waiting, Running, Done, Failed }

private class AgentProcess(val label: String, val ids: Set<String>)

private val Processes = listOf(
    AgentProcess("Region check", setOf("region")),
    AgentProcess("Rate limiter", setOf("limiter", "validate")),
    AgentProcess("Weather feed", setOf("locate", "weather")),
    AgentProcess("Language model", setOf("model", "format"))
)

private fun stepLabel(id: String): String = tr(
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
    return if (running != null) stepLabel(running.id) else tr("Working")
}

private fun processState(steps: List<AgentStep>, process: AgentProcess): ProcessState {
    val own = steps.filter { it.id in process.ids }
    return when {
        own.any { it.status == StepStatus.Fail } -> ProcessState.Failed
        own.any { it.status == StepStatus.Run } -> ProcessState.Running
        own.isNotEmpty() -> ProcessState.Done
        else -> ProcessState.Waiting
    }
}

@Composable
fun AgentScreen(vm: WeatherViewModel) {
    val scheme = MaterialTheme.colorScheme
    val chat = vm.agent
    val messages by chat.messages.collectAsStateWithLifecycle()
    val sending by chat.sending.collectAsStateWithLifecycle()
    val city by vm.city.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val blockedUntil by chat.blockedUntil.collectAsStateWithLifecycle()
    val unavailable by chat.unavailable.collectAsStateWithLifecycle()
    val limited = blockedUntil > 0L
    val canSend = text.isNotBlank() && !unavailable && !limited
    val submit = {
        if (canSend && chat.send(text, city, settings)) text = ""
    }
    val listState = rememberLazyListState()
    val bottomInset = LocalBottomInset.current
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val bottom = if (imeBottom > bottomInset) imeBottom else bottomInset
    val live = messages.lastOrNull()?.takeIf { it.pending }
    val status = if (sending) liveLabel(live?.steps.orEmpty()) else null

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

    LaunchedEffect(messages.size, live?.steps?.size, messages.lastOrNull()?.pending) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Box(
        Modifier.fillMaxSize().background(scheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight()) {
            AgentHeader(status)
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
                        AgentBubbleRow(message, settings, Modifier.animateItem())
                    }
                }
            }
            if (limited) {
                AgentLimitBar(blockedUntil, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom))
            } else if (unavailable) {
                AgentUnavailableBar(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom))
            } else {
                AgentInputBar(
                    text = text,
                    onTextChange = { text = clampAgentInput(it) },
                    canSend = canSend,
                    sending = false,
                    onSend = { submit() },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom)
                )
            }
        }
    }
}

@Composable
private fun AgentHeader(status: String?) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "agent-header")
    val corner by transition.animateFloat(
        initialValue = 50f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "corner"
    )
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(status) { if (status != null) shown = status }
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
        AnimatedVisibility(
            visible = status != null,
            enter = fadeIn() + scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)),
            exit = fadeOut() + scaleOut()
        ) {
            Surface(
                color = scheme.tertiaryContainer,
                contentColor = scheme.onTertiaryContainer,
                shape = CircleShape
            ) {
                Row(
                    Modifier.padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveLoader(size = 22.dp, color = scheme.onTertiaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        shown,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
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
    Column(
        modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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
        Spacer(Modifier.height(24.dp))
        Text(
            tr("Ask the Agent about the weather"),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
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
            if (message.fromUser) UserBubble(message.text, message.error) else AgentBubble(message, settings)
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
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (message.pending || message.steps.isNotEmpty()) {
                ActivityPanel(message, content)
            }
            if (!message.pending) {
                if (message.text.isNotEmpty()) {
                    Text(
                        if (message.notice) tr(message.text) else message.text,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, lineHeight = 24.sp),
                        color = content
                    )
                }
                message.cards.forEach { card ->
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

@Composable
private fun ActivityPanel(message: AgentMessage, content: Color) {
    val live = message.pending
    val failed = message.steps.any { it.status == StepStatus.Fail } || (!live && message.notice)
    var open by rememberSaveable(message.id) { mutableStateOf(false) }
    val expanded = live || open
    val rotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "chevron"
    )
    val done = message.steps.count { it.status == StepStatus.Done }
    Surface(
        color = content.copy(alpha = 0.1f),
        contentColor = content,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !live, role = Role.Button) { open = !open },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    if (live) {
                        ExpressiveLoader(size = 30.dp, color = content)
                    } else {
                        Icon(
                            if (failed) Icons.Rounded.Close else Icons.Rounded.Check,
                            contentDescription = null,
                            tint = if (failed) FailureTint else content,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (live) liveLabel(message.steps) else tr(if (failed) "Stopped" else "Completed"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "$done/${message.steps.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = content.copy(alpha = 0.7f)
                    )
                }
                if (!live) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = tr(if (open) "Hide activity" else "Show activity"),
                        tint = content,
                        modifier = Modifier.graphicsLayer { rotationZ = rotation }
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(200)) + expandVertically(),
                exit = fadeOut(tween(120)) + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ProcessGrid(message.steps, content)
                    CommandStream(message.steps, live, content)
                }
            }
        }
    }
}

@Composable
private fun ProcessGrid(steps: List<AgentStep>, content: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            tr("System processes"),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
            color = content.copy(alpha = 0.72f)
        )
        Processes.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { process ->
                    ProcessChip(process, processState(steps, process), content, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProcessChip(process: AgentProcess, state: ProcessState, content: Color, modifier: Modifier) {
    val alpha by animateFloatAsState(if (state == ProcessState.Running) 0.24f else 0.1f, tween(240), label = "chipAlpha")
    Surface(
        color = content.copy(alpha = alpha),
        contentColor = content,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                when (state) {
                    ProcessState.Running -> CircularProgressIndicator(Modifier.size(16.dp), color = content, strokeWidth = 2.dp)
                    ProcessState.Done -> Icon(Icons.Rounded.Check, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                    ProcessState.Failed -> Icon(Icons.Rounded.Close, contentDescription = null, tint = FailureTint, modifier = Modifier.size(18.dp))
                    ProcessState.Waiting -> Box(Modifier.size(7.dp).clip(CircleShape).background(content.copy(alpha = 0.35f)))
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                tr(process.label),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CommandStream(steps: List<AgentStep>, live: Boolean, content: Color) {
    val lines = steps.filter { it.command.isNotBlank() }.takeLast(6)
    val transition = rememberInfiniteTransition(label = "cursor")
    val blink by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "blink"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            tr("Live commands"),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
            color = content.copy(alpha = 0.72f)
        )
        Surface(
            color = Color.Black.copy(alpha = 0.32f),
            contentColor = content,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().animateContentSize()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEach { step ->
                    val tint = when (step.status) {
                        StepStatus.Run -> content
                        StepStatus.Done -> content.copy(alpha = 0.78f)
                        StepStatus.Fail -> FailureTint
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Text("\$", fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, color = content.copy(alpha = 0.5f))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            step.command,
                            modifier = Modifier.weight(1f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = tint
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (step.status) {
                                StepStatus.Run -> "…"
                                StepStatus.Done -> "✓"
                                StepStatus.Fail -> "✕"
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = tint
                        )
                    }
                }
                if (live) {
                    Text("▌", fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, color = content.copy(alpha = blink))
                }
            }
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
                if (!empty) {
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
