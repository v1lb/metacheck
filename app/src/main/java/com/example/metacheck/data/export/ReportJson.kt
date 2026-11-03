package com.example.metacheck.data.export

import com.example.metacheck.domain.metadata.GpsPrivacy
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
import com.example.metacheck.domain.model.ValueFormat
import com.example.metacheck.domain.text.MetaTexts
import org.json.JSONArray
import org.json.JSONObject

/**
 * Единый формат отчёта: история, экспорт и отправка в API.
 *
 * Схема 2: у сигналов есть "kind"/"args", у полей "key"/"format", у разделов "key".
 *  - Без [MetaTexts] (история): сохраняются машинные данные, тексты подставит интерфейс при показе.
 *  - С [MetaTexts] (экспорт, API): "title"/"detail"/"label"/"value" — готовые тексты на языке
 *    пользователя, машинное значение поля дублируется в "rawValue".
 * Схему 1 (до версии с ключами) читает [fromJson]: подписи переводятся в ключи через [LegacyLabels].
 */
object ReportJson {
    const val SCHEMA = 2

    /**
     * [includeGps] = false убирает все данные о местоположении (например, при отправке на сервер):
     * не только блок "gps", но и раздел GPS из EXIF и GPS-поля XMP/PNG. Раньше координаты из XMP
     * (Lightroom, Google Фото) уходили на сервер, хотя пользователь выключил отправку GPS.
     */
    fun toJson(r: MetaReport, includeGps: Boolean = true, texts: MetaTexts? = null): JSONObject = JSONObject().apply {
        val exifSections = if (includeGps) r.exifSections else r.exifSections.filter { it.key != SectionKey.GPS }
        val xmpItems = if (includeGps) r.xmpItems else r.xmpItems.filterNot(GpsPrivacy::isLocationItem)
        val pngItems = if (includeGps) r.pngTextItems else r.pngTextItems.filterNot(GpsPrivacy::isLocationItem)
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
                            s.kind?.let { put("kind", it.name) }
                            if (s.args.isNotEmpty()) put("args", JSONArray(s.args))
                            put("title", texts?.signalTitle(s) ?: s.title)
                            put("detail", texts?.signalDetail(s) ?: s.detail)
                        },
                    )
                }
            },
        )
        put(
            "exif",
            JSONArray().apply {
                exifSections.forEach { s ->
                    put(
                        JSONObject().apply {
                            put("title", texts?.sectionTitle(s) ?: s.title)
                            s.key?.let { put("key", it.name) }
                            put("items", itemsToJson(s.items, texts))
                        },
                    )
                }
            },
        )
        put("xmp", itemsToJson(xmpItems, texts))
        put("pngText", itemsToJson(pngItems, texts))
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
        val legacy = o.optInt("schema", 1) < 2
        val f = o.getJSONObject("file")
        val file = FileInfo(
            name = f.optString("name"),
            mimeType = f.optString("mimeType"),
            sizeBytes = f.optLong("sizeBytes", -1L),
            format = enumOrNull<ImageFormat>(f.optString("format")) ?: ImageFormat.UNKNOWN,
            scannedBytes = f.optInt("scannedBytes"),
            truncated = f.optBoolean("truncated"),
        )
        val signals = objects(o.optJSONArray("signals")).map {
            val kind = enumOrNull<SignalKind>(it.optString("kind"))
            Signal(
                severity = enumOrNull<Severity>(it.optString("severity")) ?: Severity.INFO,
                // Для сигналов с kind тексты всегда берутся из ресурсов: сохранённый текст не нужен.
                title = if (kind == null) it.optString("title") else "",
                detail = if (kind == null) it.optString("detail") else "",
                kind = kind,
                args = strings(it.optJSONArray("args")),
            )
        }
        val sections = objects(o.optJSONArray("exif")).map {
            val title = it.optString("title")
            val key = enumOrNull<SectionKey>(it.optString("key")) ?: if (legacy) LegacyLabels.sectionKey(title) else null
            MetaSection(title, itemsFromJson(it.optJSONArray("items"), legacy), key)
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
            xmpItems = itemsFromJson(o.optJSONArray("xmp"), legacy),
            pngTextItems = itemsFromJson(o.optJSONArray("pngText"), legacy),
            gps = gps,
            signals = signals,
        )
    }

    private fun itemsToJson(items: List<MetaItem>, texts: MetaTexts?): JSONArray = JSONArray().apply {
        items.forEach { item ->
            put(
                JSONObject().apply {
                    put("label", texts?.itemLabel(item) ?: item.label)
                    put("value", texts?.itemValue(item) ?: item.value)
                    item.key?.let { put("key", it) }
                    item.format?.let {
                        put("format", it.name)
                        if (texts != null) put("rawValue", item.value)
                    }
                },
            )
        }
    }

    private fun itemsFromJson(arr: JSONArray?, legacy: Boolean): List<MetaItem> =
        objects(arr).map {
            val label = it.optString("label")
            MetaItem(
                label = label,
                // В экспорте с MetaTexts "value" уже отформатирован, машинное значение лежит в "rawValue".
                value = if (it.has("rawValue")) it.optString("rawValue") else it.optString("value"),
                key = it.optString("key").ifEmpty { null } ?: if (legacy) LegacyLabels.itemKey(label) else null,
                format = enumOrNull<ValueFormat>(it.optString("format")),
            )
        }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        enumValues<T>().firstOrNull { it.name == name }

    private fun strings(arr: JSONArray?): List<String> =
        if (arr == null) emptyList() else (0 until arr.length()).map { arr.optString(it) }

    private fun objects(arr: JSONArray?): List<JSONObject> =
        if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
}
