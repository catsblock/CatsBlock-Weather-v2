plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

base.archivesName.set("com.catsblock.weather")

val buildNum = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
val ksPath: String? = System.getenv("KS_PATH")
val ksPass: String? = System.getenv("KS_PASS")

val localProps = java.util.Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(name: String): String = (System.getenv(name) ?: localProps.getProperty(name) ?: "").trim()

val keyMask = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }

fun masked(value: String): String =
    value.toByteArray(Charsets.UTF_8)
        .mapIndexed { i, b -> "%02x".format((b.toInt() xor (keyMask[i % keyMask.size].toInt() and 0xff)) and 0xff) }
        .joinToString("")

fun quoted(value: String): String = "\"$value\""

android {
    namespace = "com.catsblock.weather"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.catsblock.weather"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNum
        versionName = "1.0.$buildNum"
        buildConfigField("String", "WEATHER_KEY_DATA", quoted(masked(secret("WEATHER_API_KEY"))))
        buildConfigField("String", "VISUAL_KEY_DATA", quoted(masked(secret("VISUAL_API_KEY"))))
        buildConfigField("String", "KEY_MASK", quoted(keyMask.joinToString("") { "%02x".format(it.toInt() and 0xff) }))
    }

    signingConfigs {
        if (ksPath != null) {
            create("ci") {
                storeFile = file(ksPath)
                storePassword = ksPass
                keyAlias = "cats"
                keyPassword = ksPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (ksPath != null) signingConfigs.getByName("ci") else signingConfigs.getByName("debug")
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("../weather", "../sounds")
        }
    }

    androidResources {
        noCompress += listOf("mp3", "jpg")
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("dev.chrisbanes.haze:haze:1.6.10")
    implementation("dev.chrisbanes.haze:haze-materials:1.6.10")
}
