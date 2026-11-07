CatsBlock Weather

CatsBlock Weather is a modern weather application built with Kotlin and Jetpack Compose, designed to deliver detailed weather information through a polished, dynamic, and highly customizable interface.

The project includes an Android application and a Windows desktop distribution, bringing weather forecasts, interactive weather maps, atmospheric information, notifications, widgets, and customization features together.

Features

Weather Information

- Real-time weather conditions.
- Current temperature and feels-like temperature.
- Hourly and daily forecasts.
- Minimum and maximum temperatures.
- Precipitation probability and rainfall information.
- Wind speed and direction.
- Atmospheric pressure and visibility.
- Humidity and UV index.
- Air quality information when available.
- Sunrise and sunset times.

Weather Maps

- Interactive weather map interface.
- Radar visualization.
- Configurable weather map layers.
- Multiple weather visualization options.
- Atmospheric and environmental data overlays.

Weather Alerts

- Rain notifications.
- Snow notifications.
- Eclipse notifications.
- Daily weather alerts.
- Configurable notification preferences.
- Customizable notification lead times.

Weather Widgets

Multiple home-screen widgets are available:

- Compact Weather Widget.
- Square Weather Widget.
- Weather Tips Widget.
- Hourly Forecast Widget.
- Small Radar Widget.
- Wide Radar Widget.

Customization

- Light and dark themes.
- System theme support.
- AMOLED black theme.
- Custom color schemes.
- Multiple typography options.
- Adjustable text scaling.
- Temperature unit selection.
- Wind speed unit selection.
- Atmospheric pressure unit selection.
- Precipitation unit selection.
- Custom sounds and visual preferences.

Weather Providers

CatsBlock Weather supports multiple weather data providers:

Provider| Integration
Open-Meteo| Default weather provider
WeatherAPI| Configurable provider
Visual Crossing| Configurable provider
OpenWeather| Configurable provider

Some providers require individual API credentials.

Additional Features

- Multiple city selection.
- Background weather updates.
- Weather assistant interface.
- Live moon information.
- Astronomical and eclipse information.
- Application update notifications.
- Localization and multiple languages.
- Privacy policy and application information screens.
- Custom application icon and background tools.

Platforms

Platform| Availability
Android| Supported
Windows| Desktop distribution

Android requirement: Android 13 (API 33) or newer.

The application intentionally displays an unsupported-device screen on Android versions below Android 13.

Technology Stack

CatsBlock Weather is built using modern development technologies:

- Kotlin — Primary programming language.
- Jetpack Compose — Declarative UI framework.
- Material 3 — Modern interface components.
- Kotlin Coroutines — Asynchronous operations.
- AndroidX — Android application infrastructure.
- WorkManager — Background scheduling and updates.
- OkHttp — Network communication.
- Coil — Image, SVG, and GIF loading.
- Google Play Services Location — Location services.
- Gradle — Build automation.

Building the Project

Requirements

Before building the project, make sure you have:

- JDK 17.
- Android Studio.
- Android SDK Platform 36.
- Gradle wrapper included in the repository.
- A compatible Windows environment for Windows packaging.

Android Development

Clone the repository:

git clone https://github.com/catsblock/CatsBlock-Weather-v2.git

Navigate to the project directory:

cd CatsBlock-Weather-v2

Build the debug APK:

./gradlew :app:assembleDebug

On Windows:

.\gradlew.bat :app:assembleDebug

The generated APK will be available in:

app/build/outputs/apk/debug/

Android Release Build

To generate a release APK:

./gradlew :app:assembleRelease

Windows Desktop Build

To generate the Windows executable installer, run:

./gradlew :desktop:packageReleaseExe

The generated distribution will be available inside the desktop module's Gradle build output directory.

API Configuration

Open-Meteo is the default weather provider.

Additional providers can be configured through the application's weather settings.

Depending on the selected provider, an API key may be required.

Supported integrations include:

- WeatherAPI.
- Visual Crossing.
- OpenWeather.

Environment Variables

The project also supports build-time configuration for its Cloudflare-backed service.

The following configuration values are supported:

Variable| Description
"CLOUDFLARE_WEBSITE"| Base URL of the configured service.
"APP_TOKEN"| Application authentication token.

For local Android development, these values can be configured in the root "local.properties" file:

CLOUDFLARE_WEBSITE=https://your-service.example
APP_TOKEN=your-app-token

Never commit private API keys, authentication tokens, or signing credentials to the repository.

For automated builds, use encrypted CI/CD secrets.

Permissions

Depending on the features being used, CatsBlock Weather may request:

Permission| Purpose
Internet| Retrieve weather information and remote resources.
Network State| Monitor network connectivity.
Coarse Location| Approximate user location.
Fine Location| More accurate location-based weather.
Notifications| Deliver weather alerts and updates.
Camera| Support camera-dependent features.
Foreground Service| Support certain background operations.

Location permission is not mandatory when manually selecting a city.

Localization

CatsBlock Weather includes a localization system supporting multiple application languages.

Language-related resources are organized within the application's localization package.

Contributions to existing translations and additional language support are welcome.

Background Operations

The application uses Android background scheduling to maintain certain features even when the application is not actively open.

These operations include:

- Scheduled weather updates.
- Weather widget refreshes.
- Daily alert scheduling.
- Radar widget updates.

Background execution depends on Android system restrictions, network availability, battery optimization, and user permissions.

Contributing

Contributions are welcome.

If you want to improve CatsBlock Weather:

1. Fork this repository.
2. Create a branch for your changes.
3. Implement your improvements.
4. Test your changes.
5. Submit a pull request with a clear explanation.

Please keep contributions consistent with the existing architecture and coding conventions.

When reporting bugs, include:

- A clear description of the problem.
- Steps to reproduce it.
- Relevant error logs.
- Android version or Windows environment.
- Screenshots when applicable.

Avoid including private credentials or personal information in bug reports.

License

No license file is currently specified in this repository.

Unless a license is added, all rights remain with the copyright holder. Please contact the repository owner before redistributing or reusing the project's code.

---

<p align="center">
  **CatsBlock Weather**
  <br>
  Modern weather information, visualization, and customization.
  <br><br>
  <a href="https://github.com/catsblock/CatsBlock-Weather-v2">GitHub Repository</a>
</p>
