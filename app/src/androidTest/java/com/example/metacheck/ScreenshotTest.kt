package com.example.metacheck

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.metacheck.domain.model.FileInfo
import com.example.metacheck.domain.model.GpsPoint
import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.MetaSection
import com.example.metacheck.domain.model.SectionKey
import com.example.metacheck.domain.model.Severity
import com.example.metacheck.domain.model.Signal
import com.example.metacheck.domain.model.SignalKind
import com.example.metacheck.ui.ResultActions
import com.example.metacheck.ui.ResultView
import com.example.metacheck.ui.theme.MetaCheckTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Снимки экранов отчёта в светлой и тёмной теме для проверки оформления. Файлы кладутся во внешнюю
 * папку приложения (files/screens), CI забирает их через adb. Ничего не проверяет, только рисует.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val report = MetaReport(
        file = FileInfo("IMG_4821.jpg", "image/jpeg", 255_855L, ImageFormat.JPEG, 255_855, false),
        exifSections = listOf(
            MetaSection("", listOf(
                MetaItem("Make", "Apple", key = "exif:Make"),
                MetaItem("Model", "iPhone 15 Pro", key = "exif:Model"),
                MetaItem("BodySerialNumber", "F2LXK1Q8", key = "exif:BodySerialNumber"),
            ), key = SectionKey.CAMERA),
            MetaSection("", listOf(
                MetaItem("DateTimeOriginal", "2024:06:15 19:42:10", key = "exif:DateTimeOriginal"),
            ), key = SectionKey.DATES),
            MetaSection("", listOf(
                MetaItem("Artist", "Alex Martin", key = "exif:Artist"),
                MetaItem("Copyright", "(c) Alex Martin", key = "exif:Copyright"),
            ), key = SectionKey.AUTHORSHIP),
        ),
        xmpItems = emptyList(),
        pngTextItems = emptyList(),
        gps = GpsPoint(48.8584, 2.2945, 35.0),
        signals = listOf(Signal(Severity.INFO, kind = SignalKind.NO_AI_TRACES)),
    )

    private val actions = ResultActions({}, {}, {}, onClean = {}, onApi = null, onCleanShare = {}, onWipeGps = {})

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screens")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shoot(dark: Boolean) {
        val suffix = if (dark) "dark" else "light"
        compose.setContent {
            MetaCheckTheme(darkTheme = dark) {
                Box(Modifier.size(412.dp, 900.dp).background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
                    ResultView(report, actions)
                }
            }
        }
        compose.waitForIdle()
        save("report_$suffix")
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(target.getString(R.string.result_tab_metadata)).performClick()
        compose.waitForIdle()
        save("metadata_$suffix")
    }

    private fun dashboard(dark: Boolean) {
        val now = System.currentTimeMillis()
        val history = listOf(
            com.example.metacheck.domain.model.HistoryEntry("1", now - 3_600_000, "IMG_4821.jpg", ImageFormat.JPEG, false, true, 4),
            com.example.metacheck.domain.model.HistoryEntry("2", now - 90_000_000, "VID_20261102.mp4", ImageFormat.MP4, false, true, 3),
            com.example.metacheck.domain.model.HistoryEntry("3", now - 3_500_000, "IMG_4821-clean.jpg", ImageFormat.JPEG, false, false, 0),
        )
        compose.setContent {
            MetaCheckTheme(darkTheme = dark) {
                androidx.compose.foundation.layout.Box(Modifier.size(412.dp, 900.dp)
                    .then(Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.background))) {
                    com.example.metacheck.ui.Dashboard(history, historyEnabled = true, isPro = false,
                        onPick = {}, onCamera = {}, onClipboard = {}, onOpen = {}, onDelete = {},
                        onCsv = {}, onUpgrade = {}, onStudio = {})
                }
            }
        }
        compose.waitForIdle()
        save("home_" + if (dark) "dark" else "light")
    }

    private fun studioBatch(dark: Boolean) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val c = (target.applicationContext as MetaCheckApplication).container
        val vm = com.example.metacheck.ui.MetadataStudioViewModel(c.metadataStudio, folderAudit = c.folderAudit)
        compose.setContent {
            MetaCheckTheme(darkTheme = dark) {
                Box(Modifier.size(412.dp, 1400.dp).background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
                    com.example.metacheck.ui.MetadataStudioScreen(initialUri = null, isPro = false, onUpgrade = {}, onBack = {},
                        startInBatch = true, vm = vm)
                }
            }
        }
        compose.waitForIdle()
        save("studio_batch_" + if (dark) "dark" else "light")
    }

    @Test fun studioBatchLight() = studioBatch(dark = false)

    @Test fun studioBatchDark() = studioBatch(dark = true)

    @Test fun homeLight() = dashboard(dark = false)

    @Test fun homeDark() = dashboard(dark = true)

    @Test fun reportLight() = shoot(dark = false)

    @Test fun reportDark() = shoot(dark = true)
}
