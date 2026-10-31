package com.catsblock.weather.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.detailSidePadding
import com.catsblock.weather.ui.typography.GoogleSansFlex
import kotlinx.coroutines.delay

private val OuterRadius = 32.dp
private val InnerRadius = 8.dp

@Composable
fun Modifier.entrance(index: Int): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        shown = true
    }
    val p by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "entrance"
    )
    return graphicsLayer {
        alpha = p.coerceIn(0f, 1f)
        val s = 0.94f + 0.06f * p
        scaleX = s
        scaleY = s
        translationY = (1f - p) * 48.dp.toPx()
    }
}

private fun groupShape(index: Int, count: Int): Shape {
    if (count == 1) return RoundedCornerShape(OuterRadius)
    return when (index) {
        0 -> RoundedCornerShape(OuterRadius, OuterRadius, InnerRadius, InnerRadius)
        count - 1 -> RoundedCornerShape(InnerRadius, InnerRadius, OuterRadius, OuterRadius)
        else -> RoundedCornerShape(InnerRadius)
    }
}

@Composable
fun CardGroup(items: List<@Composable ColumnScope.() -> Unit>, baseIndex: Int = 0) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEachIndexed { i, item ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .entrance(baseIndex + i)
                    .background(scheme.surfaceContainerHigh, groupShape(i, items.size))
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                content = item
            )
        }
    }
}

@Composable
fun ChartCard(title: String, index: Int, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .entrance(index)
            .background(scheme.surfaceContainerHigh, RoundedCornerShape(OuterRadius))
            .padding(horizontal = 24.dp, vertical = 22.dp)
    ) {
        Text(title, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun GaugeRow(
    label: String,
    value: String,
    sub: String?,
    subIcon: ImageVector? = null,
    subTint: Color = Color.Unspecified,
    trailing: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { -it / 3 } + fadeOut())
                },
                label = "gauge-value"
            ) { text ->
                Text(
                    text,
                    color = scheme.onSurface,
                    fontFamily = GoogleSansFlex.family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 40.sp,
                    lineHeight = 46.sp,
                    letterSpacing = (-1).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (subIcon != null) {
                        Icon(
                            subIcon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (subTint == Color.Unspecified) scheme.onSurfaceVariant else subTint
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(sub, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun AdviceRow(icon: ImageVector, tint: Color, text: String, caption: String? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = tint)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(text, color = scheme.onSurface, fontSize = 17.sp, lineHeight = 24.sp)
            if (caption != null) {
                Spacer(Modifier.height(6.dp))
                Text(caption, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun OutlookCard(
    label: String,
    headline: String,
    sub: String?,
    index: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .entrance(index)
            .background(scheme.surfaceContainerHigh, RoundedCornerShape(OuterRadius))
            .padding(horizontal = 24.dp, vertical = 22.dp)
    ) {
        Text(label, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        AnimatedContent(
            targetState = headline,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "outlook-headline"
        ) { text ->
            Text(
                text,
                color = scheme.onSurface,
                fontFamily = GoogleSansFlex.family,
                fontWeight = FontWeight.SemiBold,
                fontSize = 40.sp,
                lineHeight = 46.sp,
                letterSpacing = (-1).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(sub, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(18.dp))
        content()
    }
}

@Composable
private fun NavCircle(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    primary: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 16.dp else 28.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 520f),
        label = "navigator-shape"
    )
    val width by animateDpAsState(
        targetValue = if (pressed) 52.dp else 44.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 520f),
        label = "navigator-width"
    )
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(width, 44.dp),
        shape = RoundedCornerShape(corner),
        interactionSource = source,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (primary) scheme.secondaryContainer else scheme.surfaceContainerHigh,
            contentColor = if (primary) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
            disabledContainerColor = scheme.surfaceContainerLow,
            disabledContentColor = scheme.onSurface.copy(alpha = 0.38f)
        )
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun DateNavigator(
    label: String,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = label,
            modifier = Modifier.weight(1f),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "navigator-label"
        ) { text ->
            Text(
                text,
                color = scheme.onSurface,
                fontFamily = GoogleSansFlex.family,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        NavCircle(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, tr("Previous day"), canPrevious, false, onPrevious)
        Spacer(Modifier.width(8.dp))
        NavCircle(Icons.AutoMirrored.Rounded.KeyboardArrowRight, tr("Next day"), canNext, true, onNext)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailDialog(
    title: String,
    onBack: () -> Unit,
    tabs: List<String>,
    selectedTab: Int,
    onTab: (Int) -> Unit,
    outlook: @Composable () -> Unit,
    navigator: @Composable () -> Unit,
    body: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val collapsePx = with(density) { 110.dp.toPx() }
    val collapse by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapsePx).coerceIn(0f, 1f)
        }
    }
    val statusTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val navBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val side = detailSidePadding()

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.background)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = side,
                    end = side,
                    top = statusTop + 64.dp,
                    bottom = navBottom + 40.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "title") {
                    Text(
                        title,
                        modifier = Modifier
                            .padding(start = 8.dp, top = 40.dp, bottom = 8.dp)
                            .graphicsLayer {
                                alpha = 1f - collapse
                                translationY = -collapse * 24.dp.toPx()
                            },
                        color = scheme.onSurface,
                        fontFamily = GoogleSansFlex.family,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 38.sp,
                        lineHeight = 44.sp,
                        letterSpacing = (-1).sp
                    )
                }
                if (tabs.size > 1) {
                    item(key = "tabs") {
                        PrimaryTabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Transparent,
                            contentColor = scheme.onSurface
                        ) {
                            tabs.forEachIndexed { i, label ->
                                Tab(
                                    selected = i == selectedTab,
                                    onClick = { onTab(i) },
                                    text = {
                                        Text(
                                            label,
                                            fontWeight = if (i == selectedTab) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                item(key = "outlook") { outlook() }
                stickyHeader(key = "navigator") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(scheme.background)
                            .padding(vertical = 8.dp)
                    ) { navigator() }
                }
                item(key = "body") { body() }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(scheme.background)
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Back"), tint = scheme.onSurface)
                }
                Text(
                    title,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                        .graphicsLayer {
                            alpha = collapse
                            translationY = (1f - collapse) * 14.dp.toPx()
                        },
                    color = scheme.onSurface,
                    fontFamily = GoogleSansFlex.family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
