package com.catsblock.weather.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.catsblock.weather.data.City
import com.catsblock.weather.data.CitySummary
import com.catsblock.weather.i18n.tr

private val CardHeight = 124.dp
private val CardShape = RoundedCornerShape(28.dp)
private val OverlayInk = Color(0xFF080C14)

@Composable
private fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

@Composable
fun CitiesOverlay(
    visible: Boolean,
    saved: List<City>,
    selectedId: String?,
    current: CitySummary?,
    summaries: Map<String, CitySummary>,
    fahrenheit: Boolean,
    onSelect: (City) -> Unit,
    onRemove: (City) -> Unit,
    onAdd: () -> Unit,
    onClose: () -> Unit
) {
    val t by animateFloatAsState(
        if (visible) 1f else 0f,
        tween(560, easing = FastOutSlowInEasing),
        label = "cities"
    )
    if (!visible && t <= 0.001f) return
    BackHandler(enabled = visible, onBack = onClose)
    val ordered = remember(saved, selectedId) { saved.sortedByDescending { it.id == selectedId } }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val cardTop = topInset + 72.dp
    val baseSide = 16.dp

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = if (maxWidth >= 600.dp) ((maxWidth - 640.dp) / 2).coerceAtLeast(baseSide) else baseSide
        val scrollDp = with(density) { scroll.value.toDp() }
        val first = ordered.firstOrNull()
        val firstSummary = if (first != null && first.id == selectedId) current ?: summaries[first.id] else first?.let { summaries[it.id] }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (t * 3f).coerceAtMost(1f) }
                .background(OverlayInk.copy(alpha = 0.94f))
                .pointerInput(Unit) { detectTapGestures { } }
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(start = side, end = side, top = cardTop, bottom = bottomInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.fillMaxWidth().height(CardHeight))
            ordered.drop(1).forEachIndexed { i, city ->
                CityCard(
                    city = city,
                    summary = summaries[city.id],
                    selected = false,
                    fahrenheit = fahrenheit,
                    modifier = Modifier.graphicsLayer {
                        val local = ((t - 0.30f - i * 0.05f) / 0.45f).coerceIn(0f, 1f)
                        alpha = local
                        translationY = (1f - local) * 70.dp.toPx()
                    },
                    onClick = { onSelect(city) },
                    onRemove = { onRemove(city) }
                )
            }
            AddCityButton(
                modifier = Modifier.graphicsLayer {
                    val local = ((t - 0.30f - ordered.size * 0.05f) / 0.45f).coerceIn(0f, 1f)
                    alpha = local
                    translationY = (1f - local) * 70.dp.toPx()
                },
                onClick = onAdd
            )
        }

        if (first != null) {
            val w = lerp(maxWidth, maxWidth - side * 2, t)
            val h = lerp(maxHeight, CardHeight, t)
            val x = lerp(0.dp, side, t)
            val y = lerp(0.dp, cardTop - scrollDp, t)
            val corner = lerp(0.dp, 28.dp, t)
            Box(
                Modifier
                    .offset(x, y)
                    .size(w, h)
                    .pressable(onClose)
                    .graphicsLayer { alpha = (t * 6f).coerceAtMost(1f) }
                    .clip(RoundedCornerShape(corner))
            ) {
                CityCardBackdrop(firstSummary, Modifier.fillMaxSize())
                CityCardBody(
                    city = first,
                    summary = firstSummary,
                    selected = first.id == selectedId,
                    fahrenheit = fahrenheit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f) },
                    onRemove = { onRemove(first) }
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(cardTop - 8.dp)
                .graphicsLayer { alpha = t }
                .background(Brush.verticalGradient(listOf(OverlayInk, OverlayInk.copy(alpha = 0f))))
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = topInset + 12.dp, start = side + 8.dp, end = side - 4.dp)
                .graphicsLayer {
                    alpha = ((t - 0.3f) / 0.5f).coerceIn(0f, 1f)
                    translationY = (1f - alpha) * -16.dp.toPx()
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                tr("Saved cities"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Box(
                Modifier
                    .size(44.dp)
                    .pressable(onClose)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, tr("Close"), Modifier.size(22.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun CityCard(
    city: City,
    summary: CitySummary?,
    selected: Boolean,
    fahrenheit: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(CardHeight)
            .pressable(onClick)
            .clip(CardShape)
            .border(1.dp, Color.White.copy(alpha = 0.12f), CardShape)
    ) {
        CityCardBackdrop(summary, Modifier.fillMaxSize())
        CityCardBody(city, summary, selected, fahrenheit, Modifier.fillMaxSize(), onRemove)
    }
}

@Composable
private fun CityCardBackdrop(summary: CitySummary?, modifier: Modifier) {
    Box(modifier) {
        if (summary != null) {
            WeatherBackground(summary.kind, summary.isDay, Modifier.fillMaxSize(), summary.localTime)
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF1B2433), Color(0xFF0F1520))))
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.44f))))
        )
    }
}

@Composable
private fun CityCardBody(
    city: City,
    summary: CitySummary?,
    selected: Boolean,
    fahrenheit: Boolean,
    modifier: Modifier,
    onRemove: (() -> Unit)?
) {
    val soft = Color.White.copy(alpha = 0.85f)
    Box(modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Column(Modifier.align(Alignment.TopStart).fillMaxWidth(0.58f)) {
            Text(
                city.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selected) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NearMe, null, Modifier.size(13.dp), tint = soft)
                    Spacer(Modifier.width(5.dp))
                    Text(tr("Your location"), style = MaterialTheme.typography.labelMedium, color = soft, maxLines = 1)
                }
            }
        }
        if (summary != null) {
            Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
                Text(
                    tempText(summary.temp, fahrenheit),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.W300,
                    color = Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(12.dp), tint = soft)
                    Text(tempText(summary.min, fahrenheit), style = MaterialTheme.typography.labelMedium, color = soft)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(12.dp), tint = soft)
                    Text(tempText(summary.max, fahrenheit), style = MaterialTheme.typography.labelMedium, color = soft)
                }
            }
            Text(
                summary.kind.label(summary.isDay),
                modifier = Modifier.align(Alignment.BottomStart),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1
            )
        }
        if (onRemove != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .pressable(onRemove)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, tr("Remove"), Modifier.size(16.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun AddCityButton(modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressable(onClick)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(tr("Add a city"), style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}
