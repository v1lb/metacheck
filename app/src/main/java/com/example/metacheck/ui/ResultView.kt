package com.example.metacheck.ui

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
import androidx.compose.material3.Text
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
import com.example.metacheck.domain.model.GpsPoint
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.Severity
import com.example.metacheck.domain.model.Signal
import com.example.metacheck.ui.theme.CyberPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class ReportTab(val label: String, val glyph: String) {
    OVERVIEW("Анализ", "◉"), EXIF("Камера", "◎"), FILE("Файл", "▤"), GPS("Карта", "⌖")
}

data class ResultActions(
    val onCopy: () -> Unit,
    val onJson: () -> Unit,
    val onPdf: () -> Unit,
    val onClean: (() -> Unit)?,
    val onApi: (() -> Unit)?,
)

@Composable
fun ResultView(report: MetaReport, actions: ResultActions, sourceUri: String? = null) {
    val tabs = remember(report.gps) {
        if (report.gps == null) listOf(ReportTab.OVERVIEW, ReportTab.EXIF, ReportTab.FILE)
        else ReportTab.entries.toList()
    }
    var selected by rememberSaveable(report.file.name) { mutableIntStateOf(0) }
    val tab = tabs[selected.coerceIn(0, tabs.lastIndex)]
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CyberEyebrow("FORENSIC REPORT  /  ON-DEVICE")
                Text(report.file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PulseIndicator()
            Text(" LOCAL", color = CyberPalette.Green, style = MaterialTheme.typography.labelMedium)
        }
        SelectionContainer(modifier = Modifier.weight(1f)) {
            when (tab) {
                ReportTab.OVERVIEW -> OverviewTab(report, actions, sourceUri)
                ReportTab.EXIF -> ExifTab(report)
                ReportTab.FILE -> FileTab(report)
                ReportTab.GPS -> report.gps?.let { GpsTab(it) }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(CyberPalette.Glass).padding(horizontal = 9.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            tabs.forEachIndexed { index, item ->
                val active = item == tab
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (active) CyberPalette.Cyan.copy(alpha = .13f) else Color.Transparent)
                        .clickable { selected = index }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(item.glyph, style = MaterialTheme.typography.titleLarge,
                        color = if (active) CyberPalette.Cyan else CyberPalette.Muted)
                    Text(item.label, style = MaterialTheme.typography.labelMedium,
                        color = if (active) CyberPalette.White else CyberPalette.Muted)
                }
            }
        }
    }
}

private val contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 26.dp)

@Composable
private fun OverviewTab(report: MetaReport, actions: ResultActions, sourceUri: String?) {
    val evidence = report.hasAiEvidence
    val attention = !evidence && (report.file.truncated || report.signals.any { it.severity == Severity.CAUTION })
    val color = when {
        evidence -> CyberPalette.Danger
        attention -> CyberPalette.Amber
        else -> CyberPalette.Cyan
    }
    LazyColumn(modifier = Modifier.testTag("overview_list"), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { PhotoPreview(sourceUri, report.file.name) }
        item {
            GlassPanel(accent = color, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        PulseIndicator(color)
                        CyberEyebrow("AI METADATA SIGNAL ENGINE", color = color)
                    }
                    Text(
                        when {
                            evidence -> "Обнаружены признаки ИИ"
                            attention -> "Нужна дополнительная проверка"
                            else -> "Явных следов ИИ не найдено"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text("Проверка только метаданных • не анализ пикселей",
                        color = CyberPalette.Muted, style = MaterialTheme.typography.bodyMedium)
                    ScanningBars(Modifier.fillMaxWidth().height(43.dp))
                    Text(
                        "Результат не подтверждает подлинность снимка. Метаданные могут быть удалены или подделаны.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricPanel(report.signals.size.toString(), "СИГНАЛОВ", Modifier.weight(1f))
                MetricPanel(report.exifSections.size.toString(), "ГРУПП EXIF", Modifier.weight(1f), CyberPalette.Purple)
                MetricPanel(report.file.format.label, "ФОРМАТ", Modifier.weight(1f), CyberPalette.Green)
            }
        }
        item { SectionLabel("ДЕЙСТВИЯ С ОТЧЁТОМ") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    CyberOutlineButton("Копировать", actions.onCopy, Modifier.weight(1f))
                    CyberOutlineButton("Сохранить PDF", actions.onPdf, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    CyberOutlineButton("Экспорт JSON", actions.onJson, Modifier.weight(1f))
                    actions.onClean?.let { CyberOutlineButton("Очистить EXIF", it, Modifier.weight(1f)) }
                }
                actions.onApi?.let { CyberOutlineButton("Проверить через API", it, Modifier.fillMaxWidth()) }
            }
        }
        item { SectionLabel("РЕЗУЛЬТАТЫ СКАНИРОВАНИЯ") }
        items(report.signals) { signal -> SignalPanel(signal) }
        item {
            Text("Оригинальный файл даёт более полные метаданные. В истории MetaCheck хранится отчёт, но не копия фото.",
                style = MaterialTheme.typography.bodySmall, color = CyberPalette.Muted)
        }
    }
}

@Composable
private fun PhotoPreview(sourceUri: String?, name: String) {
    val context = LocalContext.current
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = sourceUri) {
        value = withContext(Dispatchers.IO) {
            if (sourceUri == null) return@withContext null
            try {
                val uri = Uri.parse(sourceUri)
                if (uri.scheme != "content") return@withContext null
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
            } catch (_: Exception) { null }
        }
    }
    GlassPanel(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(190.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF192B3A), Color(0xFF101328)))),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(image!!, contentDescription = "Проверяемое изображение: $name",
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("◇", color = CyberPalette.Cyan, style = MaterialTheme.typography.headlineLarge)
                    Text(if (sourceUri == null) "Фотография не хранится в истории" else "Превью недоступно",
                        color = CyberPalette.Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            CyberEyebrow("IMAGE  /  LOCAL PREVIEW", Modifier.align(Alignment.TopStart).padding(12.dp))
        }
    }
}

