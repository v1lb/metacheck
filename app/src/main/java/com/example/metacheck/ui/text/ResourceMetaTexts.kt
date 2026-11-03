package com.example.metacheck.ui.text

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import com.example.metacheck.R
import com.example.metacheck.data.parser.ExifReader
import com.example.metacheck.data.parser.XmpExtractor
import com.example.metacheck.domain.analysis.SignalEngine
import com.example.metacheck.domain.analysis.SignalEngine.C2PA_UNREADABLE
import com.example.metacheck.domain.metadata.MetadataGroup
import com.example.metacheck.domain.model.ImageFormat
import com.example.metacheck.domain.model.MetaItem
import com.example.metacheck.domain.model.MetaSection
import com.example.metacheck.domain.model.SectionKey
import com.example.metacheck.domain.model.Severity
import com.example.metacheck.domain.model.Signal
import com.example.metacheck.domain.model.SignalKind
import com.example.metacheck.domain.model.ValueFormat
import com.example.metacheck.domain.text.MetaTexts
import com.example.metacheck.domain.text.TextKey
import java.util.Locale
import kotlin.math.roundToInt

/**
 * [MetaTexts] на строковых ресурсах. [context] должен быть контекстом Activity: только он
 * гарантированно несёт язык приложения (на Android 12 и ниже AppCompat меняет язык Activity,
 * но не Application).
 *
 * Числа в значениях метаданных форматируются с точкой (Locale.US) на любом языке: это данные
 * экспертизы, их удобно сравнивать с другими инструментами. Переводятся только слова и единицы.
 */
class ResourceMetaTexts(private val context: Context) : MetaTexts {

    /** Без аргументов строка не проходит через String.format: литеральный % в ней безопасен. */
    private fun s(@StringRes id: Int, vararg args: Any): String =
        if (args.isEmpty()) context.getString(id) else context.getString(id, *args)

    override fun itemLabel(item: MetaItem): String =
        item.key?.let { labelIds[it] }?.let { s(it) } ?: item.label

    override fun itemValue(item: MetaItem): String {
        val raw = item.value
        return when (item.format) {
            null -> raw
            ValueFormat.BAD_DATA -> s(R.string.value_bad_data)
            ValueFormat.BOOLEAN -> s(if (raw.toBoolean()) R.string.common_yes else R.string.common_no)
            ValueFormat.BYTES -> s(R.string.value_bytes, raw)
            ValueFormat.EXPOSURE_SECONDS -> raw.toDoubleOrNull()?.let { v ->
                if (v > 0 && v < 1) s(R.string.value_exposure_fraction, (1 / v).roundToInt())
                else s(R.string.value_exposure_seconds, number(v, 1))
            } ?: raw
            ValueFormat.F_NUMBER -> raw.toDoubleOrNull()?.let { s(R.string.value_f_number, number(it, 1)) } ?: raw
            ValueFormat.MILLIMETERS -> raw.toDoubleOrNull()?.let { s(R.string.value_millimeters, number(it, 1)) } ?: raw
            ValueFormat.METERS -> raw.toDoubleOrNull()?.let { s(R.string.value_meters, number(it, 1)) } ?: raw
            ValueFormat.ORIENTATION -> when (val code = raw.toIntOrNull()) {
                1 -> s(R.string.value_orientation_normal)
                3 -> s(R.string.value_orientation_180)
                6 -> s(R.string.value_orientation_90_cw)
                8 -> s(R.string.value_orientation_90_ccw)
                null -> raw
                else -> s(R.string.value_orientation_code, code)
            }
        }
    }

    override fun sectionTitle(section: MetaSection): String = when (section.key) {
        SectionKey.CAMERA -> s(R.string.section_camera)
        SectionKey.EXPOSURE -> s(R.string.section_exposure)
        SectionKey.IMAGE -> s(R.string.section_image)
        SectionKey.DATES -> s(R.string.section_dates)
        SectionKey.GPS -> s(R.string.section_gps)
        SectionKey.AUTHORSHIP -> s(R.string.section_authorship)
        null -> section.title
    }

