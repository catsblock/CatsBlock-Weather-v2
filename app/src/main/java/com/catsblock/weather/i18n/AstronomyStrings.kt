package com.catsblock.weather.i18n

private fun row(key: String, vararg values: String): Pair<String, Array<out String>> = key to values

internal val astronomyStrings: Map<String, Array<out String>> = mapOf(
    row("Moon transit", "Passagem da Lua", "Passagem da Lua", "Tránsito lunar", "Passage de la Lune", "Кульминация Луны", "Mond-Kulmination", "Transito lunare", "달 남중", "Transit Bulan")
)
