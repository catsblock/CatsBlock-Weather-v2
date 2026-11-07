# CatsBlock Weather

A weather application built with Kotlin and Jetpack Compose.

## Features

- Current weather conditions
- Hourly and daily forecasts
- Interactive weather maps and radar
- Weather alerts and notifications
- Home screen widgets
- Multiple weather providers
- Custom themes and appearance settings
- Multiple temperature and measurement units
- Moon and astronomical information
- Multiple language support

## Platforms

- Android
- Windows

## Requirements

- JDK 17
- Android Studio
- Android SDK

## Build

Clone the repository:

    git clone https://github.com/catsblock/CatsBlock-Weather-v2.git
    cd CatsBlock-Weather-v2

Build the Android application:

    ./gradlew :app:assembleDebug

On Windows:

    .\gradlew.bat :app:assembleDebug

The APK will be generated in:

    app/build/outputs/apk/debug/

## Weather Providers

Open-Meteo is the default weather provider. Other supported providers include WeatherAPI, Visual Crossing, and OpenWeather. Some providers require API keys.

## License

Distributed under the MIT License. See [LICENSE](LICENSE).
