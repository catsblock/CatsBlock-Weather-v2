package com.catsblock.weather.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import java.awt.geom.RoundRectangle2D
import javax.swing.JWindow

class WidgetModel {
    var settings by mutableStateOf(DesktopSettings())
    var conditions by mutableStateOf<Conditions?>(null)
    var active by mutableStateOf(false)
    var touched by mutableLongStateOf(0L)
}

class WidgetWindow(
    private val model: WidgetModel,
    private val onOpen: () -> Unit,
    private val onMoved: (Int, Int) -> Unit
) {
    private val window = JWindow()
    private var placed = false

    init {
        window.type = Window.Type.UTILITY
        window.focusableWindowState = false
        window.setSize(WIDTH, HEIGHT)
        runCatching { window.shape = RoundRectangle2D.Float(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), 8f, 8f) }
        val panel = ComposePanel()
        panel.setContent { WidgetContent(model, window, onOpen, onMoved) }
        window.contentPane.add(panel)
    }

    fun configure(settings: DesktopSettings) {
        window.isAlwaysOnTop = settings.alwaysOnTop
        if (!placed) {
            window.location = initialLocation(settings)
            placed = true
        }
        if (window.isVisible != settings.widgetVisible) window.isVisible = settings.widgetVisible
    }

    private fun initialLocation(settings: DesktopSettings): Point {
        val saved = Rectangle(settings.widgetX, settings.widgetY, WIDTH, HEIGHT)
        val onScreen = settings.widgetX >= 0 && GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            .any { it.defaultConfiguration.bounds.intersects(saved) }
        if (onScreen) return Point(settings.widgetX, settings.widgetY)
        val area = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        return Point(area.x + area.width - WIDTH - 16, area.y + 16)
    }

    fun dispose() {
        window.isVisible = false
        window.dispose()
    }

    companion object {
        const val WIDTH = 200
        const val HEIGHT = 64
    }
}

@Composable
private fun WidgetContent(model: WidgetModel, window: Window, onOpen: () -> Unit, onMoved: (Int, Int) -> Unit) {
    val s = model.settings
    val c = model.conditions
    LaunchedEffect(model.active, model.touched, s.idleSeconds) {
        if (model.active && s.idleSeconds > 0) {
            delay(s.idleSeconds * 1000L)
            model.active = false
        }
    }
    val target = if (model.active) 1f else s.inactivePercent / 100f
    val alpha = remember(window) { Animatable(target) }
    LaunchedEffect(target) {
        alpha.animateTo(target, tween(240)) { runCatching { window.opacity = value } }
    }
    val background = widgetColor(s, c)
    val ink = inkFor(background)
    Row(
        Modifier
            .fillMaxSize()
            .background(background)
            .pointerInput(Unit) {
                detectTapGestures {
                    model.touched = System.currentTimeMillis()
                    if (model.active) onOpen() else model.active = true
                }
            }
            .pointerInput(Unit) {
                var anchor = Point(0, 0)
                var origin = Point(0, 0)
                detectDragGestures(
                    onDragStart = {
                        anchor = MouseInfo.getPointerInfo()?.location ?: Point(0, 0)
                        origin = window.location
                    },
                    onDragEnd = { onMoved(window.x, window.y) },
                    onDrag = { change, _ ->
                        change.consume()
                        val now = MouseInfo.getPointerInfo()?.location ?: return@detectDragGestures
                        window.setLocation(origin.x + now.x - anchor.x, origin.y + now.y - anchor.y)
                    }
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (c != null) WeatherIcon(c.kind, c.isDay, 40.dp, ink)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                c?.let { formatTemp(it.temp, s.fahrenheit) } ?: "--°",
                color = ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
            )
            Text(s.city?.name.orEmpty(), color = ink.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
