package com.catsblock.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
private val FieldShape = RoundedCornerShape(4.dp)

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
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AdminContent(onDismiss)
        }
    }
}

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

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                tr("Administrator access"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) { Text(if (stage == AdminStage.Dashboard) tr("Sign out") else tr("Close")) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopStart) {
            when (stage) {
                AdminStage.Code -> GateForm(
                    prompt = tr("Enter the 10-digit access code"),
                    label = tr("Access code"),
                    value = code,
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
                AdminStage.Blocked -> Notice(tr("Access blocked. Try again in 14 hours."))
                AdminStage.Unavailable -> Notice(tr("Admin access is unavailable right now."))
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 32.dp).widthIn(max = 480.dp)
    )
}

@Composable
private fun GateForm(
    prompt: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboard: KeyboardType,
    ready: Boolean,
    busy: Boolean,
    message: String?,
    heading: String? = null,
    onSubmit: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.padding(top = 32.dp).widthIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (heading != null) {
            Text(heading, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        }
        Text(prompt, style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = !busy,
            shape = FieldShape,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboard,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { if (ready && !busy) onSubmit() }),
            modifier = Modifier.fillMaxWidth()
        )
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = scheme.error)
        }
        Button(
            onClick = onSubmit,
            enabled = ready && !busy,
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) tr("Checking…") else tr("Verify")) }
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
        Text(
            problem ?: tr("Loading metrics…"),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 32.dp)
        )
        return
    }
    val number = remember(I18n.lang) { NumberFormat.getIntegerInstance(I18n.lang.locale) }
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(32.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 64.dp)
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tr("Android client metrics"), style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Stat(tr("Active users now"), number.format(metrics.activeNow), Modifier.weight(1f))
            Stat(tr("Unique users in 24 hours"), number.format(metrics.unique24h), Modifier.weight(1f))
        }
        val updated = updatedAt(metrics.generatedAt)
        if (problem != null) {
            Text(problem, style = MaterialTheme.typography.labelMedium, color = scheme.error)
        } else if (updated != null) {
            Text(
                tr("Updated %s").format(updated),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun UsersSection(metrics: AdminMetrics, days: Int, number: NumberFormat, onRange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Unique users per day"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RANGES.forEach { range ->
                val selected = range == days
                Text(
                    tr("%d days").format(range),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onRange(range) }.padding(vertical = 8.dp)
                )
            }
        }
        LineChart(tr("Unique users per day"), metrics.users, number)
    }
}

@Composable
private fun TodaySection(metrics: AdminMetrics, number: NumberFormat) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("API requests today"), style = MaterialTheme.typography.titleSmall)
        Text(
            number.format(metrics.usageToday) + " / " + number.format(metrics.usageLimit),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            tr("Daily limit resets at 00:00 UTC"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HistorySection(metrics: AdminMetrics, number: NumberFormat) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tr("Request history, 30 days"), style = MaterialTheme.typography.titleSmall)
        LineChart(tr("Total requests"), metrics.requestsDaily, number)
        LineChart(tr("Errors"), metrics.errorsDaily, number)
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
        Text(tr("Requests per hour, last 48 hours"), style = MaterialTheme.typography.titleSmall)
        charts.forEach { (title, key) -> LineChart(title, metrics.hourly[key].orEmpty(), number) }
    }
}

@Composable
private fun LineChart(title: String, points: List<AdminPoint>, number: NumberFormat) {
    val scheme = MaterialTheme.colorScheme
    val peak = points.maxOfOrNull { it.value } ?: 0.0
    val latest = points.lastOrNull()?.value ?: 0.0
    val line = scheme.primary
    val grid = scheme.outlineVariant
    Column(
        Modifier.fillMaxWidth().semantics {
            contentDescription = tr("Chart: %s, latest value %s").format(title, number.format(latest.toLong()))
        },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                tr("Peak %s").format(number.format(peak.toLong())),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant
            )
        }
        if (points.isEmpty()) {
            Text(tr("No data yet"), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            return@Column
        }
        Canvas(Modifier.fillMaxWidth().height(96.dp).background(scheme.surface)) {
            val top = 8.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            val span = bottom - top
            for (i in 0..2) {
                val y = top + span * i / 2f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            val max = if (peak <= 0.0) 1.0 else peak
            val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f
            fun yOf(value: Double): Float = (bottom - span * (value / max)).toFloat()
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = if (points.size > 1) stepX * index else size.width / 2f
                if (index == 0) path.moveTo(x, yOf(point.value)) else path.lineTo(x, yOf(point.value))
            }
            drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val lastX = if (points.size > 1) stepX * (points.size - 1) else size.width / 2f
            drawCircle(line, 4.dp.toPx(), Offset(lastX, yOf(points.last().value)))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(axisLabel(points.first().label), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(axisLabel(points.last().label), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
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
