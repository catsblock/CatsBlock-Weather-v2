package com.catsblock.weather.i18n

import java.util.Locale

enum class AppLanguage(val code: String, val label: String, val locale: Locale) {
    English("en", "English", Locale.ENGLISH),
    PtBR("pt-BR", "Português (BR)", Locale("pt", "BR")),
    PtPT("pt-PT", "Português (PT)", Locale("pt", "PT")),
    Spanish("es", "Español", Locale("es")),
    French("fr", "Français", Locale.FRENCH),
    Russian("ru", "Русский", Locale("ru")),
    German("de", "Deutsch", Locale.GERMAN);

    companion object {
        fun from(code: String?) = entries.firstOrNull { it.code == code } ?: English
    }
}

object I18n {
    @Volatile
    var lang: AppLanguage = AppLanguage.English
}

/** Wrap any English UI literal with tr("...") and add it to T below. Order: pt-BR, pt-PT, es, fr, ru, de. */
fun tr(en: String): String {
    val l = I18n.lang
    if (l == AppLanguage.English) return en
    return T[en]?.getOrNull(l.ordinal - 1) ?: en
}

private fun s(en: String, a: String, b: String, c: String, d: String, e: String, f: String) =
    en to arrayOf(a, b, c, d, e, f)

private val T: Map<String, Array<String>> = mapOf(
    s("Settings", "Configurações", "Definições", "Ajustes", "Paramètres", "Настройки", "Einstellungen"),
    s("Back", "Voltar", "Voltar", "Atrás", "Retour", "Назад", "Zurück"),
    s("Units", "Unidades", "Unidades", "Unidades", "Unités", "Единицы", "Einheiten"),
    s("Temperature", "Temperatura", "Temperatura", "Temperatura", "Température", "Температура", "Temperatur"),
    s("Celsius", "Celsius", "Celsius", "Celsius", "Celsius", "Цельсий", "Celsius"),
    s("Fahrenheit", "Fahrenheit", "Fahrenheit", "Fahrenheit", "Fahrenheit", "Фаренгейт", "Fahrenheit"),
    s("Updates and sounds", "Atualizações e sons", "Atualizações e sons", "Actualizaciones y sonidos", "Mises à jour et sons", "Обновления и звуки", "Updates und Töne"),
    s("Mobile data updates", "Atualizar com dados móveis", "Atualizar com dados móveis", "Actualizar con datos móviles", "Mise à jour en données mobiles", "Обновление по мобильным данным", "Updates über mobile Daten"),
    s("Allow weather updates over mobile data. When off, updates only run on Wi-Fi.",
        "Permite atualizar o clima com dados móveis. Desativado, só atualiza no Wi-Fi.",
        "Permite atualizar o tempo com dados móveis. Desativado, só atualiza por Wi-Fi.",
        "Permite actualizar el clima con datos móviles. Si está desactivado, solo se actualiza con Wi-Fi.",
        "Autorise la mise à jour météo en données mobiles. Désactivé, seulement en Wi-Fi.",
        "Разрешить обновление погоды по мобильным данным. Если выключено — только по Wi-Fi.",
        "Wetter-Updates über mobile Daten erlauben. Aus: nur per WLAN."),
    s("Weather sounds", "Sons do clima", "Sons do tempo", "Sonidos del clima", "Sons météo", "Звуки погоды", "Wettergeräusche"),
    s("Play ambient sounds that match the current weather.",
        "Toca sons ambiente conforme o clima atual.",
        "Reproduz sons ambiente conforme o tempo atual.",
        "Reproduce sonidos ambientales según el clima actual.",
        "Joue des sons d'ambiance selon la météo actuelle.",
        "Воспроизводить фоновые звуки в зависимости от погоды.",
        "Spielt Umgebungsgeräusche passend zum aktuellen Wetter."),
    s("App language", "Idioma do app", "Idioma da app", "Idioma de la app", "Langue de l'app", "Язык приложения", "App-Sprache"),
    s("Privacy", "Privacidade", "Privacidade", "Privacidad", "Confidentialité", "Конфиденциальность", "Datenschutz"),
    s("Location permission", "Permissão de localização", "Permissão de localização", "Permiso de ubicación", "Autorisation de localisation", "Доступ к геолокации", "Standortberechtigung"),
    s("Used only to find the weather where you are. Stored on your device only.",
        "Usada apenas para achar o clima onde você está. Fica só no seu dispositivo.",
        "Usada apenas para obter o tempo onde está. Fica só no seu dispositivo.",
        "Solo se usa para mostrar el clima donde estás. Se guarda solo en tu dispositivo.",
        "Sert uniquement à afficher la météo où vous êtes. Stockée uniquement sur votre appareil.",
        "Используется только для определения погоды рядом с вами. Хранится только на устройстве.",
        "Wird nur genutzt, um das Wetter an Ihrem Ort zu zeigen. Nur auf dem Gerät gespeichert."),
    s("Network access", "Acesso à rede", "Acesso à rede", "Acceso a la red", "Accès réseau", "Доступ к сети", "Netzwerkzugriff"),
    s("City coordinates are sent to Open-Meteo. The radar map loads from Windy. Media is downloaded from public CDNs.",
        "As coordenadas da cidade vão para o Open-Meteo. O mapa de radar vem do Windy. Vídeos e sons são baixados de CDNs públicas.",
        "As coordenadas da cidade são enviadas ao Open-Meteo. O mapa de radar vem do Windy. Vídeos e sons são descarregados de CDNs públicas.",
        "Las coordenadas de la ciudad se envían a Open-Meteo. El mapa de radar viene de Windy. Vídeos y sonidos se descargan de CDN públicas.",
        "Les coordonnées de la ville sont envoyées à Open-Meteo. La carte radar vient de Windy. Vidéos et sons sont téléchargés depuis des CDN publics.",
        "Координаты города отправляются в Open-Meteo. Радар загружается из Windy. Видео и звуки скачиваются с публичных CDN.",
        "Stadtkoordinaten gehen an Open-Meteo. Die Radarkarte kommt von Windy. Videos und Töne werden von öffentlichen CDNs geladen."),
    s("Data stored on your device", "Dados no seu dispositivo", "Dados no seu dispositivo", "Datos en tu dispositivo", "Données sur votre appareil", "Данные на устройстве", "Daten auf dem Gerät"),
    s("Saved cities and your choices. No accounts, analytics or ads.",
        "Cidades salvas e suas escolhas. Sem contas, análises ou anúncios.",
        "Cidades guardadas e as suas escolhas. Sem contas, análises ou anúncios.",
        "Ciudades guardadas y tus ajustes. Sin cuentas, analíticas ni anuncios.",
        "Villes enregistrées et vos choix. Aucun compte, analyse ni publicité.",
        "Сохранённые города и ваши настройки. Без аккаунтов, аналитики и рекламы.",
        "Gespeicherte Städte und Ihre Auswahl. Keine Konten, Analysen oder Werbung."),
    s("Check for updates", "Verificar atualizações", "Procurar atualizações", "Buscar actualizaciones", "Rechercher des mises à jour", "Проверить обновления", "Nach Updates suchen"),
    s("Your app has not yet been checked to see if it is fully up to date or not.",
        "Seu app ainda não foi verificado para saber se está totalmente atualizado.",
        "A sua app ainda não foi verificada para saber se está totalmente atualizada.",
        "Tu app aún no se ha comprobado para saber si está totalmente actualizada.",
        "Votre application n'a pas encore été vérifiée pour savoir si elle est à jour.",
        "Приложение ещё не проверялось на наличие обновлений.",
        "Ihre App wurde noch nicht daraufhin geprüft, ob sie auf dem neuesten Stand ist."),
    s("Checking for updates...", "Verificando atualizações...", "A procurar atualizações...", "Buscando actualizaciones...", "Recherche de mises à jour...", "Проверка обновлений...", "Suche nach Updates..."),
    s("Your app is up to date!", "Seu app está atualizado!", "A sua app está atualizada!", "¡Tu app está actualizada!", "Votre application est à jour !", "Приложение обновлено!", "Ihre App ist auf dem neuesten Stand!"),
    s("Update", "Atualizar", "Atualizar", "Actualizar", "Mettre à jour", "Обновить", "Aktualisieren"),
    s("Your app is outdated. Update it to receive bug fixes and more features.",
        "Seu app está desatualizado. Atualize para receber correções de bugs e mais recursos.",
        "A sua app está desatualizada. Atualize para receber correções de erros e mais funcionalidades.",
        "Tu app está desactualizada. Actualízala para recibir correcciones de errores y más funciones.",
        "Votre application est obsolète. Mettez-la à jour pour obtenir des corrections et plus de fonctionnalités.",
        "Приложение устарело. Обновите его, чтобы получить исправления ошибок и новые функции.",
        "Ihre App ist veraltet. Aktualisieren Sie, um Fehlerbehebungen und neue Funktionen zu erhalten."),
    s("Version", "Versão", "Versão", "Versión", "Version", "Версия", "Version"),
    s("Downloading update...", "Baixando atualização...", "A descarregar atualização...", "Descargando actualización...", "Téléchargement de la mise à jour...", "Загрузка обновления...", "Update wird geladen..."),
    s("Allow installing apps from unknown sources to update the app.",
        "Permita instalar apps de fontes desconhecidas para atualizar o app.",
        "Permita instalar apps de fontes desconhecidas para atualizar a app.",
        "Permite instalar apps de fuentes desconocidas para actualizar la app.",
        "Autorisez l'installation d'apps de sources inconnues pour mettre à jour l'application.",
        "Разрешите установку приложений из неизвестных источников, чтобы обновить приложение.",
        "Erlauben Sie die Installation aus unbekannten Quellen, um die App zu aktualisieren."),
    s("Open settings", "Abrir configurações", "Abrir definições", "Abrir ajustes", "Ouvrir les paramètres", "Открыть настройки", "Einstellungen öffnen"),
    s("Could not check for updates. Tap to try again.",
        "Não foi possível verificar. Toque para tentar de novo.",
        "Não foi possível verificar. Toque para tentar novamente.",
        "No se pudo comprobar. Toca para reintentar.",
        "Vérification impossible. Touchez pour réessayer.",
        "Не удалось проверить. Нажмите, чтобы повторить.",
        "Prüfung fehlgeschlagen. Tippen zum Wiederholen."),
    s("Download failed. Tap to try again.",
        "Falha no download. Toque para tentar de novo.",
        "Falha na transferência. Toque para tentar novamente.",
        "Falló la descarga. Toca para reintentar.",
        "Échec du téléchargement. Touchez pour réessayer.",
        "Ошибка загрузки. Нажмите, чтобы повторить.",
        "Download fehlgeschlagen. Tippen zum Wiederholen."),
    s("Sunny", "Ensolarado", "Soalheiro", "Soleado", "Ensoleillé", "Солнечно", "Sonnig"),
    s("Clear", "Limpo", "Limpo", "Despejado", "Dégagé", "Ясно", "Klar"),
    s("Mostly clear", "Predomínio de sol", "Maioritariamente limpo", "Mayormente despejado", "Plutôt dégagé", "Малооблачно", "Überwiegend klar"),
    s("Mostly cloudy", "Muito nublado", "Maioritariamente nublado", "Mayormente nublado", "Plutôt nuageux", "Облачно с прояснениями", "Überwiegend bewölkt"),
    s("Cloudy", "Nublado", "Nublado", "Nublado", "Nuageux", "Облачно", "Bewölkt"),
    s("Rain", "Chuva", "Chuva", "Lluvia", "Pluie", "Дождь", "Regen"),
    s("Thunderstorm", "Tempestade", "Trovoada", "Tormenta", "Orage", "Гроза", "Gewitter"),
    s("Snow", "Neve", "Neve", "Nieve", "Neige", "Снег", "Schnee"),
    s("Fog", "Neblina", "Nevoeiro", "Niebla", "Brouillard", "Туман", "Nebel"),
    s("Yesterday", "Ontem", "Ontem", "Ayer", "Hier", "Вчера", "Gestern"),
    s("Today", "Hoje", "Hoje", "Hoy", "Aujourd'hui", "Сегодня", "Heute"),
    s("Tomorrow", "Amanhã", "Amanhã", "Mañana", "Demain", "Завтра", "Morgen")
)
