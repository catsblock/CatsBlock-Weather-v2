package com.catsblock.weather.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.catsblock.weather.data.AdminClient
import com.catsblock.weather.data.AdminMetrics
import com.catsblock.weather.data.AdminPoint
import com.catsblock.weather.data.AdminResult
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.components.ExpressiveLoader
import com.catsblock.weather.ui.components.ExpressiveProgressBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class AdminStage { Code, Question, Dashboard, Blocked, Unavailable }

private const val CODE_LENGTH = 10
private const val ANSWER_MAX = 64
private const val REFRESH_MS = 15_000L
private val RANGES = listOf(7, 14, 30, 60)

@Composable
internal fun AdminPanel(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            securePolicy = SecureFlagPolicy.SecureOn
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            AdminContent(onDismiss)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminContent(onDismiss: () -> Unit) {
    val client = remember { AdminClient() }
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(AdminStage.Code) }
    var code by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var stageToken by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf<String?>(null) }

    fun reset(next: AdminStage, notice: String?) {
        stageToken = null
        session = null
        code = ""
        answer = ""
        message = notice
        stage = next
    }

    fun fail(result: AdminResult<*>) {
        when (result) {
            is AdminResult.Rejected -> message = tr("Incorrect. Attempts left: %d").format(result.remaining)
            AdminResult.Banned -> reset(AdminStage.Blocked, null)
            AdminResult.Busy -> message = tr("Too many attempts at once. Try again shortly.")
            AdminResult.Unavailable -> reset(AdminStage.Unavailable, null)
            AdminResult.Expired -> reset(AdminStage.Code, tr("Session expired. Sign in again."))
            AdminResult.Network -> message = tr("Connection problem. Try again.")
            is AdminResult.Success -> Unit
        }
    }

    val signedIn = stage == AdminStage.Dashboard
    Scaffold(
        modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(tr("Administrator access"), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            if (signedIn) Icons.AutoMirrored.Rounded.Logout else Icons.Rounded.Close,
                            contentDescription = if (signedIn) tr("Sign out") else tr("Close")
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Crossfade(
            targetState = stage,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            modifier = Modifier.padding(padding).fillMaxSize(),
            label = "admin-stage"
        ) { current ->
            Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.TopStart) {
                when (current) {
                    AdminStage.Code -> GateForm(
                        prompt = tr("Enter the 10-digit access code"),
                        label = tr("Access code"),
                        value = code,
                        maxLength = CODE_LENGTH,
                        onValueChange = { code = it.filter(Char::isDigit).take(CODE_LENGTH) },
                        keyboard = KeyboardType.NumberPassword,
                        ready = code.length == CODE_LENGTH,
                        busy = busy,
                        message = message
                    ) {
                        scope.launch {
                            busy = true
                            message = null
                            when (val result = client.stageOne(code)) {
                                is AdminResult.Success -> {
                                    stageToken = result.value
                                    code = ""
                                    stage = AdminStage.Question
                                }
                                else -> fail(result)
                            }
                            busy = false
                        }
                    }
                    AdminStage.Question -> GateForm(
                        prompt = tr("What is the dog's name?"),
                        label = tr("Answer"),
                        heading = tr("Security question"),
                        value = answer,
                        maxLength = ANSWER_MAX,
                        onValueChange = { answer = it.take(ANSWER_MAX) },
                        keyboard = KeyboardType.Password,
                        ready = answer.isNotBlank(),
                        busy = busy,
                        message = message
                    ) {
                        val token = stageToken
                        if (token == null) {
                            reset(AdminStage.Code, tr("Session expired. Sign in again."))
                        } else {
                            scope.launch {
                                busy = true
                                message = null
                                when (val result = client.stageTwo(token, answer)) {
                                    is AdminResult.Success -> {
                                        session = result.value
                                        stageToken = null
                                        answer = ""
                                        stage = AdminStage.Dashboard
                                    }
                                    else -> fail(result)
                                }
                                busy = false
                            }
                        }
                    }
                    AdminStage.Dashboard -> {
                        val token = session
                        if (token != null) Dashboard(client, token, onFailure = { fail(it) })
                    }
                    AdminStage.Blocked -> Notice(Icons.Rounded.Lock, tr("Access blocked. Try again in 14 hours."))
                    AdminStage.Unavailable -> Notice(Icons.Rounded.CloudOff, tr("Admin access is unavailable right now."))
                }
            }
        }
    }
}

