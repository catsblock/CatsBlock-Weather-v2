package com.catsblock.weather.data

import android.graphics.Bitmap

internal object RainViewerPalette {
    private data class Sample(val dbz: Int, val rgb: Int)

    private val providerSamples = listOf(
        Sample(-10, 0x636159),
        Sample(-9, 0x66635A),
        Sample(-8, 0x69665C),
        Sample(-7, 0x6C685D),
        Sample(-6, 0x6F6B5F),
        Sample(-5, 0x726E61),
        Sample(-4, 0x757062),
        Sample(-3, 0x787364),
        Sample(-2, 0x7C7565),
        Sample(-1, 0x7F7867),
        Sample(0, 0x827B69),
        Sample(1, 0x857D6A),
        Sample(2, 0x88806C),
        Sample(3, 0x8B826D),
        Sample(4, 0x8E856F),
        Sample(5, 0x928871),
        Sample(6, 0x9E9375),
        Sample(7, 0xAA9E79),
        Sample(8, 0xB6A97E),
        Sample(9, 0xC2B482),
        Sample(10, 0xCEC087),
        Sample(11, 0xD2C48B),
        Sample(12, 0xD6C88F),
        Sample(13, 0xDACC93),
        Sample(14, 0xDED097),
        Sample(15, 0x88DDEE),
        Sample(16, 0x6CD1EB),
        Sample(17, 0x51C5E8),
        Sample(18, 0x36BAE5),
        Sample(19, 0x1BAEE2),
        Sample(20, 0x00A3E0),
        Sample(21, 0x009AD5),
        Sample(22, 0x0091CA),
        Sample(23, 0x0088BF),
        Sample(24, 0x007FB4),
        Sample(25, 0x0077AA),
        Sample(26, 0x0070A3),
        Sample(27, 0x00699C),
        Sample(28, 0x006295),
        Sample(29, 0x005B8E),
        Sample(30, 0x005588),
        Sample(31, 0x005180),
        Sample(32, 0x004E78),
        Sample(33, 0x004A70),
        Sample(34, 0x004768),
        Sample(35, 0xFFEE00),
        Sample(36, 0xFFE000),
        Sample(37, 0xFFD200),
        Sample(38, 0xFFC500),
        Sample(39, 0xFFB700),
        Sample(40, 0xFFAA00),
        Sample(41, 0xFF9F00),
        Sample(42, 0xFF9500),
        Sample(43, 0xFF8B00),
        Sample(44, 0xFF8100),
        Sample(45, 0xFF4400),
        Sample(46, 0xF23600),
        Sample(47, 0xE62800),
        Sample(48, 0xD91B00),
        Sample(49, 0xCD0D00),
        Sample(50, 0xC10000),
        Sample(51, 0xA80000),
        Sample(52, 0x8F0000),
        Sample(53, 0x760000),
        Sample(54, 0x5D0000),
        Sample(55, 0xFFAAFF),
        Sample(56, 0xFF9FFF),
        Sample(57, 0xFF95FF),
        Sample(58, 0xFF8BFF),
        Sample(59, 0xFF81FF),
        Sample(60, 0xFF77FF),
        Sample(61, 0xFF6CFF),
        Sample(62, 0xFF62FF),
        Sample(63, 0xFF58FF),
        Sample(64, 0xFF4EFF),
        Sample(65, 0xFFFFFF),
        Sample(66, 0xFFFFFF),
        Sample(67, 0xFFFFFF),
        Sample(68, 0xFFFFFF),
        Sample(69, 0xFFFFFF),
        Sample(70, 0xFFFFFF),
        Sample(71, 0xFFFFFF),
        Sample(72, 0xFFFFFF),
        Sample(73, 0xFFFFFF),
        Sample(74, 0xFFFFFF),
        Sample(75, 0x00FF00),
        Sample(76, 0x00FF00),
        Sample(77, 0x00FF00),
        Sample(78, 0x00FF00),
        Sample(79, 0x00FF00),
        Sample(80, 0x00FF00),
        Sample(81, 0x00FF00),
        Sample(82, 0x00FF00),
        Sample(83, 0x00FF00),
        Sample(84, 0x00FF00),
        Sample(85, 0x00FF00),
        Sample(86, 0x00FF00),
        Sample(87, 0x00FF00),
        Sample(88, 0x00FF00),
        Sample(89, 0x00FF00),
        Sample(90, 0x00FF00),
        Sample(91, 0x00FF00),
        Sample(92, 0x00FF00),
        Sample(93, 0x00FF00),
        Sample(94, 0x00FF00),
        Sample(95, 0x00FF00)
    )

