package com.catsblock.weather.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.MouseInfo
import java.awt.Point

private const val REFRESH_MS = 15 * 60_000L
private const val UPDATE_CHECK_MS = 6 * 60 * 60_000L

fun main() = application {
    var settings by remember { mutableStateOf(SettingsStore.load()) }
    var conditions by remember { mutableStateOf<Conditions?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var mainVisible by remember { mutableStateOf(true) }
    var tab by remember { mutableIntStateOf(0) }
    var focusTick by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<WindowsRelease?>(null) }
    var updateStatus by remember { mutableStateOf(UpdateStatus.Idle) }
    val scope = rememberCoroutineScope()

    uiLanguage.value = settings.language

    fun update(next: DesktopSettings) {
        settings = next
        SettingsStore.save(next)
    }

    fun openApp(settingsTab: Boolean) {
        if (settingsTab) tab = 1
        mainVisible = true
        focusTick++
    }

    fun checkUpdates() {
        if (updateStatus == UpdateStatus.Checking || updateStatus == UpdateStatus.Downloading) return
        updateStatus = UpdateStatus.Checking
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { WindowsUpdater.latest() } }
            result.onSuccess { release ->
                pending = release
                updateStatus = if (release == null) UpdateStatus.Current else UpdateStatus.Idle
            }.onFailure {
                updateStatus = UpdateStatus.Failed
            }
        }
    }

    fun installUpdate() {
        val release = pending ?: return
        if (updateStatus == UpdateStatus.Downloading) return
        updateStatus = UpdateStatus.Downloading
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { WindowsUpdater.install(WindowsUpdater.download(release)) } }
            if (result.isSuccess) exitApplication() else updateStatus = UpdateStatus.Failed
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            checkUpdates()
            delay(UPDATE_CHECK_MS)
        }
    }

    LaunchedEffect(settings.lat, settings.lon, settings.hasCity, reload) {
        if (!settings.hasCity) {
            conditions = null
            return@LaunchedEffect
        }
        while (true) {
            val result = withContext(Dispatchers.IO) { WeatherClient.forecast(settings.lat, settings.lon) }
            if (result != null) {
                conditions = result
                failed = false
            } else if (conditions == null) {
                failed = true
            }
            delay(if (result == null) 60_000L else REFRESH_MS)
        }
    }

    val icon = painterResource("Icon.jpg")

    Tray(
        icon = icon,
        tooltip = "CatsBlock Weather",
        onAction = { openApp(false) },
        menu = {
            Item(t("Open CatsBlock Weather")) { openApp(false) }
            Item(t("Settings")) { openApp(true) }
            val release = pending
            if (release != null) {
                Item(
                    if (updateStatus == UpdateStatus.Downloading) updateText("Downloading update…")
                    else updateText("Install update") + " (" + release.name + ")",
                    enabled = updateStatus != UpdateStatus.Downloading
                ) { installUpdate() }
            } else {
                Item(
                    when (updateStatus) {
                        UpdateStatus.Checking -> updateText("Checking for updates…")
                        UpdateStatus.Current -> updateText("Up to date")
                        UpdateStatus.Failed -> updateText("Update failed. Try again")
                        else -> updateText("Check for updates")
                    },
                    enabled = updateStatus != UpdateStatus.Checking
                ) { checkUpdates() }
            }
            Item(t("Exit")) { exitApplication() }
        }
    )

    if (settings.widgetVisible) {
        FrameWidget(settings, conditions) { openApp(false) }
    }

    Window(
        onCloseRequest = { mainVisible = false },
        visible = mainVisible,
        title = "CatsBlock Weather",
        icon = icon,
        state = rememberWindowState(size = DpSize(520.dp, 760.dp))
    ) {
        LaunchedEffect(focusTick) {
            if (focusTick > 0) {
                window.isMinimized = false
                window.toFront()
                window.requestFocus()
            }
        }
        MainWindow(
            settings = settings,
            conditions = conditions,
            failed = failed,
            tab = tab,
            onTab = { tab = it },
            onSettings = ::update,
            onRetry = { failed = false; reload++ }
        )
    }
}

@Composable
private fun androidx.compose.ui.window.ApplicationScope.FrameWidget(
    settings: DesktopSettings,
    conditions: Conditions?,
    onOpen: () -> Unit
) {
    var active by remember { mutableStateOf(false) }
    var touched by remember { mutableLongStateOf(0L) }
    val state = rememberWindowState(
        size = DpSize(136.dp, 56.dp),
        position = WindowPosition.Aligned(Alignment.TopEnd)
    )
    LaunchedEffect(active, touched, settings.idleSeconds) {
        if (active && settings.idleSeconds > 0) {
            delay(settings.idleSeconds * 1000L)
            active = false
        }
    }
    val alpha by animateFloatAsState(if (active) 1f else settings.inactivePercent / 100f, tween(240), label = "alpha")
    val background = widgetColor(settings, conditions)
    val ink = inkFor(background)
    Window(
        onCloseRequest = {},
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = settings.alwaysOnTop,
        focusable = true,
        title = "CatsBlock Weather",
        state = state
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .alpha(alpha)
                .clip(RoundedCornerShape(4.dp))
                .background(background)
                .pointerInput(active) {
                    detectTapGestures {
                        touched = System.currentTimeMillis()
                        if (active) onOpen() else active = true
                    }
                }
                .pointerInput(Unit) {
                    var anchor = Point(0, 0)
                    var origin = Point(0, 0)
                    detectDragGestures(
                        onDragStart = {
                            anchor = MouseInfo.getPointerInfo().location
                            origin = window.location
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val now = MouseInfo.getPointerInfo().location
                            window.setLocation(origin.x + now.x - anchor.x, origin.y + now.y - anchor.y)
                        }
                    )
                }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painterResource("Icon.jpg"),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                conditions?.let { formatTemp(it.tempC, settings.fahrenheit) } ?: "--°",
                color = ink,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
