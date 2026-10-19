package com.example.metacheck.data.parser

import androidx.exifinterface.media.ExifInterface
import com.example.metacheck.domain.model.GpsPoint
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaSection
import java.io.InputStream
import java.util.Locale
import kotlin.math.roundToInt

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

    private enum class Kind { TEXT, EXPOSURE, F_NUMBER, MILLIMETERS, ORIENTATION }

    private class Tag(val id: String, val label: String, val kind: Kind = Kind.TEXT)

    private val groups: List<Pair<String, List<Tag>>> = listOf(
        "Камера и объектив" to listOf(
            Tag(ExifInterface.TAG_MAKE, "Производитель"),
            Tag(ExifInterface.TAG_MODEL, "Модель"),
            Tag(ExifInterface.TAG_LENS_MAKE, "Производитель объектива"),
            Tag(ExifInterface.TAG_LENS_MODEL, "Объектив"),
            Tag(ExifInterface.TAG_BODY_SERIAL_NUMBER, "Серийный номер корпуса"),
            Tag(ExifInterface.TAG_LENS_SERIAL_NUMBER, "Серийный номер объектива"),
            Tag(ExifInterface.TAG_CAMERA_OWNER_NAME, "Владелец камеры"),
        ),
        "Параметры съёмки" to listOf(
            Tag(ExifInterface.TAG_F_NUMBER, "Диафрагма", Kind.F_NUMBER),
            Tag(ExifInterface.TAG_EXPOSURE_TIME, "Выдержка", Kind.EXPOSURE),
            Tag(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "ISO"),
            Tag(ExifInterface.TAG_FOCAL_LENGTH, "Фокусное расстояние", Kind.MILLIMETERS),
            Tag(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, "Эквивалент 35 мм"),
            Tag(ExifInterface.TAG_EXPOSURE_PROGRAM, "Программа экспозиции"),
            Tag(ExifInterface.TAG_EXPOSURE_MODE, "Режим экспозиции"),
            Tag(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, "Коррекция экспозиции"),
            Tag(ExifInterface.TAG_METERING_MODE, "Режим замера"),
            Tag(ExifInterface.TAG_FLASH, "Вспышка (код)"),
            Tag(ExifInterface.TAG_WHITE_BALANCE, "Баланс белого"),
            Tag(ExifInterface.TAG_LIGHT_SOURCE, "Источник света"),
            Tag(ExifInterface.TAG_SCENE_CAPTURE_TYPE, "Тип сцены"),
            Tag(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, "Цифровой зум"),
            Tag(ExifInterface.TAG_SUBJECT_DISTANCE, "Дистанция до объекта"),
            Tag(ExifInterface.TAG_BRIGHTNESS_VALUE, "Яркость"),
            Tag(ExifInterface.TAG_MAX_APERTURE_VALUE, "Максимальная диафрагма"),
        ),
        "Изображение" to listOf(
            Tag(ExifInterface.TAG_IMAGE_WIDTH, "Ширина"),
            Tag(ExifInterface.TAG_IMAGE_LENGTH, "Высота"),
            Tag(ExifInterface.TAG_PIXEL_X_DIMENSION, "Ширина (пиксели)"),
            Tag(ExifInterface.TAG_PIXEL_Y_DIMENSION, "Высота (пиксели)"),
            Tag(ExifInterface.TAG_ORIENTATION, "Ориентация", Kind.ORIENTATION),
            Tag(ExifInterface.TAG_COLOR_SPACE, "Цветовое пространство (код)"),
            Tag(ExifInterface.TAG_X_RESOLUTION, "Разрешение по X"),
            Tag(ExifInterface.TAG_Y_RESOLUTION, "Разрешение по Y"),
            Tag(ExifInterface.TAG_RESOLUTION_UNIT, "Единица разрешения"),
            Tag(ExifInterface.TAG_BITS_PER_SAMPLE, "Бит на канал"),
            Tag(ExifInterface.TAG_COMPRESSION, "Сжатие"),
            Tag(ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION, "Фотометрическая интерпретация"),
            Tag(ExifInterface.TAG_EXIF_VERSION, "Версия EXIF"),
        ),
        "Даты" to listOf(
            Tag(ExifInterface.TAG_DATETIME_ORIGINAL, "Дата съёмки"),
            Tag(ExifInterface.TAG_DATETIME_DIGITIZED, "Дата оцифровки"),
            Tag(ExifInterface.TAG_DATETIME, "Дата изменения"),
            Tag(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "Часовой пояс съёмки"),
            Tag(ExifInterface.TAG_OFFSET_TIME, "Часовой пояс изменения"),
            Tag(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, "Доли секунды"),
        ),
        "Геолокация EXIF" to listOf(
            Tag(ExifInterface.TAG_GPS_LATITUDE_REF, "Полушарие широты"),
            Tag(ExifInterface.TAG_GPS_LONGITUDE_REF, "Полушарие долготы"),
            Tag(ExifInterface.TAG_GPS_ALTITUDE, "Высота GPS (RAW)"),
            Tag(ExifInterface.TAG_GPS_DATESTAMP, "Дата GPS"),
            Tag(ExifInterface.TAG_GPS_TIMESTAMP, "Время GPS"),
            Tag(ExifInterface.TAG_GPS_IMG_DIRECTION, "Направление камеры"),
            Tag(ExifInterface.TAG_GPS_SPEED, "Скорость GPS"),
            Tag(ExifInterface.TAG_GPS_SPEED_REF, "Единица скорости"),
            Tag(ExifInterface.TAG_GPS_SATELLITES, "Спутники"),
            Tag(ExifInterface.TAG_GPS_PROCESSING_METHOD, "Метод геопозиционирования"),
        ),
        "Программа и авторство" to listOf(
            Tag(ExifInterface.TAG_SOFTWARE, "Программа"),
            Tag(ExifInterface.TAG_ARTIST, "Автор"),
            Tag(ExifInterface.TAG_COPYRIGHT, "Авторские права"),
            Tag(ExifInterface.TAG_IMAGE_DESCRIPTION, "Описание"),
            Tag(ExifInterface.TAG_USER_COMMENT, "Комментарий"),
        ),
    )

    fun read(stream: InputStream): ExifResult {
        return try {
            val exif = ExifInterface(stream)
            val sections = groups.mapNotNull { (title, tags) ->
                val items = tags.mapNotNull { tag -> readTag(exif, tag)?.let { MetaItem(tag.label, it) } }
                if (items.isEmpty()) null else MetaSection(title, items)
            }
            ExifResult(
                sections = sections,
                gps = readGps(exif),
                toolFields = listOfNotNull(field(exif, ExifInterface.TAG_SOFTWARE, "EXIF Software")),
                freeTextFields = listOfNotNull(
                    field(exif, ExifInterface.TAG_IMAGE_DESCRIPTION, "EXIF Описание"),
                    field(exif, ExifInterface.TAG_USER_COMMENT, "EXIF Комментарий"),
                    field(exif, ExifInterface.TAG_ARTIST, "EXIF Автор"),
                ),
            )
        } catch (e: Exception) {
            // Формат не поддерживается или файл повреждён: просто нет данных EXIF.
            EMPTY
        }
    }

    private fun field(exif: ExifInterface, tag: String, label: String): MetaItem? =
        sanitize(exif.getAttribute(tag))?.let { MetaItem(label, it) }

    private fun readTag(exif: ExifInterface, tag: Tag): String? = when (tag.kind) {
        Kind.TEXT -> sanitize(exif.getAttribute(tag.id))
        Kind.EXPOSURE -> exif.getAttributeDouble(tag.id, Double.NaN).takeIf { !it.isNaN() }?.let { v ->
            if (v > 0 && v < 1) "1/${(1 / v).roundToInt()} с" else String.format(Locale.US, "%.1f с", v)
        }
        Kind.F_NUMBER -> exif.getAttributeDouble(tag.id, Double.NaN).takeIf { !it.isNaN() }
            ?.let { String.format(Locale.US, "f/%.1f", it) }
        Kind.MILLIMETERS -> exif.getAttributeDouble(tag.id, Double.NaN).takeIf { !it.isNaN() }
            ?.let { String.format(Locale.US, "%.1f мм", it) }
        Kind.ORIENTATION -> exif.getAttributeInt(tag.id, 0).takeIf { it != 0 }?.let {
            when (it) {
                1 -> "Обычная"
                3 -> "Поворот на 180°"
                6 -> "Поворот на 90° по часовой"
                8 -> "Поворот на 90° против часовой"
                else -> "Код $it"
            }
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