    override fun groupTitle(group: MetadataGroup): String = s(groupTitleId(group))

    override fun signalTitle(signal: Signal): String {
        val kind = signal.kind ?: return signal.title
        val a = signal.args
        return when (kind) {
            SignalKind.SD_PARAMETERS -> s(R.string.signal_sd_parameters_title)
            SignalKind.COMFYUI_GRAPH -> s(R.string.signal_comfyui_title)
            SignalKind.INVOKEAI_METADATA -> s(R.string.signal_invokeai_title)
            SignalKind.AI_TOOL_FIELD -> s(R.string.signal_ai_tool_title, a.arg(0))
            SignalKind.AI_MENTION_TEXT -> s(R.string.signal_ai_mention_title, a.arg(0))
            SignalKind.IPTC_AI_SOURCE -> s(R.string.signal_iptc_title)
            SignalKind.IPTC_AI_EDITED -> s(R.string.signal_iptc_edited_title)
            SignalKind.C2PA_PRESENT -> s(R.string.signal_c2pa_title)
            SignalKind.C2PA_AI_CREATED -> a.arg(0).let {
                if (it.isBlank()) s(R.string.signal_c2pa_ai_title) else s(R.string.signal_c2pa_ai_title_generator, it)
            }
            SignalKind.C2PA_AI_EDITED -> a.arg(0).let {
                if (it.isBlank()) s(R.string.signal_c2pa_ai_edited_title) else s(R.string.signal_c2pa_ai_edited_title_generator, it)
            }
            SignalKind.GENERATION_PARAMS -> s(R.string.signal_generation_params_title)
            SignalKind.MESSENGER_COPY -> s(R.string.signal_messenger_title, a.arg(0))
            SignalKind.SCREENSHOT -> s(R.string.signal_screenshot_title)
            SignalKind.HAS_GPS -> s(R.string.signal_gps_title)
            SignalKind.NO_METADATA -> s(R.string.signal_no_metadata_title)
            SignalKind.PARTIAL_SCAN -> s(R.string.signal_partial_title)
            SignalKind.NO_AI_TRACES -> s(R.string.signal_no_ai_title)
        }
    }

    override fun signalDetail(signal: Signal): String {
        val kind = signal.kind ?: return signal.detail
        val a = signal.args
        return when (kind) {
            SignalKind.SD_PARAMETERS -> s(R.string.signal_sd_parameters_detail)
            SignalKind.COMFYUI_GRAPH -> s(R.string.signal_comfyui_detail, a.arg(0))
            SignalKind.INVOKEAI_METADATA -> s(R.string.signal_invokeai_detail, a.arg(0))
            SignalKind.AI_TOOL_FIELD -> s(R.string.signal_ai_tool_detail, a.arg(1), a.arg(2))
            SignalKind.AI_MENTION_TEXT -> s(R.string.signal_ai_mention_detail, a.arg(1), a.arg(2))
            SignalKind.IPTC_AI_SOURCE -> s(R.string.signal_iptc_detail, a.arg(0))
            SignalKind.IPTC_AI_EDITED -> s(R.string.signal_iptc_edited_detail, a.arg(0))
            // Отчёты из прежних версий: у C2PA_PRESENT аргументов не было.
            SignalKind.C2PA_PRESENT -> when {
                a.getOrNull(1) == C2PA_UNREADABLE -> s(R.string.signal_c2pa_unreadable_detail)
                a.arg(0).isBlank() -> s(R.string.signal_c2pa_detail)
                else -> s(R.string.signal_c2pa_detail_generator, a.arg(0))
            }
            SignalKind.C2PA_AI_CREATED -> s(R.string.signal_c2pa_ai_detail)
            SignalKind.C2PA_AI_EDITED -> s(R.string.signal_c2pa_ai_edited_detail)
            SignalKind.GENERATION_PARAMS -> s(R.string.signal_generation_params_detail, a.arg(0))
            SignalKind.MESSENGER_COPY -> s(R.string.signal_messenger_detail, a.arg(0))
            SignalKind.SCREENSHOT -> s(R.string.signal_screenshot_detail)
            SignalKind.HAS_GPS -> s(R.string.signal_gps_detail)
            SignalKind.NO_METADATA -> s(R.string.signal_no_metadata_detail)
            SignalKind.PARTIAL_SCAN -> s(R.string.signal_partial_detail)
            SignalKind.NO_AI_TRACES -> s(R.string.signal_no_ai_detail)
        }
    }

