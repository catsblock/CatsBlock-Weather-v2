package com.catsblock.weather.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import kotlin.system.exitProcess

private const val REFRESH_MS = 15 * 60_000L
private const val UPDATE_CHECK_MS = 6 * 60 * 60_000L

/**
 * Entry point. `--silent` is passed by the Windows startup entry and suppresses every visible window,
 * leaving only the tray icon and the floating widget. A second launch never gets past [SingleInstance].
 */
fun main(args: Array<String>) {
    val silent = args.any { it.equals("--silent", ignoreCase = true) }
    if (!SingleInstance.acquire(silent) { AppEvents.show() }) exitProcess(0)

    application(exitProcessOnExit = true) {
        var settings by remember { mutableStateOf(SettingsStore.load()) }
        var conditions by remember { mutableStateOf<Conditions?>(null) }
        var failed by remember { mutableStateOf(false) }
        var reload by remember { mutableIntStateOf(0) }
        var mainVisible by remember { mutableStateOf(!silent && settings.setupDone) }
        var setupVisible by remember { mutableStateOf(!silent && !settings.setupDone) }
        var tab by remember { mutableIntStateOf(0) }
        var focusTick by remember { mutableIntStateOf(0) }
        var pending by remember { mutableStateOf<WindowsRelease?>(null) }
        var updateStatus by remember { mutableStateOf(UpdateStatus.Idle) }
        val scope = rememberCoroutineScope()

        uiLanguage.value = settings.language

        fun update(next: DesktopSettings) {
            val previous = settings
            settings = next
            SettingsStore.save(next)
            if (previous.startWithWindows != next.startWithWindows && next.setupDone) {
                scope.launch(Dispatchers.IO) { StartupRegistration.setEnabled(next.startWithWindows) }
            }
        }

        fun openApp(settingsTab: Boolean) {
            if (!settings.setupDone) setupVisible = true else {
                if (settingsTab) tab = 1
                mainVisible = true
            }
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
                }.onFailure { updateStatus = UpdateStatus.Failed }
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
            AppEvents.showRequests.collect { openApp(false) }
        }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                if (settings.setupDone && settings.startWithWindows && StartupRegistration.supported && !StartupRegistration.isEnabled()) {
                    StartupRegistration.setEnabled(true)
                }
            }
            while (true) {
                checkUpdates()
                delay(UPDATE_CHECK_MS)
            }
        }

        val city = settings.city
        LaunchedEffect(city?.id, reload) {
            if (city == null) {
                conditions = null
                failed = false
                return@LaunchedEffect
            }
            conditions = null
            while (true) {
                val result = withContext(Dispatchers.IO) { WeatherClient.forecast(city.lat, city.lon) }
                if (result != null) {
                    conditions = result
                    failed = false
                } else if (conditions == null) failed = true
                delay(if (result == null) 60_000L else REFRESH_MS)
            }
        }

        val widgetModel = remember { WidgetModel() }
        SideEffect {
            widgetModel.settings = settings
            widgetModel.conditions = conditions
        }
        val latest = rememberUpdatedState(settings)
        val widget = remember {
            WidgetWindow(widgetModel, onOpen = { openApp(false) }, onMoved = { x, y -> update(latest.value.copy(widgetX = x, widgetY = y)) })
        }
        DisposableEffect(Unit) { onDispose { widget.dispose() } }
        LaunchedEffect(settings.widgetVisible, settings.alwaysOnTop) { widget.configure(settings) }

        TrayController(
            settings = settings,
            pending = pending,
            status = updateStatus,
            onOpen = { openApp(false) },
            onSettings = { openApp(true) },
            onWidget = { update(settings.copy(widgetVisible = it)) },
            onStartup = { update(settings.copy(startWithWindows = it)) },
            onCheck = ::checkUpdates,
            onInstall = ::installUpdate,
            onExit = { exitApplication() }
        )

        val icon = painterResource("Icon.jpg")

        Window(
            onCloseRequest = { setupVisible = false },
            visible = setupVisible,
            title = "CatsBlock Weather",
            icon = icon,
            resizable = false,
            state = rememberWindowState(size = DpSize(560.dp, 700.dp), position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center))
        ) {
            SetupScreen(
                settings = settings,
                onChange = { next -> settings = next },
                onFinish = {
                    update(settings.copy(setupDone = true))
                    setupVisible = false
                    mainVisible = true
                    focusTick++
                    scope.launch(Dispatchers.IO) { StartupRegistration.setEnabled(settings.startWithWindows) }
                }
            )
        }

        Window(
            onCloseRequest = { mainVisible = false },
            visible = mainVisible,
            title = "CatsBlock Weather",
            icon = icon,
            state = rememberWindowState(size = DpSize(1040.dp, 800.dp), position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center))
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(480, 600) }
            LaunchedEffect(focusTick) {
                if (focusTick > 0) {
                    window.isMinimized = false
                    window.isAlwaysOnTop = true
                    window.toFront()
                    window.requestFocus()
                    window.isAlwaysOnTop = false
                }
            }
            MainWindow(
                settings = settings,
                conditions = conditions,
                failed = failed,
                tab = tab,
                update = UpdateUi(pending, updateStatus, ::checkUpdates, ::installUpdate),
                onTab = { tab = it },
                onSettings = ::update,
                onRetry = { failed = false; reload++ }
            )
        }
    }
}
