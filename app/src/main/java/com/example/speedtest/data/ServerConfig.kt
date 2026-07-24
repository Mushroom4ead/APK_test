package com.example.speedtest.data

/** Протокол тестового сервера — определяет, как формировать запрос загрузки. */
enum class ServerProtocol {
    /** Cloudflare: GET {downloadUrl}?bytes=N возвращает ровно N байт, POST {uploadUrl}. */
    CLOUDFLARE,
    /** LibreSpeed: GET {downloadUrl}?ckSize=МБ отдаёт «мусорные» данные, POST {uploadUrl}. */
    LIBRESPEED
}

/**
 * Конфигурация тестового сервера.
 */
data class ServerConfig(
    val id: String,
    val name: String,
    val downloadUrl: String,
    val uploadUrl: String,
    val protocol: ServerProtocol = ServerProtocol.CLOUDFLARE,
    val custom: Boolean = false
)

object Servers {
    val CLOUDFLARE = ServerConfig(
        id = "cloudflare",
        name = "Cloudflare (авто, глобально)",
        downloadUrl = "https://speed.cloudflare.com/__down",
        uploadUrl = "https://speed.cloudflare.com/__up",
        protocol = ServerProtocol.CLOUDFLARE
    )

    val CLOUVIDER_LON = ServerConfig(
        id = "clouvider_lon",
        name = "LibreSpeed — Лондон (Clouvider)",
        downloadUrl = "https://lon.speedtest.clouvider.net/backend/garbage.php",
        uploadUrl = "https://lon.speedtest.clouvider.net/backend/empty.php",
        protocol = ServerProtocol.LIBRESPEED
    )

    val CLOUVIDER_NYC = ServerConfig(
        id = "clouvider_nyc",
        name = "LibreSpeed — Нью-Йорк (Clouvider)",
        downloadUrl = "https://nyc.speedtest.clouvider.net/backend/garbage.php",
        uploadUrl = "https://nyc.speedtest.clouvider.net/backend/empty.php",
        protocol = ServerProtocol.LIBRESPEED
    )

    /** Пресеты для выпадающего списка (кроме «своего сервера»). */
    val presets: List<ServerConfig> = listOf(CLOUDFLARE, CLOUVIDER_LON, CLOUVIDER_NYC)

    /** Конфиг «своего сервера» (Cloudflare-совместимый) из базового URL. */
    fun custom(baseUrl: String): ServerConfig {
        val base = baseUrl.trim().trimEnd('/')
        return ServerConfig(
            id = "custom",
            name = "Свой сервер",
            downloadUrl = "$base/__down",
            uploadUrl = "$base/__up",
            protocol = ServerProtocol.CLOUDFLARE,
            custom = true
        )
    }
}
