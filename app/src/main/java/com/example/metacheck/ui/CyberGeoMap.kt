package com.example.metacheck.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Point
import android.location.Geocoder
import android.os.Build
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.metacheck.domain.model.GpsPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import java.util.Locale

private val cyan = Color(0xFF00F3FF)
private val pink = Color(0xFFFF0055)

/**
 * Карта OpenStreetMap включается только по нажатию кнопки: пока пользователь не согласился,
 * приложение не делает сетевых запросов и не раскрывает серверам OSM район точки.
 * Согласие действует для текущей точки и сбрасывается при выборе другого фото.
 */
@Composable
fun CyberGeoMap(gps: GpsPoint, onSelect: ((Double, Double) -> Unit)? = null) {
    var enabled by rememberSaveable(gps.latitude, gps.longitude) { mutableStateOf(false) }
    if (enabled) {
        LiveOsmMap(gps, onSelect)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("КАРТА ВЫКЛЮЧЕНА · В СЕТЬ НИЧЕГО НЕ ОТПРАВЛЯЕТСЯ", color = cyan)
            Text("%.6f, %.6f".format(Locale.US, gps.latitude, gps.longitude), color = cyan)
            Text(
                "Чтобы показать точку на карте, приложение загрузит фрагменты карты с серверов OpenStreetMap. " +
                    "Серверы увидят ваш IP-адрес и примерный район точки. Само фото и его метаданные не отправляются.",
                color = Color.LightGray,
            )
            Button(onClick = { enabled = true }) { Text("Показать карту (запрос к OpenStreetMap)") }
            if (onSelect != null) {
                Text("Чтобы выбрать новые координаты на карте, сначала включите карту.", color = pink)
            }
        }
    }
}

/** Real slippy map; tiles are fetched over HTTPS and cached on device. */
@Composable
private fun LiveOsmMap(gps: GpsPoint, onSelect: ((Double, Double) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chosen by remember(gps.latitude, gps.longitude) { mutableStateOf(GeoPoint(gps.latitude, gps.longitude)) }
    var address by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Нажмите на карту, чтобы выбрать точку") }
    val map = remember(context) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = context.filesDir.resolve("osmdroid").apply { mkdirs() }
            osmdroidTileCache = context.cacheDir.resolve("osm-tiles").apply { mkdirs() }
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            controller.setZoom(15.0)
            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            // Dark cyan-tinted cartography without modifying server tiles.
            // Use standard high-contrast OSM tiles until a legible dark tile provider is configured.
            overlayManager.tilesOverlay.setColorFilter(null)
        }
    }
    val marker = remember(map) { Marker(map).apply {
        title = "GPS · MetaCheck"
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        icon = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(android.graphics.Color.rgb(0,243,255))
            setStroke(5, android.graphics.Color.rgb(255,0,85))
            setSize(35,35)
        }
    } }
    DisposableEffect(map) {
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                chosen = p; status = "Выбрано: %.6f, %.6f".format(Locale.US, p.latitude, p.longitude)
                return true
            }
            override fun longPressHelper(p: GeoPoint): Boolean = singleTapConfirmedHelper(p)
        }))
        map.overlays.add(marker)
        map.controller.setCenter(chosen)
        onDispose { map.onDetach() }
    }
    LaunchedEffect(chosen) { marker.position = chosen; map.invalidate() }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("LIVE OPENSTREETMAP · GPS PICKER", color = cyan)
        AndroidView(factory = { map }, modifier = Modifier.fillMaxWidth().height(290.dp), update = { it.invalidate() })
        Text("%.6f, %.6f".format(Locale.US, chosen.latitude, chosen.longitude), color = cyan)
        Text(status, color = Color.LightGray)
        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Город, улица или адрес") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("Поиск адреса отправляет введённый текст системному геокодеру телефона (на многих устройствах это сервис Google).", color = Color.LightGray)
        Button(onClick = {
            scope.launch {
                status = "Поиск адреса…"
                val found = withContext(Dispatchers.IO) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        Geocoder(context, Locale.getDefault()).getFromLocationName(address, 1)?.firstOrNull()
                    }.getOrNull()
                }
                if (found != null) {
                    chosen = GeoPoint(found.latitude, found.longitude)
                    map.controller.animateTo(chosen)
                    map.controller.setZoom(16.0)
                    status = "Адрес найден"
                } else status = "Адрес не найден или геокодер недоступен"
            }
        }, enabled = address.isNotBlank()) { Text("Найти на карте") }
        if (onSelect != null) {
            Button(onClick = { onSelect(chosen.latitude, chosen.longitude) }) { Text("Применить новые координаты") }
        } else {
            Text("Выберите точку на карте, затем нажмите «Сохранить GPS в JPEG» в разделе метаданных.", color = pink)
        }
        Text("© OpenStreetMap contributors · тайлы требуют интернет; ранее загруженные доступны из кэша", color = Color.LightGray)
    }
}
