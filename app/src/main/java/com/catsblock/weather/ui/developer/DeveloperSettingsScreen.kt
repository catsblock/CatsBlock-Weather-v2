package com.catsblock.weather.ui.developer

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import com.catsblock.weather.data.AppSettings
import com.catsblock.weather.data.SecretKeys
import com.catsblock.weather.data.ThemeMode
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.WeatherState
import com.catsblock.weather.ui.WeatherViewModel
import com.catsblock.weather.ui.alerts.AlertStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LogBackground = Color(0xFF12151A)
private val LogInfoColor = Color(0xFFFFFFFF)
private val LogWarningColor = Color(0xFFFFC857)
private val LogErrorColor = Color(0xFFFF5C5C)
private val LogMutedColor = Color(0xFF8A94A3)

private data class DiagRow(val label: String, val value: String)

private data class DiagSection(val title: String, val rows: List<DiagRow>)

private fun levelColor(level: LogLevel): Color = when (level) {
    LogLevel.Error -> LogErrorColor
    LogLevel.Warning -> LogWarningColor
    LogLevel.Info -> LogInfoColor
}

private fun levelLabel(level: LogLevel): String = when (level) {
    LogLevel.Error -> tr("Errors")
    LogLevel.Warning -> tr("Warnings")
    LogLevel.Info -> tr("Info")
}

private fun copyText(context: Context, label: String, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, tr("Copied to clipboard."), Toast.LENGTH_SHORT).show()
}

private fun yesNo(value: Boolean): String = tr(if (value) "Yes" else "No")

private fun onOff(value: Boolean): String = tr(if (value) "On" else "Off")

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / 1073741824.0)
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
    bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${rest}s"
        else -> "${rest}s"
    }
}

private fun formatDate(milliseconds: Long, locale: Locale): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(milliseconds))

