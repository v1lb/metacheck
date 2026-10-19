package com.example.metacheck.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.example.metacheck.ui.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.metacheck.domain.metadata.GpsPrivacy
import com.example.metacheck.domain.model.GpsPoint

private val danger = Color(0xFFFF467E) // higher-contrast light neon, #FF0055 for glow only
private val dangerGlow = Color(0xFFFF0055)

/** Warning is conditional on actual EXIF GPS data, not a speculative risk score. */
@Composable
fun GpsPrivacyAlert(gps: GpsPoint?, onWipe: (() -> Unit)?, partialTags: Boolean = false) {
    val wave = rememberInfiniteTransition(label = "gps-alert")
    val strength by wave.animateFloat(.35f, .85f,
        infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "gps-pulse")
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF291322), Color(0xFF120E1A))))
            .border(1.dp, danger.copy(alpha = strength), shape)
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PulseIndicator(dangerGlow)
            Text("GPS PRIVACY SHIELD / RED ALERT", color = Color(0xFFFFB0C8),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Text("УТЕЧКА ГЕОЛОКАЦИИ", color = Color.White,
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(if (gps != null) "В EXIF обнаружены GPS-координаты. Фотография может раскрыть место съёмки."
             else "В метаданных обнаружены поля геолокации, но полные координаты не удалось прочитать.",
            color = Color(0xFFF0E1E8), style = MaterialTheme.typography.bodyMedium)
        if (gps != null) {
            Text(GpsPrivacy.label(gps.latitude, gps.longitude), color = Color.White,
                fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            CyberGeoMap(gps)
        } else if (partialTags) {
            Text("Точная точка на карте недоступна: геотеги неполные или представлены только в XMP/PNG.",
                color = Color(0xFFF0E1E8), style = MaterialTheme.typography.bodySmall)
        }
        if (onWipe != null) {
            NeonButton("⌖  Уничтожить GPS · сохранить копию", onWipe, Modifier.fillMaxWidth())
            Text("Оригинал не изменяется. Android предложит выбрать место сохранения. В бесплатной версии: 1 очистка в сутки.",
                color = Color(0xFFE1D0DA), style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Для очистки требуется исходный файл. Откройте фотографию заново, не только сохранённый отчёт.",
                color = Color(0xFFE1D0DA), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private data class GeoVertex(val x: Float, val y: Float)
private typealias LandRing = List<GeoVertex>

/** Offline polygons are simplified Natural Earth land outlines. No location is sent to map servers. */
private fun readLandOutlines(context: android.content.Context): List<LandRing> = runCatching {
    context.assets.open("naturalearth_world_outline.txt").bufferedReader().useLines { lines ->
        lines.mapNotNull { line ->
            val points = line.split(';').mapNotNull { pair ->
                val parts = pair.split(',')
                if (parts.size != 2) null else {
                    val x = parts[0].toFloatOrNull(); val y = parts[1].toFloatOrNull()
                    if (x != null && y != null) GeoVertex(x, y) else null
                }
            }
            points.takeIf { it.size >= 3 }
        }.toList()
    }
}.getOrDefault(emptyList())

@Composable
fun CyberGeoMap(gps: GpsPoint) {
    val context = LocalContext.current
    val land = remember(context) { readLandOutlines(context) }
    val accent = Color(0xFF00F3FF)
    val signal = rememberInfiniteTransition(label = "gps-map-glow")
    val pulse by signal.animateFloat(.45f, 1f,
        infiniteRepeatable(tween(1150), RepeatMode.Reverse), label = "gps-map-pulse")
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Color(0xFF07101A)).border(1.dp, accent.copy(alpha = .5f), shape).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("OFFLINE GEO / ТОЧКА ИЗ EXIF", color = Color(0xFFB5F7FF),
            style = MaterialTheme.typography.labelSmall)
        Canvas(Modifier.fillMaxWidth().height(196.dp).clip(RoundedCornerShape(12.dp))) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF071627), Color(0xFF10152A))))
            for (i in 1..8) {
                val x = size.width * i / 9f
                drawLine(accent.copy(alpha = .11f), Offset(x, 0f), Offset(x, size.height), 1f)
            }
            for (i in 1..5) {
                val y = size.height * i / 6f
                drawLine(accent.copy(alpha = .11f), Offset(0f, y), Offset(size.width, y), 1f)
            }
            // A geographic equirectangular projection with center at the exact EXIF point.
            // 110° longitude and 70° latitude shown; street-level fidelity is not implied.
            val lonSpan = 110.0
            val latSpan = 70.0
            land.forEach { ring ->
                val path = Path()
                var hasPoint = false
                var lastX = 0f
                ring.forEach { point ->
                    val x = GpsPrivacy.mapX(point.x.toDouble(), gps.longitude, lonSpan) * size.width
                    val y = GpsPrivacy.mapY(point.y.toDouble(), gps.latitude, latSpan) * size.height
                    if (!hasPoint || kotlin.math.abs(x - lastX) > size.width * .65f) path.moveTo(x, y)
                    else path.lineTo(x, y)
                    lastX = x
                    hasPoint = true
                }
                drawPath(path, color = Color(0xFF245268), style = Stroke(width = 1.5f))
            }
            val middle = Offset(size.width / 2f, size.height / 2f)
            drawCircle(dangerGlow.copy(alpha = pulse * .18f), radius = 33f, center = middle)
            drawCircle(dangerGlow.copy(alpha = pulse * .32f), radius = 19f, center = middle)
            drawCircle(Color.White, radius = 5f, center = middle)
            drawCircle(dangerGlow, radius = 3f, center = middle)
            drawLine(Color.White.copy(alpha = .65f), Offset(middle.x - 15f, middle.y),
                Offset(middle.x + 15f, middle.y), width = 1f)
            drawLine(Color.White.copy(alpha = .65f), Offset(middle.x, middle.y - 15f),
                Offset(middle.x, middle.y + 15f), width = 1f)
        }
        Text("Положение маркера соответствует координатам EXIF. Контуры суши приблизительные; улицы не показаны. Без интернета.",
            color = Color(0xFFCDD9E7), style = MaterialTheme.typography.bodySmall)
    }
}
