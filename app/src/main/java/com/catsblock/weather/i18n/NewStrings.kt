package com.catsblock.weather.i18n

fun nova(en: String): String? {
    val l = I18n.lang
    if (l == AppLanguage.English) return null
    return N[en]?.getOrNull(l.ordinal - 1)
}

private fun n(en: String, br: String, pt: String, es: String, fr: String, ru: String, de: String) =
    en to arrayOf(br, pt, es, fr, ru, de)

private val N: Map<String, Array<String>> = mapOf(
    n("Earth", "Terra", "Terra", "Tierra", "Terre", "Земля", "Erde"),
    n("Clouds", "Nuvens", "Nuvens", "Nubes", "Nuages", "Облака", "Wolken"),
    n("Rain", "Chuva", "Chuva", "Lluvia", "Pluie", "Дождь", "Regen"),
    n("Borders", "Fronteiras", "Fronteiras", "Fronteras", "Frontières", "Границы", "Grenzen"),
    n("Night sky", "Céu noturno", "Céu noturno", "Cielo nocturno", "Ciel nocturne", "Ночное небо", "Nachthimmel"),
    n("Mercury", "Mercúrio", "Mercúrio", "Mercurio", "Mercure", "Меркурий", "Merkur"),
    n("Venus", "Vênus", "Vénus", "Venus", "Vénus", "Венера", "Venus"),
    n("Mars", "Marte", "Marte", "Marte", "Mars", "Марс", "Mars"),
    n("Jupiter", "Júpiter", "Júpiter", "Júpiter", "Jupiter", "Юпитер", "Jupiter"),
    n("Saturn", "Saturno", "Saturno", "Saturno", "Saturne", "Сатурн", "Saturn"),
    n("Uranus", "Urano", "Urano", "Urano", "Uranus", "Уран", "Uranus"),
    n("Neptune", "Netuno", "Neptuno", "Neptuno", "Neptune", "Нептун", "Neptun"),
    n("Constellations", "Constelações", "Constelações", "Constelaciones", "Constellations", "Созвездия", "Sternbilder"),
    n("Galaxies", "Galáxias", "Galáxias", "Galaxias", "Galaxies", "Галактики", "Galaxien"),
    n("Labels", "Rótulos", "Etiquetas", "Etiquetas", "Étiquettes", "Подписи", "Beschriftungen"),
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
    ),
    n(
        "Loading Earth…",
        "Carregando a Terra…",
        "A carregar a Terra…",
        "Cargando la Tierra…",
        "Chargement de la Terre…",
        "Загрузка Земли…",
        "Erde wird geladen…"
    ),
    n(
        "Loading sky…",
        "Carregando o céu…",
        "A carregar o céu…",
        "Cargando el cielo…",
        "Chargement du ciel…",
        "Загрузка неба…",
        "Himmel wird geladen…"
    ),
    n(
        "Could not load the sky data.",
        "Não foi possível carregar os dados do céu.",
        "Não foi possível carregar os dados do céu.",
        "No se pudieron cargar los datos del cielo.",
        "Impossible de charger les données du ciel.",
        "Не удалось загрузить данные о небе.",
        "Himmelsdaten konnten nicht geladen werden."
    ),
    n(
        "Point your phone at the sky to see the Moon, planets, stars and galaxies.",
        "Aponte o celular para o céu para ver a Lua, planetas, estrelas e galáxias.",
        "Aponte o telemóvel para o céu para ver a Lua, os planetas, as estrelas e as galáxias.",
        "Apunta tu teléfono al cielo para ver la Luna, los planetas, las estrellas y las galaxias.",
        "Pointez votre téléphone vers le ciel pour voir la Lune, les planètes, les étoiles et les galaxies.",
        "Направьте телефон на небо, чтобы увидеть Луну, планеты, звёзды и галактики.",
        "Richten Sie Ihr Handy auf den Himmel, um Mond, Planeten, Sterne und Galaxien zu sehen."
    )
)
