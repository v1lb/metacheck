package com.example.metacheck.data.export

import com.example.metacheck.domain.model.FileInfo
import com.example.metacheck.domain.model.GpsPoint
import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaReport
import com.example.metacheck.domain.model.MetaSection
import com.example.metacheck.domain.model.Severity
import com.example.metacheck.domain.model.Signal
import org.json.JSONArray
import org.json.JSONObject

/** Единый формат отчёта: используется и для экспорта, и для истории, и для отправки в API. */
object ReportJson {
    const val SCHEMA = 1

    /** [includeGps] = false убирает координаты (например, при отправке на сервер). */
    fun toJson(r: MetaReport, includeGps: Boolean = true): JSONObject = JSONObject().apply {
        put("schema", SCHEMA)
        put("generator", "MetaCheck")
        put(
            "file",
            JSONObject().apply {
                put("name", r.file.name)
                put("mimeType", r.file.mimeType)
                put("sizeBytes", r.file.sizeBytes)
                put("format", r.file.format.name)
                put("scannedBytes", r.file.scannedBytes)
                put("truncated", r.file.truncated)
            },
        )
        put("aiEvidence", r.hasAiEvidence)
        put(
            "signals",
            JSONArray().apply {
                r.signals.forEach { s ->
                    put(
                        JSONObject().apply {
                            put("severity", s.severity.name)
                            put("title", s.title)
                            put("detail", s.detail)
                        },
                    )
                }
            },
        )
        put(
            "exif",
            JSONArray().apply {
                r.exifSections.forEach { s ->
                    put(
                        JSONObject().apply {
                            put("title", s.title)
                            put("items", itemsToJson(s.items))
                        },
                    )
                }
            },
        )
        put("xmp", itemsToJson(r.xmpItems))
        put("pngText", itemsToJson(r.pngTextItems))
        if (includeGps) {
            r.gps?.let { g ->
                put(
                    "gps",
                    JSONObject().apply {
                        put("latitude", g.latitude)
                        put("longitude", g.longitude)
                        g.altitudeMeters?.let { put("altitudeMeters", it) }
                    },
                )
            }
        }
    }

    fun fromJson(text: String): MetaReport {
        val o = JSONObject(text)
        val f = o.getJSONObject("file")
        val file = FileInfo(
            name = f.optString("name"),
            mimeType = f.optString("mimeType"),
            sizeBytes = f.optLong("sizeBytes", -1L),
            format = ImageFormat.values().firstOrNull { it.name == f.optString("format") } ?: ImageFormat.UNKNOWN,
            scannedBytes = f.optInt("scannedBytes"),
            truncated = f.optBoolean("truncated"),
        )
        val signals = objects(o.optJSONArray("signals")).map {
            Signal(
                severity = Severity.values().firstOrNull { s -> s.name == it.optString("severity") } ?: Severity.INFO,
                title = it.optString("title"),
                detail = it.optString("detail"),
            )
        }
        val sections = objects(o.optJSONArray("exif")).map {
            MetaSection(it.optString("title"), itemsFromJson(it.optJSONArray("items")))
        }
        val gps = o.optJSONObject("gps")?.let {
            GpsPoint(
                latitude = it.getDouble("latitude"),
                longitude = it.getDouble("longitude"),
                altitudeMeters = if (it.has("altitudeMeters")) it.getDouble("altitudeMeters") else null,
            )
        }
        return MetaReport(
            file = file,
            exifSections = sections,
            xmpItems = itemsFromJson(o.optJSONArray("xmp")),
            pngTextItems = itemsFromJson(o.optJSONArray("pngText")),
            gps = gps,
            signals = signals,
        )
    }

    private fun itemsToJson(items: List<MetaItem>): JSONArray = JSONArray().apply {
        items.forEach { put(JSONObject().apply { put("label", it.label); put("value", it.value) }) }
    }

    private fun itemsFromJson(arr: JSONArray?): List<MetaItem> =
        objects(arr).map { MetaItem(it.optString("label"), it.optString("value")) }

    private fun objects(arr: JSONArray?): List<JSONObject> =
        if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
}
