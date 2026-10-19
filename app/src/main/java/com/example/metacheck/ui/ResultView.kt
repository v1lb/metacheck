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
    AI("ИИ-анализ"),
    EXIF("EXIF и камера"),
    FILE("Файл и ПО"),
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

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(onClick = actions.onCopy, label = { Text("Копировать всё") })
            AssistChip(onClick = actions.onJson, label = { Text("JSON") })
            AssistChip(onClick = actions.onPdf, label = { Text("PDF") })
            actions.onClean?.let { AssistChip(onClick = it, label = { Text("Очистить метаданные") }) }
            actions.onApi?.let { AssistChip(onClick = it, label = { Text("Проверить через API") }) }
        }
        ScrollableTabRow(selectedTabIndex = index, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, tab ->
                Tab(selected = i == index, onClick = { selected = i }, text = { Text(tab.title) })
            }
        }
        SelectionContainer {
            when (tabs[index]) {
                ResultTab.AI -> AiTab(report)
                ResultTab.EXIF -> ExifTab(report)
                ResultTab.FILE -> FileTab(report)
                ResultTab.MAP -> report.gps?.let { MapTab(it) }
            }
        }
    }
}

@Composable
private fun AiTab(report: MetaReport) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        item {
            if (report.hasAiEvidence) {
                BannerCard(
                    title = "Найдены следы ИИ-генерации",
                    body = "В метаданных файла есть явные признаки генерации. Подробности ниже.",
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                BannerCard(
                    title = "В метаданных следов ИИ не найдено",
                    body = "Это не доказывает подлинность: метаданные легко удалить. " +
                        "Анализ самих пикселей нейросетью появится на следующих этапах.",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        items(report.signals) { SignalCard(it) }
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
