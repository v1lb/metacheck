package com.example.metacheck.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.metacheck.domain.metadata.MetadataPatch
import com.example.metacheck.domain.model.GpsPoint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.example.metacheck.ui.LocalizedText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.metacheck.domain.metadata.MetadataCatalog
import com.example.metacheck.domain.metadata.MetadataGroup
import com.example.metacheck.domain.metadata.MetadataRecord
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.ui.theme.CyberPalette

/** Accessible, searchable native viewer for every EXIF/XMP/PNG field actually extracted. */
@Composable
fun MetadataViewer(report: MetaReport, onCsv: (() -> Unit)?, sourceUri: String? = null) {
    // Запись GPS идёт через тот же путь, что и редактор EXIF в студии (аудит, п. 15):
    // дневной лимит Free, проверка JPEG по сигнатуре, проверка координат, запись через staging-файл
    // с обрезкой ("rwt") и сверкой SHA-256 сохранённого файла. Раньше здесь был отдельный код в обход всего этого.
    val studioVm: MetadataStudioViewModel = viewModel(factory = MetadataStudioViewModel.Factory)
    val studioBusy by studioVm.busy.collectAsStateWithLifecycle()
    // rememberSaveable: выбранная на карте точка переживает поворот экрана и выгрузку процесса.
    var selectedLat by rememberSaveable(report.file.name) { mutableStateOf(report.gps?.latitude) }
    var selectedLon by rememberSaveable(report.file.name) { mutableStateOf(report.gps?.longitude) }
    val lat = selectedLat
    val lon = selectedLon
    val selectedGps = if (lat != null && lon != null) GpsPoint(lat, lon, report.gps?.altitudeMeters) else null
    var saveStatus by rememberSaveable(report.file.name) { mutableStateOf("") }
    val saveGpsFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { destination ->
        // Параметры операции берутся из ViewModel, результат приходит сообщением внизу экрана.
        studioVm.onEditCopyDestination(destination)
        if (destination != null) saveStatus = ""
    }

    val records = remember(report) { MetadataCatalog.from(report) }
    var query by rememberSaveable(report.file.name) { mutableStateOf("") }
    var groupId by rememberSaveable(report.file.name) { mutableStateOf("") }
    val group = MetadataGroup.entries.firstOrNull { it.name == groupId }
    val results = remember(records, query, group) { MetadataCatalog.filter(records, group, query) }
    val availableGroups = remember(records) { MetadataGroup.entries.filter { group -> records.any { it.group == group } } }
    LazyColumn(
        modifier = Modifier.testTag("metadata_viewer"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                CyberEyebrow("NATIVE METADATA EXPLORER  /  LOCAL")
                Text("Метаданные файла", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("${records.size} полей · просмотр прямо на устройстве", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(120) },
                modifier = Modifier.fillMaxWidth().testTag("metadata_search"),
                label = { Text("Поиск по тегам и значениям") },
                placeholder = { Text("Например: ISO, GPS, Adobe") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Сброс") }
                },
                shape = RoundedCornerShape(16.dp),
            )
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetadataChip("Все", group == null) { groupId = "" }
                availableGroups.forEach { candidate ->
                    MetadataChip(candidate.title, group == candidate) { groupId = candidate.name }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("НАЙДЕНО: ${results.size}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                if (onCsv != null) {
                    TextButton(onClick = onCsv) { Text("Экспорт CSV ↗", color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
        item(key = "gps_editor") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("GPS / ИНТЕРАКТИВНАЯ КАРТА")
                val point = selectedGps
                if (point != null) {
                    CyberGeoMap(gps = point, onSelect = { newLat, newLon ->
                        selectedLat = newLat
                        selectedLon = newLon
                        saveStatus = "Точка выбрана. Сохраните копию фотографии."
                    })
                    Button(
                        enabled = sourceUri != null && report.file.format.name == "JPEG" && !studioBusy,
                        onClick = {
                            if (sourceUri != null) {
                                studioVm.requestEditCopy(sourceUri, MetadataPatch(latitude = point.latitude, longitude = point.longitude))
                                saveGpsFile.launch("gps-${report.file.name.substringBeforeLast('.')}.jpg")
                            }
                        },
                    ) { Text(if (studioBusy) "Сохранение…" else "Сохранить GPS в JPEG") }
                    if (report.file.format.name != "JPEG") {
                        Text("Запись GPS пока поддерживается только для JPEG.")
                    }
                    if (sourceUri == null) Text("Исходный файл недоступен: откройте оригинал фотографии.")
                    if (saveStatus.isNotBlank()) Text(saveStatus)
                } else {
                    Text("В фотографии нет GPS-координат.")
                }
            }
        }
        if (results.isEmpty()) {
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Ничего не найдено", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(if (records.isEmpty()) "Файл не содержит распознанных метаданных."
                            else "Попробуйте другой запрос или выберите категорию «Все».",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (query.isNotEmpty() || group != null) {
                            CyberOutlineButton("Сбросить фильтры", { query = ""; groupId = "" })
                        }
                    }
                }
            }
        } else {
            val sections = results.groupBy { it.group }
            sections.forEach { (category, entries) ->
                item {
                    SectionLabel("${category.marker} / ${category.title.uppercase()}")
                }
                items(entries) { entry -> MetadataEntry(entry) }
            }
        }
        item {
            Text("Показаны доступные поля, извлечённые локальными парсерами MetaCheck. " +
                "Отсутствие тега не доказывает подлинность файла. GPS может содержать личные данные.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MetadataChip(title: String, active: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = active,
        onClick = onClick,
        label = { Text(title, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .22f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
    )
}

@Composable
private fun MetadataEntry(record: MetadataRecord) {
    GlassPanel(Modifier.fillMaxWidth(), accent = if (record.group == MetadataGroup.LOCATION) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(record.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(record.value, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())
            }
            Text(record.section, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = .92f))
        }
    }
}
