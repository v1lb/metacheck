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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.example.metacheck.ui.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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

