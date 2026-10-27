package com.catsblock.weather.ui.atmosphere

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AtmosphereState {
    @Volatile var tod: Float = 0.5f
    @Volatile var cover: Float = 0f
    @Volatile var rain: Float = 0f
    @Volatile var storm: Float = 0f
    @Volatile var wind: Float = 0f
    @Volatile var altoChance: Float = 0f
    @Volatile var seed: Float = 0f
    @Volatile var sunTint: Int = 0
}

private const val RENDER_SCALE = 0.5f
private const val CLOCK_WRAP_MS = 100_000_000L

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Modifier.atmosphere(state: AtmosphereState): Modifier = this.then(AtmosphereElement(state))

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Modifier.glassRain(intensity: Float, seed: Float): Modifier = this.then(GlassRainElement(intensity, seed))

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AtmosphereElement(val state: AtmosphereState) : ModifierNodeElement<AtmosphereNode>() {
    override fun create(): AtmosphereNode = AtmosphereNode(state)
    override fun update(node: AtmosphereNode) {
        node.state = state
    }
    override fun equals(other: Any?): Boolean = other is AtmosphereElement && other.state === state
    override fun hashCode(): Int = System.identityHashCode(state)
    override fun InspectorInfo.inspectableProperties() {
        name = "atmosphere"
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AtmosphereNode(var state: AtmosphereState) : Modifier.Node(), DrawModifierNode {

    private var shader: RuntimeShader? = null
    private var brush: ShaderBrush? = null
    private var layer: GraphicsLayer? = null
    private var time = 0f
    private var layerWidth = 1f
    private var layerHeight = 1f

    private val recordBlock: DrawScope.() -> Unit = {
        brush?.let { drawRect(it, size = Size(layerWidth, layerHeight)) }
    }

    private val compositeBlock: DrawScope.() -> Unit = {
        layer?.let { drawLayer(it) }
    }

    override fun onAttach() {
        val created = runCatching { RuntimeShader(AtmosphereAgsl.SOURCE) }.getOrNull() ?: return
        shader = created
        brush = ShaderBrush(created)
        layer = requireGraphicsContext().createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }
        coroutineScope.launch {
            while (isActive) {
                withFrameNanos { nanos ->
                    time = ((nanos / 1_000_000L) % CLOCK_WRAP_MS) / 1000f
                    invalidateDraw()
                }
            }
        }
    }

    override fun onDetach() {
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
        brush = null
        shader = null
    }

    override fun ContentDrawScope.draw() {
        val s = shader ?: return
        val l = layer ?: return
        val w = (size.width * RENDER_SCALE).toInt().coerceAtLeast(1)
        val h = (size.height * RENDER_SCALE).toInt().coerceAtLeast(1)
        layerWidth = w.toFloat()
        layerHeight = h.toFloat()
        val st = state
        s.setFloatUniform("resolution", layerWidth, layerHeight)
        s.setFloatUniform("time", time)
        s.setFloatUniform("tod", st.tod)
        s.setFloatUniform("cover", st.cover)
        s.setFloatUniform("rain", st.rain)
        s.setFloatUniform("storm", st.storm)
        s.setFloatUniform("wind", st.wind)
        s.setFloatUniform("altoChance", st.altoChance)
        s.setFloatUniform("seed", st.seed)
        s.setColorUniform("sunTint", st.sunTint)
        l.record(this, layoutDirection, IntSize(w, h), recordBlock)
        scale(1f / RENDER_SCALE, 1f / RENDER_SCALE, Offset.Zero, compositeBlock)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private data class GlassRainElement(val intensity: Float, val seed: Float) : ModifierNodeElement<GlassRainNode>() {
    override fun create(): GlassRainNode = GlassRainNode(intensity, seed)
    override fun update(node: GlassRainNode) {
        node.intensity = intensity
        node.seed = seed
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "glassRain"
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class GlassRainNode(var intensity: Float, var seed: Float) : Modifier.Node(), DrawModifierNode {

    private var shader: RuntimeShader? = null
    private var brush: ShaderBrush? = null
    private var time = 0f

    override fun onAttach() {
        val created = runCatching { RuntimeShader(AtmosphereAgsl.GLASS) }.getOrNull() ?: return
        shader = created
        brush = ShaderBrush(created)
        coroutineScope.launch {
            while (isActive) {
                withFrameNanos { nanos ->
                    time = ((nanos / 1_000_000L) % CLOCK_WRAP_MS) / 1000f
                    invalidateDraw()
                }
            }
        }
    }

    override fun onDetach() {
        brush = null
        shader = null
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val s = shader ?: return
        val b = brush ?: return
        s.setFloatUniform("resolution", size.width, size.height)
        s.setFloatUniform("time", time)
        s.setFloatUniform("rain", intensity)
        s.setFloatUniform("seed", seed)
        drawRect(b, size = size)
    }
}
