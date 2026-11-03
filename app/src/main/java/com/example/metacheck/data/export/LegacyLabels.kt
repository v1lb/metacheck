package com.example.metacheck.data.export

import androidx.exifinterface.media.ExifInterface
import com.example.metacheck.data.parser.ExifReader
import com.example.metacheck.data.parser.XmpExtractor
import com.example.metacheck.domain.analysis.SignalEngine
import com.example.metacheck.domain.model.SectionKey

/**
 * Совместимость с отчётами истории схемы 1 (прежние версии), где вместо ключей хранились русские
 * подписи. Это данные для чтения старых файлов, а не тексты интерфейса: при показе подпись берётся
 * из ресурсов по найденному ключу. Новые отчёты пишутся только с ключами.
 */
internal object LegacyLabels {
    private val sections = mapOf(
        "Камера и объектив" to SectionKey.CAMERA,
        "Параметры съёмки" to SectionKey.EXPOSURE,
        "Изображение" to SectionKey.IMAGE,
        "Даты" to SectionKey.DATES,
        "Геолокация EXIF" to SectionKey.GPS,
        "Программа и авторство" to SectionKey.AUTHORSHIP,
    )

    /** Подписи полей EXIF схемы 1. */
    private val exifItems = mapOf(
        "Производитель" to ExifReader.KEY_PREFIX + ExifInterface.TAG_MAKE,
        "Модель" to ExifReader.KEY_PREFIX + ExifInterface.TAG_MODEL,
        "Производитель объектива" to ExifReader.KEY_PREFIX + ExifInterface.TAG_LENS_MAKE,
        "Объектив" to ExifReader.KEY_PREFIX + ExifInterface.TAG_LENS_MODEL,
        "Серийный номер корпуса" to ExifReader.KEY_PREFIX + ExifInterface.TAG_BODY_SERIAL_NUMBER,
        "Серийный номер объектива" to ExifReader.KEY_PREFIX + ExifInterface.TAG_LENS_SERIAL_NUMBER,
        "Владелец камеры" to ExifReader.KEY_PREFIX + ExifInterface.TAG_CAMERA_OWNER_NAME,
        "Диафрагма" to ExifReader.KEY_PREFIX + ExifInterface.TAG_F_NUMBER,
        "Выдержка" to ExifReader.KEY_PREFIX + ExifInterface.TAG_EXPOSURE_TIME,
        "ISO" to ExifReader.KEY_PREFIX + ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        "Фокусное расстояние" to ExifReader.KEY_PREFIX + ExifInterface.TAG_FOCAL_LENGTH,
        "Эквивалент 35 мм" to ExifReader.KEY_PREFIX + ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        "Программа экспозиции" to ExifReader.KEY_PREFIX + ExifInterface.TAG_EXPOSURE_PROGRAM,
        "Режим экспозиции" to ExifReader.KEY_PREFIX + ExifInterface.TAG_EXPOSURE_MODE,
        "Коррекция экспозиции" to ExifReader.KEY_PREFIX + ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        "Режим замера" to ExifReader.KEY_PREFIX + ExifInterface.TAG_METERING_MODE,
        "Вспышка (код)" to ExifReader.KEY_PREFIX + ExifInterface.TAG_FLASH,
        "Баланс белого" to ExifReader.KEY_PREFIX + ExifInterface.TAG_WHITE_BALANCE,
        "Источник света" to ExifReader.KEY_PREFIX + ExifInterface.TAG_LIGHT_SOURCE,
        "Тип сцены" to ExifReader.KEY_PREFIX + ExifInterface.TAG_SCENE_CAPTURE_TYPE,
        "Цифровой зум" to ExifReader.KEY_PREFIX + ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
        "Дистанция до объекта" to ExifReader.KEY_PREFIX + ExifInterface.TAG_SUBJECT_DISTANCE,
        "Яркость" to ExifReader.KEY_PREFIX + ExifInterface.TAG_BRIGHTNESS_VALUE,
        "Максимальная диафрагма" to ExifReader.KEY_PREFIX + ExifInterface.TAG_MAX_APERTURE_VALUE,
        "Ширина" to ExifReader.KEY_PREFIX + ExifInterface.TAG_IMAGE_WIDTH,
        "Высота" to ExifReader.KEY_PREFIX + ExifInterface.TAG_IMAGE_LENGTH,
        "Ширина (пиксели)" to ExifReader.KEY_PREFIX + ExifInterface.TAG_PIXEL_X_DIMENSION,
        "Высота (пиксели)" to ExifReader.KEY_PREFIX + ExifInterface.TAG_PIXEL_Y_DIMENSION,
        "Ориентация" to ExifReader.KEY_PREFIX + ExifInterface.TAG_ORIENTATION,
        "Цветовое пространство (код)" to ExifReader.KEY_PREFIX + ExifInterface.TAG_COLOR_SPACE,
        "Разрешение по X" to ExifReader.KEY_PREFIX + ExifInterface.TAG_X_RESOLUTION,
        "Разрешение по Y" to ExifReader.KEY_PREFIX + ExifInterface.TAG_Y_RESOLUTION,
        "Единица разрешения" to ExifReader.KEY_PREFIX + ExifInterface.TAG_RESOLUTION_UNIT,
        "Бит на канал" to ExifReader.KEY_PREFIX + ExifInterface.TAG_BITS_PER_SAMPLE,
        "Сжатие" to ExifReader.KEY_PREFIX + ExifInterface.TAG_COMPRESSION,
        "Фотометрическая интерпретация" to ExifReader.KEY_PREFIX + ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION,
        "Версия EXIF" to ExifReader.KEY_PREFIX + ExifInterface.TAG_EXIF_VERSION,
        "Дата съёмки" to ExifReader.KEY_PREFIX + ExifInterface.TAG_DATETIME_ORIGINAL,
        "Дата оцифровки" to ExifReader.KEY_PREFIX + ExifInterface.TAG_DATETIME_DIGITIZED,
        "Дата изменения" to ExifReader.KEY_PREFIX + ExifInterface.TAG_DATETIME,
        "Часовой пояс съёмки" to ExifReader.KEY_PREFIX + ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        "Часовой пояс изменения" to ExifReader.KEY_PREFIX + ExifInterface.TAG_OFFSET_TIME,
        "Доли секунды" to ExifReader.KEY_PREFIX + ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        "Полушарие широты" to ExifReader.KEY_PREFIX + ExifInterface.TAG_GPS_LATITUDE_REF,
        "Полушарие долготы" to ExifReader.KEY_PREFIX + ExifInterface.TAG_GPS_LONGITUDE_REF,
        "Высота GPS (RAW)" to ExifReader.KEY_PREFIX + ExifInterface.TAG_GPS_ALTITUDE,
        "Дата GPS" to ExifReader.KEY_PREFIX + ExifInterface.TAG_GPS_DATESTAMP,
        "Время GPS" to ExifReader.KEY_PREFIX + ExifInterface.TAG_GPS_TIMESTAMP,
        "Направление камеры" to ExifReader.KEY_PREFIX + ExifInterface.TAG_GPS_IMG_DIRECTION,
        "Скорость GPS" to ExifReader.KEY_PREFIX + ExifInterface.TAG_GPS_SPEED,
        "Единица скорости" to ExifReader.KEY_PREFIX + ExifInterface.TAG_GPS_SPEED_REF,
        "Спутники" to ExifReader.KEY_PREFIX + ExifInterface.TAG_GPS_SATELLITES,
        "Метод геопозиционирования" to ExifReader.KEY_PREFIX + ExifInterface.TAG_GPS_PROCESSING_METHOD,
        "Программа" to ExifReader.KEY_PREFIX + ExifInterface.TAG_SOFTWARE,
        "Автор" to ExifReader.KEY_PREFIX + ExifInterface.TAG_ARTIST,
        "Авторские права" to ExifReader.KEY_PREFIX + ExifInterface.TAG_COPYRIGHT,
        "Описание" to ExifReader.KEY_PREFIX + ExifInterface.TAG_IMAGE_DESCRIPTION,
        "Комментарий" to ExifReader.KEY_PREFIX + ExifInterface.TAG_USER_COMMENT,
    )

