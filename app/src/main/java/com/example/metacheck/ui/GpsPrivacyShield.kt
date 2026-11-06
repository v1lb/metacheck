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
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.example.metacheck.R
import com.example.metacheck.domain.metadata.DailyCleanQuota
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
import com.example.metacheck.ui.theme.MonoText

/**
 * GPS marker notice: deliberate attention + an immediate, honest privacy action.
 * Coordinates in a local file are not evidence that tracking or data leakage occurred.
 * Nothing is sent to a map service from this panel.
 */
@Composable
fun GpsPrivacyAlert(gps: GpsPoint?, onWipe: (() -> Unit)?, partialTags: Boolean = false, video: Boolean = false) {
    val signal = MaterialTheme.colorScheme.error
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier.fillMaxWidth().testTag("gps_privacy_shield").clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(2.dp, signal, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text(
            stringResource(R.string.gps_shield_eyebrow).uppercase(),
            color = signal,
            style = MonoText.tag,
        )

        Text(
            stringResource(
                when {
                    video -> if (gps != null) R.string.gps_shield_title_point_video else R.string.gps_shield_title_tags_video
                    gps != null -> R.string.gps_shield_title_point
                    else -> R.string.gps_shield_title_tags
                },
            ),
            color = ink,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(if (gps != null) R.string.gps_shield_text_point else R.string.gps_shield_text_tags),
            color = ink,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (gps != null) {
            Text(GpsPrivacy.label(gps.latitude, gps.longitude), color = ink, style = MonoText.file)
        }

        // Keep the truth explicit: GPS data in a file is not evidence of an actual leak.
        Text(
            stringResource(R.string.gps_shield_not_tracking),
            color = muted,
            style = MaterialTheme.typography.bodySmall,
        )

        if (onWipe != null) {
            NeonButton(
                stringResource(if (video) R.string.gps_shield_action_video else R.string.gps_shield_action),
                onWipe,
                Modifier.fillMaxWidth().testTag("gps_wipe_action"),
            )
            Text(
                if (video) stringResource(R.string.gps_shield_action_note_video)
                else stringResource(R.string.gps_shield_action_note, DailyCleanQuota.FREE_DAILY_LIMIT),
                color = muted,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                stringResource(R.string.gps_shield_open_original),
                color = muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (gps != null) {
            CyberGeoMap(gps)
        } else if (partialTags) {
            Text(
                stringResource(R.string.gps_shield_partial),
                color = muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
