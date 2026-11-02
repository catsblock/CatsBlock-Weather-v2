package com.catsblock.weather.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.weather.data.AgeGroup
import com.catsblock.weather.i18n.tr
import kotlin.math.abs

private const val WheelMin = 1
private const val WheelMax = 110
private const val WheelRows = 5
private val WheelRowHeight = 52.dp
private const val LinkToken = "{link}"

@Composable
fun AgeScreen(vm: WeatherViewModel) {
    val scheme = MaterialTheme.colorScheme
    val age by vm.pendingAge.collectAsStateWithLifecycle()
    val valid = AgeGroup.isValid(age)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.surface)))
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                tr("How old are you?"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            AgeWheel(age = age, onAgeChange = vm::setPendingAge)
            Spacer(Modifier.height(12.dp))
            AnimatedVisibility(visible = !valid) {
                Text(
                    tr(if (age < AgeGroup.MIN_AGE) "You must be at least 6 years old to use CatsBlock Weather." else "The maximum age is 100. Please choose a valid age."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            PrivacyNote(vm)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { vm.confirmAge(age) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(tr("Continue"), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PrivacyNote(vm: WeatherViewModel) {
    val scheme = MaterialTheme.colorScheme
    val template = tr("Your age information will never be collected, stored, or sold. Learn more in our {link}.")
    val before = template.substringBefore(LinkToken)
    val after = if (template.contains(LinkToken)) template.substringAfter(LinkToken) else ""
    Text(
        buildAnnotatedString {
            append(before)
            withLink(
                LinkAnnotation.Clickable(
                    "privacy",
                    TextLinkStyles(SpanStyle(color = scheme.primary, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline))
                ) { vm.openPrivacy(Screen.Age) }
            ) { append(tr("privacy policy")) }
            append(after)
        },
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun AgeWheel(age: Int, onAgeChange: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ages = remember { (WheelMin..WheelMax).toList() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (age - WheelMin).coerceIn(0, ages.lastIndex))
    val fling = rememberSnapFlingBehavior(state)
    val centered by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val middle = info.viewportSize.height / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - middle) }?.index ?: -1
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { centered }.collect { if (it >= 0) onAgeChange(WheelMin + it) }
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.widthIn(max = 220.dp).fillMaxWidth()
    ) {
        Box(Modifier.height(WheelRowHeight * WheelRows), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = scheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(WheelRowHeight)
            ) {}
            LazyColumn(
                state = state,
                flingBehavior = fling,
                contentPadding = PaddingValues(vertical = WheelRowHeight * (WheelRows / 2)),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(ages) { index, value ->
                    val distance = abs(index - centered)
                    Box(Modifier.fillMaxWidth().height(WheelRowHeight), contentAlignment = Alignment.Center) {
                        Text(
                            value.toString(),
                            fontSize = if (distance == 0) 30.sp else 22.sp,
                            fontWeight = if (distance == 0) FontWeight.Black else FontWeight.Medium,
                            color = if (distance == 0) scheme.onPrimaryContainer else scheme.onSurfaceVariant.copy(alpha = if (distance == 1) 0.7f else 0.4f)
                        )
                    }
                }
            }
        }
    }
}
