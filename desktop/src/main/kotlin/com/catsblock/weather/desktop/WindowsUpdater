package com.catsblock.weather.desktop

import org.json.JSONArray
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Properties

enum class UpdateStatus { Idle, Checking, Current, Downloading, Failed }

data class WindowsRelease(val build: Int, val name: String, val url: String, val sizeBytes: Long)

private val UPDATE_STRINGS: Map<String, List<String>> = mapOf(
    "Check for updates" to listOf("Verificar atualizações", "Verificar atualizações", "Buscar actualizaciones", "Rechercher des mises à jour", "Проверить обновления", "Nach Updates suchen", "Cerca aggiornamenti", "업데이트 확인", "Periksa pembaruan"),
    "Checking for updates…" to listOf("Verificando atualizações…", "A verificar atualizações…", "Buscando actualizaciones…", "Recherche de mises à jour…", "Проверка обновлений…", "Suche nach Updates …", "Ricerca aggiornamenti…", "업데이트 확인 중…", "Memeriksa pembaruan…"),
    "Up to date" to listOf("Atualizado", "Atualizado", "Actualizado", "À jour", "Установлена последняя версия", "Auf dem neuesten Stand", "Aggiornato", "최신 버전입니다", "Sudah terbaru"),
    "Install update" to listOf("Instalar atualização", "Instalar atualização", "Instalar actualización", "Installer la mise à jour", "Установить обновление", "Update installieren", "Installa aggiornamento", "업데이트 설치", "Pasang pembaruan"),
    "Downloading update…" to listOf("Baixando atualização…", "A transferir atualização…", "Descargando actualización…", "Téléchargement de la mise à jour…", "Загрузка обновления…", "Update wird heruntergeladen …", "Download aggiornamento…", "업데이트 다운로드 중…", "Mengunduh pembaruan…"),
    "Update failed. Try again" to listOf("Falha na atualização. Tente novamente", "Falha na atualização. Tente novamente", "Error al actualizar. Reintentar", "Échec de la mise à jour. Réessayer", "Не удалось обновить. Повторить", "Update fehlgeschlagen. Erneut versuchen", "Aggiornamento non riuscito. Riprova", "업데이트 실패. 다시 시도", "Pembaruan gagal. Coba lagi")
)

fun updateText(key: String): String {
    val index = LANGUAGES.indexOfFirst { it.first == uiLanguage.value }
    if (index <= 0) return key
    return UPDATE_STRINGS[key]?.getOrNull(index - 1)?.takeIf { it.isNotEmpty() } ?: key
}

object WindowsUpdater {

    private const val REPO = "catsblock/CatsBlock-Weather-v2"
    private const val ASSET_SUFFIX = ".exe"
    private const val AGENT = "CatsBlockWeather-Desktop"
    private val digits = Regex("\\d+")

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    fun installedBuild(): Int = runCatching {
        WindowsUpdater::class.java.getResourceAsStream("/build.properties")?.use { stream ->
            Properties().apply { load(stream) }.getProperty("build")?.trim()?.toIntOrNull()
        }
    }.getOrNull() ?: 0

    fun latest(): WindowsRelease? {
        val installed = installedBuild()
        if (installed <= 0) return null
        val request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/$REPO/releases?per_page=30"))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", AGENT)
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 404) return null
        if (response.statusCode() != 200) error("http ${response.statusCode()}")
        val releases = JSONArray(response.body())
        var best: WindowsRelease? = null
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
            val build = digits.find(release.optString("tag_name"))?.value?.toIntOrNull() ?: continue
            if (build <= installed || build <= (best?.build ?: 0)) continue
            val assets = release.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.getJSONObject(j)
                if (asset.getString("name").endsWith(ASSET_SUFFIX, ignoreCase = true)) {
                    best = WindowsRelease(
                        build,
                        release.optString("name").trim().ifEmpty { "Build $build" },
                        asset.getString("browser_download_url"),
                        asset.getLong("size")
                    )
                    break
                }
            }
        }
        return best
    }

    fun download(release: WindowsRelease): File {
        val target = File(System.getProperty("java.io.tmpdir"), "CatsBlockWeather-update-${release.build}.exe")
        val request = HttpRequest.newBuilder(URI.create(release.url))
            .header("User-Agent", AGENT)
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) error("http ${response.statusCode()}")
        response.body().use { Files.copy(it, target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        if (release.sizeBytes > 0 && target.length() != release.sizeBytes) {
            target.delete()
            error("size")
        }
        return target
    }

    fun install(file: File) {
        ProcessBuilder("cmd", "/c", "start", "", file.absolutePath).start()
    }
}
