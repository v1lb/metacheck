package com.example.metacheck.ui

import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.example.metacheck.R
import com.example.metacheck.domain.model.ValueFormat
import com.example.metacheck.ui.text.rememberMetaTexts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.metacheck.domain.metadata.GpsPrivacy
import com.example.metacheck.domain.model.GpsPoint
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.Severity
import com.example.metacheck.domain.model.Signal
import com.example.metacheck.ui.theme.CyberPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class ReportTab(@StringRes val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    OVERVIEW(R.string.result_tab_analysis, androidx.compose.material.icons.Icons.Filled.Shield),
    METADATA(R.string.result_tab_metadata, androidx.compose.material.icons.Icons.AutoMirrored.Filled.List),
    GPS(R.string.result_tab_map, androidx.compose.material.icons.Icons.Filled.LocationOn)
}

data class ResultActions(
    val onCopy: () -> Unit,
    val onJson: () -> Unit,
    val onPdf: () -> Unit,
    val onClean: (() -> Unit)?,
    val onApi: (() -> Unit)?,
    val onCsv: (() -> Unit)? = null,
    val onEdit: (() -> Unit)? = null,
    val onWipeGps: (() -> Unit)? = null,
    /** Очистить и сразу открыть «Поделиться». */
    val onCleanShare: (() -> Unit)? = null,
)

