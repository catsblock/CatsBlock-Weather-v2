package com.catsblock.weather.data

import android.content.res.Resources
import android.os.Build
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min

object DeviceFingerprint {

    private const val VERSION = "catsblock-device-v1"

    val id: String by lazy { compute() }

    private fun compute(): String {
        val metrics = Resources.getSystem().displayMetrics
        val traits = listOf(
            VERSION,
            Build.MANUFACTURER,
            Build.BRAND,
            Build.MODEL,
            Build.DEVICE,
            Build.PRODUCT,
            Build.BOARD,
            Build.HARDWARE,
            Build.SUPPORTED_ABIS.joinToString(","),
            Build.VERSION.SDK_INT.toString(),
            Runtime.getRuntime().availableProcessors().toString(),
            min(metrics.widthPixels, metrics.heightPixels).toString(),
            max(metrics.widthPixels, metrics.heightPixels).toString(),
            socTraits()
        )
        val digest = MessageDigest.getInstance("SHA-256").digest(traits.joinToString("|").toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun socTraits(): String =
        if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER}/${Build.SOC_MODEL}" else ""
}
