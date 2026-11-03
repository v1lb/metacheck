package com.example.metacheck.ui.map

import android.content.Context
import com.example.metacheck.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex

/**
 * Источник тайлов и сетевые настройки osmdroid (аудит, п. 10).
 *
 * Провайдер выбирается при сборке:
 *  - задан ключ MapTiler (gradle-свойство или переменная окружения METACHECK_MAPTILER_KEY) → MapTiler.
 *    Коммерческий провайдер с SLA, бесплатный тариф рассчитан на приложения;
 *  - ключа нет → tile.openstreetmap.org, но строго по Tile Usage Policy: внятный User-Agent с контактом,
 *    не больше 2 соединений, без массовой загрузки (её запрещает и политика MAPNIK в osmdroid).
 *    OSM может заблокировать приложение при большой нагрузке, поэтому для релиза лучше MapTiler.
 *
 * Кэш ограничен 50 МБ (по умолчанию osmdroid держит до 600 МБ).
 */
internal object MapTiles {
    /** [serviceName] и [attribution] не переводятся: это названия сервисов и обязательная подпись. */
    enum class Provider(val serviceName: String, val attribution: String) {
        MAPTILER("MapTiler", "© MapTiler © OpenStreetMap contributors"),
        OSM("OpenStreetMap", "© OpenStreetMap contributors"),
    }

    private const val CACHE_MAX_BYTES = 50L * 1024 * 1024
    private const val CACHE_TRIM_BYTES = 40L * 1024 * 1024
    private const val DOWNLOAD_THREADS: Short = 2

    @Volatile
    private var configured = false

    fun provider(context: Context): Provider =
        if (mapTilerKey(context).isNotBlank()) Provider.MAPTILER else Provider.OSM

    /** Вызывать до создания MapView. Повторные вызовы ничего не делают. */
    fun configure(context: Context) {
        if (configured) return
        synchronized(this) {
            if (configured) return
            val app = context.applicationContext
            Configuration.getInstance().apply {
                userAgentValue = MapTileUrls.userAgent(versionName(app))
                osmdroidBasePath = app.filesDir.resolve("osmdroid").apply { mkdirs() }
                osmdroidTileCache = app.cacheDir.resolve("osm-tiles").apply { mkdirs() }
                tileFileSystemCacheMaxBytes = CACHE_MAX_BYTES
                tileFileSystemCacheTrimBytes = CACHE_TRIM_BYTES
                tileDownloadThreads = DOWNLOAD_THREADS
            }
            configured = true
        }
    }

    fun tileSource(context: Context): ITileSource {
        val key = mapTilerKey(context)
        return if (key.isNotBlank()) MapTilerStreets(key) else TileSourceFactory.MAPNIK
    }

    private fun mapTilerKey(context: Context): String = context.getString(R.string.maptiler_key).trim()

    private fun versionName(context: Context): String? = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    private class MapTilerStreets(private val key: String) : OnlineTileSourceBase(
        "MapTiler-Streets",
        0,
        19,
        256,
        ".png",
        arrayOf(MapTileUrls.MAPTILER_BASE),
        "© MapTiler © OpenStreetMap contributors",
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String = MapTileUrls.mapTilerUrl(
            key,
            MapTileIndex.getZoom(pMapTileIndex),
            MapTileIndex.getX(pMapTileIndex),
            MapTileIndex.getY(pMapTileIndex),
        )
    }
}
