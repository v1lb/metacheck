package com.example.metacheck.data.export

import com.example.metacheck.sampleReport
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
        assertEquals("Неизвестный", restored.file.format.label)
        assertEquals("INFO", restored.signals[0].severity.name)
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
