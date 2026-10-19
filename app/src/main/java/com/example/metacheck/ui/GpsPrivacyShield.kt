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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.metacheck.domain.metadata.GpsPrivacy
import com.example.metacheck.domain.model.GpsPoint

private val danger = Color(0xFFFF7098) // light text on graphite
private val dangerGlow = Color(0xFFFF0055) // decorative glow only
private val actionGlow = Color(0xFF00F3FF)
private val softText = Color(0xFFE2E8F0)

/**
 * GPS marker notice: deliberate attention + an immediate, honest privacy action.
 * Coordinates in a local file are not evidence that tracking or data leakage occurred.
 * Nothing is sent to a map service from this panel.
 */
@Composable
fun GpsPrivacyAlert(gps: GpsPoint?, onWipe: (() -> Unit)?, partialTags: Boolean = false) {
    val wave = rememberInfiniteTransition(label = "gps-shield")
    val glow by wave.animateFloat(
        initialValue = .40f,
        targetValue = .86f,
        animationSpec = infiniteRepeatable(animation = tween(1800), repeatMode = RepeatMode.Reverse),
        label = "gps-attention",
    )
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().testTag("gps_privacy_shield").clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF271726), Color(0xFF101723), Color(0xFF0B1520))))
            .border(1.dp, danger.copy(alpha = glow), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PulseIndicator(dangerGlow)
            Text(
                "GPS PRIVACY SHIELD · GPS В ФАЙЛЕ",
                color = Color(0xFFFFCCD9),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            if (gps != null) "Место съёмки сохранено в фото"
            else "В фотографии найдены геометки",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            if (gps != null)
                "Если отправить оригинал без очистки, получатель может увидеть координаты места съёмки."
            else
                "В метаданных найдены сведения о местоположении, но точные координаты прочитать не удалось.",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )

        // Keep the truth explicit: GPS data in a file is not evidence of an actual leak.
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF1A2831))
                .border(1.dp, Color(0xFF385363), RoundedCornerShape(13.dp))
                .padding(horizontal = 13.dp, vertical = 11.dp),
        ) {
            Text(
                "Это не означает, что за вами следят. Риск возникает, если передать файл с геометками.",
                color = softText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (onWipe != null) {
            Text(
                "Перед отправкой лучше создать копию без GPS — это простой способ защитить своё местоположение.",
                color = Color(0xFFB9F8FF),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(actionGlow.copy(alpha = .08f + .06f * glow))
                    .border(1.dp, actionGlow.copy(alpha = .40f + .35f * glow), RoundedCornerShape(18.dp))
                    .padding(5.dp),
            ) {
                NeonButton(
                    "Защитить фото · удалить GPS",
                    onWipe,
                    Modifier.fillMaxWidth().testTag("gps_wipe_action"),
                )
            }
            Text(
                "После нажатия выберите место для новой копии. Оригинал не изменится. Отправляйте именно копию. Free: 75 обработок в сутки.",
                color = softText,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                "Чтобы убрать геометки, откройте исходную фотографию JPEG/PNG: сохранённый отчёт не содержит самого фото.",
                color = softText,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (gps != null) {
            Text(
                "КООРДИНАТЫ В ИСХОДНОМ ФАЙЛЕ",
                color = Color(0xFFFFCCD9),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                GpsPrivacy.label(gps.latitude, gps.longitude),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            CyberGeoMap(gps)
        } else if (partialTags) {
            Text(
                "Точку нельзя показать на карте: GPS-данные неполные или записаны в XMP/PNG.",
                color = softText,
                style = MaterialTheme.typography.bodySmall,
            )
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
                Offset(middle.x + 15f, middle.y), strokeWidth = 1f)
            drawLine(Color.White.copy(alpha = .65f), Offset(middle.x, middle.y - 15f),
                Offset(middle.x, middle.y + 15f), strokeWidth = 1f)
        }
        Text("Положение маркера соответствует координатам EXIF. Контуры суши приблизительные; улицы не показаны. Без интернета.",
            color = Color(0xFFCDD9E7), style = MaterialTheme.typography.bodySmall)
    }
}
