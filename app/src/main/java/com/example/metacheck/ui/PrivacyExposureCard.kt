package com.example.metacheck.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.metacheck.R
import com.example.metacheck.domain.privacy.ExposureKind
import com.example.metacheck.domain.privacy.ExposureLevel
import com.example.metacheck.domain.privacy.PrivacyExposure

/**
 * «Что раскрывает это фото»: индекс риска 0–100 и список того, что увидит получатель оригинала,
 * с реальными значениями (координаты, серийный номер, имя автора). Кнопки ведут прямо к очистке.
 */
@Composable
fun PrivacyExposureCard(
    exposure: PrivacyExposure,
    onCleanShare: (() -> Unit)?,
    onClean: (() -> Unit)?,
) {
    val color = levelColor(exposure.level)
    GlassPanel(Modifier.fillMaxWidth(), color) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ScoreGauge(exposure.score, color)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CyberEyebrow(stringResource(R.string.privacy_eyebrow), color = color)
                    Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(levelText(exposure.level)), color = color,
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
            if (exposure.items.isEmpty()) {
                Text(stringResource(R.string.privacy_none_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.privacy_hint), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
                exposure.items.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("●", color = if (item.kind.weight >= 15) color else MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(kindText(item.kind)), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(item.value.ifEmpty { stringResource(R.string.privacy_value_present) },
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                onCleanShare?.let { NeonButton(stringResource(R.string.privacy_clean_share), it, Modifier.fillMaxWidth()) }
                onClean?.let { CyberOutlineButton(stringResource(R.string.privacy_clean_save), it, Modifier.fillMaxWidth()) }
            }
        }
    }
}

/** Кольцо с числом: заполняется с анимацией при открытии отчёта. */
@Composable
private fun ScoreGauge(score: Int, color: Color) {
    val track = MaterialTheme.colorScheme.outline
    val progress = remember(score) { Animatable(0f) }
    LaunchedEffect(score) { progress.animateTo(score / 100f, tween(900, easing = FastOutSlowInEasing)) }
    val description = stringResource(R.string.privacy_score_description, score)
    Box(Modifier.size(76.dp).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(76.dp)) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * progress.value, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text("$score", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun levelColor(level: ExposureLevel): Color = when (level) {
    ExposureLevel.NONE, ExposureLevel.LOW -> MaterialTheme.colorScheme.tertiary
    ExposureLevel.MEDIUM -> MaterialTheme.colorScheme.secondary
    ExposureLevel.HIGH -> MaterialTheme.colorScheme.error
}

@StringRes
private fun levelText(level: ExposureLevel): Int = when (level) {
    ExposureLevel.NONE -> R.string.privacy_level_none
    ExposureLevel.LOW -> R.string.privacy_level_low
    ExposureLevel.MEDIUM -> R.string.privacy_level_medium
    ExposureLevel.HIGH -> R.string.privacy_level_high
}

@StringRes
private fun kindText(kind: ExposureKind): Int = when (kind) {
    ExposureKind.EXACT_LOCATION -> R.string.privacy_kind_exact_location
    ExposureKind.LOCATION_TAGS -> R.string.privacy_kind_location_tags
    ExposureKind.OWNER -> R.string.privacy_kind_owner
    ExposureKind.DEVICE_SERIAL -> R.string.privacy_kind_device_serial
    ExposureKind.AUTHOR -> R.string.privacy_kind_author
    ExposureKind.AI_PROMPT -> R.string.privacy_kind_ai_prompt
    ExposureKind.CAPTURE_TIME -> R.string.privacy_kind_capture_time
    ExposureKind.DESCRIPTION -> R.string.privacy_kind_description
    ExposureKind.DEVICE -> R.string.privacy_kind_device
    ExposureKind.EDIT_HISTORY -> R.string.privacy_kind_edit_history
    ExposureKind.SOFTWARE -> R.string.privacy_kind_software
}
