package com.catsblock.weather.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray

@Composable
fun ApplicationScope.TrayController(
    settings: DesktopSettings,
    pending: WindowsRelease?,
    status: UpdateStatus,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onWidget: (Boolean) -> Unit,
    onStartup: (Boolean) -> Unit,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
    onExit: () -> Unit
) {
    Tray(
        icon = painterResource("Icon.jpg"),
        tooltip = "CatsBlock Weather",
        onAction = onOpen,
        menu = {
            Item(t("Open CatsBlock Weather"), onClick = onOpen)
            Item(t("Settings"), onClick = onSettings)
            Separator()
            CheckboxItem(t("Show floating widget"), checked = settings.widgetVisible, onCheckedChange = onWidget)
            CheckboxItem(t("Start with Windows"), checked = settings.startWithWindows, onCheckedChange = onStartup)
            Separator()
            if (pending != null) {
                Item(
                    if (status == UpdateStatus.Downloading) updateText("Downloading update…")
                    else updateText("Install update") + " (" + pending.name + ")",
                    enabled = status != UpdateStatus.Downloading,
                    onClick = onInstall
                )
            } else {
                Item(
                    when (status) {
                        UpdateStatus.Checking -> updateText("Checking for updates…")
                        UpdateStatus.Current -> updateText("Up to date")
                        UpdateStatus.Failed -> updateText("Update failed. Try again")
                        else -> updateText("Check for updates")
                    },
                    enabled = status != UpdateStatus.Checking,
                    onClick = onCheck
                )
            }
            Separator()
            Item(t("Exit"), onClick = onExit)
        }
    )
}
