package com.example.metacheck.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.metacheck.ui.LocalizedText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.metacheck.domain.metadata.MetadataPatch
import com.example.metacheck.domain.metadata.MetadataPatchValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class StudioMode { STRIP, EDIT, BATCH }

/** Standalone native workspace for safe SAF operations. Never requests storage permission. */
@Composable
fun MetadataStudioScreen(
    initialUri: String?,
    isPro: Boolean,
    onUpgrade: () -> Unit,
    onBack: () -> Unit,
    vm: MetadataStudioViewModel = viewModel(factory = MetadataStudioViewModel.Factory),
) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val batch by vm.batch.collectAsStateWithLifecycle()
    val usedToday by vm.usedToday.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(StudioMode.STRIP) }
    var source by rememberSaveable { mutableStateOf(initialUri.orEmpty()) }
    var batchSources by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var overwriteConfirm by remember { mutableStateOf(false) }
    var worldPicker by remember { mutableStateOf(false) }
    var pendingPatch by remember { mutableStateOf<MetadataPatch?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var gpsRemove by rememberSaveable { mutableStateOf(false) }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var dateTaken by rememberSaveable { mutableStateOf("") }
    var dateDigitized by rememberSaveable { mutableStateOf("") }
    var manufacturer by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var author by rememberSaveable { mutableStateOf("") }
    var copyright by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var iso by rememberSaveable { mutableStateOf("") }
    var aperture by rememberSaveable { mutableStateOf("") }
    var shutter by rememberSaveable { mutableStateOf("") }

    val selectedMime by produceState<String?>(initialValue = null, key1 = source) {
        value = withContext(Dispatchers.IO) {
            if (source.isEmpty()) null else runCatching {
                val uri = Uri.parse(source)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val header = ByteArray(8)
                    var count = 0
                    while (count < header.size) {
                        val n = stream.read(header, count, header.size - count)
                        if (n < 0) break
                        if (n == 0) continue
                        count += n
                    }
                    when {
                        count >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() -> "image/jpeg"
                        count == 8 && header.toList() == listOf(137, 80, 78, 71, 13, 10, 26, 10).map { it.toByte() } -> "image/png"
                        else -> context.contentResolver.getType(uri)?.lowercase(Locale.ROOT)
                    }
                }
            }.getOrNull()
        }
    }
    val sourceIsJpeg = selectedMime == "image/jpeg"
    val exifPreview by produceState<String>(initialValue = "", key1 = source) {
        value = withContext(Dispatchers.IO) {
            if (source.isBlank()) return@withContext ""
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(source))?.use { stream ->
                    val exif = ExifInterface(stream)
                    listOfNotNull(
                        exif.getAttribute(ExifInterface.TAG_MAKE),
                        exif.getAttribute(ExifInterface.TAG_MODEL),
                        exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                        exif.latLong?.let { "GPS: %.5f / %.5f".format(Locale.US, it[0], it[1]) },
                    ).joinToString(" · ").take(230)
                } ?: ""
            }.getOrDefault("")
        }
    }

    val chooseImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { source = uri.toString(); actionError = null }
    }
    val chooseMultiple = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        if (list.isNotEmpty()) {
            if (!isPro) onUpgrade()
            else if (list.size > 50) actionError = "Допускается до 50 фото за один запуск"
            else { batchSources = list.map(Uri::toString); actionError = null }
        }
    }
    val chooseTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folder ->
        if (folder != null && batchSources.isNotEmpty()) {
            vm.batchStrip(batchSources, folder)
        }
    }
    val saveCleanJpeg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        if (uri != null && source.isNotBlank()) vm.stripCopy(source, uri)
    }
    val saveCleanPng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null && source.isNotBlank()) vm.stripCopy(source, uri)
    }
    val saveEdited = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        val patch = pendingPatch
        if (uri != null && patch != null && source.isNotBlank()) vm.editCopy(source, uri, patch)
    }
    val backupJpeg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        if (uri != null && source.isNotBlank()) vm.overwrite(source, uri, pendingPatch)
    }
    val backupPng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null && source.isNotBlank()) vm.overwrite(source, uri, pendingPatch)
    }

    fun patchOrError(): MetadataPatch? {
        fun stringOrNull(value: String) = value.trim().takeIf { it.isNotEmpty() }
        fun numberOrNull(value: String) = stringOrNull(value)?.replace(',', '.')?.toDoubleOrNull()
        if ((latitude.isNotBlank() && numberOrNull(latitude) == null) ||
            (longitude.isNotBlank() && numberOrNull(longitude) == null) ||
            (iso.isNotBlank() && iso.toIntOrNull() == null) ||
            (aperture.isNotBlank() && numberOrNull(aperture) == null) ||
            (shutter.isNotBlank() && numberOrNull(shutter) == null)) {
            actionError = "Проверьте числа: GPS, ISO, диафрагму и выдержку"
            return null
        }
        val result = MetadataPatch(
            removeGps = gpsRemove,
            latitude = if (gpsRemove) null else numberOrNull(latitude),
            longitude = if (gpsRemove) null else numberOrNull(longitude),
            dateTaken = stringOrNull(dateTaken),
            dateDigitized = stringOrNull(dateDigitized),
            make = stringOrNull(manufacturer),
            model = stringOrNull(model),
            author = stringOrNull(author),
            copyright = stringOrNull(copyright),
            description = stringOrNull(description),
            iso = iso.toIntOrNull(),
            aperture = numberOrNull(aperture),
            exposureSeconds = numberOrNull(shutter),
        )
        val validation = MetadataPatchValidator.validate(result)
        if (validation.isNotEmpty()) { actionError = validation.joinToString(". "); return null }
        actionError = null
        return result
    }

    LaunchedEffect(message) {
        if (message != null) actionError = message
        if (message != null) vm.clearMessage()
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberEyebrow("METADATA STUDIO  /  ON-DEVICE")
                Text("Управление метаданными", style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(if (isPro) "PRO · Без ограничений на очистку" else
                    "FREE · Очищено сегодня: $usedToday из 1 · Сброс в 00:00 UTC",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text("Реальное сохранение изменений в файл. Без отправки изображения в сеть.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CyberOutlineButton("← К отчёту", onBack, Modifier.fillMaxWidth())
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioTab("Очистка", mode == StudioMode.STRIP) { mode = StudioMode.STRIP }
                StudioTab("EXIF-редактор PRO", mode == StudioMode.EDIT) {
                    if (isPro) mode = StudioMode.EDIT else onUpgrade()
                }
                StudioTab("Пакетно PRO", mode == StudioMode.BATCH) {
                    if (isPro) mode = StudioMode.BATCH else onUpgrade()
                }
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (actionError != null) item {
            GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.error) {
                Text(actionError.orEmpty(), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (mode != StudioMode.BATCH) {
            item {
                GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.primary) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CyberEyebrow("01 / ИСХОДНОЕ ИЗОБРАЖЕНИЕ")
                        Text(if (source.isBlank()) "Файл не выбран" else "Изображение выбрано",
                            color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        if (source.isNotBlank()) Text(
                            source.substringAfterLast('/').take(100), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (exifPreview.isNotBlank()) Text(exifPreview,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        CyberOutlineButton("Выбрать другой файл", { chooseImage.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (mode == StudioMode.STRIP) {
            item {
                GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.tertiary) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CyberEyebrow("02 / ПОЛНАЯ ОЧИСТКА")
                        Text("Удалить метаданные", color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge)
                        Text("Файл будет декодирован и сохранён заново без переносимых EXIF, XMP, IPTC и GPS. JPEG повторно сжимается; HEIC/GIF/WebP сохраняются как PNG, а анимация может потеряться. Подписи C2PA становятся недействительными.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        NeonButton("Сохранить очищенный дубликат", onClick = {
                            if (source.isNotBlank() && !busy) {
                                if (sourceIsJpeg) saveCleanJpeg.launch("metacheck-clean-${System.currentTimeMillis()}.jpg")
                                else saveCleanPng.launch("metacheck-clean-${System.currentTimeMillis()}.png")
                            }
                        }, modifier = Modifier.fillMaxWidth())
                        CyberOutlineButton("Перезаписать оригинал с резервной копией", {
                            pendingPatch = null; overwriteConfirm = true
                        }, Modifier.fillMaxWidth())
                        Text("Для перезаписи доступны только JPEG/PNG и только документы с правом записи. Сначала вы выберете место для полноценной резервной копии.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else if (mode == StudioMode.EDIT) {
            item {
                GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.secondary) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        CyberEyebrow("02 / РЕДАКТОР EXIF")
                        Text("Координаты и GPS", style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Удалить GPS целиком", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                            Switch(checked = gpsRemove, onCheckedChange = { gpsRemove = it })
                        }
                        if (!gpsRemove) {
                            StudioField("Широта (−90…90)", latitude) { latitude = it }
                            StudioField("Долгота (−180…180)", longitude) { longitude = it }
                            CyberOutlineButton("Выбрать точку на мировой сетке", { worldPicker = true }, Modifier.fillMaxWidth())
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text("Дата съёмки", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        StudioDatePicker("Дата оригинала", dateTaken) { dateTaken = it }
                        StudioDatePicker("Дата оцифровки", dateDigitized) { dateDigitized = it }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text("Камера и авторство", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        StudioField("Производитель", manufacturer) { manufacturer = it }
                        StudioField("Модель камеры", model) { model = it }
                        StudioField("Автор", author) { author = it }
                        StudioField("Copyright", copyright) { copyright = it }
                        StudioField("Описание", description, maxLines = 3) { description = it }
                        StudioField("ISO", iso) { iso = it }
                        StudioField("Диафрагма (например, 1.8)", aperture) { aperture = it }
                        StudioField("Выдержка в секундах (например, 0.02)", shutter) { shutter = it }
                        Text("Пустые поля остаются без изменений. Редактор работает с JPEG; противоречащие EXIF блоки XMP/IPTC в JPEG удаляются. Изменения не применяются к исходному файлу до сохранения. Любая правка может сделать C2PA-подписи недействительными.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        NeonButton("Сохранить изменённую копию JPEG", onClick = {
                            if (source.isNotBlank() && !busy) {
                                patchOrError()?.let { pendingPatch = it; saveEdited.launch("metacheck-edited-${System.currentTimeMillis()}.jpg") }
                            }
                        }, modifier = Modifier.fillMaxWidth())
                        CyberOutlineButton("Перезаписать JPEG с резервной копией", {
                            if (source.isNotBlank() && !busy) {
                                patchOrError()?.let { pendingPatch = it; overwriteConfirm = true }
                            }
                        }, Modifier.fillMaxWidth())
                    }
                }
            }
        } else {
            item {
                GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.secondary) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CyberEyebrow("BATCH STRIP / PRO")
                        Text("Массовая очистка", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("До 50 файлов за один запуск. Новые копии будут сохранены в выбранную папку. Оригиналы останутся неизменными.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Выбрано: ${batchSources.size}", color = MaterialTheme.colorScheme.onSurface)
                        CyberOutlineButton("Выбрать фотографии", { chooseMultiple.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth())
                        NeonButton("Выбрать папку и запустить", {
                            if (!busy && batchSources.isNotEmpty()) chooseTree.launch(null)
                            else actionError = "Сначала выберите файлы"
                        }, Modifier.fillMaxWidth())
                        if (batch.total > 0) {
                            LinearProgressIndicator(
                                progress = { batch.completed.toFloat() / batch.total },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("${batch.completed}/${batch.total} · Успешно: ${batch.succeeded} · Ошибок: ${batch.failed}",
                                color = MaterialTheme.colorScheme.onSurface)
                            if (batch.current.isNotBlank()) Text(batch.current, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (batch.errors.isNotEmpty()) {
                item { SectionLabel("ОШИБКИ ПО ФАЙЛАМ") }
                items(batch.errors) { error -> Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }

    if (overwriteConfirm) {
        AlertDialog(
            onDismissRequest = { overwriteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Заменить оригинальный файл?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(
                "Действие нельзя гарантированно выполнить атомарно у всех файловых провайдеров. Вы сначала сохраните отдельную резервную копию; только после проверки копии будет произведена замена. При сбое приложение попытается восстановить оригинал.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ) },
            confirmButton = {
                TextButton(onClick = {
                    overwriteConfirm = false
                    if (sourceIsJpeg) backupJpeg.launch("metacheck-original-backup-${System.currentTimeMillis()}.jpg")
                    else backupPng.launch("metacheck-original-backup-${System.currentTimeMillis()}.png")
                }) { Text("Создать резервную копию", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { overwriteConfirm = false }) { Text("Отмена") } },
        )
    }
    if (worldPicker) {
        WorldGridDialog(
            latitude = latitude.replace(',', '.').toDoubleOrNull() ?: 0.0,
            longitude = longitude.replace(',', '.').toDoubleOrNull() ?: 0.0,
            onDismiss = { worldPicker = false },
            onSelect = { lat, lon ->
                latitude = String.format(Locale.US, "%.6f", lat)
                longitude = String.format(Locale.US, "%.6f", lon)
                worldPicker = false
            },
        )
    }
}

@Composable
private fun StudioTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.background(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .19f) else MaterialTheme.colorScheme.surface,
        RoundedCornerShape(14.dp),
    ).clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 12.dp)) {
        Text(label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StudioField(label: String, value: String, maxLines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(512)) },
        label = { Text(label) },
        placeholder = { Text("Оставить без изменений") },
        singleLine = maxLines == 1,
        maxLines = maxLines,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = when {
            label == "ISO" -> KeyboardType.Number
            label.startsWith("Широта") || label.startsWith("Долгота") ||
                label.startsWith("Диафрагма") || label.startsWith("Выдержка") -> KeyboardType.Decimal
            else -> KeyboardType.Text
        }),
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun StudioDatePicker(label: String, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val cal = remember { java.util.Calendar.getInstance() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick = {
        DatePickerDialog(context, { _, year, month, day ->
            TimePickerDialog(context, { _, hour, minute ->
                onChange(String.format(Locale.ROOT, "%04d:%02d:%02d %02d:%02d:00", year, month + 1, day, hour, minute))
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show()
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
    }, modifier = Modifier.weight(1f)) {
        Text("$label: ${value.ifBlank { "не изменять" }}", color = MaterialTheme.colorScheme.onSurface)
    }
    if (value.isNotBlank()) TextButton(onClick = { onChange("") }) {
        Text("Сброс", color = MaterialTheme.colorScheme.primary)
    }
    }
}

/** Offline world-coordinate grid, not a street map or geocoding service. */
@Composable
private fun WorldGridDialog(
    latitude: Double,
    longitude: Double,
    onDismiss: () -> Unit,
    onSelect: (Double, Double) -> Unit,
) {
    var lat by remember { mutableStateOf(latitude.coerceIn(-90.0, 90.0)) }
    var lon by remember { mutableStateOf(longitude.coerceIn(-180.0, 180.0)) }
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Мировая сетка GPS", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Коснитесь сетки для выбора координат. Это схематическая карта широты/долготы без улиц.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Canvas(Modifier.fillMaxWidth().height(220.dp).pointerInput(Unit) {
                    detectTapGestures { point ->
                        lon = ((point.x / size.width) * 360.0 - 180.0).coerceIn(-180.0, 180.0)
                        lat = (90.0 - (point.y / size.height) * 180.0).coerceIn(-90.0, 90.0)
                    }
                }) {
                    drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF0A1520), Color(0xFF14243C))))
                    for (i in 1..11) {
                        val x = size.width * i / 12f
                        drawLine(outline.copy(alpha = .6f), Offset(x, 0f), Offset(x, size.height), 1f)
                    }
                    for (i in 1..5) {
                        val y = size.height * i / 6f
                        drawLine(outline.copy(alpha = .6f), Offset(0f, y), Offset(size.width, y), 1f)
                    }
                    val x = ((lon + 180) / 360 * size.width).toFloat()
                    val y = ((90 - lat) / 180 * size.height).toFloat()
                    drawCircle(primary.copy(alpha = .23f), radius = 25f, center = Offset(x, y))
                    drawCircle(primary, radius = 9f, center = Offset(x, y))
                }
                Text(String.format(Locale.US, "Широта %.6f · Долгота %.6f", lat, lon),
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(lat, lon) }) { Text("Использовать точку", color = primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = MaterialTheme.colorScheme.onSurface) } },
    )
}
