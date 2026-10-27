package com.catsblock.weather.atmosphere

import android.graphics.RuntimeShader
import android.os.Build
import android.view.Choreographer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ContentDrawScope
import androidx.compose.ui.node.ModifierNodeElement

fun Modifier.atmosphericCloudLayer(config: AtmosphereConfig): Modifier = this.then(AtmosphericCloudElement(config))

private data class AtmosphericCloudElement(
    val config: AtmosphereConfig
) : ModifierNodeElement<AtmosphericCloudNode>() {
    override fun create(): AtmosphericCloudNode = AtmosphericCloudNode(config)

    override fun update(node: AtmosphericCloudNode) {
        node.update(config)
    }
}

private class AtmosphericCloudNode(
    private var config: AtmosphereConfig
) : Modifier.Node(), DrawModifierNode {
    private var shader: RuntimeShader? = null
    private var brush: ShaderBrush? = null
    private var startTimeNanos = 0L
    private var isAnimating = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val current = shader
            if (!isAttached || current == null) return
            if (startTimeNanos == 0L) startTimeNanos = frameTimeNanos
            current.setFloatUniform("uTime", (frameTimeNanos - startTimeNanos) / 1_000_000_000f)
            invalidateDraw()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onAttach() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            val created = RuntimeShader(AtmosphericShaderSources.CLOUDS)
            shader = created
            brush = ShaderBrush(created)
            applyConfig(created)
            startTimeNanos = 0L
            updateAnimationState()
        }
    }

    override fun onDetach() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        isAnimating = false
        shader = null
        brush = null
        startTimeNanos = 0L
    }

    fun update(next: AtmosphereConfig) {
        if (config == next) return
        val wasAnimating = config.cloudCoverFraction >= 0.055f
        config = next
        shader?.let(::applyConfig)
        val shouldAnimate = config.cloudCoverFraction >= 0.055f
        if (wasAnimating != shouldAnimate) updateAnimationState()
        invalidateDraw()
    }

    private fun updateAnimationState() {
        val shouldAnimate = config.cloudCoverFraction >= 0.055f && shader != null
        if (shouldAnimate && !isAnimating) {
            startTimeNanos = 0L
            isAnimating = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } else if (!shouldAnimate && isAnimating) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            isAnimating = false
            startTimeNanos = 0L
        }
    }

    private fun applyConfig(target: RuntimeShader) {
        target.setFloatUniform("uCloudCover", config.cloudCoverFraction)
        target.setFloatUniform("uWindKmh", config.windKmh)
        target.setFloatUniform("uSunElevation", config.sunElevation)
        target.setColorUniform("uSunColor", config.sunColorArgb)
        target.setColorUniform("uSkyTint", config.skyColorArgb)
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val activeShader = shader ?: return
        val activeBrush = brush ?: return
        activeShader.setFloatUniform("uResolution", size.width, size.height)
        drawRect(brush = activeBrush)
    }
}
