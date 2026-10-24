package com.catsblock.weather.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AppShortcut
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catsblock.weather.data.EclipseLeadOptions
import com.catsblock.weather.data.RainLeadOptions
import com.catsblock.weather.data.SnowLeadOptions
import com.catsblock.weather.data.SettingsStore
import com.catsblock.weather.i18n.AppLanguage
import com.catsblock.weather.i18n.leadFull
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.i18n.trx
import com.catsblock.weather.update.UpdateInfo
import com.catsblock.weather.update.UpdateState
import com.catsblock.weather.update.UpdateViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private fun mb(bytes: Long) = String.format(Locale.US, "%.1f MB", bytes / 1048576.0)

private fun notificationsAllowed(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: WeatherViewModel, up: UpdateViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var langDialog by remember { mutableStateOf(false) }
    var leadDialog by remember { mutableStateOf<String?>(null) }
    var notificationsOn by remember { mutableStateOf(notificationsAllowed(ctx)) }
    var openSettingsOnDeny by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = notificationsAllowed(ctx)
        if (!granted && openSettingsOnDeny) openNotificationSettings(ctx)
        openSettingsOnDeny = false
    }

    fun askNotifications(explicit: Boolean) {
        val missing = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (missing) {
            openSettingsOnDeny = explicit
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (explicit) {
            openNotificationSettings(ctx)
        }
    }

    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                up.onResume()
                notificationsOn = notificationsAllowed(ctx)
            }
        }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }

    if (langDialog) {
        AlertDialog(
            onDismissRequest = { langDialog = false },
            title = { Text(tr("App language")) },
            text = {
                Column {
                    AppLanguage.entries.forEach { l ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    langDialog = false
                                    scope.launch { SettingsStore(ctx.applicationContext).setLanguage(l) }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = settings.language == l, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(l.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { langDialog = false }) { Text("OK") } }
        )
    }

    leadDialog?.let { which ->
        val options = when (which) {
            "eclipse" -> EclipseLeadOptions
            "snow" -> SnowLeadOptions
            else -> RainLeadOptions
        }
        val selected = when (which) {
            "eclipse" -> settings.eclipseLeadMinutes
            "snow" -> settings.snowLeadMinutes
            else -> settings.rainLeadMinutes
        }
        AlertDialog(
            onDismissRequest = { leadDialog = null },
            title = { Text(trx("Alert time")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { minutes ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    leadDialog = null
                                    when (which) {
                                        "eclipse" -> vm.setEclipseLead(minutes)
                                        "snow" -> vm.setSnowLead(minutes)
                                        else -> vm.setRainLead(minutes)
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected == minutes, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(leadFull(minutes), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { leadDialog = null }) { Text("OK") } }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(tr("Settings")) },
                navigationIcon = {
                    IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back")) }
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
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            UpdateCard(up)

            GroupLabel(tr("App language"))
            Group {
                ListItem(
                    modifier = Modifier.clickable { langDialog = true },
                    leadingContent = { Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(tr("App language")) },
                    supportingContent = { Text(settings.language.label) },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            GroupLabel(tr("Units"))
            Group {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(tr("Temperature"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !settings.fahrenheit,
                            onClick = { vm.setFahrenheit(false) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) { Text(tr("Celsius")) }
                        SegmentedButton(
                            selected = settings.fahrenheit,
                            onClick = { vm.setFahrenheit(true) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) { Text(tr("Fahrenheit")) }
                    }
                }
            }

            GroupLabel(trx("Customization"))
            Group {
                NavRow(
                    Icons.Rounded.Palette,
                    trx("Weather Icon Kit"),
                    trx("Replace the weather icons used in the app and widgets."),
                    vm::openIconKit
                )
                GroupDivider()
                NavRow(
                    Icons.Rounded.Wallpaper,
                    trx("Weather Background Kit"),
                    trx("Choose your own backgrounds (images or GIFs) and sounds (MP3) for each weather."),
                    vm::openBackgroundKit
                )
                GroupDivider()
                NavRow(
                    Icons.Rounded.AppShortcut,
                    trx("App icon changer"),
                    trx("Use any image as your app icon on the home screen, or restore the default."),
                    vm::openAppIcon
                )
            }

            GroupLabel(trx("Notifications"))
            Group {
                SwitchRow(
                    trx("Rain alerts"),
                    trx("Get notified before rain arrives."),
                    settings.rainAlerts
                ) { enabled ->
                    vm.setRainAlerts(enabled)
                    if (enabled) askNotifications(false)
                }
                if (settings.rainAlerts) {
                    GroupDivider()
                    ListItem(
                        modifier = Modifier.clickable { leadDialog = "rain" },
                        headlineContent = { Text(trx("Alert time")) },
                        supportingContent = { Text(leadFull(settings.rainLeadMinutes)) },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                GroupDivider()
                SwitchRow(
                    trx("Snow alerts"),
                    trx("Get notified before snow arrives."),
                    settings.snowAlerts
                ) { enabled ->
                    vm.setSnowAlerts(enabled)
                    if (enabled) askNotifications(false)
                }
                if (settings.snowAlerts) {
                    GroupDivider()
                    ListItem(
                        modifier = Modifier.clickable { leadDialog = "snow" },
                        headlineContent = { Text(trx("Alert time")) },
                        supportingContent = { Text(leadFull(settings.snowLeadMinutes)) },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                GroupDivider()
                SwitchRow(
                    trx("Be notified when there is a lunar eclipse or a solar eclipse"),
                    trx("Only eclipses you can see from your city are announced."),
                    settings.eclipseAlerts
                ) { enabled ->
                    vm.setEclipseAlerts(enabled)
                    if (enabled) askNotifications(false)
                }
                if (settings.eclipseAlerts) {
                    GroupDivider()
                    ListItem(
                        modifier = Modifier.clickable { leadDialog = "eclipse" },
                        headlineContent = { Text(trx("Alert time")) },
                        supportingContent = { Text(leadFull(settings.eclipseLeadMinutes)) },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                GroupDivider()
                SwitchRow(
                    trx("Notify when there is a new app update"),
                    trx("Get notified with the new version, and update straight from the notification."),
                    settings.updateAlerts
                ) { enabled ->
                    vm.setUpdateAlerts(enabled)
                    if (enabled) askNotifications(false)
                }
                if ((settings.rainAlerts || settings.snowAlerts || settings.eclipseAlerts || settings.updateAlerts) && !notificationsOn) {
                    GroupDivider()
                    ListItem(
                        headlineContent = { Text(trx("Notifications are turned off")) },
                        supportingContent = { Text(trx("Allow notifications so alerts can reach you.")) },
                        trailingContent = {
                            TextButton(onClick = { askNotifications(true) }) { Text(trx("Allow")) }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            GroupLabel(tr("Updates and sounds"))
            Group {
                SwitchRow(
                    tr("Mobile data updates"),
                    tr("Allow weather updates over mobile data. When off, updates only run on Wi-Fi."),
                    settings.mobileData,
                    vm::setMobileData
                )
                GroupDivider()
                SwitchRow(
                    tr("Weather sounds"),
                    tr("Play ambient sounds that match the current weather."),
                    settings.sounds,
                    vm::setSounds
                )
            }

            GroupLabel(tr("Privacy"))
            Group {
                InfoRow(
                    tr("Location permission"),
                    tr("Used only to find the weather where you are. Stored on your device only.")
                )
                GroupDivider()
                InfoRow(
                    tr("Network access"),
                    tr("City coordinates are sent to Open-Meteo. Moon images come from NASA and map tiles from OpenStreetMap. Media is downloaded from public CDNs.")
                )
                GroupDivider()
                InfoRow(
                    tr("Data stored on your device"),
                    tr("Saved cities and your choices. No accounts, analytics or ads.")
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UpdateCard(up: UpdateViewModel) {
    val state by up.state.collectAsStateWithLifecycle()
    val s = state
    val scheme = MaterialTheme.colorScheme
    val tappable = s is UpdateState.Idle || s is UpdateState.UpToDate || s is UpdateState.Error

    val status = when (s) {
        is UpdateState.Idle -> tr("Your app has not yet been checked to see if it is fully up to date or not.")
        is UpdateState.Checking -> tr("Checking for updates...")
        is UpdateState.UpToDate -> tr("Your app is up to date!")
        is UpdateState.Available -> tr("Your app is outdated. Update it to receive bug fixes and more features.")
        is UpdateState.NeedsPermission -> tr("Allow installing apps from unknown sources to update the app.")
        is UpdateState.Downloading -> tr("Downloading update...")
        is UpdateState.Error -> if (s.download) tr("Download failed. Tap to try again.") else tr("Could not check for updates. Tap to try again.")
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
    ) {
        Column(
            Modifier
                .clickable(enabled = tappable) {
                    if (s is UpdateState.Error && s.download && s.info != null) up.update() else up.check()
                }
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(scheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (s is UpdateState.UpToDate) Icons.Rounded.CheckCircle else Icons.Rounded.SystemUpdate,
                        null,
                        tint = scheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Check for updates"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                if (s is UpdateState.Checking) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                }
            }

            when (s) {
                is UpdateState.Available -> {
                    Spacer(Modifier.height(18.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InfoBlock(s.info)
                        Button(onClick = up::update) { Text(tr("Update")) }
                    }
                }
                is UpdateState.NeedsPermission -> {
                    Spacer(Modifier.height(18.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InfoBlock(s.info)
                        Button(onClick = up::openUnknownSourcesSettings) { Text(tr("Open settings")) }
                    }
                }
                is UpdateState.Downloading -> {
                    Spacer(Modifier.height(22.dp))
                    SnakeProgress(s.progress)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${mb((s.info.sizeBytes * s.progress).toLong())} / ${mb(s.info.sizeBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun InfoBlock(info: UpdateInfo) {
    Column {
        Text("${tr("Version")} ${info.versionName}", style = MaterialTheme.typography.titleSmall)
        Text(mb(info.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SnakeProgress(progress: Float) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val transition = rememberInfiniteTransition(label = "snake")
    val phase by transition.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(300), label = "progress")
    Canvas(Modifier.fillMaxWidth().height(32.dp)) {
        val w = size.width
        val cy = size.height / 2
        val sw = 5.dp.toPx()
        val amp = 7.dp.toPx()
        val wave = 30.dp.toPx()
        val taper = 48.dp.toPx()
        val end = w * p
        if (end < w) drawLine(track, Offset(end, cy), Offset(w, cy), sw, StrokeCap.Round)
        if (end > 0f) {
            val path = Path()
            path.moveTo(0f, cy)
            var x = 0f
            while (x <= end) {
                val k = (x / taper).coerceAtMost(1f)
                path.lineTo(x, cy + amp * k * sin(x / wave * 2f * PI.toFloat() - phase))
                x += 2f
            }
            drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val lx = end.coerceIn(sw / 2, w - sw / 2)
        drawLine(color, Offset(lx, cy - 13.dp.toPx()), Offset(lx, cy + 13.dp.toPx()), sw, StrokeCap.Round)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun NavRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                thumbContent = if (checked) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun InfoRow(title: String, description: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
