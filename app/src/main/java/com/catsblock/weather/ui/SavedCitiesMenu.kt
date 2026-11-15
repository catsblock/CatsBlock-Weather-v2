package com.catsblock.weather.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.weather.data.City
import com.catsblock.weather.data.CitySummary
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.typography.GoogleSansFlex

@Composable
fun SavedCitiesMenu(vm: WeatherViewModel, modifier: Modifier = Modifier) {
    val saved by vm.savedCities.collectAsStateWithLifecycle()
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val city by vm.city.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "citiesButtonPress"
    )
    Box(modifier) {
        Surface(
            shape = CircleShape,
            color = Color(0x2EFFFFFF),
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
            modifier = Modifier
                .clip(CircleShape)
                .graphicsLayer {
                    scaleX = press
                    scaleY = press
                }
        ) {
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            expanded = true
                        }
                    )
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (expanded) Icons.Rounded.LocationCity else Icons.Outlined.LocationCity,
                    contentDescription = tr("Saved cities"),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                if (saved.isNotEmpty()) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${saved.size}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandHorizontally(
                        spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                        expandFrom = Alignment.Start
                    ) + fadeIn(),
                    exit = shrinkHorizontally(
                        spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                        shrinkTowards = Alignment.Start
                    ) + fadeOut()
                ) {
                    Text(
                        tr("Saved cities"),
                        modifier = Modifier.padding(start = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, 8.dp),
            shape = RoundedCornerShape(28.dp),
            containerColor = scheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Column(Modifier.widthIn(min = 236.dp, max = 320.dp).padding(vertical = 8.dp)) {
                Text(
                    tr("Saved cities"),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                if (saved.isEmpty()) {
                    Text(
                        tr("No saved cities yet"),
                        modifier = Modifier.padding(horizontal = 18.dp).padding(top = 4.dp, bottom = 10.dp),
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    CityMenuAction(Icons.Rounded.Add, tr("Add a city")) {
                        expanded = false
                        vm.openPicker()
                    }
                } else {
                    saved.forEach { entry ->
                        CityMenuRow(
                            city = entry,
                            summary = summaries[entry.id],
                            current = entry.id == city?.id,
                            fahrenheit = settings.fahrenheit,
                            onSelect = {
                                expanded = false
                                vm.selectCity(entry)
                            }
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        color = scheme.outlineVariant.copy(alpha = 0.7f)
                    )
                    CityMenuAction(Icons.Rounded.Tune, tr("Manage cities")) {
                        expanded = false
                        vm.openCities()
                    }
                }
            }
        }
    }
}

@Composable
private fun CityMenuRow(
    city: City,
    summary: CitySummary?,
    current: Boolean,
    fahrenheit: Boolean,
    onSelect: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (current) scheme.primary.copy(alpha = 0.12f) else Color.Transparent
    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                city.name,
                color = if (current) scheme.primary else scheme.onSurface,
                fontFamily = GoogleSansFlex.family,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val detail = summary?.let {
                "${tempText(it.temp, fahrenheit)} · ${it.kind.label(it.isDay)}"
            } ?: city.country
            Text(
                detail,
                color = scheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (current) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Check, contentDescription = tr("Current city"), tint = scheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CityMenuAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = scheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
