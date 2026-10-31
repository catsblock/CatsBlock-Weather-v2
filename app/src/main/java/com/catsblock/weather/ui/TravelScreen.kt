package com.catsblock.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.catsblock.weather.data.AppSettings
import com.catsblock.weather.data.City
import com.catsblock.weather.data.NetworkPolicy
import com.catsblock.weather.data.TravelException
import com.catsblock.weather.data.TravelPlan
import com.catsblock.weather.data.TravelPlanner
import com.catsblock.weather.data.TravelStop
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.components.ExpressiveLoader
import com.catsblock.weather.ui.detail.DetailDialog
import com.catsblock.weather.ui.detail.entrance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface TravelUi {
    data object Idle : TravelUi
    data object Loading : TravelUi
    data class Failed(val message: String) : TravelUi
    data class Ready(val plan: TravelPlan) : TravelUi
}

@Composable
fun TravelCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassCard(modifier.fillMaxWidth(), onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Route, null, Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                tr("What will the weather be like when you're traveling?"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable
fun TravelWeatherScreen(city: City?, settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var from by rememberSaveable { mutableStateOf(city?.name.orEmpty()) }
    var to by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<TravelUi>(TravelUi.Idle) }

    fun go() {
        focus.clearFocus()
        if (to.isBlank()) {
            state = TravelUi.Failed(tr("Enter a start and a destination."))
            return
        }
        if (!NetworkPolicy.foregroundAllowed(context, settings)) {
            state = TravelUi.Failed(tr("Mobile data updates are disabled. Connect to Wi-Fi or enable them in Settings."))
            return
        }
        state = TravelUi.Loading
        scope.launch {
            state = try {
                TravelUi.Ready(TravelPlanner.plan(from, to, city))
            } catch (e: CancellationException) {
                throw e
            } catch (e: TravelException) {
                TravelUi.Failed(tr(e.key))
            } catch (e: Exception) {
                TravelUi.Failed(tr("The route service is unavailable. Try again later."))
            }
        }
    }

    DetailDialog(
        title = tr("Travel weather"),
        onBack = onBack,
        tabs = emptyList(),
        selectedTab = 0,
        onTab = {},
        outlook = {
            PlacesCard(
                from = from,
                to = to,
                loading = state is TravelUi.Loading,
                onFrom = { from = it },
                onTo = { to = it },
                onSwap = {
                    val keep = from
                    from = to
                    to = keep
                },
                onGo = ::go
            )
        },
        navigator = {},
        body = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (val current = state) {
                    TravelUi.Idle -> HintCard(tr("Enter where you are leaving from and where you are going to see the weather along the way."))
                    TravelUi.Loading -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        ExpressiveLoader(size = 56.dp)
                    }
                    is TravelUi.Failed -> ErrorCard(current.message)
                    is TravelUi.Ready -> PlanBody(current.plan)
                }
            }
        }
    )
}

