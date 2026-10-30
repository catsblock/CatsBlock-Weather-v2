package com.catsblock.weather.ui.developer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.weather.R
import com.catsblock.weather.ui.WeatherViewModel
import com.catsblock.weather.ui.alerts.AlertStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun readLogs(level: String): List<String> = runCatching {
    val process = ProcessBuilder("logcat", "-d", "-t", "300", "--pid=${Process.myPid()}", "*:$level")
        .redirectErrorStream(true)
        .start()
    val lines = process.inputStream.bufferedReader().readLines()
    process.destroy()
    lines.filter { it.isNotBlank() && !it.startsWith("---") }.map { LogRedactor.redact(it) }
}.getOrDefault(emptyList())

private fun copyText(context: Context, label: String, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, context.getString(R.string.dev_copied), Toast.LENGTH_SHORT).show()
}

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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var levelIndex by remember { mutableIntStateOf(1) }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var resetDialog by remember { mutableStateOf(false) }
    val levels = listOf("I", "W", "E")
    val levelLabels = listOf(R.string.dev_level_info, R.string.dev_level_warn, R.string.dev_level_error)

    LaunchedEffect(levelIndex, reloadTick) {
        logs = withContext(Dispatchers.IO) { readLogs(levels[levelIndex]) }
    }

    val versionName = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val on = stringResource(R.string.dev_on)
    val off = stringResource(R.string.dev_off)
    val configuredKeys = settings.apiKeys.values.count { it.isNotBlank() }
    val diagnostics = listOf(
        stringResource(R.string.dev_app_version) to versionName,
        stringResource(R.string.dev_package) to ctx.packageName,
        stringResource(R.string.dev_android) to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        stringResource(R.string.dev_device) to "${Build.MANUFACTURER} ${Build.MODEL}",
        stringResource(R.string.dev_language) to settings.language.label,
        stringResource(R.string.dev_source) to settings.weatherSource.name,
        stringResource(R.string.dev_keys) to stringResource(R.string.dev_keys_value, configuredKeys),
        stringResource(R.string.dev_background) to if (settings.backgroundUpdates) on else off,
        stringResource(R.string.dev_mobile_data) to if (settings.mobileData) on else off
    )

    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text(stringResource(R.string.dev_reset_confirm_title)) },
            text = { Text(stringResource(R.string.dev_reset_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    resetDialog = false
                    scope.launch {
                        AlertStore(ctx.applicationContext).reset()
                        Toast.makeText(ctx, ctx.getString(R.string.dev_reset_done), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.dev_reset_confirm)) }
            },
            dismissButton = { TextButton(onClick = { resetDialog = false }) { Text(stringResource(R.string.alerts_cancel)) } }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = scheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.dev_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.alerts_back))
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
                    stringResource(R.string.dev_privacy),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onTertiaryContainer
                )
            }

            Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHigh) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.dev_section_diagnostics),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    diagnostics.forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                    FilledTonalButton(
                        onClick = {
                            copyText(ctx, "diagnostics", diagnostics.joinToString("\n") { "${it.first}: ${it.second}" })
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.dev_copy_diagnostics)) }
                }
            }

            Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHigh) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.dev_section_logs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        levelLabels.forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = levelIndex == index,
                                onClick = { levelIndex = index },
                                shape = SegmentedButtonDefaults.itemShape(index, levelLabels.size)
                            ) { Text(stringResource(label), maxLines = 1) }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = scheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth().height(320.dp)
                    ) {
                        if (logs.isEmpty()) {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    stringResource(R.string.dev_logs_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                                items(logs) { line ->
                                    Text(
                                        line,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        color = scheme.onSurface,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { reloadTick++ }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.dev_refresh), maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = { copyText(ctx, "logs", logs.joinToString("\n")) },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.dev_copy_logs), maxLines = 1) }
                        OutlinedButton(onClick = { logs = emptyList() }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.dev_clear_logs), maxLines = 1)
                        }
                    }
                }
            }

            Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHigh) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.dev_section_actions),
                        Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    ActionRow(stringResource(R.string.dev_reset_alerts), stringResource(R.string.dev_reset_alerts_desc)) {
                        resetDialog = true
                    }
                    ActionRow(stringResource(R.string.dev_hide), stringResource(R.string.dev_hide_desc)) {
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
