package com.catsblock.weather.i18n

fun nova(en: String): String? {
    val l = I18n.lang
    if (l == AppLanguage.English) return null
    return N[en]?.getOrNull(l.ordinal - 1) ?: fresh(en)
}

private fun n(en: String, br: String, pt: String, es: String, fr: String, ru: String, de: String) =
    en to arrayOf(br, pt, es, fr, ru, de)

private val N: Map<String, Array<String>> = mapOf(
    n("Your location", "Sua localização", "A sua localização", "Tu ubicación", "Votre position", "Ваше местоположение", "Dein Standort"),
    n("Rain radar", "Radar de chuva", "Radar de chuva", "Radar de lluvia", "Radar de pluie", "Радар осадков", "Regenradar"),
    n("Blowing to", "Soprando para", "A soprar para", "Soplando hacia", "Soufflant vers", "Дует на", "Weht nach"),
    n("Theme", "Tema", "Tema", "Tema", "Thème", "Тема", "Design"),
    n("Clouds", "Nuvens", "Nuvens", "Nubes", "Nuages", "Облака", "Wolken"),
    n("Rain", "Chuva", "Chuva", "Lluvia", "Pluie", "Дождь", "Regen"),
    n("Weather API", "API Weather", "API Weather", "API Weather", "API Weather", "API Weather", "Weather API"),
    n("Visual API", "API Visual", "API Visual", "API Visual", "API Visual", "API Visual", "Visual API"),
    n("Free limit used", "Limite gratuito usado", "Limite gratuito usado", "Límite gratuito usado", "Limite gratuite utilisée", "Использовано бесплатного лимита", "Kostenloses Limit verbraucht"),
    n("Monthly limit used", "Limite mensal usado", "Limite mensal usado", "Límite mensual usado", "Limite mensuelle utilisée", "Использовано месячного лимита", "Monatslimit verbraucht"),
    n("requests left", "pedidos restantes", "pedidos restantes", "solicitudes restantes", "requêtes restantes", "запросов осталось", "Anfragen übrig"),
    n("Available again in", "Disponível novamente em", "Disponível novamente em", "Disponible de nuevo en", "De nouveau disponible dans", "Снова доступно через", "Wieder verfügbar in"),
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
