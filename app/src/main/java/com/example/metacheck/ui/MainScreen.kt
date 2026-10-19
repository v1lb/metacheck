package com.example.metacheck.ui

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.metacheck.domain.export.CleanFormat
import com.example.metacheck.domain.export.ReportText
import com.example.metacheck.domain.model.HistoryEntry
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val history by viewModel.historyEntries.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val apiDialog by viewModel.apiDialog.collectAsStateWithLifecycle()
    val proVm: ProViewModel = viewModel(factory = ProViewModel.Factory)
    val entitlement by proVm.entitlement.collectAsStateWithLifecycle()
    val paywallVisible by proVm.paywallVisible.collectAsStateWithLifecycle()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var confirmApi by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // OpenDocument, а не фотопикер/MediaStore: для проверки метаданных нужен оригинальный файл,
    // а MediaStore-URI на Android 10+ могут скрывать GPS.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onImagePicked(uri)
    }
    val context = LocalContext.current
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (ok && uri != null) viewModel.onImagePicked(uri)
    }

    // Снимок пишется в cache/camera через FileProvider. Разрешение CAMERA не нужно: снимает системная камера.
    fun launchCamera() {
        try {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCameraUri = uri
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            pendingCameraUri = null
            viewModel.showMessage("Не найдено приложение камеры")
        } catch (e: Exception) {
            pendingCameraUri = null
            viewModel.showMessage("Не удалось открыть камеру")
        }
    }

    fun pasteFromClipboard() {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboardManager.primaryClip
        val uri = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).uri else null
        if (uri == null || uri.scheme != ContentResolver.SCHEME_CONTENT) {
            viewModel.showMessage("В буфере нет изображения")
        } else {
            viewModel.onImagePicked(uri)
        }
    }

    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportJson(uri)
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) viewModel.exportPdf(uri)
    }
    val saveJpeg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        if (uri != null) viewModel.saveCleaned(uri)
    }
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) viewModel.saveCleaned(uri)
    }

    val inResult = state is UiState.Done
    BackHandler(enabled = showSettings || inResult) {
        if (showSettings) {
            showSettings = false
            viewModel.refreshHistory()
        } else {
            viewModel.close()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (showSettings) "Настройки" else "MetaCheck") },
                navigationIcon = {
                    if (showSettings || inResult) {
                        IconButton(onClick = {
                            if (showSettings) {
                                showSettings = false
                                viewModel.refreshHistory()
                            } else {
                                viewModel.close()
                            }
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                    }
                },
                actions = {
                    if (!showSettings && !inResult) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (showSettings) {
                SettingsScreen(onMessage = viewModel::showMessage)
            } else {
                if (!inResult) {
                    Button(
                        onClick = { pick.launch(arrayOf("image/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    ) { Text("Выбрать из галереи") }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { launchCamera() }, modifier = Modifier.weight(1f)) {
                            Text("Сделать фото")
                        }
                        OutlinedButton(onClick = { pasteFromClipboard() }, modifier = Modifier.weight(1f)) {
                            Text("Из буфера")
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

                when (val s = state) {
                    UiState.Idle -> HistoryList(
                        entries = history,
                        historyEnabled = settings.saveHistory,
                        onOpen = viewModel::openFromHistory,
                        onDelete = viewModel::deleteFromHistory,
                    )
                    UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    is UiState.Failed -> Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    is UiState.Done -> {
                        val report = s.report
                        val baseName = suggestedName(report.file.name)
                        val cleanAction: (() -> Unit)? = if (s.source != null) {
                            {
                                if (CleanFormat.outputFor(report.file.format).extension == "jpg") {
                                    saveJpeg.launch("$baseName-clean.jpg")
                                } else {
                                    savePng.launch("$baseName-clean.png")
                                }
                            }
                        } else {
                            null
                        }
                        val apiAction: (() -> Unit)? = if (settings.apiEnabled) {
                            { if (entitlement.isPro) confirmApi = true else proVm.openPaywall() }
                        } else {
                            null
                        }
                        ResultView(
                            report = report,
                            actions = ResultActions(
                                onCopy = {
                                    clipboard.setText(AnnotatedString(ReportText.format(report)))
                                    viewModel.showMessage("Метаданные скопированы")
                                },
                                onJson = {
                                    if (entitlement.isPro) saveJson.launch("$baseName-metacheck.json") else proVm.openPaywall()
                                },
                                onPdf = {
                                    if (entitlement.isPro) savePdf.launch("$baseName-metacheck.pdf") else proVm.openPaywall()
                                },
                                onClean = cleanAction,
                                onApi = apiAction,
                            ),
                        )
                    }
                }
            }
        }
    }

    // Купили или ввели ключ: окно покупки закрывается само.
    LaunchedEffect(entitlement.isPro, paywallVisible) {
        if (entitlement.isPro && paywallVisible) proVm.dismissPaywall()
    }
    if (paywallVisible && !entitlement.isPro) PaywallDialog(proVm)

    if (confirmApi) {
        val host = remember(settings.apiEndpoint) {
            runCatching { URI(settings.apiEndpoint.trim()).host }.getOrNull() ?: settings.apiEndpoint
        }
        AlertDialog(
            onDismissRequest = { confirmApi = false },
            title = { Text("Отправить отчёт?") },
            text = {
                Text(
                    "JSON-отчёт будет отправлен на $host. Само изображение не отправляется. " +
                        "Координаты GPS: " + if (settings.apiSendGps) "будут отправлены." else "не отправляются.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmApi = false
                    viewModel.sendToApi()
                }) { Text("Отправить") }
            },
            dismissButton = { TextButton(onClick = { confirmApi = false }) { Text("Отмена") } },
        )
    }

    apiDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = viewModel::dismissApiDialog,
            title = { Text("Ответ API") },
            text = {
                when (dialog) {
                    ApiDialog.Loading -> Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Text("Отправляю…")
                    }
                    is ApiDialog.Success -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("HTTP ${dialog.statusCode}", style = MaterialTheme.typography.titleSmall)
                        SelectionContainer {
                            Text(dialog.body.take(4000).ifEmpty { "(пустой ответ)" })
                        }
                    }
                    is ApiDialog.Error -> Text(dialog.message)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissApiDialog) { Text("Закрыть") } },
        )
    }
}

@Composable
private fun HistoryList(
    entries: List<HistoryEntry>,
    historyEnabled: Boolean,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Выбери изображение, и я покажу метаданные и следы ИИ. Файл остаётся на телефоне. " +
                    "В сеть уходит только JSON-отчёт, и только если ты сам включил API в настройках " +
                    "и нажал «Проверить через API».",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item { Text("Недавние отчёты", style = MaterialTheme.typography.titleMedium) }
        if (entries.isEmpty()) {
            item {
                Text(
                    if (historyEnabled) "Пока пусто." else "История выключена в настройках.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(entries, key = { it.id }) { entry ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(entry.id) }) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val details = buildString {
                            append(dateFormat.format(Date(entry.createdAt)))
                            append(" · ").append(entry.format.label)
                            if (entry.hasAiEvidence) append(" · следы ИИ")
                            if (entry.hasGps) append(" · GPS")
                        }
                        Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onDelete(entry.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить отчёт")
                    }
                }
            }
        }
    }
}

private fun suggestedName(fileName: String): String =
    fileName.substringBeforeLast('.')
        .filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == ' ' }
        .trim()
        .ifEmpty { "image" }
        .take(60)
