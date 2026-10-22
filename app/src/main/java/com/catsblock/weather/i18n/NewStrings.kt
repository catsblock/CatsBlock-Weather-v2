package com.catsblock.weather.i18n

fun nova(en: String): String? {
    val l = I18n.lang
    if (l == AppLanguage.English) return null
    return N[en]?.getOrNull(l.ordinal - 1)
}

private fun n(en: String, br: String, pt: String, es: String, fr: String, ru: String, de: String) =
    en to arrayOf(br, pt, es, fr, ru, de)

private val N: Map<String, Array<String>> = mapOf(
    n("Clouds", "Nuvens", "Nuvens", "Nubes", "Nuages", "Облака", "Wolken"),
    n("Rain", "Chuva", "Chuva", "Lluvia", "Pluie", "Дождь", "Regen"),
    n("Close", "Fechar", "Fechar", "Cerrar", "Fermer", "Закрыть", "Schließen"),
    n(
        "Blood Moon · total lunar eclipse",
        "Lua de Sangue · eclipse lunar total",
        "Lua de Sangue · eclipse lunar total",
        "Luna de sangre · eclipse lunar total",
        "Lune de sang · éclipse lunaire totale",
        "Кровавая Луна · полное лунное затмение",
        "Blutmond · totale Mondfinsternis"
    ),
    n(
        "Partial lunar eclipse",
        "Eclipse lunar parcial",
        "Eclipse lunar parcial",
        "Eclipse lunar parcial",
        "Éclipse lunaire partielle",
        "Частное лунное затмение",
        "Partielle Mondfinsternis"
    ),
    n(
        "Penumbral lunar eclipse",
        "Eclipse lunar penumbral",
        "Eclipse lunar penumbral",
        "Eclipse lunar penumbral",
        "Éclipse lunaire pénombrale",
        "Полутеневое лунное затмение",
        "Halbschattenfinsternis"
    )
)