private fun directorySize(file: File): Long =
    if (file.isFile) file.length() else file.listFiles()?.sumOf { directorySize(it) } ?: 0L

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun collectDiagnostics(
    ctx: Context,
    settings: AppSettings,
    state: WeatherState,
    citySelected: Boolean,
    cacheBytes: Long
): List<DiagSection> {
    val locale = settings.language.locale
    val packageInfo = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
    val appInfo = ctx.applicationInfo
    val debuggable = appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val metrics = ctx.resources.displayMetrics
    val runtime = Runtime.getRuntime()
    val usedMemory = runtime.totalMemory() - runtime.freeMemory()

    val connectivity = ctx.getSystemService(ConnectivityManager::class.java)
    val capabilities = connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
    val connection = when {
        capabilities == null -> tr("None")
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> tr("Wi-Fi")
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> tr("Mobile data")
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> tr("Ethernet")
        else -> tr("Other")
    }
    val power = ctx.getSystemService(PowerManager::class.java)
    val ignoringOptimizations = power?.isIgnoringBatteryOptimizations(ctx.packageName) == true

    val location = when {
        isGranted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) -> tr("Precise")
        isGranted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) -> tr("Approximate")
        else -> tr("Denied")
    }
    val notifications = if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) tr("Allowed") else tr("Blocked")
    val camera = if (isGranted(ctx, Manifest.permission.CAMERA)) tr("Granted") else tr("Denied")

    val app = DiagSection(
        tr("App"),
        listOf(
            DiagRow(tr("App version"), packageInfo?.versionName ?: "?"),
            DiagRow(tr("Build number"), packageInfo?.let { PackageInfoCompat.getLongVersionCode(it).toString() } ?: "?"),
            DiagRow(tr("Package"), ctx.packageName),
            DiagRow(tr("Build type"), tr(if (debuggable) "Debug" else "Release")),
            DiagRow(tr("Target SDK"), appInfo.targetSdkVersion.toString()),
            DiagRow(tr("Minimum SDK"), appInfo.minSdkVersion.toString()),
            DiagRow(tr("Installed on"), packageInfo?.let { formatDate(it.firstInstallTime, locale) } ?: "?"),
            DiagRow(tr("Updated on"), packageInfo?.let { formatDate(it.lastUpdateTime, locale) } ?: "?")
        )
    )

    val device = DiagSection(
        tr("Device"),
        listOf(
            DiagRow(tr("Device"), "${Build.MANUFACTURER} ${Build.MODEL}"),
            DiagRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
            DiagRow(tr("Security patch"), Build.VERSION.SECURITY_PATCH.ifBlank { "?" }),
            DiagRow(tr("Architecture"), Build.SUPPORTED_ABIS.firstOrNull() ?: "?"),
            DiagRow(tr("Screen"), "${metrics.widthPixels} x ${metrics.heightPixels} @ ${metrics.densityDpi} dpi"),
            DiagRow(tr("Processor cores"), runtime.availableProcessors().toString())
        )
    )

    val runtimeSection = DiagSection(
        tr("Runtime"),
        listOf(
            DiagRow(tr("Session time"), formatDuration(SystemClock.elapsedRealtime() - AppLog.startedElapsed)),
            DiagRow(tr("Memory in use"), formatBytes(usedMemory)),
            DiagRow(tr("Maximum memory"), formatBytes(runtime.maxMemory())),
            DiagRow(tr("Cache size"), formatBytes(cacheBytes)),
            DiagRow(tr("Free storage"), formatBytes(ctx.filesDir.usableSpace))
        )
    )

    val network = DiagSection(
        tr("Network"),
        listOf(
            DiagRow(tr("Connection"), connection),
            DiagRow(tr("Internet reachable"), yesNo(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)),
            DiagRow(tr("Metered connection"), yesNo(connectivity?.isActiveNetworkMetered == true)),
            DiagRow("VPN", yesNo(capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)),
            DiagRow(tr("Power saving"), onOff(power?.isPowerSaveMode == true)),
            DiagRow(tr("Battery optimization"), tr(if (ignoringOptimizations) "Unrestricted" else "Optimized"))
        )
    )

    val permissions = DiagSection(
        tr("Permissions"),
        listOf(
            DiagRow(tr("Location"), location),
            DiagRow(tr("Notifications"), notifications),
            DiagRow(tr("Camera"), camera)
        )
    )

    val configuration = DiagSection(
        tr("Configuration"),
        listOf(
            DiagRow(tr("Language"), settings.language.label),
            DiagRow(tr("Weather provider"), settings.weatherSource.name),
            DiagRow(tr("Personal keys"), String.format(locale, tr("%1\$d configured"), settings.apiKeys.values.count { it.isNotBlank() })),
            DiagRow(tr("Backend"), tr(if (SecretKeys.baseUrl().isNotEmpty()) "Configured" else "Missing")),
            DiagRow(tr("App token"), tr(if (SecretKeys.appToken().isNotEmpty()) "Configured" else "Missing")),
            DiagRow(tr("Temperature unit"), tr(if (settings.fahrenheit) "Fahrenheit" else "Celsius")),
            DiagRow(tr("Wind unit"), settings.windUnit.name),
            DiagRow(tr("Pressure unit"), settings.pressureUnit.name),
            DiagRow(
                tr("Theme"),
                tr(
                    when (settings.theme) {
                        ThemeMode.System -> "System"
                        ThemeMode.Light -> "Light"
                        ThemeMode.Dark -> "Dark"
                    }
                )
            ),
            DiagRow(tr("Background updates"), onOff(settings.backgroundUpdates)),
            DiagRow(tr("Update interval"), String.format(locale, tr("%1\$d min"), settings.backgroundIntervalMinutes)),
            DiagRow(tr("Mobile data updates"), onOff(settings.mobileData)),
            DiagRow(tr("Quiet hours"), onOff(settings.quietHours)),
            DiagRow(tr("Sounds"), onOff(settings.sounds)),
            DiagRow(tr("Rain alerts"), onOff(settings.rainAlerts)),
            DiagRow(tr("Snow alerts"), onOff(settings.snowAlerts)),
            DiagRow(tr("Eclipse alerts"), onOff(settings.eclipseAlerts)),
            DiagRow(tr("Update alerts"), onOff(settings.updateAlerts))
        )
    )

    val weatherRows = mutableListOf(
        DiagRow(
            tr("State"),
            when (state) {
                is WeatherState.Idle -> tr("Idle")
                is WeatherState.Loading -> tr("Loading")
                is WeatherState.Success -> tr("Ready")
                is WeatherState.Error -> tr("Error")
            }
        ),
        DiagRow(tr("City selected"), yesNo(citySelected))
    )
    when (state) {
        is WeatherState.Success -> {
            weatherRows += DiagRow(tr("Forecast hours"), state.bundle.hours.size.toString())
            weatherRows += DiagRow(tr("Forecast days"), state.bundle.days.size.toString())
            weatherRows += DiagRow(tr("Official alerts"), state.bundle.alerts.size.toString())
            weatherRows += DiagRow(tr("Rain slots"), state.bundle.rain.size.toString())
        }
        is WeatherState.Error -> weatherRows += DiagRow(tr("Last error"), LogRedactor.redact(state.message))
        else -> Unit
    }
    val weather = DiagSection(tr("Weather data"), weatherRows)

    return listOf(app, device, runtimeSection, network, permissions, configuration, weather)
}

