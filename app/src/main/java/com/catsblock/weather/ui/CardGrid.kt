package com.catsblock.weather.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.CardId
import com.catsblock.weather.data.CardShape
import com.catsblock.weather.data.CardStyle
import com.catsblock.weather.data.CardTheme
import com.catsblock.weather.data.UiPrefs

private val Palette: List<Pair<Long, Long>> = listOf(
    0xFF4FACFE to 0xFF00F2FE,
    0xFF667EEA to 0xFF764BA2,
    0xFFF093FB to 0xFFF5576C,
    0xFFFA709A to 0xFFFEE140,
    0xFF43E97B to 0xFF38F9D7,
    0xFFFF9A9E to 0xFFFECFEF,
    0xFFFDC830 to 0xFFF37335,
    0xFF0F2027 to 0xFF2C5364,
    0xFF232526 to 0xFF414345,
    0xFF8E2DE2 to 0xFF4A00E0,
    0xFFEB3349 to 0xFFF45C43,
    0xFF11998E to 0xFF38EF7D,
    0xFFFFFFFF to 0xFFDDE6F0,
    0xFF1E3C72 to 0xFF2A5298
)

private fun buildRows(order: List<CardId>, shapeOf: (CardId) -> CardShape): List<List<CardId>> {
    val rows = mutableListOf<MutableList<CardId>>()
    var open: MutableList<CardId>? = null
    for (id in order) {
        if (shapeOf(id).span == 2) {
            rows += mutableListOf(id)
            open = null
        } else {
            val o = open
            if (o == null) {
                val n = mutableListOf(id)
                rows += n
                open = n
            } else {
                o.add(id)
                open = null
            }
        }
    }
    return rows
}

@Composable
private fun wiggle(on: Boolean, seed: Int): State<Float> {
    if (!on) return remember { mutableFloatStateOf(0f) }
    val t = rememberInfiniteTransition(label = "wiggle")
    return t.animateFloat(
        initialValue = -0.8f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(120 + seed * 14, easing = LinearEasing), RepeatMode.Reverse),
        label = "wiggleValue"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardGrid(cards: Map<CardId, @Composable () -> Unit>, modifier: Modifier = Modifier) {
    val prefs = LocalUiPrefs.current
    val actions = LocalUiActions.current
    var sheetFor by remember { mutableStateOf<CardId?>(null) }
    val order = prefs.order.filter { it in cards }
    val rows = buildRows(order) { prefs.shapeOf(it) }
    val gap = 12.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val full = maxWidth
        val half = (full - gap) / 2
        Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            AnimatedVisibility(
                visible = prefs.edit,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.35f))
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Editing cards", Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleSmall)
                    FilledTonalButton(onClick = { actions.update { it.copy(edit = false) } }) { Text("Done") }
                }
            }
            rows.forEach { row ->
                val target = row.maxOf { id ->
                    val s = prefs.shapeOf(id)
                    (if (s.span == 2) full else half) / s.ratio
                }
                val h by animateDpAsState(target, spring(stiffness = Spring.StiffnessLow), label = "rowHeight")
                Row(Modifier.fillMaxWidth().height(h), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { id ->
                        val s = prefs.shapeOf(id)
                        Cell(
                            id = id,
                            shape = s,
                            edit = prefs.edit,
                            modifier = Modifier.width(if (s.span == 2) full else half).fillMaxHeight(),
                            onEdit = { sheetFor = id },
                            onMove = { dir -> actions.update { it.move(id, dir, cards.keys) } },
                            content = cards.getValue(id)
                        )
                    }
                }
            }
        }
    }
    sheetFor?.let { CardEditorSheet(it, prefs, actions) { sheetFor = null } }
}

@Composable
private fun Cell(
    id: CardId,
    shape: CardShape,
    edit: Boolean,
    modifier: Modifier,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    content: @Composable () -> Unit
) {
    val w = wiggle(edit, id.ordinal)
    val scale by animateFloatAsState(if (edit) 0.97f else 1f, spring(), label = "cellScale")
    Box(
        modifier.graphicsLayer {
            rotationZ = w.value * if (id.ordinal % 2 == 0) 1f else -1f
            scaleX = scale
            scaleY = scale
        }
    ) {
        CompositionLocalProvider(LocalCardId provides id, LocalCardShape provides shape) {
            Box(Modifier.fillMaxSize()) { content() }
        }
        AnimatedVisibility(visible = edit, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Color.Black.copy(alpha = 0.4f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
                    .clickable(onClick = onEdit)
            ) {
                IconButton(onClick = { onMove(-1) }, Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = Color.White)
                }
                Icon(Icons.Rounded.Edit, null, Modifier.align(Alignment.Center).size(30.dp), tint = Color.White)
                IconButton(onClick = { onMove(1) }, Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White)
                }
                Text(
                    id.title,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Swatch(colors: Pair<Long, Long>?, selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier.size(42.dp).clip(CircleShape)
            .background(
                if (colors == null) Brush.linearGradient(listOf(Color(0xFF9E9E9E), Color(0xFFE0E0E0)))
                else Brush.linearGradient(listOf(Color(colors.first), Color(colors.second)))
            )
            .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (colors == null) Text("A", color = Color.Black, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CardEditorSheet(id: CardId, prefs: UiPrefs, actions: UiActions, onDismiss: () -> Unit) {
    val style = prefs.styles[id] ?: CardStyle()
    fun set(s: CardStyle) = actions.update { it.copy(styles = it.styles + (id to s)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(id.title, style = MaterialTheme.typography.titleLarge)
            SectionLabel("Theme")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(style.theme == null, { set(style.copy(theme = null)) }, { Text("Default") })
                CardTheme.entries.forEach { t ->
                    FilterChip(style.theme == t, { set(style.copy(theme = t)) }, { Text(t.label) })
                }
            }
            SectionLabel("Proportion")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CardShape.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = (style.shape ?: id.defaultShape) == s,
                        onClick = { set(style.copy(shape = s)) },
                        shape = SegmentedButtonDefaults.itemShape(i, CardShape.entries.size)
                    ) { Text(s.label, maxLines = 1) }
                }
            }
            SectionLabel("Color / gradient")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Swatch(null, style.c1 == null) { set(style.copy(c1 = null, c2 = null)) } }
                items(Palette) { p ->
                    Swatch(p, style.c1 == p.first && style.c2 == p.second) { set(style.copy(c1 = p.first, c2 = p.second)) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { actions.update { it.copy(styles = it.styles - id) } }, Modifier.weight(1f)) { Text("Reset") }
                Button(onClick = onDismiss, Modifier.weight(1f)) { Text("Done") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
