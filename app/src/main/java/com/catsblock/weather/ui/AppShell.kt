package com.catsblock.weather.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.catsblock.weather.i18n.tr
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.launch

val LocalBottomInset = compositionLocalOf { 0.dp }

enum class AppTab(
    val screen: Screen,
    val label: String,
    val idle: ImageVector,
    val active: ImageVector
) {
    Weather(Screen.Main, "Weather", Icons.Outlined.Cloud, Icons.Rounded.Cloud),
    WeatherMap(Screen.Map, "Weather map", Icons.Outlined.Map, Icons.Rounded.Map),
    Settings(Screen.Settings, "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings);

    companion object {
        fun of(screen: Screen?): AppTab? = entries.firstOrNull { it.screen == screen }
    }
}

private val BarHeight = 68.dp
private val BarGap = 12.dp
private val RailWidth = 96.dp
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
fun AppShell(vm: WeatherViewModel, screen: Screen?) {
    var last by remember { mutableStateOf(AppTab.Weather) }
    val tab = AppTab.of(screen) ?: last
    SideEffect { last = tab }

    val layout = LocalWindowLayout.current
    val navBottom: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val inset = if (layout.useRail) navBottom + 16.dp else navBottom + BarHeight + BarGap * 2

    val onSelect: (AppTab) -> Unit = {
        if (it != tab) {
            when (it) {
                AppTab.Weather -> vm.openWeather()
                AppTab.WeatherMap -> vm.openMap()
                AppTab.Settings -> vm.openSettings()
            }
        }
    }

    val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalBottomInset provides inset) {
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (
                        slideInHorizontally(tween(440, easing = Emphasized)) { dir * it / 6 } +
                            fadeIn(tween(320, delayMillis = 80)) +
                            scaleIn(tween(440, easing = Emphasized), initialScale = 0.94f)
                        ) togetherWith (
                        slideOutHorizontally(tween(440, easing = Emphasized)) { -dir * it / 6 } +
                            fadeOut(tween(180)) +
                            scaleOut(tween(440, easing = Emphasized), targetScale = 0.96f)
                        )
                },
                label = "tabs"
            ) { target ->
                when (target) {
                    AppTab.Weather -> MainScreen(vm)
                    AppTab.WeatherMap -> MapScreen(vm)
                    AppTab.Settings -> SettingsScreen(vm)
                }
            }
        }
    }

    if (layout.useRail) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ExpressiveNavigationRail(selected = tab, onSelect = onSelect)
            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        }
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            content()
            ExpressiveNavigationBar(
                selected = tab,
                onSelect = onSelect,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun ExpressiveNavigationRail(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val onWeather = selected == AppTab.Weather
    val container by animateColorAsState(
        if (onWeather) Color(0xFF0E141C) else scheme.surfaceContainer, tween(280), label = "railContainer"
    )
    val idleTint by animateColorAsState(
        if (onWeather) Color(0xB3FFFFFF) else scheme.onSurfaceVariant, tween(280), label = "railIdle"
    )
    val pill by animateColorAsState(
        if (onWeather) Color(0x33FFFFFF) else scheme.secondaryContainer, tween(280), label = "railPill"
    )
    val activeTint by animateColorAsState(
        if (onWeather) Color.White else scheme.onSecondaryContainer, tween(280), label = "railActive"
    )
    Box(
        modifier
            .fillMaxHeight()
            .width(RailWidth)
            .background(container)
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppTab.entries.forEach { tab ->
                RailItem(tab, tab == selected, idleTint, activeTint, pill) { onSelect(tab) }
            }
        }
    }
}

@Composable
private fun RailItem(
    tab: AppTab,
    selected: Boolean,
    idleTint: Color,
    activeTint: Color,
    pill: Color,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val indicator by animateColorAsState(if (selected) pill else Color.Transparent, tween(240), label = "indicator")
    val tint = if (selected) activeTint else idleTint
    Column(
        Modifier
            .width(RailWidth - 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Tab) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .width(60.dp)
                .height(34.dp)
                .background(indicator, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedNavigationIcon(tab, selected, tint)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            tr(tab.label),
            color = tint,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExpressiveNavigationBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = BarGap)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .height(BarHeight),
            shape = CircleShape,
            color = scheme.surfaceContainerHigh.copy(alpha = 0.97f),
            tonalElevation = 3.dp,
            shadowElevation = 12.dp
        ) {
            Row(
                Modifier.fillMaxSize().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTab.entries.forEach { tab ->
                    NavigationItem(tab, tab == selected) { onSelect(tab) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavigationItem(tab: AppTab, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val weight by animateFloatAsState(
        if (selected) 2.3f else 1f,
        spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow),
        label = "weight"
    )
    val container by animateColorAsState(
        if (selected) scheme.secondaryContainer else Color.Transparent,
        tween(280),
        label = "container"
    )
    val tint by animateColorAsState(
        if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        tween(280),
        label = "tint"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .clip(CircleShape)
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            AnimatedNavigationIcon(tab, selected, tint)
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(
                    spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                    expandFrom = Alignment.Start
                ) + fadeIn(tween(240, delayMillis = 90)),
                exit = shrinkHorizontally(
                    tween(220, easing = Emphasized),
                    shrinkTowards = Alignment.Start
                ) + fadeOut(tween(110))
            ) {
                Text(
                    tr(tab.label),
                    modifier = Modifier.padding(start = 8.dp),
                    color = tint,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun AnimatedNavigationIcon(tab: AppTab, selected: Boolean, tint: Color) {
    val kick = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            launch {
                pop.snapTo(0.68f)
                pop.animateTo(1f, spring(dampingRatio = 0.34f, stiffness = Spring.StiffnessLow))
            }
            kick.snapTo(0f)
            kick.animateTo(1f, tween(720, easing = Emphasized))
        } else {
            kick.snapTo(0f)
            pop.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
        }
    }
    Box(
        Modifier
            .size(28.dp)
            .graphicsLayer {
                val t = kick.value
                val arc = sin(t * PI).toFloat()
                val decay = 1f - t
                scaleX = pop.value
                scaleY = pop.value
                when (tab) {
                    AppTab.Weather -> {
                        translationY = -9.dp.toPx() * arc
                        translationX = 4.dp.toPx() * sin(t * PI * 2.0).toFloat() * decay
                    }
                    AppTab.WeatherMap -> {
                        rotationZ = 24f * sin(t * PI * 3.0).toFloat() * decay
                        scaleX *= 1f + 0.12f * arc
                        scaleY *= 1f + 0.12f * arc
                    }
                    AppTab.Settings -> rotationZ = 180f * t
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = selected, animationSpec = tween(200), label = "icon") { on ->
            Icon(
                imageVector = if (on) tab.active else tab.idle,
                contentDescription = tr(tab.label),
                modifier = Modifier.size(26.dp),
                tint = tint
            )
        }
    }
}
