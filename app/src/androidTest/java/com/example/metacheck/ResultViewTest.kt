package com.example.metacheck

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.metacheck.domain.model.FileInfo
import com.example.metacheck.domain.model.GpsPoint
import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.MetaSection
import com.example.metacheck.domain.model.Severity
import com.example.metacheck.domain.model.Signal
import com.example.metacheck.ui.ResultActions
import com.example.metacheck.ui.ResultView
import com.example.metacheck.ui.theme.MetaCheckTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultViewTest {

    @get:Rule
    val compose = createComposeRule()

    private fun report(ai: Boolean, gps: Boolean) = MetaReport(
        file = FileInfo("photo.jpg", "image/jpeg", 1000L, ImageFormat.JPEG, 1000, false),
        exifSections = listOf(MetaSection("Камера и объектив", listOf(MetaItem("Модель", "Pixel 8")))),
        xmpItems = emptyList(),
        pngTextItems = emptyList(),
        gps = if (gps) GpsPoint(55.75, 37.62, null) else null,
        signals = listOf(
            if (ai) Signal(Severity.AI_EVIDENCE, "Параметры Stable Diffusion", "детали")
            else Signal(Severity.INFO, "Явных следов ИИ в метаданных нет", "детали"),
        ),
    )

    private val noActions = ResultActions({}, {}, {}, null, null)

    private fun show(r: MetaReport, actions: ResultActions = noActions) {
        compose.setContent { MetaCheckTheme { ResultView(r, actions) } }
    }

    @Test
    fun showsAiVerdictWhenEvidenceFound() {
        show(report(ai = true, gps = false))
        compose.onNodeWithTag("overview_list").performScrollToIndex(1)
        compose.onNodeWithText("Обнаружены признаки ИИ").assertExists()
    }

    @Test
    fun showsNeutralVerdictWithoutEvidence() {
        show(report(ai = false, gps = false))
        compose.onNodeWithTag("overview_list").performScrollToIndex(1)
        compose.onNodeWithText("Явных следов ИИ не найдено").assertExists()
    }

    @Test
    fun searchableMetadataTabShowsCameraData() {
        show(report(ai = false, gps = false))
        compose.onNodeWithText("Метаданные").performClick()
        compose.onNodeWithTag("metadata_search").performTextInput("Модель")
        compose.onNodeWithText("Pixel 8").assertIsDisplayed()
    }

    @Test
    fun mapTabAppearsOnlyWithGps() {
        show(report(ai = false, gps = true))
        compose.onNodeWithText("Карта").assertIsDisplayed()
    }

    @Test
    fun gpsAlertIsVisibleWhenExifContainsCoordinates() {
        show(report(ai = false, gps = true))
        compose.onNodeWithTag("overview_list").performScrollToIndex(1)
        compose.onNodeWithText("Место съёмки сохранено в фото").assertExists()
        compose.onNodeWithText(
            "Это не означает, что за вами следят. Риск возникает, если передать файл с геометками.",
        ).assertExists()
    }

    @Test
    fun gpsCardPromotesSafeCopyOnlyWhenSourceActionExists() {
        var pressed = false
        val wipeActions = ResultActions({}, {}, {}, null, null, onWipeGps = { pressed = true })
        show(report(ai = false, gps = true), wipeActions)
        compose.onNodeWithTag("overview_list").performScrollToIndex(1)
        compose.onNodeWithText("Защитить фото · удалить GPS").assertExists()
        compose.onNodeWithTag("gps_wipe_action").performScrollTo().performClick()
        assertTrue(pressed)
    }

    @Test
    fun noPrivacyAlertWithoutGpsTags() {
        show(report(ai = false, gps = false))
        compose.onNodeWithTag("gps_privacy_shield").assertDoesNotExist()
    }

    @Test
    fun cleanAndApiButtonsAreHiddenWhenUnavailable() {
        show(report(ai = false, gps = false))
        compose.onNodeWithTag("overview_list").performScrollToIndex(5)
        compose.onNodeWithText("Копировать").assertExists()
        compose.onNodeWithText("Удалить все метаданные").assertDoesNotExist()
    }

    @Test
    fun cleanButtonShownWhenProvided() {
        show(report(ai = false, gps = false), ResultActions({}, {}, {}, {}, {}))
        compose.onNodeWithTag("overview_list").performScrollToIndex(5)
        compose.onNodeWithText("Удалить все метаданные").assertExists()
        compose.onNodeWithText("Проверить через API").assertExists()
    }
}
