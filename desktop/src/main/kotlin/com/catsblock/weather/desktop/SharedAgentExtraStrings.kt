package com.catsblock.weather.desktop

private fun row(key: String, vararg values: String): Pair<String, Array<out String>> = key to values

internal val agentExtraStrings: Map<String, Array<out String>> = mapOf(
    row("Lowest", "Mais baixo", "Mais baixo", "Más bajo", "Plus bas", "Минимум", "Niedrigster", "Più basso", "최저", "Terendah"),
    row("Average", "Média", "Média", "Media", "Moyenne", "Среднее", "Durchschnitt", "Media", "평균", "Rata-rata"),
    row("Highest", "Mais alto", "Mais alto", "Más alto", "Plus haut", "Максимум", "Höchster", "Più alto", "최고", "Tertinggi"),
    row("3 days ago", "3 dias atrás", "Há 3 dias", "Hace 3 días", "Il y a 3 jours", "3 дня назад", "Vor 3 Tagen", "3 giorni fa", "3일 전", "3 hari lalu"),
    row("Message", "Mensagem", "Mensagem", "Mensaje", "Message", "Сообщение", "Nachricht", "Messaggio", "메시지", "Pesan")
)

internal fun ta(key: String): String {
    val index = LANGUAGES.indexOfFirst { it.first == uiLanguage.value }
    if (index <= 0) return t(key)
    return agentStrings[key]?.getOrNull(index - 1)?.takeIf { it.isNotEmpty() }
        ?: agentExtraStrings[key]?.getOrNull(index - 1)?.takeIf { it.isNotEmpty() }
        ?: t(key)
}