    override fun severityTag(severity: Severity): String = s(
        when (severity) {
            Severity.AI_EVIDENCE -> R.string.severity_ai
            Severity.CAUTION -> R.string.severity_caution
            Severity.INFO -> R.string.severity_info
        },
    )

    override fun formatName(format: ImageFormat): String =
        if (format == ImageFormat.UNKNOWN) s(R.string.format_unknown) else format.label

    override fun text(key: TextKey, vararg args: Any): String = when (key) {
        TextKey.REPORT_TITLE -> s(R.string.report_title)
        TextKey.FILE -> s(R.string.report_file)
        TextKey.FILE_NAME_LINE -> s(R.string.report_file_name, *args)
        TextKey.FILE_FORMAT_LINE -> s(R.string.report_file_format, *args)
        TextKey.FILE_MIME_LINE -> s(R.string.report_file_mime, *args)
        TextKey.FILE_SIZE_LINE -> s(R.string.report_file_size, *args)
        TextKey.FILE_SIZE_UNKNOWN_LINE -> s(R.string.report_file_size_unknown)
        TextKey.FILE_PARTIAL_LINE -> s(R.string.report_file_partial, *args)
        TextKey.AI_ANALYSIS -> s(R.string.report_ai_analysis)
        TextKey.AI_FOUND -> s(R.string.report_ai_found)
        TextKey.AI_NOT_FOUND -> s(R.string.report_ai_not_found)
        TextKey.XMP_IPTC -> s(R.string.report_xmp_iptc)
        TextKey.PNG_TEXT -> s(R.string.report_png_text)
        TextKey.GEOLOCATION -> s(R.string.report_geolocation)
        TextKey.COORDINATES_LINE -> s(R.string.report_coordinates, *args.map { number(it, 6) }.toTypedArray())
        TextKey.ALTITUDE_LINE -> s(R.string.report_altitude, *args.map { number(it, 0) }.toTypedArray())
        TextKey.PDF_TRUNCATED -> s(R.string.report_pdf_truncated, *args)
        TextKey.CATALOG_FILE_SECTION -> s(R.string.catalog_file_section)
        TextKey.CATALOG_COORDINATES_SECTION -> s(R.string.catalog_coordinates_section)
        TextKey.FIELD_NAME -> s(R.string.field_name)
        TextKey.FIELD_FORMAT -> s(R.string.field_format)
        TextKey.FIELD_MIME -> s(R.string.field_mime)
        TextKey.FIELD_SIZE_BYTES -> s(R.string.field_size_bytes)
        TextKey.FIELD_SCANNED_BYTES -> s(R.string.field_scanned_bytes)
        TextKey.FIELD_PARTIAL -> s(R.string.field_partial)
        TextKey.FIELD_LATITUDE -> s(R.string.field_latitude)
        TextKey.FIELD_LONGITUDE -> s(R.string.field_longitude)
        TextKey.FIELD_ALTITUDE_M -> s(R.string.field_altitude_m)
        TextKey.YES -> s(R.string.common_yes)
        TextKey.NO -> s(R.string.common_no)
        TextKey.UNNAMED -> s(R.string.common_unnamed)
        TextKey.UNKNOWN -> s(R.string.common_unknown)
    }

    private fun List<String>.arg(i: Int): String = getOrElse(i) { "" }

    private fun number(value: Any, decimals: Int): String = when (value) {
        is Number -> String.format(Locale.US, "%.${decimals}f", value.toDouble())
        else -> value.toString()
    }

