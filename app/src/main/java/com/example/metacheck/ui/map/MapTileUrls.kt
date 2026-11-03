package com.example.metacheck.ui.map

import java.net.URLEncoder

/** Чистые функции без Android/osmdroid: проверяются JVM-тестами. */
internal object MapTileUrls {
    const val SUPPORT_URL = "https://v1lb.github.io/legal-metacheck/"
    const val MAPTILER_BASE = "https://api.maptiler.com/maps/streets-v2/256/"

    /**
     * Tile Usage Policy OSM требует User-Agent, который однозначно называет приложение и даёт контакт.
     * Раньше отправлялось только имя пакета.
     */
    fun userAgent(versionName: String?): String =
        "MetaCheck/${versionName?.takeIf { it.isNotBlank() } ?: "unknown"} (Android; +$SUPPORT_URL)"

    fun mapTilerUrl(key: String, zoom: Int, x: Int, y: Int): String {
        require(key.isNotBlank()) { "MapTiler key is empty" }
        require(zoom in 0..22 && x >= 0 && y >= 0) { "Bad tile index" }
        return "$MAPTILER_BASE$zoom/$x/$y.png?key=" + URLEncoder.encode(key, "UTF-8")
    }
}