@Composable
private fun Notice(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.padding(top = 24.dp).widthIn(max = 480.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.errorContainer,
        contentColor = scheme.onErrorContainer
    ) {
        Row(
            Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun GateForm(
    prompt: String,
    label: String,
    value: String,
    maxLength: Int,
    onValueChange: (String) -> Unit,
    keyboard: KeyboardType,
    ready: Boolean,
    busy: Boolean,
    message: String?,
    heading: String? = null,
    onSubmit: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val number = remember(I18n.lang) { NumberFormat.getIntegerInstance(I18n.lang.locale) }
    Column(
        Modifier.padding(top = 24.dp).widthIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (heading != null) {
                Text(heading, style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            }
            Text(prompt, style = MaterialTheme.typography.headlineMedium)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = !busy,
            isError = message != null,
            shape = MaterialTheme.shapes.large,
            visualTransformation = PasswordVisualTransformation(),
            supportingText = {
                if (message != null) {
                    Text(message)
                } else {
                    Text(number.format(value.length) + " / " + number.format(maxLength))
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboard,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { if (ready && !busy) onSubmit() }),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onSubmit,
            enabled = ready && !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) {
            if (busy) {
                ExpressiveLoader(size = 28.dp, color = scheme.onPrimary)
                Text(tr("Checking…"), modifier = Modifier.padding(start = 12.dp))
            } else {
                Text(tr("Verify"), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private enum class Section { Headline, Users, Today, History, Hourly }

@Composable
private fun Dashboard(client: AdminClient, session: String, onFailure: (AdminResult<*>) -> Unit) {
    var metrics by remember { mutableStateOf<AdminMetrics?>(null) }
    var days by remember { mutableIntStateOf(30) }
    var problem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session, days) {
        while (true) {
            when (val result = client.metrics(session, days)) {
                is AdminResult.Success -> {
                    metrics = result.value
                    problem = null
                }
                AdminResult.Network, AdminResult.Busy -> problem = tr("Connection problem. Try again.")
                else -> {
                    onFailure(result)
                    return@LaunchedEffect
                }
            }
            delay(REFRESH_MS)
        }
    }

    val current = metrics
    if (current == null) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (problem == null) ExpressiveLoader(size = 64.dp)
            Text(
                problem ?: tr("Loading metrics…"),
                style = MaterialTheme.typography.bodyLarge,
                color = if (problem == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
        }
        return
    }
    val number = remember(I18n.lang) { NumberFormat.getIntegerInstance(I18n.lang.locale) }
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(32.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 64.dp)
    ) {
        items(Section.entries, key = { it.name }) { section ->
            when (section) {
                Section.Headline -> Headline(current, problem, number)
                Section.Users -> UsersSection(current, days, number) { days = it }
                Section.Today -> TodaySection(current, number)
                Section.History -> HistorySection(current, number)
                Section.Hourly -> HourlySection(current, number)
            }
        }
    }
}

@Composable
private fun Headline(metrics: AdminMetrics, problem: String?, number: NumberFormat) {
    val scheme = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tr("Android client metrics"), style = MaterialTheme.typography.headlineLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(
                tr("Active users now"),
                number.format(metrics.activeNow),
                scheme.primaryContainer,
                scheme.onPrimaryContainer,
                shapes.extraLarge.copy(bottomEnd = CornerSize(8.dp)),
                Modifier.weight(1f)
            )
            Stat(
                tr("Unique users in 24 hours"),
                number.format(metrics.unique24h),
                scheme.tertiaryContainer,
                scheme.onTertiaryContainer,
                shapes.extraLarge.copy(bottomStart = CornerSize(8.dp)),
                Modifier.weight(1f)
            )
        }
        val updated = updatedAt(metrics.generatedAt)
        if (problem != null) {
            Text(problem, style = MaterialTheme.typography.labelLarge, color = scheme.error)
        } else if (updated != null) {
            Text(
                tr("Updated %s").format(updated),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
    container: Color,
    content: Color,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier
) {
    Surface(modifier, shape = shape, color = container, contentColor = content) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.displaySmall)
        }
    }
}

@Composable
private fun UsersSection(metrics: AdminMetrics, days: Int, number: NumberFormat, onRange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tr("Unique users per day"), style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RANGES.forEach { range ->
                RangeButton(range == days, tr("%d days").format(range), { onRange(range) }, Modifier.weight(1f))
            }
        }
        LineChart(tr("Unique users per day"), metrics.users, number)
    }
}

@Composable
private fun RangeButton(selected: Boolean, text: String, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val radius by animateDpAsState(
        targetValue = if (selected) 28.dp else 12.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "range-radius"
    )
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).semantics {
            role = Role.RadioButton
            this.selected = selected
        },
        shape = RoundedCornerShape(radius),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) scheme.primary else scheme.secondaryContainer,
            contentColor = if (selected) scheme.onPrimary else scheme.onSecondaryContainer
        ),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) { Text(text, maxLines = 1) }
}

