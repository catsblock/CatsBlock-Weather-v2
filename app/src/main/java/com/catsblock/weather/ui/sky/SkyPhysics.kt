package com.catsblock.weather.ui.sky

import kotlin.math.acos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

internal object SkyPhysics {

    const val MIE_COEFFICIENT = 0.3 * 21e-6
    const val EXPOSURE = 0.6

    private const val EARTH_RADIUS = 6371000.0
    private const val ATMOSPHERE_RADIUS = 6471000.0
    private const val RAYLEIGH_HEIGHT = 7994.0
    private const val MIE_HEIGHT = 1200.0
    private const val OZONE_HEIGHT = 15000.0
    private val rayleigh = doubleArrayOf(5.8e-6, 13.5e-6, 33.1e-6)
    private val ozone = doubleArrayOf(0.65e-6, 1.881e-6, 0.085e-6)

    private fun airmass(cosZenith: Double): Double {
        val c = cosZenith.coerceIn(0.0, 1.0)
        val zenithDeg = Math.toDegrees(acos(c))
        return 1.0 / (c + 0.50572 * (96.07995 - zenithDeg).coerceAtLeast(0.01).pow(-1.6364))
    }

    fun sunTransmittance(sunY: Double): FloatArray {
        val m = airmass(sunY)
        return FloatArray(3) { i ->
            exp(
                -(rayleigh[i] * RAYLEIGH_HEIGHT + MIE_COEFFICIENT * 1.11 * MIE_HEIGHT + ozone[i] * OZONE_HEIGHT) * m
            ).toFloat()
        }
    }

    fun zenithAmbient(sun: DoubleArray, steps: Int): FloatArray {
        val originY = EARTH_RADIUS + 2.0
        val length = -originY + sqrt(originY * originY - (originY * originY - ATMOSPHERE_RADIUS * ATMOSPHERE_RADIUS))
        val mu = sun[1]
        val rayleighPhase = 3.0 / (16.0 * Math.PI) * (1.0 + mu * mu)
        val g = 0.76
        val miePhase = 3.0 / (8.0 * Math.PI) * ((1.0 - g * g) * (1.0 + mu * mu)) /
            ((2.0 + g * g) * (1.0 + g * g - 2.0 * g * mu).pow(1.5))
        var opticalR = 0.0
        var opticalM = 0.0
        val sumR = DoubleArray(3)
        val sumM = DoubleArray(3)
        for (i in 0 until steps) {
            val u = (i + 0.5) / steps
            val t = length * u * u
            val dt = length * 2.0 * u / steps
            val py = originY + t
            val r = py
            val h = (r - EARTH_RADIUS).coerceAtLeast(0.0)
            val er = exp(-h / RAYLEIGH_HEIGHT)
            val em = exp(-h / MIE_HEIGHT)
            val hr = er * dt
            val hm = em * dt
            opticalR += hr
            opticalM += hm
            val cz = (py * sun[1]) / r
            val m = airmass(cz)
            val shadow = (cz * 20.0 + 1.0).coerceIn(0.0, 1.0)
            for (c in 0 until 3) {
                val tauSun = (rayleigh[c] * RAYLEIGH_HEIGHT * er + MIE_COEFFICIENT * 1.11 * MIE_HEIGHT * em +
                    ozone[c] * OZONE_HEIGHT) * m
                val tauView = rayleigh[c] * opticalR + MIE_COEFFICIENT * 1.11 * opticalM +
                    ozone[c] * OZONE_HEIGHT * (opticalR / RAYLEIGH_HEIGHT)
                val attenuation = exp(-(tauView + tauSun)) * shadow
                sumR[c] += attenuation * hr
                sumM[c] += attenuation * hm
            }
        }
        return FloatArray(3) { c ->
            (20.0 * (sumR[c] * rayleigh[c] * rayleighPhase + sumM[c] * MIE_COEFFICIENT * miePhase) * EXPOSURE).toFloat()
        }
    }
}
