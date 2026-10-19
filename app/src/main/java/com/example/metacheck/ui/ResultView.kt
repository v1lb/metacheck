package com.example.metacheck.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.metacheck.domain.model.GpsPoint
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.Severity
import com.example.metacheck.domain.model.Signal
import java.util.Locale

private enum class ResultTab(val title: String) {
    AI("Анализ"),
    EXIF("Камера"),
    FILE("Файл"),
    MAP("Карта"),
}

private val listPadding = PaddingValues(16.dp)
private val listSpacing = Arrangement.spacedBy(12.dp)

/** Действия над отчётом. null означает, что кнопка не показывается. */
data class ResultActions(
    val onCopy: () -> Unit,
    val onJson: () -> Unit,
    val onPdf: () -> Unit,
    val onClean: (() -> Unit)?,
    val onApi: (() -> Unit)?,
)

@Composable
fun ResultView(report: MetaReport, actions: ResultActions) {
    val tabs = remember(report) {
        buildList {
            add(ResultTab.AI)
            add(ResultTab.EXIF)
            add(ResultTab.FILE)
            if (report.gps != null) add(ResultTab.MAP)
        }
    }
    var selected by rememberSaveable { mutableStateOf(0) }
    val index = selected.coerceIn(0, tabs.lastIndex)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = i == index, onClick = { selected = i },
                        icon = { Icon(when(tab) {
                            ResultTab.AI -> Icons.Default.Info
                            ResultTab.EXIF -> Icons.Default.Info
                            ResultTab.FILE -> Icons.Default.Info
                            ResultTab.MAP -> Icons.Default.Place
                        }, contentDescription = null) },
                        label = { Text(tab.title) }, alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ОТЧЁТ  /  METACHECK", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text(report.file.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("●  LOCAL", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            SelectionContainer {
                when (tabs[index]) {
                    ResultTab.AI -> AiTab(report, actions)
                    ResultTab.EXIF -> ExifTab(report)
                    ResultTab.FILE -> FileTab(report)
                    ResultTab.MAP -> report.gps?.let { MapTab(it) }
                }
            }
        }
    }

}

@Composable
private fun AiTab(report: MetaReport, actions: ResultActions) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        item { CyberSummary(report) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryMetric("${report.signals.size}", "СИГНАЛОВ", Modifier.weight(1f))
                SummaryMetric("${report.exifSections.size}", "EXIF ГРУПП", Modifier.weight(1f))
                SummaryMetric(report.file.format.label, "ФОРМАТ", Modifier.weight(1f))
            }
        }
        item {
            Text("ДЕЙСТВИЯ С ОТЧЁТОМ", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = actions.onCopy, modifier = Modifier.weight(1f)) { Text("Копировать") }
                OutlinedButton(onClick = actions.onPdf, modifier = Modifier.weight(1f)) { Text("PDF") }
                OutlinedButton(onClick = actions.onJson, modifier = Modifier.weight(1f)) { Text("JSON") }
            }
            actions.onClean?.let { OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                Text("Очистить метаданные") } }
            actions.onApi?.let { OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                Text("Проверить через API") } }
        }
        item { Text("ОБНАРУЖЕННЫЕ ПРИЗНАКИ", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium) }
        items(report.signals) { SignalCard(it) }
        item {
            Text("Метаданные можно удалить или изменить. Отсутствие следов ИИ не подтверждает подлинность снимка.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

}

@Composable
private fun SummaryMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CyberSummary(report: MetaReport) {
    val warning = report.hasAiEvidence
    val accent = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .65f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("✦  IMAGE FORENSICS", style = MaterialTheme.typography.labelMedium, color = accent)
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp))
                .background(accent.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                Text(if (warning) "!" else "✓", style = MaterialTheme.typography.headlineLarge,
                    color = accent, fontWeight = FontWeight.Bold)
            }
            Text(if (warning) "Найдены признаки\nИИ-генерации" else "Явных следов\nИИ не найдено",
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Проверка доступных метаданных", color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = accent.copy(alpha = .25f))
            Text(if (warning) "В файле обнаружены признаки, требующие дополнительной проверки."
                else "Это не означает, что изображение подлинное: анализ пикселей не выполнялся.",
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SignalCard(signal: Signal) {
    val scheme = MaterialTheme.colorScheme
    val (label, container, content) = when (signal.severity) {
        Severity.AI_EVIDENCE -> Triple("Следы ИИ", scheme.errorContainer, scheme.onErrorContainer)
        Severity.CAUTION -> Triple("Внимание", scheme.tertiaryContainer, scheme.onTertiaryContainer)
        Severity.INFO -> Triple("Заметка", scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
    BannerCard(title = "$label: ${signal.title}", body = signal.detail, container = container, content = content)
}

@Composable
private fun ExifTab(report: MetaReport) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        if (report.exifSections.isEmpty()) {
            item { Text("EXIF не найден: файл не содержит данных камеры.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(report.exifSections) { SectionCard(it.title, it.items) }
    }
}

@Composable
private fun FileTab(report: MetaReport) {
    val f = report.file
    val fileItems = buildList {
        add(MetaItem("Имя", f.name))
        add(MetaItem("Формат", f.format.label))
        add(MetaItem("MIME", f.mimeType))
        add(MetaItem("Размер", formatSize(f.sizeBytes)))
        add(MetaItem("Просканировано", formatSize(f.scannedBytes.toLong()) + if (f.truncated) " (начало файла)" else ""))
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        item { SectionCard("Файл", fileItems) }
        if (report.xmpItems.isNotEmpty()) item { SectionCard("XMP / IPTC", report.xmpItems) }
        if (report.pngTextItems.isNotEmpty()) item { SectionCard("Текстовые блоки PNG", report.pngTextItems) }
    }
}

@Composable
private fun MapTab(gps: GpsPoint) {
    val context = LocalContext.current
    val coords = String.format(Locale.US, "%.6f, %.6f", gps.latitude, gps.longitude)
    val items = buildList {
        add(MetaItem("Координаты", coords))
        gps.altitudeMeters?.let { add(MetaItem("Высота", String.format(Locale.US, "%.0f м", it))) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        item { SectionCard("Геолокация", items) }
        item {
            Button(onClick = { openInMaps(context, gps) }, modifier = Modifier.fillMaxWidth()) {
                Text("Открыть в приложении карт")
            }
        }
        item {
            Text(
                "Карта открывается в другом приложении: MetaCheck сам в сеть не выходит.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun openInMaps(context: Context, gps: GpsPoint) {
    val uri = Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f", gps.latitude, gps.longitude, gps.latitude, gps.longitude))
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: Exception) {
        // Нет приложения карт: ничего не делаем, координаты остаются на экране.
    }
}
