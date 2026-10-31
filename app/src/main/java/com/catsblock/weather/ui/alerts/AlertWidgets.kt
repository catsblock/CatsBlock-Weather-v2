package com.catsblock.weather.ui.alerts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catsblock.weather.R
import java.time.LocalTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal fun formatMinutes(minutes: Int): String =
    LocalTime.of((minutes / 60) % 24, minutes % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
internal fun AlertCard(
    icon: ImageVector,
    title: String,
    summary: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(false) }
    val corner by animateDpAsState(
        if (expanded) 36.dp else 28.dp,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "alert-corner"
    )
    val container by animateColorAsState(
        if (enabled) scheme.surfaceContainerHigh else scheme.surfaceContainer,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "alert-container"
    )
    val badge by animateColorAsState(
        if (enabled) scheme.primaryContainer else scheme.surfaceContainerHighest,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "alert-badge"
    )
    val badgeContent by animateColorAsState(
        if (enabled) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "alert-badge-content"
    )
    val rotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "alert-chevron"
    )
    Surface(shape = RoundedCornerShape(corner), color = container, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(corner))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(badge),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = badgeContent)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                Icon(
                    Icons.Rounded.ExpandMore,
                    alertText(if (expanded) R.string.alerts_collapse else R.string.alerts_expand),
                    Modifier.rotate(rotation),
                    tint = scheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    thumbContent = if (enabled) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                    } else null
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
            ) {
                Column(
                    Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
internal fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
internal fun TimeRow(label: String, minutes: Int, onChange: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { open = true }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Schedule, null, tint = scheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Surface(shape = RoundedCornerShape(14.dp), color = scheme.secondaryContainer) {
            Text(
                formatMinutes(minutes),
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSecondaryContainer
            )
        }
    }
    if (open) {
        TimeDialog(label, minutes, { open = false }) {
            open = false
            onChange(it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(label: String, minutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(minutes / 60, minutes % 60, is24)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(24.dp),
            shape = RoundedCornerShape(32.dp),
            color = scheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                TimePicker(state)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(alertText(R.string.alerts_cancel)) }
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                        Text(alertText(R.string.alerts_ok))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selected == index,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size)
            ) { Text(label, maxLines = 1) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MonthChips(mask: Int, onChange: (Int) -> Unit) {
    val locale = Locale.getDefault()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (m in 0..11) {
            val on = (mask and (1 shl m)) != 0
            FilterChip(
                selected = on,
                onClick = { onChange(mask xor (1 shl m)) },
                label = { Text(Month.of(m + 1).getDisplayName(TextStyle.SHORT, locale)) },
                leadingIcon = if (on) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else null
            )
        }
    }
}

@Composable
internal fun StepSlider(
    label: String,
    value: Int,
    range: IntRange,
    valueText: @Composable (Int) -> String,
    onCommit: (Int) -> Unit
) {
    var drag by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                valueText(drag.roundToInt()),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = drag,
            onValueChange = { drag = it },
            onValueChangeFinished = { onCommit(drag.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0)
        )
    }
}

@Composable
internal fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun ToggleRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun InfoBanner(text: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = scheme.tertiaryContainer) {
        Text(
            text,
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onTertiaryContainer
        )
    }
}

@Composable
internal fun LineChart(
    values: List<Float>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    thresholdValue: Float? = null,
    thresholdLabel: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    if (values.size < 2) {
        Box(
            modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Text(
                alertText(R.string.alerts_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }
        return
    }
    val measurer = rememberTextMeasurer()
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(values) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val line = scheme.primary
    val grid = scheme.outlineVariant.copy(alpha = 0.6f)
    val warn = scheme.error
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.surfaceContainerHighest)
        ) {
            val padX = 16.dp.toPx()
            val padTop = 18.dp.toPx()
            val padBottom = 14.dp.toPx()
            var lo = values.min()
            var hi = values.max()
            if (thresholdValue != null) {
                lo = min(lo, thresholdValue)
                hi = max(hi, thresholdValue)
            }
            val span = (hi - lo).coerceAtLeast(1f)
            lo -= span * 0.1f
            hi += span * 0.1f
            val n = values.size
            fun xOf(i: Int): Float = padX + (size.width - 2 * padX) * i / (n - 1).toFloat()
            fun yOf(v: Float): Float = padTop + (size.height - padTop - padBottom) * (1f - (v - lo) / (hi - lo))
            for (g in 0..2) {
                val y = padTop + (size.height - padTop - padBottom) * g / 2f
                drawLine(grid, Offset(padX, y), Offset(size.width - padX, y), 1.dp.toPx())
            }
            val curve = Path()
            val fill = Path()
            for (i in 0 until n) {
                val x = xOf(i)
                val y = yOf(values[i])
                if (i == 0) {
                    curve.moveTo(x, y)
                    fill.moveTo(x, y)
                } else {
                    curve.lineTo(x, y)
                    fill.lineTo(x, y)
                }
            }
            fill.lineTo(xOf(n - 1), size.height)
            fill.lineTo(xOf(0), size.height)
            fill.close()
            clipRect(0f, 0f, size.width * reveal.value, size.height) {
                drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.35f), Color.Transparent)))
                drawPath(curve, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (thresholdValue != null) {
                val y = yOf(thresholdValue)
                drawLine(
                    warn,
                    Offset(padX, y),
                    Offset(size.width - padX, y),
                    1.5f.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                )
                if (thresholdLabel != null) {
                    val m = measurer.measure(thresholdLabel, labelStyle.copy(color = warn))
                    drawText(m, topLeft = Offset(size.width - padX - m.size.width, (y - m.size.height - 2.dp.toPx()).coerceAtLeast(2.dp.toPx())))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            xLabels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant) }
        }
    }
}
