package com.example.metacheck.ui

import com.example.metacheck.domain.metadata.GpsPrivacy
import com.example.metacheck.domain.model.ImageFormat

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.example.metacheck.R
import com.example.metacheck.ui.text.UiText
import com.example.metacheck.ui.text.asString
import com.example.metacheck.ui.text.rememberMetaTexts
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.metacheck.domain.analytics.HistoryInsights
import com.example.metacheck.domain.export.CleanFormat
import com.example.metacheck.domain.export.ReportText
import com.example.metacheck.domain.model.HistoryEntry
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory),
    uiPreferences: com.example.metacheck.ui.preferences.UiPreferences,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val history by viewModel.historyEntries.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val apiDialog by viewModel.apiDialog.collectAsStateWithLifecycle()
    val proVm: ProViewModel = viewModel(factory = ProViewModel.Factory)
    val studioVm: MetadataStudioViewModel = viewModel(factory = MetadataStudioViewModel.Factory)
    val studioMessage by studioVm.message.collectAsStateWithLifecycle()
    val studioBusy by studioVm.busy.collectAsStateWithLifecycle()
    val shareReady by studioVm.shareReady.collectAsStateWithLifecycle()
    val cleanShareRequest by viewModel.cleanShareRequest.collectAsStateWithLifecycle()
    val entitlement by proVm.entitlement.collectAsStateWithLifecycle()
    val paywallVisible by proVm.paywallVisible.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val texts = rememberMetaTexts()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showStudio by rememberSaveable { mutableStateOf(false) }
    var confirmApi by rememberSaveable { mutableStateOf(false) }

    // Сообщения ViewModel приходят как UiText и превращаются в строку здесь, на языке экрана.
    LaunchedEffect(studioMessage) {
        studioMessage?.let { snackbar.showSnackbar(it.asString(context)); studioVm.clearMessage() }
    }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it.asString(context)); viewModel.consumeMessage() }
    }
    val proMessage by proVm.message.collectAsStateWithLifecycle()
    LaunchedEffect(proMessage) {
        proMessage?.let { snackbar.showSnackbar(it.asString(context)); proVm.consumeMessage() }
    }
    // «Отправить без метаданных» из меню «Поделиться» другого приложения: очищаем сразу.
    LaunchedEffect(cleanShareRequest) {
        cleanShareRequest?.let { studioVm.cleanAndShare(it); viewModel.consumeCleanShare() }
    }
    // Копия очищена и проверена: системное окно «Поделиться» с разовым правом чтения для получателя.
    val chooserTitle = stringResource(R.string.share_chooser_title)
    LaunchedEffect(shareReady) {
        val target = shareReady ?: return@LaunchedEffect
        studioVm.consumeShare()
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = target.mime
            putExtra(android.content.Intent.EXTRA_STREAM, target.uri)
            clipData = android.content.ClipData.newRawUri("", target.uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(android.content.Intent.createChooser(send, chooserTitle)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: ActivityNotFoundException) {
            viewModel.showMessage(UiText.Res(R.string.share_no_app))
        }
    }

    // Google требует сверять покупки при возврате в приложение: отложенные платежи, возвраты,
    // покупки на другом устройстве. Срабатывает и при первом показе экрана.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { proVm.refresh() }

    // OpenDocument preserves more of the source file's original metadata than a transcoding photo picker.
    // rememberSaveable: системный диалог разрешения может пересоздать Activity, выбранное фото не должно теряться.
    var pendingGpsUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val gpsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        pendingGpsUri?.let(viewModel::onImagePicked)
        pendingGpsUri = null
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Доступ должен пережить выгрузку процесса: из результата можно перейти в студию очистки.
            (context.applicationContext as com.example.metacheck.MetaCheckApplication).container.uriAccess.persist(uri)
            if (Build.VERSION.SDK_INT >= 29 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                pendingGpsUri = uri
                gpsPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
            } else viewModel.onImagePicked(uri)
        }
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
            viewModel.showMessage(UiText.Res(R.string.main_msg_no_camera_app))
        } catch (_: Exception) {
            pendingCameraUri = null
            viewModel.showMessage(UiText.Res(R.string.main_msg_camera_failed))
        }
    }
    fun pasteImage() {
        try {
            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            val uri = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).uri else null
            if (uri?.scheme == ContentResolver.SCHEME_CONTENT) viewModel.onImagePicked(uri)
            else viewModel.showMessage(UiText.Res(R.string.main_msg_clipboard_no_image))
        } catch (_: Exception) { viewModel.showMessage(UiText.Res(R.string.main_msg_clipboard_failed)) }
    }

    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { viewModel.exportJson(it, texts) } }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> uri?.let { viewModel.exportPdf(it, texts) } }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(viewModel::exportHistoryCsv) }
    val saveMetadataCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { viewModel.exportMetadataCsv(it, texts) } }
    var pendingStripSource by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingWipeSource by rememberSaveable { mutableStateOf<String?>(null) }
    val saveJpeg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        val original = pendingStripSource
        pendingStripSource = null
        if (uri != null && original != null) studioVm.stripCopy(original, uri) { viewModel.onImagePicked(uri, saveToHistory = false) }
    }
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val original = pendingStripSource
        pendingStripSource = null
        if (uri != null && original != null) studioVm.stripCopy(original, uri) { viewModel.onImagePicked(uri, saveToHistory = false) }
    }
    val saveMp4 = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        val original = pendingStripSource
        pendingStripSource = null
        if (uri != null && original != null) studioVm.stripCopy(original, uri) { viewModel.onImagePicked(uri, saveToHistory = false) }
    }
    val saveMov = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/quicktime")) { uri ->
        val original = pendingStripSource
        pendingStripSource = null
        if (uri != null && original != null) studioVm.stripCopy(original, uri) { viewModel.onImagePicked(uri, saveToHistory = false) }
    }
    val saveGpsJpeg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        val original = pendingWipeSource
        pendingWipeSource = null
        if (uri != null && original != null) studioVm.wipeGpsCopy(original, uri)
    }
    val saveGpsPng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val original = pendingWipeSource
        pendingWipeSource = null
        if (uri != null && original != null) studioVm.wipeGpsCopy(original, uri)
    }
    val inResult = state is UiState.Done
    // «Назад» с экрана ошибки и во время анализа раньше закрывало приложение: возвращаемся на главный.
    val inFlow = inResult || state is UiState.Failed || state is UiState.Loading

    BackHandler(enabled = showStudio || showSettings || inFlow) {
        if (showStudio) showStudio = false
        else if (showSettings) { showSettings = false; viewModel.refreshHistory() }
        else viewModel.close()
    }

    CyberBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val home = !showStudio && !showSettings
                            Text(
                                if (showStudio) stringResource(R.string.studio_name)
                                else if (showSettings) stringResource(R.string.main_title_settings)
                                else stringResource(R.string.app_name).uppercase(),
                                style = if (home) com.example.metacheck.ui.theme.MonoText.file.copy(
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize, fontWeight = FontWeight.SemiBold,
                                    letterSpacing = androidx.compose.ui.unit.TextUnit(0.5f, androidx.compose.ui.unit.TextUnitType.Sp))
                                else MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (!showSettings && !showStudio && !inResult) {
                                // Версия из PackageManager, а не строкой в коде (раньше здесь была устаревшая "2.5.1").
                                val version = remember { runCatching {
                                    @Suppress("DEPRECATION")
                                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                                }.getOrNull().orEmpty() }
                                if (version.isNotEmpty()) CyberEyebrow(version)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = {
                        if (showStudio || showSettings || inFlow) IconButton(onClick = {
                            if (showStudio) showStudio = false
                            else if (showSettings) { showSettings = false; viewModel.refreshHistory() }
                            else viewModel.close()
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                    },
                    actions = {
                        if (!showSettings && !showStudio && !inFlow) {
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, stringResource(R.string.main_title_settings), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface,
                    trackColor = MaterialTheme.colorScheme.outlineVariant)
                // Плавный переход между экранами. Снимок состояния передаётся в каждый экран, поэтому
                // уходящий экран дорисовывает свои данные, даже когда состояние уже сменилось
                // (например, отчёт при возврате на главный).
                val screen = Screen(
                    when {
                        showStudio -> ScreenKind.STUDIO
                        showSettings -> ScreenKind.SETTINGS
                        else -> ScreenKind.of(state)
                    },
                    state,
                )
                // Просьбу об оценке показываем только на главном экране: не поверх окна «Поделиться»
                // сразу после очистки и не посреди работы в студии.
                val reviewActivity = LocalContext.current.findActivity()
                LaunchedEffect(screen.kind) {
                    if (screen.kind == ScreenKind.HOME) reviewActivity?.let(ReviewPrompt::maybeAsk)
                }
                AnimatedContent(
                    targetState = screen,
                    contentKey = { it.kind },
                    transitionSpec = { screenTransition(initialState.kind, targetState.kind) },
                    modifier = Modifier.fillMaxSize(),
                    label = "screens",
                ) { current ->
                if (current.kind == ScreenKind.STUDIO) MetadataStudioScreen(
                    initialUri = (current.ui as? UiState.Done)?.source,
                    isPro = entitlement.isPro,
                    onUpgrade = proVm::openPaywall,
                    onBack = { showStudio = false },
                )
                else if (current.kind == ScreenKind.SETTINGS) SettingsScreen(onMessage = viewModel::showMessage, uiPreferences = uiPreferences)
                else when (val s = current.ui) {
                    UiState.Idle -> Dashboard(
                        history = history, historyEnabled = settings.saveHistory, isPro = entitlement.isPro,
                        onPick = { pick.launch(PICK_TYPES) },
                        onCamera = ::launchCamera, onClipboard = ::pasteImage,
                        onOpen = viewModel::openFromHistory, onDelete = viewModel::deleteFromHistory,
                        onCsv = { saveCsv.launch("metacheck-history.csv") },
                        onUpgrade = proVm::openPaywall, onStudio = { showStudio = true },
                    )
                    UiState.Loading -> LoadingScreen()
                    is UiState.Failed -> ErrorScreen(s.message, { pick.launch(PICK_TYPES) }, viewModel::close)
                    is UiState.Done -> {
                        val name = suggestedName(s.report.file.name)
                        val format = CleanFormat.outputFor(s.report.file.format)
                        val video = s.report.file.format.isVideo
                        // Видео: очистка только в Pro, без пережатия, в исходном контейнере.
                        val cleanVideo: (() -> Unit)? = if (s.source == null || !video) null else {
                            videoClean@{
                                if (!entitlement.isPro) { proVm.openPaywall(); return@videoClean }
                                if (studioBusy) { viewModel.showMessage(UiText.Res(R.string.main_msg_busy)); return@videoClean }
                                pendingStripSource = s.source
                                if (format.extension == "mov") saveMov.launch("$name-clean.mov") else saveMp4.launch("$name-clean.mp4")
                            }
                        }
                        ResultView(
                            report = s.report,
                            sourceUri = s.source,
                            actions = ResultActions(
                                onCsv = { saveMetadataCsv.launch("$name-metadata.csv") },
                                onEdit = if (s.source == null || video) null else { { if (entitlement.isPro) showStudio = true else proVm.openPaywall() } },
                                onWipeGps = if (video) cleanVideo.takeIf { GpsPrivacy.hasLocationMarkers(s.report) } else if (s.source == null || !GpsPrivacy.hasLocationMarkers(s.report) ||
                                    s.report.file.format !in setOf(ImageFormat.JPEG, ImageFormat.PNG)) null else {
                                    wipe@{
                                        if (studioBusy) { viewModel.showMessage(UiText.Res(R.string.main_msg_busy)); return@wipe }
                                        pendingWipeSource = s.source
                                        if (s.report.file.format == ImageFormat.JPEG) saveGpsJpeg.launch("${name}-gps-safe.jpg")
                                        else saveGpsPng.launch("${name}-gps-safe.png")
                                    }
                                },
                                onCopy = {
                                    val shownBySystem = runCatching {
                                        SensitiveClipboard.copy(context, context.getString(R.string.app_name), ReportText.format(s.report, texts))
                                    }
                                    // Android 13+ сам показывает «Скопировано»: второе сообщение было бы дублем.
                                    shownBySystem.onSuccess { if (!it) viewModel.showMessage(UiText.Res(R.string.main_msg_report_copied)) }
                                        .onFailure { viewModel.showMessage(UiText.Res(R.string.main_msg_operation_failed)) }
                                },
                                onJson = { if (entitlement.isPro) saveJson.launch("$name-metacheck.json") else proVm.openPaywall() },
                                onPdf = { if (entitlement.isPro) savePdf.launch("$name-metacheck.pdf") else proVm.openPaywall() },
                                onClean = if (video) cleanVideo else if (s.source == null) null else {
                                    clean@{
                                        if (studioBusy) { viewModel.showMessage(UiText.Res(R.string.main_msg_busy)); return@clean }
                                        pendingStripSource = s.source
                                        if (format.extension == "jpg") saveJpeg.launch("$name-clean.jpg")
                                        else savePng.launch("$name-clean.png")
                                    }
                                },
                                onCleanShare = if (s.source == null) null else {
                                    share@{
                                        if (video && !entitlement.isPro) { proVm.openPaywall(); return@share }
                                        if (studioBusy) { viewModel.showMessage(UiText.Res(R.string.main_msg_busy)); return@share }
                                        studioVm.cleanAndShare(s.source)
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
    }

    val promptActivity = LocalContext.current.findActivity()
    LaunchedEffect(Unit) {
        AppPrompts.paywall.collect { if (!entitlement.isPro) proVm.openPaywallForLimit() }
    }
    LaunchedEffect(Unit) {
        AppPrompts.pro.collect { if (!entitlement.isPro) proVm.openPaywall() }
    }
    LaunchedEffect(promptActivity) {
        AppPrompts.review.collect { promptActivity?.let(ReviewPrompt::recordClean) }
    }
    LaunchedEffect(entitlement.isPro, paywallVisible) {
        if (entitlement.isPro && paywallVisible) proVm.dismissPaywall()
    }
    if (paywallVisible && !entitlement.isPro) PaywallDialog(proVm)
    if (confirmApi) {
        val host = remember(settings.apiEndpoint) {
            runCatching { URI(settings.apiEndpoint.trim()).host }.getOrNull()
        } ?: stringResource(R.string.main_api_unknown_server)
        AlertDialog(
            onDismissRequest = { confirmApi = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(8.dp),
            title = { Text(stringResource(R.string.main_api_confirm_title)) },
            text = {
                Text(stringResource(
                    if (settings.apiSendGps) R.string.main_api_confirm_text_with_gps else R.string.main_api_confirm_text_without_gps,
                    host,
                ))
            },
            confirmButton = { TextButton(onClick = { confirmApi = false; viewModel.sendToApi(texts) }) { Text(stringResource(R.string.main_api_send)) } },
            dismissButton = { TextButton(onClick = { confirmApi = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    apiDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = viewModel::dismissApiDialog,
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(8.dp),
            title = { Text(stringResource(R.string.main_api_dialog_title)) },
            text = {
                when (dialog) {
                    ApiDialog.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(28.dp)); Text(stringResource(R.string.main_api_waiting))
                    }
                    is ApiDialog.Success -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("HTTP ${dialog.statusCode}", color = MaterialTheme.colorScheme.tertiary)
                        SelectionContainer { Text(dialog.body.take(4000).ifEmpty { stringResource(R.string.main_api_empty_response) }) }
                    }
                    is ApiDialog.Error -> Text(dialog.message.asString(), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissApiDialog) { Text(stringResource(R.string.common_close)) } },
        )
    }
}

@Composable
private fun Dashboard(
    history: List<HistoryEntry>, historyEnabled: Boolean, isPro: Boolean,
    onPick: () -> Unit, onCamera: () -> Unit, onClipboard: () -> Unit,
    onOpen: (String) -> Unit, onDelete: (String) -> Unit,
    onCsv: () -> Unit, onUpgrade: () -> Unit, onStudio: () -> Unit,
) {
    val insight = remember(history) { HistoryInsights.from(history) }
    // Те же числа, что в AppContainer: раньше здесь были отдельно записанные 10 и 50.
    // Удаление отчёта необратимо: раньше хватало одного касания по значку корзины.
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(8.dp),
            title = { Text(stringResource(R.string.dash_delete_title)) },
            text = { Text(stringResource(R.string.dash_delete_text)) },
            confirmButton = { TextButton(onClick = { pendingDelete = null; onDelete(id) }) { Text(stringResource(R.string.common_delete)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    val historyLimit = if (isPro) com.example.metacheck.data.history.FileHistoryRepository.STORAGE_LIMIT
        else com.example.metacheck.AppContainer.FREE_HISTORY_LIMIT
    val texts = rememberMetaTexts()
    // Формат даты по языку интерфейса (день/месяц и 12/24 часа в привычном для него порядке).
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val dateFormat = remember(locale) {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "ddMMjjmm"), locale)
    }
    // Порядок: главное действие и объяснение, затем история, студия, статистика и Pro.
    // Раньше новый пользователь видел пять блоков подряд до списка своих проверок.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.dash_title), style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.dash_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            // Пунктирная рамка «бланка»: сюда кладут файл.
            Column(
                Modifier.fillMaxWidth().dashedFrame(MaterialTheme.colorScheme.outline)
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(DASH_FORMATS, style = com.example.metacheck.ui.theme.MonoText.tag,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                NeonButton(stringResource(R.string.dash_choose_photo), onPick, Modifier.fillMaxWidth().height(56.dp),
                    icon = Icons.Filled.Add)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CyberOutlineButton(stringResource(R.string.dash_camera), onCamera, Modifier.weight(1f), icon = Icons.Filled.PhotoCamera)
                    CyberOutlineButton(stringResource(R.string.dash_clipboard), onClipboard, Modifier.weight(1f), icon = Icons.Filled.ContentPaste)
                }
            }
            Text(stringResource(R.string.dash_scan_hint), Modifier.padding(top = 8.dp, start = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.dash_history_title), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface)
                    if (history.isNotEmpty()) TextButton(onClick = onCsv,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                        Text(stringResource(R.string.dash_history_csv))
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
            }
        }
        if (history.isEmpty()) item {
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(stringResource(R.string.dash_history_empty_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(if (historyEnabled) R.string.dash_history_empty else R.string.dash_history_disabled),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(history, key = { it.id }) { entry ->
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().clickable { onOpen(entry.id) }.padding(top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(entry.fileName.ifBlank { stringResource(R.string.common_unnamed) }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = com.example.metacheck.ui.theme.MonoText.file)
                        Text("${dateFormat.format(Date(entry.createdAt))} · ${texts.formatName(entry.format)}" +
                            if (entry.hasAiEvidence) " · " + stringResource(R.string.dash_history_ai_badge) else "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    if (entry.exposureCount > 0) {
                        Stamp(listOf(stringResource(R.string.history_badge_reveals, entry.exposureCount)),
                            MaterialTheme.colorScheme.error, small = true)
                    } else {
                        Stamp(listOf(stringResource(R.string.history_badge_clean)), MaterialTheme.colorScheme.tertiary, small = true)
                    }
                    IconButton(onClick = { pendingDelete = entry.id }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.dash_history_delete, entry.fileName), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item {
            GlassPanel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().clickable(onClick = onStudio).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.dash_eyebrow_studio), style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.dash_studio_features),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.dash_open_studio),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (history.isNotEmpty()) item {
            GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.tertiary) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.dash_section_analytics), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Column(Modifier.weight(1f)) {
                            CyberEyebrow(stringResource(R.string.dash_stat_total))
                            Text("${insight.total}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(Modifier.weight(1f)) {
                            CyberEyebrow(stringResource(R.string.dash_stat_ai), color = MaterialTheme.colorScheme.secondary)
                            Text("${insight.aiEvidenceCount}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(Modifier.weight(1f)) {
                            CyberEyebrow(stringResource(R.string.dash_stat_gps), color = MaterialTheme.colorScheme.tertiary)
                            Text("${insight.gpsCount}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    CyberEyebrow(stringResource(R.string.dash_stat_activity))
                    ActivityChart(insight.lastSevenDays, Modifier.fillMaxWidth().height(64.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.dash_history_limit), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                        Text("${history.size} / $historyLimit", color = MaterialTheme.colorScheme.onSurface, style = com.example.metacheck.ui.theme.MonoText.tag)
                    }
                    LinearProgressIndicator(
                        progress = { (history.size.toFloat() / historyLimit).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurface,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(stringResource(if (isPro) R.string.dash_history_limit_pro else R.string.dash_history_limit_free),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.dash_stat_note),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.tertiary) {
                Row(Modifier.fillMaxWidth().then(if (isPro) Modifier else Modifier.clickable(onClick = onUpgrade)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(if (isPro) R.string.dash_pro_active else R.string.dash_pro_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(if (isPro) R.string.dash_pro_active_text else R.string.dash_pro_text),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!isPro) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.dash_upgrade_limit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Text(stringResource(R.string.dash_privacy_footer),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ActivityChart(bins: List<Int>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Canvas(modifier) {
        val maxCount = max(1, bins.maxOrNull() ?: 1)
        val widthPerBar = size.width / 7f
        bins.forEachIndexed { index, count ->
            val height = if (count == 0) 2.dp.toPx() else size.height * (.12f + .82f * count / maxCount)
            drawRect(
                color = primary,
                topLeft = Offset(index * widthPerBar + widthPerBar * .3f, size.height - height),
                size = Size(widthPerBar * .4f, height),
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
                CyberEyebrow(stringResource(R.string.loading_eyebrow))
                Text(stringResource(R.string.loading_title), style = MaterialTheme.typography.titleLarge)
                ScanningBars(Modifier.fillMaxWidth().height(65.dp))
                Text(stringResource(R.string.loading_text),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ErrorScreen(message: UiText, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.error) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CyberEyebrow(stringResource(R.string.error_eyebrow), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.error_title), style = MaterialTheme.typography.titleLarge)
                Text(message.asString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                NeonButton(stringResource(R.string.error_choose_other), onRetry, Modifier.fillMaxWidth())
                CyberOutlineButton(stringResource(R.string.error_home), onBack, Modifier.fillMaxWidth())
            }
        }
    }
}

private fun suggestedName(fileName: String): String =
    fileName.substringBeforeLast('.').filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == ' ' }
        .trim().take(60).ifBlank { "image" } // иначе файл назывался бы «-clean.jpg»
        .trim().ifEmpty { "image" }.take(60)

/** Экран верхнего уровня. [depth] задаёт направление перехода: вглубь — справа, назад — слева. */
private enum class ScreenKind(val depth: Int) {
    HOME(0), LOADING(1), FAILED(1), SETTINGS(1), REPORT(2), STUDIO(3);

    companion object {
        fun of(state: UiState): ScreenKind = when (state) {
            UiState.Idle -> HOME
            UiState.Loading -> LOADING
            is UiState.Failed -> FAILED
            is UiState.Done -> REPORT
        }
    }
}

private data class Screen(val kind: ScreenKind, val ui: UiState)

/**
 * Вглубь — новый экран выезжает справа, назад — слева; плюс растворение.
 * Всё, что связано с загрузкой, только растворяется: это смена содержимого, а не переход
 * (иначе повторный анализ очищенной копии «уезжал» бы назад и снова вперёд).
 */
private fun screenTransition(from: ScreenKind, to: ScreenKind): ContentTransform {
    val easing = FastOutSlowInEasing
    if (from == ScreenKind.LOADING || to == ScreenKind.LOADING) {
        return fadeIn(tween(260, easing = easing)) + scaleIn(tween(260, easing = easing), initialScale = .98f) togetherWith
            fadeOut(tween(160))
    }
    val direction = if (to.depth >= from.depth) 1 else -1
    return (slideInHorizontally(tween(320, easing = easing)) { width -> direction * width / 5 } +
        fadeIn(tween(260, delayMillis = 40, easing = easing))) togetherWith
        (slideOutHorizontally(tween(260, easing = easing)) { width -> -direction * width / 8 } +
            fadeOut(tween(180, easing = easing)))
}

/** Что можно выбрать для проверки: фото и видео (MP4/MOV; прочие видео отклонит проверка сигнатуры). */
private val PICK_TYPES = arrayOf("image/*", "video/*")

/** Строка форматов в рамке главного экрана: названия форматов не переводятся. */
private const val DASH_FORMATS = "JPEG   PNG   HEIC   WEBP   MP4   MOV"
