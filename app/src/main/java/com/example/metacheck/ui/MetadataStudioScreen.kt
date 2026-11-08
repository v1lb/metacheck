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
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.R
import com.example.metacheck.domain.metadata.DailyCleanQuota
import com.example.metacheck.ui.text.ErrorTexts
import com.example.metacheck.ui.text.UiText
import com.example.metacheck.ui.text.asString
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.defaultMinSize
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

private const val UNKNOWN_MIME = "application/octet-stream"

/** Standalone native workspace for safe SAF operations. Never requests storage permission. */
@Composable
fun MetadataStudioScreen(
    initialUri: String?,
    isPro: Boolean,
    onUpgrade: () -> Unit,
    onBack: () -> Unit,
    startAudit: Boolean = false,
    onAuditStarted: () -> Unit = {},
    startInBatch: Boolean = false,
    vm: MetadataStudioViewModel = viewModel(factory = MetadataStudioViewModel.Factory),
) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val batch by vm.batch.collectAsStateWithLifecycle()
    val usedToday by vm.usedToday.collectAsStateWithLifecycle()
    val audit by vm.audit.collectAsStateWithLifecycle()
    val perRun = com.example.metacheck.domain.metadata.BatchLimits.perRun(isPro, usedToday)
    var mode by rememberSaveable { mutableStateOf(if (startInBatch) StudioMode.BATCH else StudioMode.STRIP) }
    var source by rememberSaveable { mutableStateOf(initialUri.orEmpty()) }
    var batchSources by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var worldPicker by remember { mutableStateOf(false) }
    val pending by vm.pending.collectAsStateWithLifecycle()
    var actionError by remember { mutableStateOf<UiText?>(null) }
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
        // produceState не сбрасывает значение при смене ключа: без этого после выбора другого файла
        // какое-то время действовал формат предыдущего.
        value = null
        value = withContext(Dispatchers.IO) {
            if (source.isEmpty()) null else runCatching {
                val uri = Uri.parse(source)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val header = ByteArray(16)
                    var count = 0
                    while (count < header.size) {
                        val n = stream.read(header, count, header.size - count)
                        if (n < 0) break
                        if (n == 0) continue
                        count += n
                    }
                    when (com.example.metacheck.data.parser.ImageFormatDetector.detect(header, count)) {
                        ImageFormat.JPEG -> "image/jpeg"
                        ImageFormat.PNG -> "image/png"
                        ImageFormat.WEBP -> "image/webp"
                        ImageFormat.HEIC -> "image/heic"
                        else -> context.contentResolver.getType(uri)?.lowercase(Locale.ROOT)
                    }
                }
            }.getOrNull() ?: UNKNOWN_MIME // проверка завершена, но формат не распознан: операция сама сообщит причину
        }
    }
    val sourceIsJpeg = selectedMime == "image/jpeg"
    // Формат ещё не определён (чтение идёт в фоне). Раньше быстрое нажатие сохраняло JPEG под именем .png.
    val mimeReady = source.isNotBlank() && selectedMime != null
    /** Имя файла для показа вместо хвоста URI вида «document/primary%3ADCIM...». */
    val sourceName by produceState<String?>(initialValue = null, key1 = source) {
        value = null
        value = withContext(Dispatchers.IO) {
            if (source.isBlank()) null else runCatching {
                context.contentResolver.query(Uri.parse(source), arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull()
        }
    }
    /**
     * Проверка до системного диалога сохранения: иначе диалог успевал создать пустой файл,
     * а операция затем отказывала («только JPEG»), и пустой файл оставался у пользователя.
     */
    fun formatError(jpegOnly: Boolean, overwrite: Boolean): UiText? = when {
        !mimeReady -> UiText.Res(R.string.studio_wait_format)
        jpegOnly && !sourceIsJpeg ->
            UiText.Res(if (overwrite) R.string.failure_overwrite_edit_jpeg_only else R.string.failure_edit_jpeg_only)
        overwrite && selectedMime != "image/jpeg" && selectedMime != "image/png" ->
            UiText.Res(R.string.failure_overwrite_strip_jpeg_png_only)
        else -> null
    }
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
        if (uri != null) { vm.persistAccess(uri); source = uri.toString(); actionError = null }
    }
    // «Взять данные из другого фото»: дата, камера, автор, параметры съёмки и место — в поля редактора.
    val donorScope = rememberCoroutineScope()
    var donorInfo by remember { mutableStateOf<UiText?>(null) }
    val chooseDonor = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        donorScope.launch {
            val donor = withContext(Dispatchers.IO) { readDonorExif(context, uri) }
            if (donor == null || donor.filled == 0) { donorInfo = UiText.Res(R.string.studio_copy_none); return@launch }
            donor.dateTaken?.let { dateTaken = it }
            donor.dateDigitized?.let { dateDigitized = it }
            donor.make?.let { manufacturer = it }
            donor.model?.let { model = it }
            donor.author?.let { author = it }
            donor.copyright?.let { copyright = it }
            donor.description?.let { description = it }
            donor.iso?.let { iso = it }
            donor.aperture?.let { aperture = it }
            donor.shutter?.let { shutter = it }
            if (donor.latitude != null && donor.longitude != null) {
                gpsRemove = false
                latitude = donor.latitude; longitude = donor.longitude
            }
            donorInfo = UiText.Res(R.string.studio_copy_done, donor.filled)
        }
    }
    // После выгрузки процесса rememberSaveable возвращает URI, но временное право на него уже отозвано.
    // Проверяем один раз при восстановлении и честно просим выбрать файл снова.
    LaunchedEffect(Unit) {
        if (source.isNotBlank() && !vm.canRead(source)) {
            source = ""
            actionError = MetadataStudioViewModel.LOST_ACCESS
        }
        if (batchSources.isNotEmpty()) {
            val still = vm.readable(batchSources)
            if (still.size != batchSources.size) {
                batchSources = still
                actionError = MetadataStudioViewModel.LOST_ACCESS
            }
        }
    }
    // Выбрать можно сколько угодно: за один запуск очищается [perRun] фото, остальные ждут следующего запуска.
    val chooseMultiple = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        if (list.isNotEmpty()) {
            batchSources = list.map(Uri::toString).distinct().take(MAX_QUEUE); actionError = null
        }
    }
    val chooseAuditTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            vm.persistAccess(tree)
            actionError = null
            vm.auditFolder(tree) { risky -> batchSources = risky.take(MAX_QUEUE) }
        }
    }
    LaunchedEffect(startAudit) {
        if (startAudit) {
            mode = StudioMode.BATCH
            onAuditStarted()
            if (!vm.audit.value.running) chooseAuditTree.launch(null)
        }
    }
    var shiftBack by rememberSaveable { mutableStateOf(false) }
    var shiftHours by rememberSaveable { mutableStateOf("") }
    var shiftMinutes by rememberSaveable { mutableStateOf("") }
    var pendingShift by rememberSaveable { mutableStateOf<Long?>(null) }
    val chooseShiftTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folder ->
        val seconds = pendingShift
        pendingShift = null
        val run = batchSources.take(com.example.metacheck.domain.metadata.BatchLimits.PRO_PER_RUN)
        if (folder != null && seconds != null && run.isNotEmpty()) {
            vm.shiftDates(run, folder, seconds) { batchSources = batchSources.filterNot { it in run } }
        }
    }
    val chooseTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folder ->
        val run = batchSources.take(perRun)
        if (folder != null && run.isNotEmpty()) {
            vm.batchStrip(run, folder) { batchSources = batchSources.filterNot { it in run } }
        }
    }
    val saveCleanJpeg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        if (uri != null && source.isNotBlank()) vm.stripCopy(source, uri) { vm.persistAccess(uri); source = uri.toString(); actionError = null }
    }
    val saveCleanPng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null && source.isNotBlank()) vm.stripCopy(source, uri) { vm.persistAccess(uri); source = uri.toString(); actionError = null }
    }
    // WebP и HEIC чистятся без пережатия и остаются в своём формате.
    val saveCleanWebp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/webp")) { uri ->
        if (uri != null && source.isNotBlank()) vm.stripCopy(source, uri) { vm.persistAccess(uri); source = uri.toString(); actionError = null }
    }
    val saveCleanHeic = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/heic")) { uri ->
        if (uri != null && source.isNotBlank()) vm.stripCopy(source, uri) { vm.persistAccess(uri); source = uri.toString(); actionError = null }
    }
    // Операция и её параметры берутся только из ViewModel (SavedStateHandle), а не из состояния Composable:
    // после пересоздания Activity локальные remember-переменные уже потеряны.
    val saveEdited = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        vm.onEditCopyDestination(uri)
    }
    val backupJpeg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        vm.onBackupDestination(uri)
    }
    val backupPng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        vm.onBackupDestination(uri)
    }

    fun patchOrError(): MetadataPatch? {
        fun stringOrNull(value: String) = value.trim().takeIf { it.isNotEmpty() }
        fun numberOrNull(value: String) = stringOrNull(value)?.replace(',', '.')?.toDoubleOrNull()
        if ((latitude.isNotBlank() && numberOrNull(latitude) == null) ||
            (longitude.isNotBlank() && numberOrNull(longitude) == null) ||
            (iso.isNotBlank() && iso.toIntOrNull() == null) ||
            (aperture.isNotBlank() && numberOrNull(aperture) == null) ||
            (shutter.isNotBlank() && numberOrNull(shutter) == null)) {
            actionError = UiText.Res(R.string.studio_check_numbers)
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
        if (validation.isNotEmpty()) { actionError = ErrorTexts.patchIssues(validation); return null }
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
                Text(stringResource(R.string.studio_title), style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(if (isPro) stringResource(R.string.studio_quota_pro) else
                    stringResource(R.string.studio_quota_free, usedToday, DailyCleanQuota.FREE_DAILY_LIMIT),
                    style = com.example.metacheck.ui.theme.MonoText.tag, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.studio_subtitle),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CyberOutlineButton(stringResource(R.string.studio_back_to_report), onBack, Modifier.fillMaxWidth())
            }
        }
        item {
            Row(Modifier.fillMaxWidth()) {
                StudioTab(stringResource(R.string.studio_tab_strip), mode == StudioMode.STRIP) { mode = StudioMode.STRIP }
                StudioTab(stringResource(R.string.studio_tab_edit), mode == StudioMode.EDIT) {
                    mode = StudioMode.EDIT
                }
                StudioTab(stringResource(R.string.studio_tab_batch), mode == StudioMode.BATCH) {
                    mode = StudioMode.BATCH
                }
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (actionError != null) item {
            GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.error) {
                Text(actionError?.asString().orEmpty(), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (mode != StudioMode.BATCH) {
            item {
                GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.primary) {
                    Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CyberEyebrow(stringResource(R.string.studio_source_eyebrow))
                        Text(stringResource(if (source.isBlank()) R.string.studio_source_none else R.string.studio_source_selected),
                            color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        if (source.isNotBlank()) Text(
                            (sourceName ?: Uri.decode(source.substringAfterLast('/')).substringAfterLast('/')).take(100),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (exifPreview.isNotBlank()) Text(exifPreview,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        CyberOutlineButton(stringResource(R.string.error_choose_other), { chooseImage.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (mode == StudioMode.STRIP) {
            item {
                GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.tertiary) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CyberEyebrow(stringResource(R.string.studio_strip_eyebrow))
                        Text(stringResource(R.string.studio_strip_title), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.studio_strip_text),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        NeonButton(stringResource(R.string.studio_strip_save_copy), onClick = {
                            if (source.isNotBlank() && !busy) {
                                val error = formatError(jpegOnly = false, overwrite = false)
                                if (error != null) actionError = error
                                else when (selectedMime) {
                                    "image/jpeg" -> saveCleanJpeg.launch("metacheck-clean-${System.currentTimeMillis()}.jpg")
                                    "image/webp" -> saveCleanWebp.launch("metacheck-clean-${System.currentTimeMillis()}.webp")
                                    "image/heic" -> saveCleanHeic.launch("metacheck-clean-${System.currentTimeMillis()}.heic")
                                    else -> saveCleanPng.launch("metacheck-clean-${System.currentTimeMillis()}.png")
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth())
                        CyberOutlineButton(stringResource(R.string.studio_strip_overwrite), {
                            if (source.isNotBlank() && !busy) {
                                val error = formatError(jpegOnly = false, overwrite = true)
                                if (error != null) actionError = error else vm.requestOverwrite(source, OverwriteOp.Strip)
                            }
                        }, Modifier.fillMaxWidth())
                        Text(stringResource(R.string.studio_strip_overwrite_note),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else if (mode == StudioMode.EDIT) {
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        CyberOutlineButton(stringResource(R.string.studio_copy_from_photo), { chooseDonor.launch(arrayOf("image/*")) },
                            Modifier.fillMaxWidth())
                        donorInfo?.let { Text(it.asString(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(stringResource(R.string.studio_edit_gps_title), style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.studio_edit_remove_gps), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                            Switch(checked = gpsRemove, onCheckedChange = { gpsRemove = it })
                        }
                        if (!gpsRemove) {
                            StudioField(stringResource(R.string.studio_field_latitude), latitude, KeyboardType.Decimal) { latitude = it }
                            StudioField(stringResource(R.string.studio_field_longitude), longitude, KeyboardType.Decimal) { longitude = it }
                            CyberOutlineButton(stringResource(R.string.studio_pick_on_grid), { worldPicker = true }, Modifier.fillMaxWidth())
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text(stringResource(R.string.studio_edit_dates_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        StudioDatePicker(stringResource(R.string.studio_field_date_taken), dateTaken) { dateTaken = it }
                        StudioDatePicker(stringResource(R.string.studio_field_date_digitized), dateDigitized) { dateDigitized = it }
                        Text(stringResource(R.string.studio_dates_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text(stringResource(R.string.studio_edit_camera_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        StudioField(stringResource(R.string.patch_field_make), manufacturer) { manufacturer = it }
                        StudioField(stringResource(R.string.studio_field_camera_model), model) { model = it }
                        StudioField(stringResource(R.string.patch_field_author), author) { author = it }
                        StudioField(stringResource(R.string.patch_field_copyright), copyright) { copyright = it }
                        StudioField(stringResource(R.string.patch_field_description), description, maxLines = 3) { description = it }
                        StudioField(stringResource(R.string.studio_field_iso), iso, KeyboardType.Number) { iso = it }
                        StudioField(stringResource(R.string.studio_field_aperture), aperture, KeyboardType.Decimal) { aperture = it }
                        StudioField(stringResource(R.string.studio_field_shutter), shutter, KeyboardType.Decimal) { shutter = it }
                        Text(stringResource(R.string.studio_edit_note),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        NeonButton(stringResource(R.string.studio_edit_save_copy), onClick = {
                            if (source.isNotBlank() && !busy) {
                                val error = formatError(jpegOnly = true, overwrite = false)
                                if (error != null) actionError = error
                                else patchOrError()?.let { vm.requestEditCopy(source, it); saveEdited.launch("metacheck-edited-${System.currentTimeMillis()}.jpg") }
                            }
                        }, modifier = Modifier.fillMaxWidth())
                        CyberOutlineButton(stringResource(R.string.studio_edit_overwrite), {
                            if (source.isNotBlank() && !busy) {
                                val error = formatError(jpegOnly = true, overwrite = true)
                                if (error != null) actionError = error
                                else patchOrError()?.let { vm.requestOverwrite(source, OverwriteOp.Edit(it)) }
                            }
                        }, Modifier.fillMaxWidth())
                    }
                }
            }
        } else {
            item {
                GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.secondary) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CyberEyebrow(stringResource(R.string.studio_batch_eyebrow))
                        Text(stringResource(R.string.studio_batch_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.studio_batch_text),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        CyberOutlineButton(stringResource(R.string.studio_audit_button), {
                            if (!audit.running) chooseAuditTree.launch(null)
                        }, Modifier.fillMaxWidth())
                        Text(stringResource(R.string.studio_audit_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        CyberOutlineButton(stringResource(R.string.studio_batch_choose_photos), { chooseMultiple.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth())
                        if (audit.running) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(stringResource(R.string.studio_audit_running, audit.scanned), color = MaterialTheme.colorScheme.onSurface)
                        }
                        audit.result?.let { result -> AuditSummary(result) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(stringResource(R.string.studio_batch_selected, batchSources.size), color = MaterialTheme.colorScheme.onSurface)
                        if (batchSources.isNotEmpty()) Text(
                            stringResource(R.string.studio_batch_run_size, minOf(perRun, batchSources.size)),
                            style = com.example.metacheck.ui.theme.MonoText.tag, color = MaterialTheme.colorScheme.onSurface)
                        if (!isPro) Text(stringResource(R.string.studio_batch_free_note, com.example.metacheck.domain.metadata.BatchLimits.FREE_PER_RUN,
                            com.example.metacheck.domain.metadata.BatchLimits.PRO_PER_RUN),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        NeonButton(stringResource(R.string.studio_batch_choose_folder_start), {
                            when {
                                busy || audit.running -> Unit
                                batchSources.isEmpty() -> actionError = UiText.Res(R.string.studio_batch_choose_files_first)
                                perRun == 0 -> onUpgrade()
                                else -> chooseTree.launch(null)
                            }
                        }, Modifier.fillMaxWidth())
                        if (!isPro && batchSources.size > perRun) CyberOutlineButton(stringResource(R.string.studio_batch_upgrade), onUpgrade, Modifier.fillMaxWidth())
                        if (batch.total > 0) {
                            LinearProgressIndicator(
                                progress = { batch.completed.toFloat() / batch.total },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(stringResource(R.string.studio_batch_progress, batch.completed, batch.total, batch.succeeded, batch.failed),
                                color = MaterialTheme.colorScheme.onSurface)
                            if (batch.current.isNotBlank()) Text(batch.current, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                // Сдвиг даты у выбранных фото: камера была в другом часовом поясе или с неверным временем.
                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.studio_shift_title), style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.studio_shift_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1.1f)) {
                                StudioTab("+", !shiftBack) { shiftBack = false }
                                StudioTab("−", shiftBack) { shiftBack = true }
                            }
                            OutlinedTextField(shiftHours, { shiftHours = it.filter(Char::isDigit).take(4) },
                                Modifier.weight(1f), label = { Text(stringResource(R.string.studio_shift_hours)) }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(4.dp))
                            OutlinedTextField(shiftMinutes, { shiftMinutes = it.filter(Char::isDigit).take(2) },
                                Modifier.weight(1f), label = { Text(stringResource(R.string.studio_shift_minutes)) }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(4.dp))
                        }
                        val shiftRun = minOf(batchSources.size, com.example.metacheck.domain.metadata.BatchLimits.PRO_PER_RUN)
                        if (batchSources.isNotEmpty()) Text(stringResource(R.string.studio_batch_run_size, shiftRun),
                            style = com.example.metacheck.ui.theme.MonoText.tag, color = MaterialTheme.colorScheme.onSurface)
                        NeonButton(stringResource(R.string.studio_shift_run), {
                            val seconds = com.example.metacheck.domain.metadata.DateShift.seconds(shiftBack, shiftHours, shiftMinutes)
                            when {
                                busy || audit.running -> Unit
                                !isPro -> onUpgrade()
                                batchSources.isEmpty() -> actionError = UiText.Res(R.string.studio_batch_choose_files_first)
                                seconds == null -> actionError = UiText.Res(R.string.studio_shift_invalid)
                                else -> { pendingShift = seconds; chooseShiftTree.launch(null) }
                            }
                        }, Modifier.fillMaxWidth())
                        if (!isPro) Text(stringResource(R.string.studio_shift_pro), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (batch.errors.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.studio_batch_errors)) }
                items(batch.errors) { error ->
                    Text(stringResource(R.string.studio_batch_file_error, error.file, ErrorTexts.reason(error.error).asString()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    val awaitingConfirm = (pending as? PendingWrite.Overwrite)?.takeIf { !it.confirmed }
    if (awaitingConfirm != null) {
        AlertDialog(
            onDismissRequest = { vm.cancelPending() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.studio_overwrite_title), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(
                stringResource(R.string.studio_overwrite_text),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ) },
            confirmButton = {
                TextButton(onClick = {
                    if (vm.confirmOverwrite()) {
                        if (sourceIsJpeg) backupJpeg.launch("metacheck-original-backup-${System.currentTimeMillis()}.jpg")
                        else backupPng.launch("metacheck-original-backup-${System.currentTimeMillis()}.png")
                    }
                }) { Text(stringResource(R.string.studio_overwrite_confirm), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { vm.cancelPending() }) { Text(stringResource(R.string.common_cancel)) } },
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

/** Не больше стольких фото в очереди пакета: выбор и проверка папки могут дать сотни. */
private const val MAX_QUEUE = 200

@Composable
private fun AuditSummary(result: com.example.metacheck.domain.metadata.AuditResult) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.studio_audit_summary, result.scanned), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface)
        if (result.findings.isEmpty()) {
            Text(stringResource(R.string.studio_audit_clean_none), color = MaterialTheme.colorScheme.tertiary)
        } else {
            val mono = com.example.metacheck.ui.theme.MonoText.value
            if (result.withLocation > 0) Text(stringResource(R.string.studio_audit_location, result.withLocation), style = mono,
                color = MaterialTheme.colorScheme.error)
            if (result.withSerial > 0) Text(stringResource(R.string.studio_audit_serial, result.withSerial), style = mono,
                color = MaterialTheme.colorScheme.onSurface)
            if (result.withPerson > 0) Text(stringResource(R.string.studio_audit_person, result.withPerson), style = mono,
                color = MaterialTheme.colorScheme.onSurface)
            if (result.otherOnly > 0) Text(stringResource(R.string.studio_audit_other, result.otherOnly), style = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.studio_audit_selected), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (result.limited) Text(stringResource(R.string.studio_audit_limited, com.example.metacheck.domain.metadata.FolderAuditRepository.MAX_FILES),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StudioTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(Modifier.weight(1f)) {
        Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) ink else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
        }
        Box(Modifier.fillMaxWidth().height(if (selected) 3.dp else 1.dp)
            .background(if (selected) ink else MaterialTheme.colorScheme.outlineVariant))
    }
}

/** Поля EXIF другого фото для подстановки в редактор; [filled] — сколько полей найдено. */
private data class DonorExif(
    val dateTaken: String?, val dateDigitized: String?, val make: String?, val model: String?,
    val author: String?, val copyright: String?, val description: String?,
    val iso: String?, val aperture: String?, val shutter: String?,
    val latitude: String?, val longitude: String?,
) {
    val filled: Int get() = listOf(dateTaken, dateDigitized, make, model, author, copyright, description, iso, aperture, shutter)
        .count { it != null } + if (latitude != null && longitude != null) 1 else 0
}

/** Читает EXIF выбранного фото; координаты — через «оригинальный» URI, если Android это позволяет. */
private fun readDonorExif(context: android.content.Context, uri: Uri): DonorExif? {
    fun open(u: Uri) = runCatching { context.contentResolver.openInputStream(u)?.use { ExifInterface(it) } }.getOrNull()
    val exif = open(uri) ?: return null
    val gps = (if (android.os.Build.VERSION.SDK_INT >= 29) runCatching {
        open(android.provider.MediaStore.setRequireOriginal(uri))?.latLong
    }.getOrNull() else null) ?: exif.latLong
    fun text(tag: String) = exif.getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() }
    val fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }
    val exposure = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }
    return DonorExif(
        dateTaken = text(ExifInterface.TAG_DATETIME_ORIGINAL),
        dateDigitized = text(ExifInterface.TAG_DATETIME_DIGITIZED),
        make = text(ExifInterface.TAG_MAKE),
        model = text(ExifInterface.TAG_MODEL),
        author = text(ExifInterface.TAG_ARTIST),
        copyright = text(ExifInterface.TAG_COPYRIGHT),
        description = text(ExifInterface.TAG_IMAGE_DESCRIPTION),
        iso = (text(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY))?.substringBefore(',')?.trim(),
        aperture = fNumber?.let { String.format(Locale.US, "%.1f", it) },
        // Поле выдержки принимает секунды десятичной дробью: 1/50 → 0.02.
        shutter = exposure?.let { java.math.BigDecimal(it).round(java.math.MathContext(4)).stripTrailingZeros().toPlainString() },
        latitude = gps?.let { String.format(Locale.US, "%.6f", it[0]) },
        longitude = gps?.let { String.format(Locale.US, "%.6f", it[1]) },
    )
}

@Composable
private fun StudioField(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    maxLines: Int = 1,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(512)) },
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.studio_field_keep)) },
        singleLine = maxLines == 1,
        maxLines = maxLines,
        modifier = Modifier.fillMaxWidth(),
        // Тип клавиатуры передаётся явно: раньше он угадывался по русской подписи поля.
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(4.dp),
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
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE),
                android.text.format.DateFormat.is24HourFormat(context)).show()
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
    }, modifier = Modifier.weight(1f)) {
        Text(stringResource(R.string.studio_date_value, label, value.ifBlank { stringResource(R.string.studio_date_unchanged) }),
            color = MaterialTheme.colorScheme.onSurface)
    }
    if (value.isNotBlank()) TextButton(onClick = { onChange("") }) {
        Text(stringResource(R.string.viewer_search_clear), color = MaterialTheme.colorScheme.primary)
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
    val outline = MaterialTheme.colorScheme.outlineVariant
    val signal = MaterialTheme.colorScheme.error
    val surfaceTone = MaterialTheme.colorScheme.surfaceVariant
    val context = LocalContext.current
    // Контуры материков (Natural Earth, общественное достояние) из assets: без них сетка была пустой,
    // и выбрать точку «на глаз» было невозможно. Файл лежал в APK, но больше нигде не использовался.
    val coastlines by produceState(initialValue = emptyList<FloatArray>()) {
        value = withContext(Dispatchers.IO) { runCatching { WorldOutline.load(context) }.getOrDefault(emptyList()) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.studio_grid_title), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.studio_grid_text),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Canvas(Modifier.fillMaxWidth().height(220.dp).pointerInput(Unit) {
                    detectTapGestures { point ->
                        lon = ((point.x / size.width) * 360.0 - 180.0).coerceIn(-180.0, 180.0)
                        lat = (90.0 - (point.y / size.height) * 180.0).coerceIn(-90.0, 90.0)
                    }
                }) {
                    drawRect(color = surfaceTone)
                    for (i in 1..11) {
                        val x = size.width * i / 12f
                        drawLine(outline.copy(alpha = .6f), Offset(x, 0f), Offset(x, size.height), 1f)
                    }
                    for (i in 1..5) {
                        val y = size.height * i / 6f
                        drawLine(outline.copy(alpha = .6f), Offset(0f, y), Offset(size.width, y), 1f)
                    }
                    for (ring in coastlines) {
                        val path = androidx.compose.ui.graphics.Path()
                        var i = 0
                        while (i + 1 < ring.size) {
                            val px = ((ring[i] + 180f) / 360f) * size.width
                            val py = ((90f - ring[i + 1]) / 180f) * size.height
                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                            i += 2
                        }
                        drawPath(path, primary.copy(alpha = .55f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f))
                    }
                    val x = ((lon + 180) / 360 * size.width).toFloat()
                    val y = ((90 - lat) / 180 * size.height).toFloat()
                    drawCircle(signal, radius = 22f, center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
                    drawCircle(signal, radius = 6f, center = Offset(x, y))
                }
                Text(stringResource(R.string.studio_grid_point,
                    String.format(Locale.US, "%.6f", lat), String.format(Locale.US, "%.6f", lon)),
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(lat, lon) }) { Text(stringResource(R.string.studio_grid_use), color = primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurface) } },
    )
}

/** Упрощённые контуры суши: строка файла — кольцо точек «долгота,широта;…». */
private object WorldOutline {
    @Volatile private var cache: List<FloatArray>? = null

    fun load(context: android.content.Context): List<FloatArray> {
        cache?.let { return it }
        val rings = context.assets.open("naturalearth_world_outline.txt").bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val points = line.split(';').mapNotNull { pair ->
                    val comma = pair.indexOf(',')
                    if (comma <= 0) null else {
                        val x = pair.substring(0, comma).trim().toFloatOrNull()
                        val y = pair.substring(comma + 1).trim().toFloatOrNull()
                        if (x == null || y == null) null else floatArrayOf(x, y)
                    }
                }
                if (points.size < 2) null else FloatArray(points.size * 2) { points[it / 2][it % 2] }
            }.toList()
        }
        return rings.also { cache = it }
    }
}
