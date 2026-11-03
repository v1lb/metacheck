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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.example.metacheck.R
import com.example.metacheck.ui.text.UiText
import com.example.metacheck.ui.text.asString
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.metacheck.domain.model.GpsPoint
import com.example.metacheck.ui.map.MapTiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
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
    val provider = MapTiles.provider(LocalContext.current)
    if (enabled) {
        LiveOsmMap(gps, onSelect)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(stringResource(R.string.map_off), color = cyan)
            Text("%.6f, %.6f".format(Locale.US, gps.latitude, gps.longitude), color = cyan)
            // Пользователь должен знать, чей сервер увидит район точки: текст зависит от провайдера.
            Text(stringResource(R.string.map_consent, provider.serviceName), color = Color.LightGray)
            Button(onClick = { enabled = true }) {
                Text(stringResource(R.string.map_show, provider.serviceName))
            }
            if (onSelect != null) {
                Text(stringResource(R.string.map_enable_first), color = pink)
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
    var status by remember { mutableStateOf<UiText>(UiText.Res(R.string.map_tap_to_pick)) }
    // Геокодер ищет на языке интерфейса: Locale.getDefault() на Android 8–12 не знает о выборе в приложении.
    val uiLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val map = remember(context) {
        // User-Agent с контактом, лимит кэша 50 МБ, не больше 2 загрузок одновременно (Tile Usage Policy).
        MapTiles.configure(context)
        MapView(context).apply {
            setTileSource(MapTiles.tileSource(context))
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
                chosen = p
                status = UiText.Res(R.string.map_picked, "%.6f, %.6f".format(Locale.US, p.latitude, p.longitude))
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
        Text(stringResource(R.string.map_live_eyebrow), color = cyan)
        AndroidView(factory = { map }, modifier = Modifier.fillMaxWidth().height(290.dp), update = { it.invalidate() })
        Text("%.6f, %.6f".format(Locale.US, chosen.latitude, chosen.longitude), color = cyan)
        Text(status.asString(), color = Color.LightGray)
        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(stringResource(R.string.map_address_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(stringResource(R.string.map_geocoder_note), color = Color.LightGray)
        Button(onClick = {
            scope.launch {
                status = UiText.Res(R.string.map_searching)
                val found = withContext(Dispatchers.IO) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        Geocoder(context, uiLocale).getFromLocationName(address, 1)?.firstOrNull()
                    }.getOrNull()
                }
                if (found != null) {
                    chosen = GeoPoint(found.latitude, found.longitude)
                    map.controller.animateTo(chosen)
                    map.controller.setZoom(16.0)
                    status = UiText.Res(R.string.map_address_found)
                } else status = UiText.Res(R.string.map_address_not_found)
            }
        }, enabled = address.isNotBlank()) { Text(stringResource(R.string.map_find)) }
        if (onSelect != null) {
            Button(onClick = { onSelect(chosen.latitude, chosen.longitude) }) { Text(stringResource(R.string.map_apply)) }
        } else {
            Text(stringResource(R.string.map_then_save), color = pink)
        }
        Text(stringResource(R.string.map_attribution_note, MapTiles.provider(context).attribution), color = Color.LightGray)
    }
}
