package com.catsblock.weather.data

object Cities {
    val continents: List<Continent> = listOf(
        Continent(
            "North America",
            listOf(
                City("New York", "New York", "United States", "US", 40.7128, -74.0060),
                City("Los Angeles", "California", "United States", "US", 34.0522, -118.2437),
                City("Miami", "Florida", "United States", "US", 25.7617, -80.1918),
                City("Toronto", "Ontario", "Canada", "CA", 43.6532, -79.3832),
                City("Mexico City", "Mexico City", "Mexico", "MX", 19.4326, -99.1332)
            )
        ),
        Continent(
            "Europe",
            listOf(
                City("London", "England", "United Kingdom", "GB", 51.5074, -0.1278),
                City("Paris", "Île-de-France", "France", "FR", 48.8566, 2.3522),
                City("Berlin", "Berlin", "Germany", "DE", 52.5200, 13.4050),
                City("Madrid", "Community of Madrid", "Spain", "ES", 40.4168, -3.7038),
                City("Rome", "Lazio", "Italy", "IT", 41.9028, 12.4964),
                City("Istanbul", "Istanbul", "Turkey", "TR", 41.0082, 28.9784)
            )
        ),
        Continent(
            "Asia & Oceania",
            listOf(
                City("Tokyo", "Tokyo", "Japan", "JP", 35.6762, 139.6503),
                City("Hong Kong", "Hong Kong", "China", "CN", 22.3193, 114.1694),
                City("Beijing", "Beijing", "China", "CN", 39.9042, 116.4074),
                City("Singapore", "Singapore", "Singapore", "SG", 1.3521, 103.8198),
                City("New Delhi", "Delhi", "India", "IN", 28.6139, 77.2090),
                City("Sydney", "New South Wales", "Australia", "AU", -33.8688, 151.2093),
                City("Dubai", "Dubai", "United Arab Emirates", "AE", 25.2048, 55.2708)
            )
        ),
        Continent(
            "Africa",
            listOf(
                City("Cairo", "Cairo", "Egypt", "EG", 30.0444, 31.2357),
                City("Cape Town", "Western Cape", "South Africa", "ZA", -33.9249, 18.4241)
            )
        ),
        Continent(
            "South America",
            listOf(
                City("São Paulo", "São Paulo", "Brazil", "BR", -23.5505, -46.6333),
                City("Rio de Janeiro", "Rio de Janeiro", "Brazil", "BR", -22.9068, -43.1729),
                City("Brasília", "Federal District", "Brazil", "BR", -15.7939, -47.8828),
                City("Florianópolis", "Santa Catarina", "Brazil", "BR", -27.5954, -48.5480),
                City("Goiânia", "Goiás", "Brazil", "BR", -16.6869, -49.2648),
                City("Salvador", "Bahia", "Brazil", "BR", -12.9777, -38.5016),
                City("Belo Horizonte", "Minas Gerais", "Brazil", "BR", -19.9167, -43.9345),
                City("Fortaleza", "Ceará", "Brazil", "BR", -3.7319, -38.5267),
                City("Recife", "Pernambuco", "Brazil", "BR", -8.0476, -34.8770),
                City("Buenos Aires", "Buenos Aires", "Argentina", "AR", -34.6037, -58.3816),
                City("Bogotá", "Bogotá", "Colombia", "CO", 4.7110, -74.0721),
                City("Tierra del Fuego", "Tierra del Fuego", "Argentina", "AR", -54.8019, -68.3030)
            )
        )
    )
}
