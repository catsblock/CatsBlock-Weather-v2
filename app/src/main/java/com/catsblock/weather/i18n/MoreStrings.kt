package com.catsblock.weather.i18n

fun more(en: String): String? {
    val l = I18n.lang
    if (l == AppLanguage.English) return null
    return M[en]?.getOrNull(l.ordinal - 1)
}

private fun m(en: String, br: String, pt: String, es: String, fr: String, ru: String, de: String) =
    en to arrayOf(br, pt, es, fr, ru, de)

private val M: Map<String, Array<String>> = mapOf(
    m("Weather alerts", "Alertas meteorológicos", "Alertas meteorológicos", "Alertas meteorológicas", "Alertes météo", "Погодные предупреждения", "Wetterwarnungen"),
    m("Weather alert", "Alerta meteorológico", "Alerta meteorológico", "Alerta meteorológica", "Alerte météo", "Погодное предупреждение", "Wetterwarnung"),
    m("more", "mais", "mais", "más", "de plus", "ещё", "weitere"),
    m("Source", "Fonte", "Fonte", "Fuente", "Source", "Источник", "Quelle"),
    m("What to do", "O que fazer", "O que fazer", "Qué hacer", "Que faire", "Что делать", "Was zu tun ist"),
    m("Issued by", "Emitido por", "Emitido por", "Emitido por", "Émis par", "Выпущено:", "Herausgegeben von"),
    m("Open official page", "Abrir página oficial", "Abrir página oficial", "Abrir página oficial", "Ouvrir la page officielle", "Открыть официальную страницу", "Offizielle Seite öffnen"),
    m("From", "De", "De", "Desde", "À partir de", "С", "Ab"),
    m("Until", "Até", "Até", "Hasta", "Jusqu'à", "До", "Bis"),
    m("Severe", "Severo", "Severo", "Severo", "Sévère", "Сильная", "Schwer"),
    m("Minor", "Leve", "Ligeiro", "Leve", "Mineure", "Незначительная", "Gering"),
    m("Unknown", "Desconhecido", "Desconhecido", "Desconocido", "Inconnu", "Неизвестно", "Unbekannt"),
    m("Immediate", "Imediato", "Imediato", "Inmediato", "Immédiat", "Немедленно", "Sofort"),
    m("Expected", "Esperado", "Esperado", "Esperado", "Attendu", "Ожидается", "Erwartet"),
    m("Future", "Futuro", "Futuro", "Futuro", "Futur", "В будущем", "Zukünftig"),
    m("Past", "Passado", "Passado", "Pasado", "Passé", "Уже прошло", "Vergangen"),
    m("Observed", "Observado", "Observado", "Observado", "Observé", "Наблюдается", "Beobachtet"),
    m("Likely", "Provável", "Provável", "Probable", "Probable", "Вероятно", "Wahrscheinlich"),
    m("Possible", "Possível", "Possível", "Posible", "Possible", "Возможно", "Möglich"),
    m("Unlikely", "Improvável", "Improvável", "Improbable", "Peu probable", "Маловероятно", "Unwahrscheinlich"),
    m("Wind speed", "Velocidade do vento", "Velocidade do vento", "Velocidad del viento", "Vitesse du vent", "Скорость ветра", "Windgeschwindigkeit"),
    m("Atmospheric pressure", "Pressão atmosférica", "Pressão atmosférica", "Presión atmosférica", "Pression atmosphérique", "Атмосферное давление", "Luftdruck"),
    m("Update weather in background", "Atualizar o clima em segundo plano", "Atualizar o tempo em segundo plano", "Actualizar el clima en segundo plano", "Actualiser la météo en arrière-plan", "Обновлять погоду в фоне", "Wetter im Hintergrund aktualisieren"),
    m("Refresh the weather automatically, even when the app is closed.", "Atualiza o clima automaticamente, mesmo com o app fechado.", "Atualiza o tempo automaticamente, mesmo com a app fechada.", "Actualiza el clima automáticamente, incluso con la app cerrada.", "Actualise la météo automatiquement, même lorsque l'app est fermée.", "Автоматически обновлять погоду, даже когда приложение закрыто.", "Aktualisiert das Wetter automatisch, auch wenn die App geschlossen ist."),
    m("Update interval", "Intervalo de atualização", "Intervalo de atualização", "Intervalo de actualización", "Intervalle de mise à jour", "Интервал обновления", "Aktualisierungsintervall"),
    m("4 hours", "4 horas", "4 horas", "4 horas", "4 heures", "4 часа", "4 Stunden"),
    m("8 hours", "8 horas", "8 horas", "8 horas", "8 heures", "8 часов", "8 Stunden"),
    m("14 hours", "14 horas", "14 horas", "14 horas", "14 heures", "14 часов", "14 Stunden"),
    m("Speed in %s with direction", "Velocidade em %s com direção", "Velocidade em %s com direção", "Velocidad en %s con dirección", "Vitesse en %s avec direction", "Скорость в %s с направлением", "Geschwindigkeit in %s mit Richtung"),
    m("Kilopascals (kPa)", "Quilopascais (kPa)", "Quilopascais (kPa)", "Kilopascales (kPa)", "Kilopascals (kPa)", "Килопаскали (кПа)", "Kilopascal (kPa)"),
    m("Inches of mercury (inHg)", "Polegadas de mercúrio (inHg)", "Polegadas de mercúrio (inHg)", "Pulgadas de mercurio (inHg)", "Pouces de mercure (inHg)", "Дюймы ртутного столба (inHg)", "Zoll Quecksilbersäule (inHg)"),
    m("Millimeters of mercury (mmHg)", "Milímetros de mercúrio (mmHg)", "Milímetros de mercúrio (mmHg)", "Milímetros de mercurio (mmHg)", "Millimètres de mercure (mmHg)", "Миллиметры ртутного столба (мм рт. ст.)", "Millimeter Quecksilbersäule (mmHg)"),
    m("Weather data source", "Fonte dos dados do clima", "Fonte dos dados meteorológicos", "Fuente de datos del clima", "Source des données météo", "Источник данных о погоде", "Wetterdatenquelle"),
    m("Open-Meteo (free, no key)", "Open-Meteo (grátis, sem chave)", "Open-Meteo (grátis, sem chave)", "Open-Meteo (gratis, sin clave)", "Open-Meteo (gratuit, sans clé)", "Open-Meteo (бесплатно, без ключа)", "Open-Meteo (kostenlos, ohne Schlüssel)"),
    m("Default. Free and works without an API key.", "Padrão. Gratuita e funciona sem chave de API.", "Predefinição. Gratuita e funciona sem chave de API.", "Predeterminada. Gratuita y funciona sin clave de API.", "Par défaut. Gratuite et sans clé API.", "По умолчанию. Бесплатно и без ключа API.", "Standard. Kostenlos und ohne API-Schlüssel."),
    m("Forecast from WeatherAPI.com. Free plans cover a shorter forecast range.", "Previsão do WeatherAPI.com. Planos gratuitos cobrem menos dias de previsão.", "Previsão do WeatherAPI.com. Os planos gratuitos cobrem menos dias de previsão.", "Pronóstico de WeatherAPI.com. Los planes gratuitos cubren menos días de pronóstico.", "Prévisions de WeatherAPI.com. Les offres gratuites couvrent moins de jours.", "Прогноз от WeatherAPI.com. Бесплатные тарифы охватывают меньше дней.", "Vorhersage von WeatherAPI.com. Kostenlose Tarife decken weniger Tage ab."),
    m("Forecast from Visual Crossing. Falls back to Open-Meteo if a request fails.", "Previsão do Visual Crossing. Usa o Open-Meteo se uma requisição falhar.", "Previsão do Visual Crossing. Usa o Open-Meteo se um pedido falhar.", "Pronóstico de Visual Crossing. Usa Open-Meteo si falla una solicitud.", "Prévisions de Visual Crossing. Bascule sur Open-Meteo si une requête échoue.", "Прогноз от Visual Crossing. При ошибке запроса используется Open-Meteo.", "Vorhersage von Visual Crossing. Bei einem Fehler wird Open-Meteo genutzt."),
    m("Not available in this build", "Indisponível nesta versão", "Indisponível nesta versão", "No disponible en esta versión", "Indisponible dans cette version", "Недоступно в этой сборке", "In diesem Build nicht verfügbar"),
    m("Low pressure. Unsettled weather is possible.", "Pressão baixa. O tempo pode ficar instável.", "Pressão baixa. O tempo pode ficar instável.", "Presión baja. Es posible tiempo inestable.", "Basse pression. Temps instable possible.", "Низкое давление. Возможна нестабильная погода.", "Niedriger Luftdruck. Unbeständiges Wetter möglich."),
    m("Normal pressure.", "Pressão normal.", "Pressão normal.", "Presión normal.", "Pression normale.", "Нормальное давление.", "Normaler Luftdruck."),
    m("High pressure. Stable weather is likely.", "Pressão alta. O tempo tende a ficar estável.", "Pressão alta. O tempo tende a ficar estável.", "Presión alta. Es probable tiempo estable.", "Haute pression. Temps stable probable.", "Высокое давление. Вероятна стабильная погода.", "Hoher Luftdruck. Stabiles Wetter wahrscheinlich."),
    m("Very poor visibility. Drive carefully.", "Visibilidade muito baixa. Dirija com cuidado.", "Visibilidade muito reduzida. Conduza com cuidado.", "Visibilidad muy mala. Conduce con cuidado.", "Visibilité très mauvaise. Conduisez prudemment.", "Очень плохая видимость. Будьте осторожны за рулём.", "Sehr schlechte Sicht. Fahren Sie vorsichtig."),
    m("Poor visibility.", "Visibilidade baixa.", "Visibilidade reduzida.", "Visibilidad mala.", "Mauvaise visibilité.", "Плохая видимость.", "Schlechte Sicht."),
    m("Moderate visibility.", "Visibilidade moderada.", "Visibilidade moderada.", "Visibilidad moderada.", "Visibilité modérée.", "Средняя видимость.", "Mäßige Sicht."),
    m("Excellent visibility.", "Visibilidade excelente.", "Visibilidade excelente.", "Visibilidad excelente.", "Excellente visibilité.", "Отличная видимость.", "Ausgezeichnete Sicht."),
    m("Current location", "Local atual", "Localização atual", "Ubicación actual", "Position actuelle", "Текущее местоположение", "Aktueller Standort")
)
