package com.catsblock.weather.desktop

import androidx.compose.runtime.mutableStateOf
import java.util.Locale

val LANGUAGES = listOf(
    "en" to "English", "pt-BR" to "Português (BR)", "pt-PT" to "Português (PT)", "es" to "Español",
    "fr" to "Français", "ru" to "Русский", "de" to "Deutsch", "it" to "Italiano", "ko" to "한국어", "id" to "Bahasa Indonesia"
)

val uiLanguage = mutableStateOf("en")

private fun row(key: String, vararg values: String): Pair<String, List<String>> = key to values.toList()

private val STRINGS: Map<String, List<String>> = mapOf(
    row("Weather", "Clima", "Meteorologia", "Clima", "Météo", "Погода", "Wetter", "Meteo", "날씨", "Cuaca"),
    row("Settings", "Configurações", "Definições", "Ajustes", "Paramètres", "Настройки", "Einstellungen", "Impostazioni", "설정", "Pengaturan"),
    row("Widget", "Widget", "Widget", "Widget", "Widget", "Виджет", "Widget", "Widget", "위젯", "Widget"),
    row("Show floating widget", "Mostrar widget flutuante", "Mostrar widget flutuante", "Mostrar widget flotante", "Afficher le widget flottant", "Показывать плавающий виджет", "Schwebendes Widget anzeigen", "Mostra widget flottante", "플로팅 위젯 표시", "Tampilkan widget mengambang"),
    row("Widget color", "Cor do widget", "Cor do widget", "Color del widget", "Couleur du widget", "Цвет виджета", "Widget-Farbe", "Colore del widget", "위젯 색상", "Warna widget"),
    row("Follow the weather", "Seguir o clima", "Seguir o tempo", "Seguir el clima", "Suivre la météo", "По погоде", "Dem Wetter folgen", "Segui il meteo", "날씨에 따라 변경", "Ikuti cuaca"),
    row("Fixed color", "Cor fixa", "Cor fixa", "Color fijo", "Couleur fixe", "Фиксированный цвет", "Feste Farbe", "Colore fisso", "고정 색상", "Warna tetap"),
    row("Inactive opacity", "Opacidade inativa", "Opacidade inativa", "Opacidad inactiva", "Opacité inactive", "Прозрачность в покое", "Deckkraft im Ruhezustand", "Opacità inattiva", "비활성 투명도", "Opasitas nonaktif"),
    row("Return to inactive after", "Voltar a inativo após", "Voltar a inativo após", "Volver a inactivo tras", "Redevenir inactif après", "Становиться неактивным через", "Wieder inaktiv nach", "Torna inattivo dopo", "비활성 전환 시간", "Kembali nonaktif setelah"),
    row("Never", "Nunca", "Nunca", "Nunca", "Jamais", "Никогда", "Nie", "Mai", "안 함", "Tidak pernah"),
    row("Always on top", "Sempre no topo", "Sempre no topo", "Siempre visible", "Toujours au premier plan", "Поверх всех окон", "Immer im Vordergrund", "Sempre in primo piano", "항상 위에 표시", "Selalu di atas"),
    row("Temperature unit", "Unidade de temperatura", "Unidade de temperatura", "Unidad de temperatura", "Unité de température", "Единица температуры", "Temperatureinheit", "Unità di temperatura", "온도 단위", "Satuan suhu"),
    row("Celsius", "Celsius", "Celsius", "Celsius", "Celsius", "Цельсий", "Celsius", "Celsius", "섭씨", "Celsius"),
    row("Fahrenheit", "Fahrenheit", "Fahrenheit", "Fahrenheit", "Fahrenheit", "Фаренгейт", "Fahrenheit", "Fahrenheit", "화씨", "Fahrenheit"),
    row("Language", "Idioma", "Idioma", "Idioma", "Langue", "Язык", "Sprache", "Lingua", "언어", "Bahasa"),
    row("City", "Cidade", "Cidade", "Ciudad", "Ville", "Город", "Stadt", "Città", "도시", "Kota"),
    row("Search city", "Buscar cidade", "Pesquisar cidade", "Buscar ciudad", "Rechercher une ville", "Поиск города", "Stadt suchen", "Cerca città", "도시 검색", "Cari kota"),
    row("No results", "Sem resultados", "Sem resultados", "Sin resultados", "Aucun résultat", "Ничего не найдено", "Keine Ergebnisse", "Nessun risultato", "결과 없음", "Tidak ada hasil"),
    row("Choose a city to begin", "Escolha uma cidade para começar", "Escolha uma cidade para começar", "Elige una ciudad para empezar", "Choisissez une ville pour commencer", "Выберите город, чтобы начать", "Wähle eine Stadt, um zu beginnen", "Scegli una città per iniziare", "시작하려면 도시를 선택하세요", "Pilih kota untuk memulai"),
    row("Feels like", "Sensação térmica", "Sensação térmica", "Sensación térmica", "Ressenti", "Ощущается как", "Gefühlt", "Percepita", "체감", "Terasa seperti"),
    row("Humidity", "Umidade", "Humidade", "Humedad", "Humidité", "Влажность", "Luftfeuchtigkeit", "Umidità", "습도", "Kelembapan"),
    row("Wind", "Vento", "Vento", "Viento", "Vent", "Ветер", "Wind", "Vento", "바람", "Angin"),
    row("Hourly", "Por hora", "Por hora", "Por horas", "Par heure", "По часам", "Stündlich", "Orario", "시간별", "Per jam"),
    row("Daily", "Diário", "Diário", "Diario", "Par jour", "По дням", "Täglich", "Giornaliero", "일별", "Harian"),
    row("Loading…", "Carregando…", "A carregar…", "Cargando…", "Chargement…", "Загрузка…", "Wird geladen …", "Caricamento…", "불러오는 중…", "Memuat…"),
    row("Weather unavailable", "Clima indisponível", "Tempo indisponível", "Clima no disponible", "Météo indisponible", "Погода недоступна", "Wetter nicht verfügbar", "Meteo non disponibile", "날씨를 불러올 수 없음", "Cuaca tidak tersedia"),
    row("Retry", "Tentar novamente", "Tentar novamente", "Reintentar", "Réessayer", "Повторить", "Erneut versuchen", "Riprova", "다시 시도", "Coba lagi"),
    row("Open CatsBlock Weather", "Abrir CatsBlock Weather", "Abrir CatsBlock Weather", "Abrir CatsBlock Weather", "Ouvrir CatsBlock Weather", "Открыть CatsBlock Weather", "CatsBlock Weather öffnen", "Apri CatsBlock Weather", "CatsBlock Weather 열기", "Buka CatsBlock Weather"),
    row("Exit", "Sair", "Sair", "Salir", "Quitter", "Выход", "Beenden", "Esci", "종료", "Keluar"),
    row("Clear sky", "Céu limpo", "Céu limpo", "Cielo despejado", "Ciel dégagé", "Ясное небо", "Klarer Himmel", "Cielo sereno", "맑음", "Langit cerah"),
    row("Partly cloudy", "Parcialmente nublado", "Parcialmente nublado", "Parcialmente nublado", "Partiellement nuageux", "Переменная облачность", "Teilweise bewölkt", "Parzialmente nuvoloso", "구름 조금", "Berawan sebagian"),
    row("Cloudy", "Nublado", "Nublado", "Nublado", "Nuageux", "Облачно", "Bewölkt", "Nuvoloso", "흐림", "Berawan"),
    row("Fog", "Neblina", "Nevoeiro", "Niebla", "Brouillard", "Туман", "Nebel", "Nebbia", "안개", "Kabut"),
    row("Drizzle", "Garoa", "Chuvisco", "Llovizna", "Bruine", "Морось", "Nieselregen", "Pioviggine", "이슬비", "Gerimis"),
    row("Rain", "Chuva", "Chuva", "Lluvia", "Pluie", "Дождь", "Regen", "Pioggia", "비", "Hujan"),
    row("Snow", "Neve", "Neve", "Nieve", "Neige", "Снег", "Schnee", "Neve", "눈", "Salju"),
    row("Thunderstorm", "Tempestade", "Trovoada", "Tormenta", "Orage", "Гроза", "Gewitter", "Temporale", "뇌우", "Badai petir"),
    row("Today", "Hoje", "Hoje", "Hoy", "Aujourd'hui", "Сегодня", "Heute", "Oggi", "오늘", "Hari ini"),
    row("Air quality", "Qualidade do ar", "Qualidade do ar", "Calidad del aire", "Qualité de l'air", "Качество воздуха", "Luftqualität", "Qualità dell'aria", "대기질", "Kualitas udara"),
    row("UV index", "Índice UV", "Índice UV", "Índice UV", "Indice UV", "УФ-индекс", "UV-Index", "Indice UV", "자외선 지수", "Indeks UV"),
    row("Pressure", "Pressão", "Pressão", "Presión", "Pression", "Давление", "Luftdruck", "Pressione", "기압", "Tekanan"),
    row("Visibility", "Visibilidade", "Visibilidade", "Visibilidad", "Visibilité", "Видимость", "Sichtweite", "Visibilità", "가시거리", "Jarak pandang"),
    row("Precipitation", "Precipitação", "Precipitação", "Precipitación", "Précipitations", "Осадки", "Niederschlag", "Precipitazioni", "강수", "Curah hujan"),
    row("Moon", "Lua", "Lua", "Luna", "Lune", "Луна", "Mond", "Luna", "달", "Bulan"),
    row("Saved cities", "Cidades salvas", "Cidades guardadas", "Ciudades guardadas", "Villes enregistrées", "Сохранённые города", "Gespeicherte Städte", "Città salvate", "저장한 도시", "Kota tersimpan"),
    row("Pressure unit", "Unidade de pressão", "Unidade de pressão", "Unidad de presión", "Unité de pression", "Единица давления", "Druckeinheit", "Unità di pressione", "기압 단위", "Satuan tekanan"),
    row("Wind unit", "Unidade de vento", "Unidade de vento", "Unidad de viento", "Unité du vent", "Единица скорости ветра", "Windeinheit", "Unità del vento", "풍속 단위", "Satuan angin"),
    row("Theme", "Tema", "Tema", "Tema", "Thème", "Тема", "Design", "Tema", "테마", "Tema"),
    row("Light theme", "Claro", "Claro", "Claro", "Clair", "Светлая", "Hell", "Chiaro", "라이트", "Terang"),
    row("Dark", "Escuro", "Escuro", "Oscuro", "Sombre", "Тёмная", "Dunkel", "Scuro", "다크", "Gelap"),
    row("System", "Sistema", "Sistema", "Sistema", "Système", "Системная", "System", "Sistema", "시스템", "Sistem"),
    row("Good", "Bom", "Bom", "Bueno", "Bon", "Хороший", "Gut", "Buona", "좋음", "Baik"),
    row("Moderate", "Moderada", "Moderada", "Moderada", "Modérée", "Умеренная", "Mäßig", "Moderata", "보통", "Sedang"),
    row("Poor", "Ruim", "Mau", "Malo", "Mauvais", "Плохой", "Schlecht", "Scarsa", "나쁨", "Buruk"),
    row("Low", "Baixo", "Baixo", "Bajo", "Faible", "Низкий", "Niedrig", "Basso", "낮음", "Rendah"),
    row("High", "Alto", "Alto", "Alto", "Élevé", "Высокий", "Hoch", "Alto", "높음", "Tinggi"),
    row("Very high", "Muito alto", "Muito alto", "Muy alto", "Très élevé", "Очень высокий", "Sehr hoch", "Molto alto", "매우 높음", "Sangat tinggi"),
    row("Extreme", "Extrema", "Extrema", "Extrema", "Extrême", "Экстремальная", "Extrem", "Estrema", "극심", "Ekstrem"),
    row("Very poor", "Muito ruim", "Muito fraca", "Muy mala", "Très mauvaise", "Очень плохая", "Sehr schlecht", "Molto scarsa", "매우 나쁨", "Sangat buruk"),
    row("New moon", "Lua nova", "Lua nova", "Luna nueva", "Nouvelle lune", "Новолуние", "Neumond", "Luna nuova", "신월", "Bulan baru"),
    row("Full moon", "Lua cheia", "Lua cheia", "Luna llena", "Pleine lune", "Полнолуние", "Vollmond", "Luna piena", "보름달", "Bulan purnama"),
    row("Waxing crescent", "Lua crescente", "Lua crescente", "Luna creciente", "Premier croissant", "Растущий серп", "Zunehmende Sichel", "Luna crescente", "초승달", "Sabit awal"),
    row("First quarter", "Quarto crescente", "Quarto crescente", "Cuarto creciente", "Premier quartier", "Первая четверть", "Erstes Viertel", "Primo quarto", "상현달", "Kuartir pertama"),
    row("Waxing gibbous", "Crescente gibosa", "Crescente gibosa", "Gibosa creciente", "Gibbeuse croissante", "Растущая Луна", "Zunehmender Mond", "Gibbosa crescente", "차오르는 볼록달", "Cembung awal"),
    row("Waning gibbous", "Minguante gibosa", "Minguante gibosa", "Gibosa menguante", "Gibbeuse décroissante", "Убывающая Луна", "Abnehmender Mond", "Gibbosa calante", "기우는 볼록달", "Cembung akhir"),
    row("Last quarter", "Quarto minguante", "Quarto minguante", "Cuarto menguante", "Dernier quartier", "Последняя четверть", "Letztes Viertel", "Ultimo quarto", "하현달", "Kuartir terakhir"),
    row("Waning crescent", "Lua minguante", "Lua minguante", "Luna menguante", "Dernier croissant", "Убывающий серп", "Abnehmende Sichel", "Luna calante", "그믐달", "Sabit akhir"),
    row("Mostly clear", "Predomínio de sol", "Maioritariamente limpo", "Mayormente despejado", "Plutôt dégagé", "Малооблачно", "Überwiegend klar", "Prevalentemente sereno", "대체로 맑음", "Sebagian besar cerah"),
    row("Mostly cloudy", "Muito nublado", "Maioritariamente nublado", "Mayormente nublado", "Plutôt nuageux", "Облачно с прояснениями", "Überwiegend bewölkt", "Prevalentemente nuvoloso", "대체로 흐림", "Sebagian besar berawan"),
    row("Wintry mix", "Chuva com gelo", "Chuva gelada", "Aguanieve", "Pluie verglaçante", "Дождь со снегом", "Schneeregen", "Pioggia e neve", "비 또는 눈", "Hujan dan salju"),
    row("Hail", "Granizo", "Granizo", "Granizo", "Grêle", "Град", "Hagel", "Grandine", "우박", "Hujan es"),
    row("Heavy snow", "Neve forte", "Neve forte", "Nevada fuerte", "Forte neige", "Сильный снег", "Starker Schneefall", "Forte nevicata", "폭설", "Salju lebat"),
    row("Blizzard", "Nevasca", "Nevão", "Ventisca", "Blizzard", "Метель", "Schneesturm", "Bufera di neve", "눈보라", "Badai salju"),
    row("Sunny", "Ensolarado", "Soalheiro", "Soleado", "Ensoleillé", "Солнечно", "Sonnig", "Soleggiato", "화창함", "Cerah"),
    row("Clear", "Limpo", "Limpo", "Despejado", "Dégagé", "Ясно", "Klar", "Sereno", "맑음", "Langit cerah"),
    row("Units", "Unidades", "Unidades", "Unidades", "Unités", "Единицы", "Einheiten", "Unità", "단위", "Satuan"),
    row("Appearance", "Aparência", "Aparência", "Apariencia", "Apparence", "Оформление", "Darstellung", "Aspetto", "화면", "Tampilan"),
    row("Back", "Voltar", "Voltar", "Atrás", "Retour", "Назад", "Zurück", "Indietro", "뒤로", "Kembali"),
    row("Cancel", "Cancelar", "Cancelar", "Cancelar", "Annuler", "Отмена", "Abbrechen", "Annulla", "취소", "Batal"),
    row("Next 36 hours", "Próximas 36 horas", "Próximas 36 horas", "Próximas 36 horas", "36 prochaines heures", "Ближайшие 36 часов", "Nächste 36 Stunden", "Prossime 36 ore", "향후 36시간", "36 jam ke depan"),
    row("Sunrise", "Nascer do sol", "Nascer do sol", "Amanecer", "Lever du soleil", "Восход", "Sonnenaufgang", "Alba", "일출", "Matahari terbit"),
    row("Sunset", "Pôr do sol", "Pôr do sol", "Atardecer", "Coucher du soleil", "Закат", "Sonnenuntergang", "Tramonto", "일몰", "Matahari terbenam"),
    row("Remove", "Remover", "Remover", "Eliminar", "Supprimer", "Удалить", "Entfernen", "Rimuovi", "삭제", "Hapus"),
    row("Wind speed", "Velocidade do vento", "Velocidade do vento", "Velocidad del viento", "Vitesse du vent", "Скорость ветра", "Windgeschwindigkeit", "Velocità del vento", "풍속", "Kecepatan angin"),
    row("Start with Windows", "Iniciar com o Windows", "Iniciar com o Windows", "Iniciar con Windows", "Lancer avec Windows", "Запускать вместе с Windows", "Mit Windows starten", "Avvia con Windows", "Windows 시작 시 실행", "Mulai bersama Windows"),
    row("Get started", "Começar", "Começar", "Empezar", "Commencer", "Начать", "Los geht's", "Inizia", "시작하기", "Mulai"),
    row("Continue", "Continuar", "Continuar", "Continuar", "Continuer", "Продолжить", "Weiter", "Continua", "계속", "Lanjutkan"),
    row("Finish", "Concluir", "Concluir", "Finalizar", "Terminer", "Готово", "Fertig", "Fine", "완료", "Selesai"),
    row("Add city", "Adicionar cidade", "Adicionar cidade", "Añadir ciudad", "Ajouter une ville", "Добавить город", "Stadt hinzufügen", "Aggiungi città", "도시 추가", "Tambah kota"),
    row("Illumination", "Iluminação", "Iluminação", "Iluminación", "Illumination", "Освещённость", "Beleuchtung", "Illuminazione", "조명률", "Iluminasi"),
    row("Rain expected within %d h", "Chuva prevista em até %d h", "Chuva prevista em até %d h", "Lluvia prevista en %d h", "Pluie prévue d'ici %d h", "Дождь ожидается в течение %d ч", "Regen in den nächsten %d h erwartet", "Pioggia prevista entro %d h", "%d시간 내 비 예상", "Hujan diperkirakan dalam %d jam"),
    row("No rain expected soon", "Sem chuva prevista em breve", "Sem chuva prevista em breve", "No se espera lluvia pronto", "Pas de pluie prévue prochainement", "Дождь в ближайшее время не ожидается", "Kein Regen in Kürze erwartet", "Nessuna pioggia prevista a breve", "곧 비 소식 없음", "Tidak ada hujan dalam waktu dekat"),
    row("Weather background", "Fundo do clima", "Fundo do tempo", "Fondo del clima", "Arrière-plan météo", "Фон погоды", "Wetterhintergrund", "Sfondo meteo", "날씨 배경", "Latar cuaca"),
    row("Widget size", "Tamanho do widget", "Tamanho do widget", "Tamaño del widget", "Taille du widget", "Размер виджета", "Widget-Größe", "Dimensione del widget", "위젯 크기", "Ukuran widget"),
    row("Gusts", "Rajadas", "Rajadas", "Ráfagas", "Rafales", "Порывы", "Böen", "Raffiche", "돌풍", "Embusan"),
    row("Radar", "Radar", "Radar", "Radar", "Radar", "Радар", "Radar", "Radar", "레이더", "Radar"),
    row("Satellite", "Satélite", "Satélite", "Satélite", "Satellite", "Спутник", "Satellit", "Satellite", "위성", "Satelit"),
    row("Running in the background", "Em execução em segundo plano", "A executar em segundo plano", "Ejecutándose en segundo plano", "S'exécute en arrière-plan", "Работает в фоне", "Läuft im Hintergrund", "In esecuzione in background", "백그라운드에서 실행 중", "Berjalan di latar belakang")
)

fun t(key: String): String {
    val index = LANGUAGES.indexOfFirst { it.first == uiLanguage.value }
    if (index <= 0) return androidEnglish[key] ?: key
    return STRINGS[key]?.getOrNull(index - 1)?.takeIf { it.isNotEmpty() }
        ?: androidTable[key]?.getOrNull(index - 1)?.takeIf { it.isNotEmpty() }
        ?: androidEnglish[key] ?: key
}

fun uiLocale(): Locale = Locale.forLanguageTag(uiLanguage.value)