@Composable
private fun TodaySection(metrics: AdminMetrics, number: NumberFormat) {
    val scheme = MaterialTheme.colorScheme
    val limit = metrics.usageLimit.toFloat()
    val used = metrics.usageToday.toFloat()
    val fraction = if (limit > 0f) (used / limit).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tr("API requests today"), style = MaterialTheme.typography.titleLarge)
        Surface(
            shape = MaterialTheme.shapes.large,
            color = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    number.format(metrics.usageToday) + " / " + number.format(metrics.usageLimit),
                    style = MaterialTheme.typography.headlineMedium
                )
                ExpressiveProgressBar(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = scheme.secondary,
                    trackColor = scheme.surface
                )
                Text(tr("Daily limit resets at 00:00 UTC"), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun HistorySection(metrics: AdminMetrics, number: NumberFormat) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tr("Request history, 30 days"), style = MaterialTheme.typography.titleLarge)
        LineChart(tr("Total requests"), metrics.requestsDaily, number)
        LineChart(tr("Errors"), metrics.errorsDaily, number, MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun HourlySection(metrics: AdminMetrics, number: NumberFormat) {
    val charts = listOf(
        tr("Total requests") to "total",
        tr("Weather API") to "weather",
        tr("Visual API") to "visual",
        tr("OpenWeather API") to "openweather",
        tr("Agent requests") to "agent"
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tr("Requests per hour, last 48 hours"), style = MaterialTheme.typography.titleLarge)
        charts.forEach { (title, key) -> LineChart(title, metrics.hourly[key].orEmpty(), number) }
    }
}

@Composable
private fun LineChart(
    title: String,
    points: List<AdminPoint>,
    number: NumberFormat,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val scheme = MaterialTheme.colorScheme
    val peak = points.maxOfOrNull { it.value } ?: 0.0
    val latest = points.lastOrNull()?.value ?: 0.0
    val grid = scheme.outlineVariant
    val fill = accent.copy(alpha = 0.14f)
    Surface(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = tr("Chart: %s, latest value %s").format(title, number.format(latest.toLong()))
        },
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    tr("Peak %s").format(number.format(peak.toLong())),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant
                )
            }
            if (points.isEmpty()) {
                Text(tr("No data yet"), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                return@Column
            }
            Canvas(Modifier.fillMaxWidth().height(112.dp)) {
                val top = 8.dp.toPx()
                val bottom = size.height - 8.dp.toPx()
                val span = bottom - top
                for (i in 0..2) {
                    val y = top + span * i / 2f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                val max = if (peak <= 0.0) 1.0 else peak
                val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f
                fun xOf(index: Int): Float = if (points.size > 1) stepX * index else size.width / 2f
                fun yOf(value: Double): Float = (bottom - span * (value / max)).toFloat()
                val line = Path()
                points.forEachIndexed { index, point ->
                    if (index == 0) line.moveTo(xOf(index), yOf(point.value)) else line.lineTo(xOf(index), yOf(point.value))
                }
                if (points.size > 1) {
                    val area = Path()
                    area.addPath(line)
                    area.lineTo(xOf(points.lastIndex), bottom)
                    area.lineTo(xOf(0), bottom)
                    area.close()
                    drawPath(area, fill)
                }
                drawPath(line, accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(scheme.surfaceContainerLow, 7.dp.toPx(), Offset(xOf(points.lastIndex), yOf(points.last().value)))
                drawCircle(accent, 5.dp.toPx(), Offset(xOf(points.lastIndex), yOf(points.last().value)))
            }
            Row(Modifier.fillMaxWidth()) {
                Text(
                    axisLabel(points.first().label),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    axisLabel(points.last().label),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun axisLabel(raw: String): String = when {
    raw.length >= 16 && raw[10] == 'T' -> raw.substring(11, 16)
    raw.length == 10 -> raw.substring(5)
    else -> raw
}

private fun updatedAt(iso: String): String? = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(I18n.lang.locale))
}.getOrNull()