    val trackingColors = intArrayOf(0x88DDEE, 0x6CD1EB, 0x51C5E8, 0x36BAE5, 0x1BAEE2, 0x00A3E0, 0x009AD5, 0x0091CA, 0x0088BF, 0x007FB4, 0x0077AA, 0x0070A3, 0x00699C, 0x006295, 0x005B8E, 0x005588, 0x005180, 0x004E78, 0x004A70, 0x004768, 0xFFEE00, 0xFFE000, 0xFFD200, 0xFFC500, 0xFFB700, 0xFFAA00, 0xFF9F00, 0xFF9500, 0xFF8B00, 0xFF8100, 0xFF4400, 0xF23600, 0xE62800, 0xD91B00, 0xCD0D00, 0xC10000, 0xA80000, 0x8F0000, 0x760000, 0x5D0000, 0xFFAAFF, 0xFF9FFF, 0xFF95FF, 0xFF8BFF, 0xFF81FF, 0xFF77FF, 0xFF6CFF, 0xFF62FF, 0xFF58FF, 0xFF4EFF, 0xFFFFFF)
    private val targetStops = listOf(
        15 to 0x00DFF2,
        35 to 0x287BFF,
        45 to 0x8655D8,
        55 to 0xED5BAF,
        65 to 0xFFFFFF
    )

    fun samplesJson(): org.json.JSONArray = org.json.JSONArray().apply {
        providerSamples.forEach { put(org.json.JSONArray().put(it.dbz).put(it.rgb)) }
    }

    fun targetStopsJson(): org.json.JSONArray = org.json.JSONArray().apply {
        targetStops.forEach { put(org.json.JSONArray().put(it.first).put(it.second)) }
    }

    fun dbzOf(argb: Int): Int? {
        if ((argb ushr 24) < 16) return null
        val rgb = argb and 0xFFFFFF
        var best = Int.MAX_VALUE
        var answer: Int? = null
        providerSamples.forEach { sample ->
            val dr = ((rgb shr 16) and 255) - ((sample.rgb shr 16) and 255)
            val dg = ((rgb shr 8) and 255) - ((sample.rgb shr 8) and 255)
            val db = (rgb and 255) - (sample.rgb and 255)
            val distance = dr * dr + dg * dg + db * db
            if (distance < best) {
                best = distance
                answer = sample.dbz
            }
        }
        return answer?.takeIf { best <= 900 }
    }

    fun recolor(argb: Int): Int {
        val dbz = dbzOf(argb) ?: return argb
        if (dbz < targetStops.first().first) return argb and 0x00FFFFFF
        return (argb and -0x1000000) or colorAt(dbz)
    }

    fun recolor(bitmap: Bitmap): Bitmap {
        val source = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        for (index in pixels.indices) pixels[index] = recolor(pixels[index])
        return Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
    }

    private fun colorAt(dbz: Int): Int {
        if (dbz <= targetStops.first().first) return targetStops.first().second
        for (index in 1 until targetStops.size) {
            val (hiDbz, hiColor) = targetStops[index]
            if (dbz <= hiDbz) {
                val (loDbz, loColor) = targetStops[index - 1]
                val fraction = (dbz - loDbz).toFloat() / (hiDbz - loDbz)
                val r = (((loColor shr 16) and 255) + (((hiColor shr 16) and 255) - ((loColor shr 16) and 255)) * fraction).toInt()
                val g = (((loColor shr 8) and 255) + (((hiColor shr 8) and 255) - ((loColor shr 8) and 255)) * fraction).toInt()
                val b = ((loColor and 255) + ((hiColor and 255) - (loColor and 255)) * fraction).toInt()
                return (r shl 16) or (g shl 8) or b
            }
        }
        return targetStops.last().second
    }
}
