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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.HorizontalDivider
import com.example.metacheck.ui.theme.MonoText
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
 * «Что выдаёт файл»: штамп с числом найденных личных фактов, индекс риска 0–100 и пронумерованный
 * список того, что увидит получатель оригинала, с реальными значениями. Кнопки ведут прямо к очистке.
 * [preview] — миниатюра файла слева от штампа.
 */
@Composable
fun PrivacyExposureCard(
    exposure: PrivacyExposure,
    onCleanShare: (() -> Unit)?,
    onClean: (() -> Unit)?,
    preview: (@Composable () -> Unit)? = null,
) {
    val color = levelColor(exposure.level)
    val count = exposure.items.size
    val stampLines = listOf(
        stringResource(R.string.stamp_reveals),
        if (count == 0) stringResource(R.string.stamp_nothing) else pluralStringResource(R.plurals.stamp_facts, count, count),
    )
    val scoreText = stringResource(R.string.privacy_score_description, exposure.score)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            preview?.invoke()
            Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Stamp(stampLines, color, Modifier.semantics { contentDescription = "${stampLines.joinToString(" ")}. $scoreText" })
                Text(scoreText + " · " + stringResource(levelText(exposure.level)),
                    style = MaterialTheme.typography.labelMedium, color = color)
                Text(stringResource(if (count == 0) R.string.privacy_none_text else R.string.privacy_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (count > 0) {
            SectionLabel(stringResource(R.string.privacy_title))
            Column {
                exposure.items.forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("%02d".format(index + 1), style = MonoText.tag,
                            color = if (item.kind.weight >= 15) color else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stringResource(kindText(item.kind)), style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(item.value.ifEmpty { stringResource(R.string.privacy_value_present) },
                                style = MonoText.value, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            onCleanShare?.let { NeonButton(stringResource(R.string.privacy_clean_share), it, Modifier.fillMaxWidth()) }
            onClean?.let { CyberOutlineButton(stringResource(R.string.privacy_clean_save), it, Modifier.fillMaxWidth()) }
        }
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
    ExposureKind.PLACE_NAME -> R.string.privacy_kind_place_name
    ExposureKind.AUTHOR -> R.string.privacy_kind_author
    ExposureKind.AI_PROMPT -> R.string.privacy_kind_ai_prompt
    ExposureKind.CAPTURE_TIME -> R.string.privacy_kind_capture_time
    ExposureKind.DESCRIPTION -> R.string.privacy_kind_description
    ExposureKind.DEVICE -> R.string.privacy_kind_device
    ExposureKind.EDIT_HISTORY -> R.string.privacy_kind_edit_history
    ExposureKind.SOFTWARE -> R.string.privacy_kind_software
}
