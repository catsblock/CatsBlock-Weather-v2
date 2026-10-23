// ===== GlassCard.kt =====
package com.catsblock.weather.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp


@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit
) {
    val backdrop = LocalBackdrop.current
    val registry = LocalCardRegistry.current
    val id = remember { Any() }
    val blurLayer = rememberGraphicsLayer()
    var pos by remember { mutableStateOf(Offset.Zero) }
    val canBlur = Build.VERSION.SDK_INT >= 31

    DisposableEffect(id) { onDispose { registry.rects.remove(id) } }

    Box(
        modifier
            .onGloballyPositioned {
                pos = it.positionInRoot()
                registry.rects[id] = it.boundsInRoot()
            }
            .clip(shape)
            .drawBehind {
                if (backdrop != null && canBlur) {
                    val pad = 64
                    val sz = IntSize(size.width.toInt() + pad * 2, size.height.toInt() + pad * 2)
                    blurLayer.renderEffect = BlurEffect(48f, 48f, TileMode.Clamp)
                    blurLayer.record(size = sz) {
                        translate(-pos.x + pad, -pos.y + pad) { drawLayer(backdrop) }
                    }
                    translate(-pad.toFloat(), -pad.toFloat()) { drawLayer(blurLayer) }
                }
                
                drawRect(Color.White.copy(alpha = if (canBlur) 0.13f else 0.28f))
            }
    ) {
        content()
    }
}