    companion object {
        @StringRes
        fun groupTitleId(group: MetadataGroup): Int = when (group) {
            MetadataGroup.MAIN -> R.string.group_main
            MetadataGroup.CAMERA -> R.string.group_camera
            MetadataGroup.LOCATION -> R.string.group_location
            MetadataGroup.EXIF -> R.string.group_exif
            MetadataGroup.XMP -> R.string.group_xmp
            MetadataGroup.PNG -> R.string.group_png
        }

        private fun exif(tag: String) = ExifReader.KEY_PREFIX + tag
        private fun xmp(property: String) = XmpExtractor.KEY_PREFIX + property

        /** Ключ поля → подпись. Поля без записи здесь показываются по [MetaItem.label]. */
        private val labelIds: Map<String, Int> = mapOf(
            exif(ExifInterface.TAG_MAKE) to R.string.exif_make,
            exif(ExifInterface.TAG_MODEL) to R.string.exif_model,
            exif(ExifInterface.TAG_LENS_MAKE) to R.string.exif_lens_make,
            exif(ExifInterface.TAG_LENS_MODEL) to R.string.exif_lens_model,
            exif(ExifInterface.TAG_BODY_SERIAL_NUMBER) to R.string.exif_body_serial_number,
            exif(ExifInterface.TAG_LENS_SERIAL_NUMBER) to R.string.exif_lens_serial_number,
            exif(ExifInterface.TAG_CAMERA_OWNER_NAME) to R.string.exif_camera_owner_name,
            exif(ExifInterface.TAG_F_NUMBER) to R.string.exif_f_number,
            exif(ExifInterface.TAG_EXPOSURE_TIME) to R.string.exif_exposure_time,
            exif(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) to R.string.exif_photographic_sensitivity,
            exif(ExifInterface.TAG_FOCAL_LENGTH) to R.string.exif_focal_length,
            exif(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM) to R.string.exif_focal_length_in_35mm_film,
            exif(ExifInterface.TAG_EXPOSURE_PROGRAM) to R.string.exif_exposure_program,
            exif(ExifInterface.TAG_EXPOSURE_MODE) to R.string.exif_exposure_mode,
            exif(ExifInterface.TAG_EXPOSURE_BIAS_VALUE) to R.string.exif_exposure_bias_value,
            exif(ExifInterface.TAG_METERING_MODE) to R.string.exif_metering_mode,
            exif(ExifInterface.TAG_FLASH) to R.string.exif_flash,
            exif(ExifInterface.TAG_WHITE_BALANCE) to R.string.exif_white_balance,
            exif(ExifInterface.TAG_LIGHT_SOURCE) to R.string.exif_light_source,
            exif(ExifInterface.TAG_SCENE_CAPTURE_TYPE) to R.string.exif_scene_capture_type,
            exif(ExifInterface.TAG_DIGITAL_ZOOM_RATIO) to R.string.exif_digital_zoom_ratio,
            exif(ExifInterface.TAG_SUBJECT_DISTANCE) to R.string.exif_subject_distance,
            exif(ExifInterface.TAG_BRIGHTNESS_VALUE) to R.string.exif_brightness_value,
            exif(ExifInterface.TAG_MAX_APERTURE_VALUE) to R.string.exif_max_aperture_value,
            exif(ExifInterface.TAG_IMAGE_WIDTH) to R.string.exif_image_width,
            exif(ExifInterface.TAG_IMAGE_LENGTH) to R.string.exif_image_length,
            exif(ExifInterface.TAG_PIXEL_X_DIMENSION) to R.string.exif_pixel_x_dimension,
            exif(ExifInterface.TAG_PIXEL_Y_DIMENSION) to R.string.exif_pixel_y_dimension,
            exif(ExifInterface.TAG_ORIENTATION) to R.string.exif_orientation,
            exif(ExifInterface.TAG_COLOR_SPACE) to R.string.exif_color_space,
            exif(ExifInterface.TAG_X_RESOLUTION) to R.string.exif_x_resolution,
            exif(ExifInterface.TAG_Y_RESOLUTION) to R.string.exif_y_resolution,
            exif(ExifInterface.TAG_RESOLUTION_UNIT) to R.string.exif_resolution_unit,
            exif(ExifInterface.TAG_BITS_PER_SAMPLE) to R.string.exif_bits_per_sample,
            exif(ExifInterface.TAG_COMPRESSION) to R.string.exif_compression,
            exif(ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION) to R.string.exif_photometric_interpretation,
            exif(ExifInterface.TAG_EXIF_VERSION) to R.string.exif_exif_version,
            exif(ExifInterface.TAG_DATETIME_ORIGINAL) to R.string.exif_datetime_original,
            exif(ExifInterface.TAG_DATETIME_DIGITIZED) to R.string.exif_datetime_digitized,
            exif(ExifInterface.TAG_DATETIME) to R.string.exif_datetime,
            exif(ExifInterface.TAG_OFFSET_TIME_ORIGINAL) to R.string.exif_offset_time_original,
            exif(ExifInterface.TAG_OFFSET_TIME) to R.string.exif_offset_time,
            exif(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL) to R.string.exif_subsec_time_original,
            exif(ExifInterface.TAG_GPS_LATITUDE_REF) to R.string.exif_gps_latitude_ref,
            exif(ExifInterface.TAG_GPS_LONGITUDE_REF) to R.string.exif_gps_longitude_ref,
            exif(ExifInterface.TAG_GPS_ALTITUDE) to R.string.exif_gps_altitude,
            exif(ExifInterface.TAG_GPS_DATESTAMP) to R.string.exif_gps_datestamp,
            exif(ExifInterface.TAG_GPS_TIMESTAMP) to R.string.exif_gps_timestamp,
            exif(ExifInterface.TAG_GPS_IMG_DIRECTION) to R.string.exif_gps_img_direction,
            exif(ExifInterface.TAG_GPS_SPEED) to R.string.exif_gps_speed,
            exif(ExifInterface.TAG_GPS_SPEED_REF) to R.string.exif_gps_speed_ref,
            exif(ExifInterface.TAG_GPS_SATELLITES) to R.string.exif_gps_satellites,
            exif(ExifInterface.TAG_GPS_PROCESSING_METHOD) to R.string.exif_gps_processing_method,
            exif(ExifInterface.TAG_SOFTWARE) to R.string.exif_software,
            exif(ExifInterface.TAG_ARTIST) to R.string.exif_artist,
            exif(ExifInterface.TAG_COPYRIGHT) to R.string.exif_copyright,
            exif(ExifInterface.TAG_IMAGE_DESCRIPTION) to R.string.exif_image_description,
            exif(ExifInterface.TAG_USER_COMMENT) to R.string.exif_user_comment,

            xmp(XmpExtractor.KEY_CREATOR_TOOL) to R.string.xmp_creator_tool,
            xmp("xmp:CreateDate") to R.string.xmp_create_date,
            xmp("xmp:ModifyDate") to R.string.xmp_modify_date,
            xmp("dc:title") to R.string.xmp_title,
            xmp("dc:creator") to R.string.xmp_creator,
            xmp("dc:description") to R.string.xmp_description,
            xmp("dc:rights") to R.string.xmp_rights,
            xmp("tiff:Make") to R.string.xmp_make,
            xmp("tiff:Model") to R.string.xmp_model,
            xmp("exif:GPSLatitude") to R.string.xmp_gps_latitude,
            xmp("exif:GPSLongitude") to R.string.xmp_gps_longitude,
            xmp("exif:GPSAltitude") to R.string.xmp_gps_altitude,
            xmp(SignalEngine.KEY_DIGITAL_SOURCE_TYPE) to R.string.xmp_digital_source_type,
            XmpExtractor.KEY_HISTORY_AGENTS to R.string.xmp_history_agents,
        )
    }
}

/** Тексты на языке текущего экрана. Пересоздаются при смене конфигурации (языка). */
@Composable
fun rememberMetaTexts(): MetaTexts {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { ResourceMetaTexts(context) }
}
