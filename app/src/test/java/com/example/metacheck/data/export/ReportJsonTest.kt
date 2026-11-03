package com.example.metacheck.data.export

import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.MetaSection
import com.example.metacheck.domain.model.SectionKey
import com.example.metacheck.domain.model.ValueFormat
import com.example.metacheck.sampleReport
import com.example.metacheck.testutil.FakeTexts
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportJsonTest {

    @Test
    fun roundTripKeepsEverything() {
        val report = sampleReport(ai = true)
        val restored = ReportJson.fromJson(ReportJson.toJson(report).toString())
        assertEquals(report, restored)
    }

    @Test
    fun quotesNewlinesAndUnicodeSurvive() {
        val report = sampleReport(prompt = "line1\nline2 \"quoted\" \\ слэш \u2603")
        val restored = ReportJson.fromJson(ReportJson.toJson(report).toString())
        assertEquals(report.pngTextItems, restored.pngTextItems)
    }

    @Test
    fun gpsCanBeOmitted() {
        val json = ReportJson.toJson(sampleReport(gps = true), includeGps = false)
        assertFalse(json.has("gps"))
        assertTrue(ReportJson.toJson(sampleReport(gps = true)).has("gps"))
    }

    @Test
    fun hasSchemaAndVerdict() {
        val json = ReportJson.toJson(sampleReport(ai = true))
        assertEquals(ReportJson.SCHEMA, json.getInt("schema"))
        assertTrue(json.getBoolean("aiEvidence"))
    }

    @Test
    fun unknownEnumValuesFallBack() {
        val json = ReportJson.toJson(sampleReport())
        json.getJSONObject("file").put("format", "TIFF")
        json.getJSONArray("signals").getJSONObject(0).put("severity", "???")
        val restored = ReportJson.fromJson(json.toString())
        assertEquals(ImageFormat.UNKNOWN, restored.file.format)
        assertEquals("INFO", restored.signals[0].severity.name)
    }

    @Test
    fun historyStoresCodesNotTexts() {
        val json = ReportJson.toJson(sampleReport(ai = true))
        val signal = json.getJSONArray("signals").getJSONObject(0)
        assertEquals("SD_PARAMETERS", signal.getString("kind"))
        assertEquals("", signal.getString("title"))
        val section = json.getJSONArray("exif").getJSONObject(0)
        assertEquals("CAMERA", section.getString("key"))
        assertEquals("exif:Model", section.getJSONArray("items").getJSONObject(0).getString("key"))
    }

    @Test
    fun exportWithTextsHasReadableTextsAndRawValues() {
        val report = sampleReport(ai = true).let {
            it.copy(exifSections = it.exifSections + MetaSection("", listOf(
                MetaItem("FNumber", "1.8", key = "exif:FNumber", format = ValueFormat.F_NUMBER)), key = SectionKey.EXPOSURE))
        }
        val json = ReportJson.toJson(report, texts = FakeTexts())
        val signal = json.getJSONArray("signals").getJSONObject(0)
        assertEquals("SD_PARAMETERS", signal.getString("title"))
        val item = json.getJSONArray("exif").getJSONObject(1).getJSONArray("items").getJSONObject(0)
        assertEquals("FNumber", item.getString("label"))
        assertEquals("1.8 <F_NUMBER>", item.getString("value"))
        assertEquals("1.8", item.getString("rawValue"))
        // Экспорт читается обратно без двойного форматирования; подписи всё равно берутся по ключам.
        val restored = ReportJson.fromJson(json.toString())
        fun MetaReport.machine() = exifSections.map { s -> s.key to s.items.map { Triple(it.key, it.value, it.format) } }
        assertEquals(report.machine(), restored.machine())
        assertEquals(report.signals, restored.signals)
    }

    /** История прежних версий: русские подписи без ключей и сигналы с готовым текстом. */
    @Test
    fun legacySchemaOneIsMappedToKeys() {
        val legacy = """
            {"schema":1,"generator":"MetaCheck",
             "file":{"name":"a.jpg","mimeType":"image/jpeg","sizeBytes":10,"format":"JPEG","scannedBytes":10,"truncated":false},
             "aiEvidence":false,
             "signals":[{"severity":"CAUTION","title":"В файле есть геолокация","detail":"текст"}],
             "exif":[{"title":"Камера и объектив","items":[{"label":"Модель","value":"Pixel 8"},{"label":"Странное поле","value":"x"}]},
                     {"title":"Неизвестный раздел","items":[]}],
             "xmp":[{"label":"Инструмент создания","value":"Editor"}],
             "pngText":[{"label":"parameters","value":"p"}]}
        """.trimIndent()
        val r = ReportJson.fromJson(legacy)
        assertEquals(SectionKey.CAMERA, r.exifSections[0].key)
        assertEquals(null, r.exifSections[1].key)
        assertEquals("Неизвестный раздел", r.exifSections[1].title)
        assertEquals("exif:Model", r.exifSections[0].items[0].key)
        // Незнакомая подпись остаётся как есть: показывается без перевода, но не теряется.
        assertEquals(null, r.exifSections[0].items[1].key)
        assertEquals("Странное поле", r.exifSections[0].items[1].label)
        assertEquals("xmp:xmp:CreatorTool", r.xmpItems.single().key)
        assertEquals(null, r.pngTextItems.single().key)
        // Сигнал без kind сохраняет свой текст: интерфейс покажет его как есть.
        assertEquals(null, r.signals.single().kind)
        assertEquals("В файле есть геолокация", r.signals.single().title)
    }

    @Test
    fun schemaTwoNeverGuessesKeysFromLabels() {
        val json = ReportJson.toJson(sampleReport())
        json.getJSONArray("exif").getJSONObject(0).getJSONArray("items").getJSONObject(0).remove("key")
        json.getJSONArray("exif").getJSONObject(0).getJSONArray("items").getJSONObject(0).put("label", "Модель")
        assertEquals(null, ReportJson.fromJson(json.toString()).exifSections[0].items[0].key)
    }

    @Test(expected = org.json.JSONException::class)
    fun garbageIsRejected() {
        ReportJson.fromJson("not json")
    }

    @Test
    fun exportedTextIsValidJson() {
        JSONObject(ReportJson.toJson(sampleReport()).toString(2))
    }
}
