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
import androidx.compose.material3.Text
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.metacheck.R
import com.example.metacheck.ui.text.ResourceMetaTexts
import com.example.metacheck.ui.text.rememberMetaTexts
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.metacheck.ui.theme.MonoText
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.metacheck.domain.metadata.MetadataCatalog
import com.example.metacheck.domain.metadata.MetadataGroup
import com.example.metacheck.domain.metadata.MetadataRecord
import com.example.metacheck.domain.model.MetaReport

/** Accessible, searchable native viewer for every EXIF/XMP/PNG field actually extracted. */
@Composable
fun MetadataViewer(report: MetaReport, onCsv: (() -> Unit)?, sourceUri: String? = null) {
    // Запись GPS идёт через тот же путь, что и редактор EXIF в студии (аудит, п. 15):
    // дневной лимит Free, проверка JPEG по сигнатуре, проверка координат, запись через staging-файл
    // с обрезкой ("rwt") и сверкой SHA-256 сохранённого файла. Раньше здесь был отдельный код в обход всего этого.
    val studioVm: MetadataStudioViewModel = viewModel(factory = MetadataStudioViewModel.Factory)
    val studioBusy by studioVm.busy.collectAsStateWithLifecycle()
    // rememberSaveable: выбранная на карте точка переживает поворот экрана и выгрузку процесса.
    // Ключ — не только имя файла: два разных фото «IMG_0001.jpg» делили бы точку на карте,
    // и «Сохранить GPS» записал бы координаты чужого фото.
    val reportKey = remember(report) { reportStateKey(report) }
    var selectedLat by rememberSaveable(reportKey) { mutableStateOf(report.gps?.latitude) }
    var selectedLon by rememberSaveable(reportKey) { mutableStateOf(report.gps?.longitude) }
    val lat = selectedLat
    val lon = selectedLon
    val selectedGps = if (lat != null && lon != null) GpsPoint(lat, lon, report.gps?.altitudeMeters) else null
    // Флаг, а не текст: текст берётся из ресурсов при показе и следует за языком интерфейса.
    var pointChanged by rememberSaveable(reportKey) { mutableStateOf(false) }
    val saveGpsFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { destination ->
        // Параметры операции берутся из ViewModel, результат приходит сообщением внизу экрана.
        studioVm.onEditCopyDestination(destination)
        if (destination != null) pointChanged = false
    }

    val texts = rememberMetaTexts()
    val records = remember(report, texts) { MetadataCatalog.from(report, texts) }
    var query by rememberSaveable(reportKey) { mutableStateOf("") }
    var groupId by rememberSaveable(reportKey) { mutableStateOf("") }
    val group = MetadataGroup.entries.firstOrNull { it.name == groupId }
    val results = remember(records, query, group) { MetadataCatalog.filter(records, group, query) }
    val availableGroups = remember(records) { MetadataGroup.entries.filter { group -> records.any { it.group == group } } }
    LazyColumn(
        modifier = Modifier.testTag("metadata_viewer"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.viewer_title), style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(pluralStringResource(R.plurals.viewer_field_count, records.size, records.size),
                    style = MonoText.tag, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(120) },
                modifier = Modifier.fillMaxWidth().testTag("metadata_search"),
                label = { Text(stringResource(R.string.viewer_search_label)) },
                placeholder = { Text(stringResource(R.string.viewer_search_placeholder)) },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text(stringResource(R.string.viewer_search_clear)) }
                },
                shape = RoundedCornerShape(4.dp),
            )
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetadataChip(stringResource(R.string.viewer_group_all), group == null) { groupId = "" }
                availableGroups.forEach { candidate ->
                    MetadataChip(stringResource(ResourceMetaTexts.groupTitleId(candidate)), group == candidate) { groupId = candidate.name }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.viewer_found, results.size), style = MonoText.tag,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
                if (onCsv != null) {
                    TextButton(onClick = onCsv) { Text(stringResource(R.string.viewer_export_csv), color = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
        item(key = "gps_editor") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.viewer_gps_section))
                val point = selectedGps
                if (point != null) {
                    CyberGeoMap(gps = point, onSelect = { newLat, newLon ->
                        selectedLat = newLat
                        selectedLon = newLon
                        pointChanged = true
                    })
                    CyberOutlineButton(
                        stringResource(if (studioBusy) R.string.viewer_saving else R.string.viewer_save_gps),
                        onClick = {
                            if (sourceUri != null) {
                                studioVm.requestEditCopy(sourceUri, MetadataPatch(latitude = point.latitude, longitude = point.longitude))
                                saveGpsFile.launch("gps-${report.file.name.substringBeforeLast('.')}.jpg")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = sourceUri != null && report.file.format.name == "JPEG" && !studioBusy,
                    )
                    if (report.file.format.name != "JPEG") {
                        Text(stringResource(R.string.viewer_gps_jpeg_only))
                    }
                    if (sourceUri == null) Text(stringResource(R.string.viewer_source_unavailable))
                    if (pointChanged) Text(stringResource(R.string.viewer_point_selected))
                } else {
                    Text(stringResource(R.string.viewer_no_gps))
                }
            }
        }
        if (results.isEmpty()) {
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(stringResource(R.string.viewer_nothing_found), style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(if (records.isEmpty()) R.string.viewer_no_metadata else R.string.viewer_try_other_query),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (query.isNotEmpty() || group != null) {
                            CyberOutlineButton(stringResource(R.string.viewer_reset_filters), { query = ""; groupId = "" })
                        }
                    }
                }
            }
        } else {
            val sections = results.groupBy { it.group }
            sections.forEach { (category, entries) ->
                item {
                    SectionLabel(stringResource(ResourceMetaTexts.groupTitleId(category)),
                        Modifier.padding(top = 8.dp), trailing = "${category.marker} · ${entries.size}")
                }
                items(entries) { entry -> MetadataEntry(entry) }
            }
        }
        item {
            Text(stringResource(R.string.viewer_footer),
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
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    )
}

/** Поле: подпись слева, значение моноширинным справа (длинное — под подписью); линия снизу. */
@Composable
private fun MetadataEntry(record: MetadataRecord) {
    val personal = record.group == MetadataGroup.LOCATION
    SelectionContainer {
        FieldRow(record.label, record.value,
            valueColor = if (personal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}