    /** Подписи полей XMP схемы 1 (без совпадающих с EXIF «Автор» и «Описание»: их не различить). */
    private val xmpItems = mapOf(
        "Инструмент создания" to XmpExtractor.KEY_PREFIX + XmpExtractor.KEY_CREATOR_TOOL,
        "Дата создания (XMP)" to XmpExtractor.KEY_PREFIX + "xmp:CreateDate",
        "Дата изменения (XMP)" to XmpExtractor.KEY_PREFIX + "xmp:ModifyDate",
        "Название" to XmpExtractor.KEY_PREFIX + "dc:title",
        "Права" to XmpExtractor.KEY_PREFIX + "dc:rights",
        "Производитель (XMP)" to XmpExtractor.KEY_PREFIX + "tiff:Make",
        "Модель (XMP)" to XmpExtractor.KEY_PREFIX + "tiff:Model",
        "GPSLatitude (XMP)" to XmpExtractor.KEY_PREFIX + "exif:GPSLatitude",
        "GPSLongitude (XMP)" to XmpExtractor.KEY_PREFIX + "exif:GPSLongitude",
        "GPSAltitude (XMP)" to XmpExtractor.KEY_PREFIX + "exif:GPSAltitude",
        "Тип источника (IPTC)" to XmpExtractor.KEY_PREFIX + SignalEngine.KEY_DIGITAL_SOURCE_TYPE,
        "Программы в истории правок" to XmpExtractor.KEY_HISTORY_AGENTS,
    )

    fun sectionKey(title: String): SectionKey? = sections[title]

    fun itemKey(label: String): String? = exifItems[label] ?: xmpItems[label]
}
