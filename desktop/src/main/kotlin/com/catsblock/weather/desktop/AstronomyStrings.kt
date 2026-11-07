package com.catsblock.weather.desktop

private fun row(key: String, vararg values: String): Pair<String, Array<out String>> = key to values

internal val astronomyStrings: Map<String, Array<out String>> = mapOf(
    row("Solar noon", "Meio-dia solar", "Meio-dia solar", "Mediodía solar", "Midi solaire", "Солнечный полдень", "Sonnenhöchststand", "Mezzogiorno solare", "태양 남중", "Tengah hari surya"),
    row("Daylight", "Duração do dia", "Duração do dia", "Duración del día", "Durée du jour", "Длина дня", "Tageslänge", "Durata del giorno", "낮 길이", "Lama siang"),
    row("First light", "Primeira luz", "Primeira luz", "Primera luz", "Première lumière", "Начало рассвета", "Erstes Licht", "Prima luce", "첫 빛", "Cahaya pertama"),
    row("Last light", "Última luz", "Última luz", "Última luz", "Dernière lumière", "Конец сумерек", "Letztes Licht", "Ultima luce", "마지막 빛", "Cahaya terakhir"),
    row("Moonrise", "Nascer da Lua", "Nascer da Lua", "Salida de la Luna", "Lever de la Lune", "Восход Луны", "Mondaufgang", "Sorgere della Luna", "월출", "Bulan terbit"),
    row("Moonset", "Pôr da Lua", "Pôr da Lua", "Puesta de la Luna", "Coucher de la Lune", "Заход Луны", "Monduntergang", "Tramonto della Luna", "월몰", "Bulan terbenam"),
    row("Moon transit", "Passagem da Lua", "Passagem da Lua", "Tránsito lunar", "Passage de la Lune", "Кульминация Луны", "Mond-Kulmination", "Transito lunare", "달 남중", "Transit Bulan"),
    row("Moon age", "Idade da Lua", "Idade da Lua", "Edad de la Luna", "Âge de la Lune", "Возраст Луны", "Mondalter", "Età della Luna", "달의 나이", "Usia Bulan"),
    row("Next full moon", "Próxima lua cheia", "Próxima lua cheia", "Próxima luna llena", "Prochaine pleine lune", "Следующее полнолуние", "Nächster Vollmond", "Prossima luna piena", "다음 보름달", "Bulan purnama berikutnya"),
    row("Next new moon", "Próxima lua nova", "Próxima lua nova", "Próxima luna nueva", "Prochaine nouvelle lune", "Следующее новолуние", "Nächster Neumond", "Prossima luna nuova", "다음 신월", "Bulan baru berikutnya"),
    row("%s days", "%s dias", "%s dias", "%s días", "%s jours", "%s дн.", "%s Tage", "%s giorni", "%s일", "%s hari"),
    row("%d h %02d min", "%d h %02d min", "%d h %02d min", "%d h %02d min", "%d h %02d min", "%d ч %02d мин", "%d Std. %02d Min.", "%d h %02d min", "%d시간 %02d분", "%d jam %02d mnt"),
    row("Precipitation (mm/h)", "Precipitação (mm/h)", "Precipitação (mm/h)", "Precipitación (mm/h)", "Précipitations (mm/h)", "Осадки (мм/ч)", "Niederschlag (mm/h)", "Precipitazioni (mm/h)", "강수 (mm/h)", "Curah hujan (mm/jam)"),
    row("Wind (km/h)", "Vento (km/h)", "Vento (km/h)", "Viento (km/h)", "Vent (km/h)", "Ветер (км/ч)", "Wind (km/h)", "Vento (km/h)", "바람 (km/h)", "Angin (km/jam)")
)
