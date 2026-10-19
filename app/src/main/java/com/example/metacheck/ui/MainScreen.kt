package com.example.metacheck.ui

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.metacheck.domain.analytics.HistoryInsights
import com.example.metacheck.domain.export.CleanFormat
import com.example.metacheck.domain.export.ReportText
import com.example.metacheck.domain.model.HistoryEntry
import com.example.metacheck.ui.theme.CyberPalette
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

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
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var confirmApi by rememberSaveable { mutableStateOf(false) }
    val onboardingPrefs = remember(context) { context.getSharedPreferences("intro", Context.MODE_PRIVATE) }
    var showOnboarding by rememberSaveable { mutableStateOf(!onboardingPrefs.getBoolean("completed_v2", false)) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    // OpenDocument preserves more of the source file's original metadata than a transcoding photo picker.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onImagePicked(uri)
    }
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) viewModel.onImagePicked(uri)
    }
    fun launchCamera() {
        try {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCameraUri = uri
            takePicture.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pendingCameraUri = null
            viewModel.showMessage("На устройстве нет приложения камеры")
        } catch (_: Exception) {
            pendingCameraUri = null
            viewModel.showMessage("Не удалось запустить камеру")
        }
    }
    fun pasteImage() {
        try {
            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            val uri = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).uri else null
            if (uri?.scheme == ContentResolver.SCHEME_CONTENT) viewModel.onImagePicked(uri)
            else viewModel.showMessage("В буфере нет ссылки на изображение")
        } catch (_: Exception) { viewModel.showMessage("Не удалось прочитать буфер") }
    }

    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(viewModel::exportJson) }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { it?.let(viewModel::exportPdf) }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(viewModel::exportHistoryCsv) }
    val saveJpeg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { it?.let(viewModel::saveCleaned) }
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { it?.let(viewModel::saveCleaned) }
    val inResult = state is UiState.Done

    BackHandler(enabled = showSettings || inResult) {
        if (showSettings) { showSettings = false; viewModel.refreshHistory() }
        else viewModel.close()
    }

    CyberBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("◈", color = CyberPalette.Cyan, style = MaterialTheme.typography.titleLarge)
                            Text(if (showSettings) "Настройки" else "MetaCheck", style = MaterialTheme.typography.titleLarge)
                            if (!showSettings && !inResult) CyberEyebrow("2.1")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberPalette.Black.copy(alpha = .7f)),
                    navigationIcon = {
                        if (showSettings || inResult) IconButton(onClick = {
                            if (showSettings) { showSettings = false; viewModel.refreshHistory() }
                            else viewModel.close()
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    },
                    actions = {
                        if (!showSettings && !inResult) {
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, "Настройки", tint = CyberPalette.Cyan)
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (showSettings) SettingsScreen(onMessage = viewModel::showMessage)
                else when (val s = state) {
                    UiState.Idle -> Dashboard(
                        history = history, historyEnabled = settings.saveHistory, isPro = entitlement.isPro,
                        onPick = { pick.launch(arrayOf("image/*")) },
                        onCamera = ::launchCamera, onClipboard = ::pasteImage,
                        onOpen = viewModel::openFromHistory, onDelete = viewModel::deleteFromHistory,
                        onCsv = { saveCsv.launch("metacheck-history.csv") },
                        onUpgrade = proVm::openPaywall,
                    )
                    UiState.Loading -> LoadingScreen()
                    is UiState.Failed -> ErrorScreen(s.message, { pick.launch(arrayOf("image/*")) }, viewModel::close)
                    is UiState.Done -> {
                        val name = suggestedName(s.report.file.name)
                        val format = CleanFormat.outputFor(s.report.file.format)
                        ResultView(
                            report = s.report,
                            sourceUri = s.source,
                            actions = ResultActions(
                                onCopy = {
                                    clipboard.setText(AnnotatedString(ReportText.format(s.report)))
                                    viewModel.showMessage("Отчёт скопирован")
                                },
                                onJson = { if (entitlement.isPro) saveJson.launch("$name-metacheck.json") else proVm.openPaywall() },
                                onPdf = { if (entitlement.isPro) savePdf.launch("$name-metacheck.pdf") else proVm.openPaywall() },
                                onClean = if (s.source == null) null else {
                                    {
                                        if (format.extension == "jpg") saveJpeg.launch("$name-clean.jpg")
                                        else savePng.launch("$name-clean.png")
                                    }
                                },
                                onApi = if (!settings.apiEnabled) null else {
                                    { if (entitlement.isPro) confirmApi = true else proVm.openPaywall() }
                                },
                            ),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(entitlement.isPro, paywallVisible) {
        if (entitlement.isPro && paywallVisible) proVm.dismissPaywall()
    }
    if (paywallVisible && !entitlement.isPro) PaywallDialog(proVm)
    if (showOnboarding) OnboardingDialog(onFinish = {
        showOnboarding = false
        onboardingPrefs.edit().putBoolean("completed_v2", true).apply()
    })
    if (confirmApi) {
        val host = remember(settings.apiEndpoint) {
            runCatching { URI(settings.apiEndpoint.trim()).host }.getOrNull() ?: "указанный сервер"
        }
        AlertDialog(
            onDismissRequest = { confirmApi = false },
            title = { Text("Подтвердить отправку") },
            text = { Text("JSON-отчёт будет отправлен на $host. Исходная фотография не отправляется. GPS: " +
                if (settings.apiSendGps) "включён в отчёт." else "исключён из отчёта.") },
            confirmButton = { TextButton(onClick = { confirmApi = false; viewModel.sendToApi() }) { Text("Отправить") } },
            dismissButton = { TextButton(onClick = { confirmApi = false }) { Text("Отмена") } },
        )
    }
    apiDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = viewModel::dismissApiDialog,
            title = { Text("Внешний API") },
            text = {
                when (dialog) {
                    ApiDialog.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(28.dp)); Text("Ожидание ответа сервера…")
                    }
                    is ApiDialog.Success -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("HTTP ${dialog.statusCode}", color = CyberPalette.Green)
                        SelectionContainer { Text(dialog.body.take(4000).ifEmpty { "Ответ пуст" }) }
                    }
                    is ApiDialog.Error -> Text(dialog.message, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissApiDialog) { Text("Закрыть") } },
        )
    }
}

@Composable
private fun Dashboard(
    history: List<HistoryEntry>, historyEnabled: Boolean, isPro: Boolean,
    onPick: () -> Unit, onCamera: () -> Unit, onClipboard: () -> Unit,
    onOpen: (String) -> Unit, onDelete: (String) -> Unit,
    onCsv: () -> Unit, onUpgrade: () -> Unit,
) {
    val insight = remember(history) { HistoryInsights.from(history) }
    val dateFormat = remember { SimpleDateFormat("dd.MM · HH:mm", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    PulseIndicator(); CyberEyebrow("SECURE IMAGE INTELLIGENCE")
                }
                Text("Цифровая\nэкспертиза фото", style = MaterialTheme.typography.headlineLarge)
                Text("Проверьте происхождение файла, метаданные камеры и технические признаки ИИ.",
                    color = CyberPalette.Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            GlassPanel(Modifier.fillMaxWidth(), CyberPalette.Cyan) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        CyberEyebrow("NEW SCAN / 001")
                        Text("⌁", color = CyberPalette.Purple, style = MaterialTheme.typography.headlineMedium)
                    }
                    Text("Исследовать изображение", style = MaterialTheme.typography.titleLarge)
                    Text("Выбирайте исходный JPEG, PNG или HEIC: в скриншоте часть данных уже потеряна.",
                        color = CyberPalette.Muted, style = MaterialTheme.typography.bodyMedium)
                    ScanningBars(Modifier.fillMaxWidth().height(52.dp))
                    NeonButton("＋  Выбрать фотографию", onPick, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        CyberOutlineButton("◎  Камера", onCamera, Modifier.weight(1f))
                        CyberOutlineButton("▣  Буфер", onClipboard, Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricPanel("EXIF", "КАМЕРА И GPS", Modifier.weight(1f))
                MetricPanel("XMP", "МЕТАДАННЫЕ", Modifier.weight(1f), CyberPalette.Purple)
                MetricPanel("AI", "МЕТКИ ГЕНЕРАЦИИ", Modifier.weight(1f), CyberPalette.Green)
            }
        }
        item { SectionLabel("ВАША АНАЛИТИКА") }
        item {
            GlassPanel(Modifier.fillMaxWidth(), CyberPalette.Purple) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Column(Modifier.weight(1f)) {
                            CyberEyebrow("ВСЕГО ОТЧЁТОВ")
                            Text("${insight.total}", style = MaterialTheme.typography.headlineMedium)
                        }
                        Column(Modifier.weight(1f)) {
                            CyberEyebrow("С ПРИЗНАКАМИ ИИ", color = CyberPalette.Purple)
                            Text("${insight.aiEvidenceCount}", style = MaterialTheme.typography.headlineMedium)
                        }
                        Column(Modifier.weight(1f)) {
                            CyberEyebrow("С GPS", color = CyberPalette.Green)
                            Text("${insight.gpsCount}", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    CyberEyebrow("АКТИВНОСТЬ ЗА 7 ДНЕЙ")
                    ActivityChart(insight.lastSevenDays, Modifier.fillMaxWidth().height(78.dp))
                    Text("Статистика только по сохранённой истории, без облака и оценок подлинности.",
                        color = CyberPalette.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                CyberEyebrow("ИСТОРИЯ ПРОВЕРОК")
                Text("CSV ↗", modifier = Modifier.clickable { onCsv() }.padding(8.dp), color = CyberPalette.Cyan)
            }
        }
        if (history.isEmpty()) item {
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Здесь появятся отчёты", style = MaterialTheme.typography.titleMedium)
                    Text(if (historyEnabled) "История пока пуста. Начните первую проверку." else "Сохранение истории отключено в настройках.",
                        color = CyberPalette.Muted)
                }
            }
        }
        items(history, key = { it.id }) { entry ->
            GlassPanel(Modifier.fillMaxWidth(), if (entry.hasAiEvidence) CyberPalette.Danger else CyberPalette.Cyan) {
                Row(Modifier.fillMaxWidth().clickable { onOpen(entry.id) }.padding(start = 15.dp, top = 10.dp, bottom = 10.dp, end = 5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium)
                        Text("${dateFormat.format(Date(entry.createdAt))}  •  ${entry.format.label}" +
                            if (entry.hasAiEvidence) "  •  AI TRACE" else "",
                            color = CyberPalette.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onDelete(entry.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить ${entry.fileName}", tint = CyberPalette.Muted)
                    }
                }
            }
        }
        item {
            GlassPanel(Modifier.fillMaxWidth(), CyberPalette.Green) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("◈", style = MaterialTheme.typography.headlineMedium, color = CyberPalette.Green)
                    Column(Modifier.weight(1f)) {
                        Text(if (isPro) "PRO ACTIVE" else "METACHECK PRO", style = MaterialTheme.typography.titleMedium)
                        Text(if (isPro) "Экспорт PDF/JSON и расширенная история доступны" else "PDF, JSON, 50 отчётов и собственный API",
                            color = CyberPalette.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!isPro) Text("↗", modifier = Modifier.clickable { onUpgrade() }.padding(8.dp),
                        color = CyberPalette.Cyan, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        item { Text("Приватность по умолчанию: изображения не отправляются MetaCheck в сеть. Для API требуется отдельное согласие.",
            color = CyberPalette.Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ActivityChart(bins: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val maxCount = max(1, bins.maxOrNull() ?: 1)
        val widthPerBar = size.width / 7f
        bins.forEachIndexed { index, count ->
            val height = if (count == 0) 2.dp.toPx() else size.height * (.12f + .82f * count / maxCount)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(CyberPalette.Cyan, CyberPalette.Purple.copy(alpha = .38f))),
                topLeft = Offset(index * widthPerBar + widthPerBar * .24f, size.height - height),
                size = Size(widthPerBar * .52f, height),
                cornerRadius = CornerRadius(7f, 7f),
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.fillMaxWidth().padding(22.dp)) {
            Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(17.dp)) {
                CircularProgressIndicator(color = CyberPalette.Cyan)
                CyberEyebrow("ANALYZING / ON DEVICE")
                Text("Сканирование метаданных", style = MaterialTheme.typography.titleLarge)
                ScanningBars(Modifier.fillMaxWidth().height(65.dp))
                Text("Проверяем структуру файла, EXIF, XMP и признаки генерации.",
                    color = CyberPalette.Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.fillMaxWidth(), CyberPalette.Danger) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CyberEyebrow("SCAN ERROR", color = CyberPalette.Danger)
                Text("Не удалось проверить файл", style = MaterialTheme.typography.titleLarge)
                Text(message, color = CyberPalette.Muted)
                NeonButton("Выбрать другой файл", onRetry, Modifier.fillMaxWidth())
                CyberOutlineButton("На главную", onBack, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun OnboardingDialog(onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val pages = listOf(
        Triple("Приватный анализ", "Метаданные анализируются на устройстве. Исходные фотографии не сохраняются в истории.", "01 / PRIVACY"),
        Triple("Что мы проверяем", "EXIF, XMP, программные маркеры, техническую структуру и доступные сигналы генерации ИИ.", "02 / FORENSICS"),
        Triple("Как читать результат", "Отсутствие меток ИИ не является доказательством подлинности. Анализ содержимого пикселей не производится.", "03 / LIMITATIONS"),
    )
    AlertDialog(
        onDismissRequest = onFinish,
        title = { Text(pages[step].first) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                CyberEyebrow(pages[step].third)
                Text(pages[step].second)
                ScanningBars(Modifier.fillMaxWidth().height(45.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { if (step == pages.lastIndex) onFinish() else step++ }) {
                Text(if (step == pages.lastIndex) "Начать" else "Далее")
            }
        },
        dismissButton = { TextButton(onClick = onFinish) { Text("Пропустить") } },
    )
}

private fun suggestedName(fileName: String): String =
    fileName.substringBeforeLast('.').filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == ' ' }
        .trim().ifEmpty { "image" }.take(60)