@Composable
private fun PlacesCard(
    from: String,
    to: String,
    loading: Boolean,
    onFrom: (String) -> Unit,
    onTo: (String) -> Unit,
    onSwap: () -> Unit,
    onGo: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .entrance(0)
            .background(scheme.surfaceContainerHigh, RoundedCornerShape(32.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = from,
                    onValueChange = onFrom,
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    leadingIcon = { Icon(Icons.Rounded.MyLocation, null) },
                    label = { Text(tr("Current location")) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = to,
                    onValueChange = onTo,
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    leadingIcon = { Icon(Icons.Rounded.Place, null) },
                    label = { Text(tr("Destination")) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onGo() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(onClick = onSwap) {
                Icon(Icons.Rounded.SwapVert, tr("Swap places"))
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onGo,
            enabled = !loading,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Rounded.Route, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(tr("Show weather on the route"))
        }
    }
}

@Composable
private fun HintCard(text: String) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().entrance(1)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Route, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(14.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun ErrorCard(text: String) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().entrance(1)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(14.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun PlanBody(plan: TravelPlan) {
    val unit = LocalUnits.current.precip
    RouteMapCard(plan)
    Row(Modifier.fillMaxWidth().entrance(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val peak = plan.stops.maxBy { it.precipMm }
        val lowest = plan.stops.minBy { it.visibilityKm }
        val cloudiest = plan.stops.maxBy { it.cloudPercent }
        SummaryTile(Icons.Rounded.Umbrella, tr("Peak precipitation"), precipText(peak.precipMm, unit), Modifier.weight(1f))
        SummaryTile(Icons.Rounded.Visibility, tr("Lowest visibility"), "${f1(lowest.visibilityKm)} km", Modifier.weight(1f))
        SummaryTile(Icons.Rounded.Cloud, tr("Highest cloud cover"), "${cloudiest.cloudPercent}%", Modifier.weight(1f))
    }
    Text(
        tr("Weather along the route"),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 8.dp, top = 6.dp)
    )
    plan.stops.forEach { stop ->
        StopBubble(plan, stop, unit)
    }
}

@Composable
private fun SummaryTile(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun RouteMapCard(plan: TravelPlan) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().entrance(1)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                Icon(Icons.Rounded.Route, null, tint = scheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("${plan.from.label} → ${plan.to.label}", style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(
                        "${plan.distanceKm.roundToInt()} km · ${formatWait(plan.durationMinutes * 60L)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            TravelMap(
                plan,
                Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
        }
    }
}

private class BubbleShape(private val tail: Dp, private val corner: Dp, private val tailTop: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val t = with(density) { tail.toPx() }
        val c = with(density) { corner.toPx() }
        val top = with(density) { tailTop.toPx() }
        val path = Path().apply {
            addRoundRect(RoundRect(t, 0f, size.width, size.height, CornerRadius(c, c)))
            moveTo(t + 2f, top)
            lineTo(0f, top + t * 0.9f)
            lineTo(t + 2f, top + t * 1.8f)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun StopBubble(plan: TravelPlan, stop: TravelStop, unit: com.catsblock.weather.data.PrecipUnit) {
    val scheme = MaterialTheme.colorScheme
    val wet = stop.precipMm >= 0.1
    val container = if (wet) scheme.primaryContainer else scheme.surfaceContainerHigh
    val content = if (wet) scheme.onPrimaryContainer else scheme.onSurface
    val last = stop.index == plan.stops.lastIndex
    val title = when (stop.index) {
        0 -> plan.from.label
        plan.stops.lastIndex -> plan.to.label
        else -> stop.name ?: String.format(I18n.lang.locale, tr("%1\$d km from start"), stop.distanceKm.roundToInt())
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).entrance(stop.index + 3)) {
        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = scheme.primary, modifier = Modifier.size(34.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${stop.index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (!last) {
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .width(4.dp)
                        .weight(1f)
                        .background(scheme.primary.copy(alpha = 0.3f), CircleShape)
                )
            }
        }
        Surface(
            shape = BubbleShape(12.dp, 28.dp, 10.dp),
            color = container,
            contentColor = content,
            modifier = Modifier.weight(1f).padding(bottom = if (last) 0.dp else 12.dp)
        ) {
            Column(Modifier.padding(start = 28.dp, end = 18.dp, top = 16.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(stop.kind, stop.isDay, Modifier.size(46.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        Text(
                            "${clockLabel(stop.arrival)} · ${stop.kind.label(stop.isDay)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = content.copy(alpha = 0.78f)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric(Icons.Rounded.Umbrella, tr("Precipitation"), precipText(stop.precipMm, unit), Modifier.weight(1f))
                    Metric(Icons.Rounded.Visibility, tr("Visibility"), "${f1(stop.visibilityKm)} km", Modifier.weight(1f))
                    Metric(Icons.Rounded.Cloud, tr("Cloud cover"), "${stop.cloudPercent}%", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Metric(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
