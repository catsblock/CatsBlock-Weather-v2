package com.catsblock.weather.i18n

private fun row(key: String, vararg values: String): Pair<String, Array<out String>> = key to values

internal val radarMapStrings: Map<String, Array<out String>> = mapOf(
    row("Zoom in", "Aumentar zoom", "Ampliar", "Acercar", "Zoom avant", "Приблизить", "Vergrößern", "Ingrandisci", "확대", "Perbesar"),
    row("Zoom out", "Diminuir zoom", "Reduzir", "Alejar", "Zoom arrière", "Отдалить", "Verkleinern", "Riduci", "축소", "Perkecil"),
    row("Loading radar…", "Carregando radar…", "A carregar radar…", "Cargando radar…", "Chargement du radar…", "Загрузка радара…", "Radar wird geladen…", "Caricamento radar…", "레이더 불러오는 중…", "Memuat radar…"),
    row("Radar data unavailable", "Dados de radar indisponíveis", "Dados de radar indisponíveis", "Datos de radar no disponibles", "Données radar indisponibles", "Данные радара недоступны", "Radardaten nicht verfügbar", "Dati radar non disponibili", "레이더 데이터를 사용할 수 없음", "Data radar tidak tersedia"),
    row(
        "Could not load the map. Check your connection and try again.",
        "Não foi possível carregar o mapa. Verifique sua conexão e tente novamente.",
        "Não foi possível carregar o mapa. Verifique a ligação e tente novamente.",
        "No se pudo cargar el mapa. Comprueba tu conexión e inténtalo de nuevo.",
        "Impossible de charger la carte. Vérifiez votre connexion et réessayez.",
        "Не удалось загрузить карту. Проверьте подключение и повторите попытку.",
        "Die Karte konnte nicht geladen werden. Prüfe deine Verbindung und versuche es erneut.",
        "Impossibile caricare la mappa. Controlla la connessione e riprova.",
        "지도를 불러올 수 없습니다. 연결을 확인하고 다시 시도하세요.",
        "Tidak dapat memuat peta. Periksa koneksi Anda dan coba lagi."
    )
)

internal val radarMapAdditionalStrings: Map<String, Array<String>> = mapOf(
    "Zoom in" to arrayOf("ज़ूम इन", "Powiększ", "تكبير", "I-zoom in", "Μεγέθυνση", "Inzoomen", "Mărește", "Phóng to", "放大"),
    "Zoom out" to arrayOf("ज़ूम आउट", "Pomniejsz", "تصغير", "I-zoom out", "Σμίκρυνση", "Uitzoomen", "Micșorează", "Thu nhỏ", "缩小"),
    "Loading radar…" to arrayOf("रडार लोड हो रहा है…", "Wczytywanie radaru…", "جارٍ تحميل الرادار…", "Nilo-load ang radar…", "Φόρτωση ραντάρ…", "Radar laden…", "Se încarcă radarul…", "Đang tải radar…", "正在加载雷达…"),
    "Radar data unavailable" to arrayOf("रडार डेटा उपलब्ध नहीं है", "Dane radarowe niedostępne", "بيانات الرادار غير متاحة", "Hindi available ang data ng radar", "Τα δεδομένα ραντάρ δεν είναι διαθέσιμα", "Radargegevens niet beschikbaar", "Datele radar nu sunt disponibile", "Không có dữ liệu radar", "雷达数据不可用"),
    "Could not load the map. Check your connection and try again." to arrayOf(
        "मानचित्र लोड नहीं हो सका। अपना कनेक्शन जाँचें और फिर प्रयास करें।",
        "Nie udało się wczytać mapy. Sprawdź połączenie i spróbuj ponownie.",
        "تعذّر تحميل الخريطة. تحقق من اتصالك وحاول مرة أخرى.",
        "Hindi ma-load ang mapa. Tingnan ang iyong koneksyon at subukan muli.",
        "Δεν ήταν δυνατή η φόρτωση του χάρτη. Ελέγξτε τη σύνδεσή σας και δοκιμάστε ξανά.",
        "De kaart kon niet worden geladen. Controleer je verbinding en probeer het opnieuw.",
        "Harta nu a putut fi încărcată. Verifică conexiunea și încearcă din nou.",
        "Không thể tải bản đồ. Hãy kiểm tra kết nối và thử lại.",
        "无法加载地图。请检查网络连接后重试。"
    )
)