@Composable
fun ResultView(report: MetaReport, actions: ResultActions, sourceUri: String? = null) {
    val tabs = remember(report.gps) {
        if (report.gps == null) listOf(ReportTab.OVERVIEW, ReportTab.METADATA)
        else ReportTab.entries.toList()
    }
    var selected by rememberSaveable(reportStateKey(report)) { mutableIntStateOf(0) }
    val tab = tabs[selected.coerceIn(0, tabs.lastIndex)]
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CyberEyebrow(stringResource(R.string.result_eyebrow_report))
                Text(report.file.name.ifBlank { stringResource(R.string.common_unnamed) },
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.PhoneAndroid, contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
            Text(" " + stringResource(R.string.result_badge_local), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
        }
        // Вкладки сменяются растворением, а не мгновенно.
        Crossfade(targetState = tab, modifier = Modifier.weight(1f), animationSpec = tween(220), label = "report-tab") { tab ->
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                ReportTab.OVERVIEW -> OverviewTab(report, actions, sourceUri, onMetadata = { selected = tabs.indexOf(ReportTab.METADATA) })
                ReportTab.METADATA -> MetadataViewer(report, actions.onCsv, sourceUri)
                ReportTab.GPS -> {
                    val point = report.gps
                    if (point != null) GpsTab(point, actions.onWipeGps, report.file.format.isVideo)
                    else Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.result_no_gps),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        }
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 9.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            tabs.forEachIndexed { index, item ->
                val active = item == tab
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = .13f) else Color.Transparent)
                        .clickable { selected = index }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.material3.Icon(item.icon, contentDescription = null,
                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(item.label), style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 26.dp)

@Composable
private fun OverviewTab(report: MetaReport, actions: ResultActions, sourceUri: String?, onMetadata: () -> Unit) {
    val evidence = report.hasAiEvidence
    val attention = report.needsAiReview
    val color = when {
        evidence -> MaterialTheme.colorScheme.error
        attention -> if (MaterialTheme.colorScheme.background.red < .15f) Color(0xFFFFC875) else Color(0xFF7F4A00)
        else -> MaterialTheme.colorScheme.primary
    }
    LazyColumn(modifier = Modifier.testTag("overview_list"), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { PhotoPreview(sourceUri, report.file.name, report.file.format.isVideo) }
        item {
            val exposure = remember(report) { com.example.metacheck.domain.privacy.PrivacyExposures.of(report) }
            PrivacyExposureCard(exposure, actions.onCleanShare, actions.onClean)
        }
        if (GpsPrivacy.hasLocationMarkers(report)) {
            item { GpsPrivacyAlert(report.gps, actions.onWipeGps, partialTags = report.gps == null, video = report.file.format.isVideo) }
        }
        item {
            GlassPanel(accent = color, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CyberEyebrow(stringResource(R.string.result_eyebrow_engine), color = color)
                    Text(
                        stringResource(
                            when {
                                evidence -> R.string.result_verdict_ai
                                attention -> R.string.result_verdict_attention
                                else -> R.string.result_verdict_clean
                            },
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(stringResource(R.string.result_metadata_only),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.result_disclaimer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricPanel(report.actionableSignalCount.toString(), stringResource(R.string.result_metric_signals), Modifier.weight(1f))
                MetricPanel(report.exifSections.size.toString(), stringResource(R.string.result_metric_exif_groups), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                MetricPanel(rememberMetaTexts().formatName(report.file.format), stringResource(R.string.result_metric_format), Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
            }
        }
        item { NeonButton(stringResource(R.string.result_view_all_metadata), onMetadata, Modifier.fillMaxWidth()) }
        item { SectionLabel(stringResource(R.string.result_section_actions)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    CyberOutlineButton(stringResource(R.string.result_action_copy), actions.onCopy, Modifier.weight(1f))
                    CyberOutlineButton(stringResource(R.string.result_action_pdf), actions.onPdf, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    CyberOutlineButton(stringResource(R.string.result_action_json), actions.onJson, Modifier.weight(1f))
                    actions.onClean?.let { CyberOutlineButton(stringResource(R.string.result_action_clean), it, Modifier.weight(1f)) }
                }
                actions.onEdit?.let { CyberOutlineButton(stringResource(R.string.result_action_edit), it, Modifier.fillMaxWidth()) }
                actions.onApi?.let { CyberOutlineButton(stringResource(R.string.result_action_api), it, Modifier.fillMaxWidth()) }
            }
        }
        item { SectionLabel(stringResource(R.string.result_section_signals)) }
        items(report.signals) { signal -> SignalPanel(signal) }
        item {
            Text(stringResource(R.string.result_footer_original),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PhotoPreview(sourceUri: String?, name: String, video: Boolean = false) {
    val context = LocalContext.current
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = sourceUri) {
        value = withContext(Dispatchers.IO) {
            if (sourceUri == null) return@withContext null
            try {
                val uri = Uri.parse(sourceUri)
                if (uri.scheme != "content") return@withContext null
                if (video) return@withContext videoFrame(context, uri)
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                if (opts.outWidth <= 0 || opts.outHeight <= 0) return@withContext null
                var sample = 1
                while (opts.outWidth / sample > 1024 || opts.outHeight / sample > 1024) sample *= 2
                opts.inJustDecodeBounds = false
                opts.inSampleSize = sample
                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                } ?: return@withContext null
                val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
                    when (ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                    )) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
                if (rotation == 0f) bitmap.asImageBitmap() else {
                    val matrix = Matrix().apply { postRotate(rotation) }
                    val rotated = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    if (rotated !== bitmap) bitmap.recycle()
                    rotated.asImageBitmap()
                }
            } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null } catch (_: StackOverflowError) { null } // превью необязательно: без него, но без крэша
        }
    }
    GlassPanel(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(190.dp)
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface))),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(image!!, contentDescription = stringResource(R.string.result_preview_description, name),
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.HideImage, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    Text(stringResource(if (sourceUri == null) R.string.result_preview_not_stored else R.string.result_preview_unavailable),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            CyberEyebrow(stringResource(R.string.result_eyebrow_preview), Modifier.align(Alignment.TopStart).padding(12.dp))
        }
    }
}

/** Первый кадр видео для превью (только на устройстве). */
private fun videoFrame(context: android.content.Context, uri: Uri): androidx.compose.ui.graphics.ImageBitmap? {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val frame = if (android.os.Build.VERSION.SDK_INT >= 27) {
            retriever.getScaledFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 1024, 1024)
        } else retriever.getFrameAtTime(0)
        frame?.asImageBitmap()
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

@Composable
private fun SignalPanel(signal: Signal) {
    val color = when (signal.severity) {
        Severity.AI_EVIDENCE -> MaterialTheme.colorScheme.error
        Severity.CAUTION -> if (MaterialTheme.colorScheme.background.red < .15f) Color(0xFFFFC875) else Color(0xFF7F4A00)
        Severity.INFO -> MaterialTheme.colorScheme.primary
    }
    GlassPanel(modifier = Modifier.fillMaxWidth(), accent = color) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val texts = rememberMetaTexts()
            CyberEyebrow(stringResource(when (signal.severity) {
                Severity.AI_EVIDENCE -> R.string.signal_eyebrow_ai
                Severity.CAUTION -> R.string.signal_eyebrow_caution
                Severity.INFO -> R.string.signal_eyebrow_info
            }), color = color)
            Text(texts.signalTitle(signal), style = MaterialTheme.typography.titleMedium)
            Text(texts.signalDetail(signal), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun GpsTab(gps: GpsPoint, onWipeGps: (() -> Unit)?, video: Boolean = false) {
    val context = LocalContext.current
    val noMapsApp = stringResource(R.string.result_no_maps_app)
    val coords = String.format(Locale.US, "%.6f, %.6f", gps.latitude, gps.longitude)
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionLabel(stringResource(R.string.result_eyebrow_geolocation)) }
        item { GpsPrivacyAlert(gps, onWipeGps, video = video) }
        item {
            val latLon = stringResource(R.string.result_lat_lon)
            val altitude = stringResource(R.string.result_altitude)
            SectionCard(stringResource(R.string.catalog_coordinates_section), buildList {
                add(MetaItem(latLon, coords))
                gps.altitudeMeters?.let { add(MetaItem(altitude, it.toString(), format = ValueFormat.METERS)) }
            }, initiallyExpanded = true)
        }
        item {
            NeonButton(stringResource(R.string.result_open_in_maps), onClick = {
                val geo = Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f", gps.latitude, gps.longitude, gps.latitude, gps.longitude))
                // Нет приложения карт -> открываем OpenStreetMap в браузере.
                val web = Uri.parse(String.format(Locale.US, "https://www.openstreetmap.org/?mlat=%f&mlon=%f#map=16/%f/%f",
                    gps.latitude, gps.longitude, gps.latitude, gps.longitude))
                try { context.startActivity(Intent(Intent.ACTION_VIEW, geo)) }
                catch (_: Exception) {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, web)) }
                    catch (_: Exception) { android.widget.Toast.makeText(context, noMapsApp, android.widget.Toast.LENGTH_SHORT).show() }
                }
            }, modifier = Modifier.fillMaxWidth())
        }
        item { Text(stringResource(R.string.result_map_privacy_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * Ключ состояния экрана отчёта: имя, размер, формат и координаты. Переживает поворот экрана
 * (строка), но различает разные фото с одинаковым именем.
 */
internal fun reportStateKey(report: MetaReport): String =
    "${report.file.name}|${report.file.sizeBytes}|${report.file.format}|${report.gps?.latitude}|${report.gps?.longitude}"