private fun List<DiagSection>.asText(): String = joinToString("\n\n") { section ->
    section.title + "\n" + section.rows.joinToString("\n") { "${it.label}: ${it.value}" }
}

private fun plainLine(entry: LogEntry, formatter: SimpleDateFormat): String =
    "${formatter.format(Date(entry.time))} ${entry.level.letter}/${entry.tag}: ${entry.message}"

@Composable
fun DeveloperSettingsScreen(vm: WeatherViewModel, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            DeveloperContent(vm, onDismiss)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeveloperContent(vm: WeatherViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val settings by vm.settings.collectAsStateWithLifecycle()
    val weatherState by vm.weather.collectAsStateWithLifecycle()
    val city by vm.city.collectAsStateWithLifecycle()
    val revision by AppLog.revision.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var resetDialog by remember { mutableStateOf(false) }
    var diagnosticsTick by remember { mutableIntStateOf(0) }
    var cacheBytes by remember { mutableStateOf(0L) }
    var selectedLevels by remember { mutableStateOf(LogLevel.entries.toSet()) }
    var query by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(diagnosticsTick) {
        cacheBytes = withContext(Dispatchers.IO) { directorySize(ctx.cacheDir) }
    }

    val formatter = remember { SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US) }
    val allLogs = remember(revision) { AppLog.snapshot() }
    val tags = remember(allLogs) { allLogs.map { it.tag }.distinct().sorted() }
    val counts = remember(allLogs) { LogLevel.entries.associateWith { level -> allLogs.count { it.level == level } } }

    LaunchedEffect(tags) {
        if (selectedTag != null && selectedTag !in tags) selectedTag = null
    }

    val filteredLogs = remember(allLogs, selectedLevels, query, selectedTag) {
        allLogs.asReversed().filter { entry ->
            entry.level in selectedLevels &&
                (selectedTag == null || entry.tag == selectedTag) &&
                (query.isBlank() || entry.message.contains(query, ignoreCase = true) || entry.tag.contains(query, ignoreCase = true))
        }
    }

    val sections = remember(diagnosticsTick, settings, weatherState, city, cacheBytes, revision) {
        collectDiagnostics(ctx, settings, weatherState, city != null, cacheBytes)
    }

    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text(tr("Reset alert settings?")) },
            text = { Text(tr("All alert and warning options will return to their defaults.")) },
            confirmButton = {
                TextButton(onClick = {
                    resetDialog = false
                    scope.launch {
                        AlertStore(ctx.applicationContext).reset()
                        AppLog.info("Developer", "Alert settings were reset")
                        Toast.makeText(ctx, tr("Alert settings restored."), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(tr("Reset")) }
            },
            dismissButton = { TextButton(onClick = { resetDialog = false }) { Text(tr("Cancel")) } }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = scheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(tr("Developer options"), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back"))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = RoundedCornerShape(24.dp), color = scheme.tertiaryContainer) {
                Text(
                    tr("API keys, tokens, coordinates and personal data are always masked in everything shown or copied here."),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onTertiaryContainer
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { diagnosticsTick++ }, modifier = Modifier.weight(1f)) {
                    Text(tr("Refresh diagnostics"), maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { copyText(ctx, "diagnostics", sections.asText()) },
                    modifier = Modifier.weight(1f)
                ) { Text(tr("Copy diagnostics"), maxLines = 1) }
            }

            sections.forEach { section -> DiagnosticsCard(section) }

            Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHigh) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        tr("Logs"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedLevels.size == LogLevel.entries.size,
                            onClick = { selectedLevels = LogLevel.entries.toSet() },
                            label = { Text("${tr("All")} (${allLogs.size})", maxLines = 1) }
                        )
                        LogLevel.entries.forEach { level ->
                            FilterChip(
                                selected = level in selectedLevels,
                                onClick = {
                                    selectedLevels = if (level in selectedLevels) selectedLevels - level else selectedLevels + level
                                },
                                label = { Text("${levelLabel(level)} (${counts[level] ?: 0})", maxLines = 1) },
                                leadingIcon = {
                                    Box(Modifier.size(10.dp).background(levelColor(level), CircleShape))
                                }
                            )
                        }
                    }

                    if (tags.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                tr("Tags"),
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurfaceVariant
                            )
                            FilterChip(
                                selected = selectedTag == null,
                                onClick = { selectedTag = null },
                                label = { Text(tr("All tags"), maxLines = 1) }
                            )
                            tags.forEach { tag ->
                                FilterChip(
                                    selected = selectedTag == tag,
                                    onClick = { selectedTag = if (selectedTag == tag) null else tag },
                                    label = { Text(tag, maxLines = 1) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(tr("Search logs")) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, null) }
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    )

                    Text(
                        String.format(settings.language.locale, tr("Showing %1\$d of %2\$d lines"), filteredLogs.size, allLogs.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = LogBackground,
                        modifier = Modifier.fillMaxWidth().height(360.dp)
                    ) {
                        if (filteredLogs.isEmpty()) {
                            Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    tr("No log lines to show."),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LogMutedColor
                                )
                            }
                        } else {
                            LazyColumn(
                                Modifier.fillMaxSize().padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                items(filteredLogs, key = { it.id }) { entry -> LogLine(entry, formatter) }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                copyText(ctx, "logs", filteredLogs.asReversed().joinToString("\n") { plainLine(it, formatter) })
                            },
                            enabled = filteredLogs.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text(tr("Copy"), maxLines = 1) }
                        OutlinedButton(
                            onClick = { AppLog.clear() },
                            enabled = allLogs.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text(tr("Clear logs"), maxLines = 1) }
                    }
                }
            }

            Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHigh) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        tr("Maintenance"),
                        Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    ActionRow(
                        tr("Add sample logs"),
                        tr("Add one info, one warning and one error line to check the colors and filters.")
                    ) {
                        AppLog.info("Sample", "Sample information line")
                        AppLog.warn("Sample", "Sample warning line")
                        AppLog.error("Sample", "Sample error line", IllegalStateException("Sample failure"))
                        Toast.makeText(ctx, tr("Sample log lines added."), Toast.LENGTH_SHORT).show()
                    }
                    ActionRow(
                        tr("Clear image cache"),
                        tr("Remove cached images and animations. They are downloaded again when needed.")
                    ) {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                ctx.imageLoader.memoryCache?.clear()
                                ctx.imageLoader.diskCache?.clear()
                            }
                            AppLog.info("Developer", "Image cache cleared")
                            diagnosticsTick++
                            Toast.makeText(ctx, tr("Image cache cleared."), Toast.LENGTH_SHORT).show()
                        }
                    }
                    ActionRow(tr("Reset alert settings"), tr("Restore every alert and warning option to its default.")) {
                        resetDialog = true
                    }
                    ActionRow(
                        tr("Hide developer options"),
                        tr("Lock this menu again. Tap the app version five times to unlock it.")
                    ) {
                        scope.launch {
                            DeveloperStore(ctx.applicationContext).setEnabled(false)
                            onDismiss()
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DiagnosticsCard(section: DiagSection) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHigh) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            section.rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        row.label,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        row.value,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun LogLine(entry: LogEntry, formatter: SimpleDateFormat) {
    val color = levelColor(entry.level)
    val tint = if (entry.level == LogLevel.Info) Color.Transparent else color.copy(alpha = 0.10f)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = LogMutedColor)) {
                append(formatter.format(Date(entry.time)))
                append("  ")
            }
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                append(entry.level.letter)
                append("  ")
            }
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) {
                append(entry.tag)
                append(": ")
            }
            withStyle(SpanStyle(color = color)) { append(entry.message) }
        },
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(tint)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

@Composable
private fun ActionRow(title: String, description: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