@Composable
private fun SignalPanel(signal: Signal) {
    val color = when (signal.severity) {
        Severity.AI_EVIDENCE -> CyberPalette.Danger
        Severity.CAUTION -> CyberPalette.Amber
        Severity.INFO -> CyberPalette.Cyan
    }
    GlassPanel(modifier = Modifier.fillMaxWidth(), accent = color) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CyberEyebrow(when (signal.severity) {
                Severity.AI_EVIDENCE -> "AI INDICATOR"
                Severity.CAUTION -> "CAUTION"
                Severity.INFO -> "INFORMATION"
            }, color = color)
            Text(signal.title, style = MaterialTheme.typography.titleMedium)
            Text(signal.detail, color = CyberPalette.Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ExifTab(report: MetaReport) {
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionLabel("EXIF / CAMERA INTELLIGENCE") }
        if (report.exifSections.isEmpty()) item {
            GlassPanel(Modifier.fillMaxWidth()) {
                Text("EXIF не найден. Это не означает, что изображение сгенерировано ИИ.", Modifier.padding(18.dp))
            }
        }
        items(report.exifSections) { section -> SectionCard(section.title, section.items) }
    }
}

@Composable
private fun FileTab(report: MetaReport) {
    val file = report.file
    val details = listOf(
        MetaItem("Имя", file.name), MetaItem("Формат", file.format.label),
        MetaItem("Тип MIME", file.mimeType), MetaItem("Размер", formatSize(file.sizeBytes)),
        MetaItem("Прочитано", formatSize(file.scannedBytes.toLong()) + if (file.truncated) " (частично)" else ""),
    )
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionLabel("FILE / TECHNICAL DETAILS") }
        item { SectionCard("Свойства файла", details, initiallyExpanded = true) }
        if (report.xmpItems.isNotEmpty()) item { SectionCard("XMP / IPTC", report.xmpItems) }
        if (report.pngTextItems.isNotEmpty()) item { SectionCard("Текстовые блоки PNG", report.pngTextItems) }
    }
}

@Composable
private fun GpsTab(gps: GpsPoint) {
    val context = LocalContext.current
    val coords = String.format(Locale.US, "%.6f, %.6f", gps.latitude, gps.longitude)
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionLabel("GEOLOCATION / EXIF") }
        item {
            SectionCard("Координаты", buildList {
                add(MetaItem("Широта / долгота", coords))
                gps.altitudeMeters?.let { add(MetaItem("Высота", String.format(Locale.US, "%.1f м", it))) }
            }, initiallyExpanded = true)
        }
        item {
            NeonButton("Открыть в картах", onClick = {
                val uri = Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f", gps.latitude, gps.longitude, gps.latitude, gps.longitude))
                try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                catch (_: Exception) { android.widget.Toast.makeText(context, "Приложение карт не найдено", android.widget.Toast.LENGTH_SHORT).show() }
            }, modifier = Modifier.fillMaxWidth())
        }
        item { Text("Для открытия карты потребуется другое приложение. GPS из отчёта не отправляется MetaCheck на сервер.",
            style = MaterialTheme.typography.bodySmall, color = CyberPalette.Muted) }
    }
}
