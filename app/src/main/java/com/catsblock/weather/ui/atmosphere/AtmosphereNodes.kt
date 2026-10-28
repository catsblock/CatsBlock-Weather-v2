package com.catsblock.weather.ui.atmosphere

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AtmosphereState {
    @Volatile var tod: Float = 0.5f
    @Volatile var cover: Float = 0f
    @Volatile var rain: Float = 0f
    @Volatile var storm: Float = 0f
    @Volatile var wind: Float = 0f
    @Volatile var windX: Float = 0f
    @Volatile var windZ: Float = 0f
    @Volatile var snow: Float = 0f
    @Volatile var altoChance: Float = 0f
    @Volatile var seed: Float = 0f
    @Volatile var sunTint: Int = 0
    @Volatile var pressure: Float = 0f
}

private const val RENDER_SCALE = 0.5f
private const val CLOCK_WRAP_MS = 100_000_000L

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Modifier.atmosphere(state: AtmosphereState): Modifier = this.then(AtmosphereElement(state))

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Modifier.glassRain(intensity: Float, seed: Float, snow: Float = 0f): Modifier = this.then(GlassRainElement(intensity, seed, snow))

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
    private val resolutionUniform = FloatArray(2)
    private val timeUniform = FloatArray(1)
    private val todUniform = FloatArray(1)
    private val coverUniform = FloatArray(1)
    private val rainUniform = FloatArray(1)
    private val stormUniform = FloatArray(1)
    private val windUniform = FloatArray(1)
    private val windVectorUniform = FloatArray(2)
    private val snowUniform = FloatArray(1)
    private val altoChanceUniform = FloatArray(1)
    private val seedUniform = FloatArray(1)
    private val pressureUniform = FloatArray(1)
    private val gradeSize = FloatArray(2)
    private var gradeShader: RuntimeShader? = null
    private var gradeW = 0
    private var gradeH = 0

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
        gradeShader = runCatching { RuntimeShader(AtmosphereAgsl.GRADE) }.getOrNull()
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
        gradeShader = null
    }

    override fun ContentDrawScope.draw() {
        val s = shader ?: return
        val l = layer ?: return
        val w = (size.width * RENDER_SCALE).toInt().coerceAtLeast(1)
        val h = (size.height * RENDER_SCALE).toInt().coerceAtLeast(1)
        layerWidth = w.toFloat()
        layerHeight = h.toFloat()
        val st = state
        resolutionUniform[0] = layerWidth
        resolutionUniform[1] = layerHeight
        timeUniform[0] = time
        todUniform[0] = st.tod
        coverUniform[0] = st.cover
        rainUniform[0] = st.rain
        stormUniform[0] = st.storm
        windUniform[0] = st.wind
        windVectorUniform[0] = st.windX
        windVectorUniform[1] = st.windZ
        snowUniform[0] = st.snow
        altoChanceUniform[0] = st.altoChance
        seedUniform[0] = st.seed
        pressureUniform[0] = st.pressure
        RuntimeShaderUniforms.setFloatUniform(s, "pressure", pressureUniform)
        RuntimeShaderUniforms.setFloatUniform(s, "resolution", resolutionUniform)
        RuntimeShaderUniforms.setFloatUniform(s, "time", timeUniform)
        RuntimeShaderUniforms.setFloatUniform(s, "tod", todUniform)
        RuntimeShaderUniforms.setFloatUniform(s, "cover", coverUniform)
        RuntimeShaderUniforms.setFloatUniform(s, "rain", rainUniform)
        RuntimeShaderUniforms.setFloatUniform(s, "storm", stormUniform)
        RuntimeShaderUniforms.setFloatUniform(s, "wind", windUniform)
        RuntimeShaderUniforms.setFloatUniform(s, "windVector", windVectorUniform)
        RuntimeShaderUniforms.setFloatUniform(s, "snow", snowUniform)
        RuntimeShaderUniforms.setFloatUniform(s, "altoChance", altoChanceUniform)
        RuntimeShaderUniforms.setFloatUniform(s, "seed", seedUniform)
        s.setColorUniform("sunTint", st.sunTint)
        val g = gradeShader
        if (g != null && (gradeW != w || gradeH != h)) {
            gradeW = w
            gradeH = h
            gradeSize[0] = layerWidth
            gradeSize[1] = layerHeight
            RuntimeShaderUniforms.setFloatUniform(g, "size", gradeSize)
            l.renderEffect = RenderEffect.createRuntimeShaderEffect(g, "content").asComposeRenderEffect()
        }
        l.record(this, layoutDirection, IntSize(w, h), recordBlock)
        scale(1f / RENDER_SCALE, 1f / RENDER_SCALE, Offset.Zero, compositeBlock)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private data class GlassRainElement(val intensity: Float, val seed: Float, val snow: Float) : ModifierNodeElement<GlassRainNode>() {
    override fun create(): GlassRainNode = GlassRainNode(intensity, seed, snow)
    override fun update(node: GlassRainNode) {
        node.configure(intensity, seed, snow)
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "glassRain"
    }
}

private const val TRAIL_POINTS = 10
private const val TRAIL_FADE = 2.6f
private const val DROP_LIFE = 8f
private const val TAP_TRAVEL = 48f
private const val TAP_TIME = 0.6f
private const val TRAIL_STEP = 8f

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class GlassRainNode(
    var intensity: Float,
    var seed: Float,
    var snow: Float
) : Modifier.Node(), DrawModifierNode, PointerInputModifierNode {

    private var shader: RuntimeShader? = null
    private var brush: ShaderBrush? = null
    private var snowMode = false
    private var time = 0f

    private val trail = FloatArray(TRAIL_POINTS * 3)
    private val trailUniform = FloatArray(TRAIL_POINTS * 4)
    private val resolutionUniform = FloatArray(2)
    private val timeUniform = FloatArray(1)
    private val seedUniform = FloatArray(1)
    private val rainUniform = FloatArray(1)
    private val snowUniform = FloatArray(1)
    private val dropStateUniform = FloatArray(4)
    private var head = 0
    private var pressed = false
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0f
    private var travel = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dropX = 0f
    private var dropY = 0f
    private var dropBirth = -1000f

    init {
        for (i in 0 until TRAIL_POINTS) trail[i * 3 + 2] = -1000f
    }

    fun configure(newIntensity: Float, newSeed: Float, newSnow: Float) {
        intensity = newIntensity
        seed = newSeed
        val modeChanged = (newSnow > 0f) != (snow > 0f)
        snow = newSnow
        if (isAttached && modeChanged) buildShader()
        if (isAttached) invalidateDraw()
    }

    private fun buildShader() {
        snowMode = snow > 0f
        val source = if (snowMode) AtmosphereAgsl.GLASS_SNOW else AtmosphereAgsl.GLASS
        val created = runCatching { RuntimeShader(source) }.getOrNull()
        shader = created
        brush = created?.let { ShaderBrush(it) }
    }

    override fun onAttach() {
        buildShader()
        coroutineScope.launch {
            while (isActive) {
                withFrameNanos { nanos ->
                    time = ((nanos / 1_000_000L) % CLOCK_WRAP_MS) / 1000f
                    if (shader != null) invalidateDraw()
                }
            }
        }
    }

    override fun onDetach() {
        brush = null
        shader = null
    }

    private fun addPoint(x: Float, y: Float) {
        head = (head + 1) % TRAIL_POINTS
        trail[head * 3] = x
        trail[head * 3 + 1] = y
        trail[head * 3 + 2] = time
        lastX = x
        lastY = y
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Initial || !snowMode) return
        val change = pointerEvent.changes.firstOrNull() ?: return
        val x = change.position.x
        val y = change.position.y
        when (pointerEvent.type) {
            PointerEventType.Press -> {
                pressed = true
                downX = x
                downY = y
                downTime = time
                travel = 0f
                addPoint(x, y)
            }
            PointerEventType.Move -> if (pressed) {
                val dx = x - lastX
                val dy = y - lastY
                val dist = sqrt(dx * dx + dy * dy)
                travel += dist
                if (dist > TRAIL_STEP) addPoint(x, y)
            }
            PointerEventType.Release -> {
                if (pressed && travel < TAP_TRAVEL && time - downTime < TAP_TIME) {
                    dropX = downX
                    dropY = downY
                    dropBirth = time
                }
                pressed = false
            }
            else -> Unit
        }
        invalidateDraw()
    }

    override fun onCancelPointerInput() {
        pressed = false
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val s = shader ?: return
        val b = brush ?: return
        drawGlass(s, b)
    }

    private fun updateTouchUniforms(s: RuntimeShader) {
        if (pressed) trail[head * 3 + 2] = time
        for (i in 0 until TRAIL_POINTS) {
            val age = time - trail[i * 3 + 2]
            val k = (1f - age / TRAIL_FADE).coerceIn(0f, 1f)
            trailUniform[i * 4] = trail[i * 3]
            trailUniform[i * 4 + 1] = trail[i * 3 + 1]
            trailUniform[i * 4 + 2] = k * k * (3f - 2f * k)
            trailUniform[i * 4 + 3] = 0f
        }
        val dropAge = time - dropBirth
        dropStateUniform[0] = dropX
        dropStateUniform[1] = dropY
        dropStateUniform[2] = dropAge
        dropStateUniform[3] = if (dropAge in 0f..DROP_LIFE) 1f else 0f
        RuntimeShaderUniforms.setFloatUniform(s, "tp", trailUniform)
        RuntimeShaderUniforms.setFloatUniform(s, "dropState", dropStateUniform)
    }

    private fun ContentDrawScope.drawGlass(s: RuntimeShader, b: ShaderBrush) {
        resolutionUniform[0] = size.width
        resolutionUniform[1] = size.height
        timeUniform[0] = time
        seedUniform[0] = seed
        RuntimeShaderUniforms.setFloatUniform(s, "resolution", resolutionUniform)
        RuntimeShaderUniforms.setFloatUniform(s, "time", timeUniform)
        RuntimeShaderUniforms.setFloatUniform(s, "seed", seedUniform)
        if (snowMode) {
            snowUniform[0] = snow
            RuntimeShaderUniforms.setFloatUniform(s, "snow", snowUniform)
            updateTouchUniforms(s)
        } else {
            rainUniform[0] = intensity
            RuntimeShaderUniforms.setFloatUniform(s, "rain", rainUniform)
        }
        drawRect(b, size = size)
    }
}
