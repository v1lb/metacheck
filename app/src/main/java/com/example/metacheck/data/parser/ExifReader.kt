package com.example.metacheck.data.parser

import androidx.exifinterface.media.ExifInterface
import com.example.metacheck.domain.model.GpsPoint
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaSection
import com.example.metacheck.domain.model.SectionKey
import com.example.metacheck.domain.model.ValueFormat
import java.io.InputStream

data class ExifResult(
    val sections: List<MetaSection>,
    val gps: GpsPoint?,
    val toolFields: List<MetaItem>,
    val freeTextFields: List<MetaItem>,
) {
    val hasData: Boolean get() = gps != null || sections.any { it.items.isNotEmpty() }
}

object ExifReader {

    val EMPTY = ExifResult(emptyList(), null, emptyList(), emptyList())

    /** Ключ поля EXIF: "exif:" + имя тега ExifInterface (например, "exif:Make"). */
    const val KEY_PREFIX = "exif:"

    private enum class Kind { TEXT, EXPOSURE, F_NUMBER, MILLIMETERS, ORIENTATION }

    private class Tag(val id: String, val kind: Kind = Kind.TEXT)

    /**
     * Порядок разделов и тегов. Подписи берутся из ресурсов по ключу "exif:<тег>" (ui/text),
     * поэтому здесь только технические имена тегов ExifInterface.
     */
    private val groups: List<Pair<SectionKey, List<Tag>>> = listOf(
        SectionKey.CAMERA to listOf(
            Tag(ExifInterface.TAG_MAKE),
            Tag(ExifInterface.TAG_MODEL),
            Tag(ExifInterface.TAG_LENS_MAKE),
            Tag(ExifInterface.TAG_LENS_MODEL),
            Tag(ExifInterface.TAG_BODY_SERIAL_NUMBER),
            Tag(ExifInterface.TAG_LENS_SERIAL_NUMBER),
            Tag(ExifInterface.TAG_CAMERA_OWNER_NAME),
        ),
        SectionKey.EXPOSURE to listOf(
            Tag(ExifInterface.TAG_F_NUMBER, Kind.F_NUMBER),
            Tag(ExifInterface.TAG_EXPOSURE_TIME, Kind.EXPOSURE),
            Tag(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
            Tag(ExifInterface.TAG_FOCAL_LENGTH, Kind.MILLIMETERS),
            Tag(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM),
            Tag(ExifInterface.TAG_EXPOSURE_PROGRAM),
            Tag(ExifInterface.TAG_EXPOSURE_MODE),
            Tag(ExifInterface.TAG_EXPOSURE_BIAS_VALUE),
            Tag(ExifInterface.TAG_METERING_MODE),
            Tag(ExifInterface.TAG_FLASH),
            Tag(ExifInterface.TAG_WHITE_BALANCE),
            Tag(ExifInterface.TAG_LIGHT_SOURCE),
            Tag(ExifInterface.TAG_SCENE_CAPTURE_TYPE),
            Tag(ExifInterface.TAG_DIGITAL_ZOOM_RATIO),
            Tag(ExifInterface.TAG_SUBJECT_DISTANCE),
            Tag(ExifInterface.TAG_BRIGHTNESS_VALUE),
            Tag(ExifInterface.TAG_MAX_APERTURE_VALUE),
        ),
        SectionKey.IMAGE to listOf(
            Tag(ExifInterface.TAG_IMAGE_WIDTH),
            Tag(ExifInterface.TAG_IMAGE_LENGTH),
            Tag(ExifInterface.TAG_PIXEL_X_DIMENSION),
            Tag(ExifInterface.TAG_PIXEL_Y_DIMENSION),
            Tag(ExifInterface.TAG_ORIENTATION, Kind.ORIENTATION),
            Tag(ExifInterface.TAG_COLOR_SPACE),
            Tag(ExifInterface.TAG_X_RESOLUTION),
            Tag(ExifInterface.TAG_Y_RESOLUTION),
            Tag(ExifInterface.TAG_RESOLUTION_UNIT),
            Tag(ExifInterface.TAG_BITS_PER_SAMPLE),
            Tag(ExifInterface.TAG_COMPRESSION),
            Tag(ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION),
            Tag(ExifInterface.TAG_EXIF_VERSION),
        ),
        SectionKey.DATES to listOf(
            Tag(ExifInterface.TAG_DATETIME_ORIGINAL),
            Tag(ExifInterface.TAG_DATETIME_DIGITIZED),
            Tag(ExifInterface.TAG_DATETIME),
            Tag(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
            Tag(ExifInterface.TAG_OFFSET_TIME),
            Tag(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL),
        ),
        SectionKey.GPS to listOf(
            Tag(ExifInterface.TAG_GPS_LATITUDE_REF),
            Tag(ExifInterface.TAG_GPS_LONGITUDE_REF),
            Tag(ExifInterface.TAG_GPS_ALTITUDE),
            Tag(ExifInterface.TAG_GPS_DATESTAMP),
            Tag(ExifInterface.TAG_GPS_TIMESTAMP),
            Tag(ExifInterface.TAG_GPS_IMG_DIRECTION),
            Tag(ExifInterface.TAG_GPS_SPEED),
            Tag(ExifInterface.TAG_GPS_SPEED_REF),
            Tag(ExifInterface.TAG_GPS_SATELLITES),
            Tag(ExifInterface.TAG_GPS_PROCESSING_METHOD),
        ),
        SectionKey.AUTHORSHIP to listOf(
            Tag(ExifInterface.TAG_SOFTWARE),
            Tag(ExifInterface.TAG_ARTIST),
            Tag(ExifInterface.TAG_COPYRIGHT),
            Tag(ExifInterface.TAG_IMAGE_DESCRIPTION),
            Tag(ExifInterface.TAG_USER_COMMENT),
        ),
    )

    fun read(stream: InputStream): ExifResult {
        return try {
            val exif = ExifInterface(stream)
            val sections = groups.mapNotNull { (section, tags) ->
                val items = tags.mapNotNull { tag -> readTag(exif, tag) }
                if (items.isEmpty()) null else MetaSection(section.name, items, section)
            }
            ExifResult(
                sections = sections,
                gps = readGps(exif),
                // Технические имена полей: попадают в текст сигнала как есть, одинаково во всех языках.
                toolFields = listOfNotNull(field(exif, ExifInterface.TAG_SOFTWARE, "EXIF Software")),
                freeTextFields = listOfNotNull(
                    field(exif, ExifInterface.TAG_IMAGE_DESCRIPTION, "EXIF ImageDescription"),
                    field(exif, ExifInterface.TAG_USER_COMMENT, "EXIF UserComment"),
                    field(exif, ExifInterface.TAG_ARTIST, "EXIF Artist"),
                ),
            )
        } catch (e: Exception) {
            // Формат не поддерживается или файл повреждён: просто нет данных EXIF.
            EMPTY
        }
    }

    private fun field(exif: ExifInterface, tag: String, label: String): MetaItem? =
        sanitize(exif.getAttribute(tag))?.let { MetaItem(label, it) }

    /** Значение тега. Для числовых видов в value лежит машинное число, единицы подставит интерфейс. */
    private fun readTag(exif: ExifInterface, tag: Tag): MetaItem? {
        fun item(value: String, format: ValueFormat? = null) = MetaItem(tag.id, value, KEY_PREFIX + tag.id, format)
        fun number() = exif.getAttributeDouble(tag.id, Double.NaN).takeIf { !it.isNaN() && it.isFinite() }
        return when (tag.kind) {
            Kind.TEXT -> sanitize(exif.getAttribute(tag.id))?.let { item(it) }
            Kind.EXPOSURE -> number()?.let { item(it.toString(), ValueFormat.EXPOSURE_SECONDS) }
            Kind.F_NUMBER -> number()?.let { item(it.toString(), ValueFormat.F_NUMBER) }
            Kind.MILLIMETERS -> number()?.let { item(it.toString(), ValueFormat.MILLIMETERS) }
            Kind.ORIENTATION -> exif.getAttributeInt(tag.id, 0).takeIf { it != 0 }
                ?.let { item(it.toString(), ValueFormat.ORIENTATION) }
        }
    }

    private fun readGps(exif: ExifInterface): GpsPoint? {
        val ll = exif.latLong ?: return null
        val lat = ll[0]
        val lon = ll[1]
        // Проверка диапазона: в испорченных файлах бывают абсурдные координаты.
        if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val alt = exif.getAltitude(Double.NaN).takeIf { !it.isNaN() }
        return GpsPoint(lat, lon, alt)
    }

    private fun sanitize(raw: String?): String? {
        val s = raw?.filter { it == '\n' || it == '\t' || !it.isISOControl() }?.trim()?.take(500)
        return s?.takeIf { it.isNotEmpty() }
    }
}
