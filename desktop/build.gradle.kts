import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.json:json:20240303")
}

fun secret(name: String): String = (System.getenv(name) ?: "").trim()

val buildNumber = (findProperty("buildNumber") as String?)?.toIntOrNull() ?: 0

val generateBuildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildinfo")
    inputs.property("buildNumber", buildNumber)
    inputs.property("secretsHash", (secret("CLOUDFLARE_WEBSITE") + secret("APP_TOKEN")).hashCode())
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        File(dir, "build.properties").writeText(
            "build=$buildNumber\ncloudflare=${secret("CLOUDFLARE_WEBSITE").trimEnd('/')}\ntoken=${secret("APP_TOKEN")}\n"
        )
    }
}

tasks.processResources {
    from(rootProject.file("assets/appicon")) {
        include("Icon.jpg")
    }
    from(generateBuildInfo)
}

compose.desktop {
    application {
        mainClass = "com.catsblock.weather.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "CatsBlockWeather"
            packageVersion = "2.0.$buildNumber"
            modules("java.net.http", "java.prefs")
            windows {
                menuGroup = "CatsBlock"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                val ico = file("icon.ico")
                if (ico.exists()) iconFile.set(ico)
            }
        }
    }
}
