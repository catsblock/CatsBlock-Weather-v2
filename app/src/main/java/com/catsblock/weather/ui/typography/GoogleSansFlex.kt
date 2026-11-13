package com.catsblock.weather.ui.typography

import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private const val FAMILY_NAME = "Google Sans Flex"
private const val MIN_WEIGHT = 100
private const val MAX_WEIGHT = 700
private const val WEIGHT_STEP = 25

private fun variable(weight: Int): Font = Font(
    familyName = DeviceFontFamilyName(FAMILY_NAME),
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(100f),
        FontVariation.opticalSizing(48.sp)
    )
)

object GoogleSansFlex {

    val family: FontFamily = FontFamily(
        (MIN_WEIGHT..MAX_WEIGHT step WEIGHT_STEP).map { variable(it) }
    )

    val thin: FontFamily = FontFamily(variable(MIN_WEIGHT))

    fun weightAt(progress: Float, from: Int = 300, to: Int = MIN_WEIGHT): FontWeight {
        val t = progress.coerceIn(0f, 1f)
        val raw = from + (to - from) * t
        val snapped = (Math.round(raw / WEIGHT_STEP.toFloat()) * WEIGHT_STEP).coerceIn(MIN_WEIGHT, MAX_WEIGHT)
        return FontWeight(snapped)
    }
}
