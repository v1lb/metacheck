package com.example.metacheck.domain.metadata

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/**
 * Nullable attributes mean "leave the existing EXIF tag unchanged".
 * Serializable: патч хранится в SavedStateHandle, пока открыт системный диалог сохранения.
 */
data class MetadataPatch(
    val removeGps: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val dateTaken: String? = null,
    val dateDigitized: String? = null,
    val make: String? = null,
    val model: String? = null,
    val author: String? = null,
    val copyright: String? = null,
    val description: String? = null,
    val iso: Int? = null,
    val aperture: Double? = null,
    val exposureSeconds: Double? = null,
) : java.io.Serializable {
    val hasChanges: Boolean get() = removeGps || latitude != null || longitude != null ||
        dateTaken != null || dateDigitized != null || make != null || model != null ||
        author != null || copyright != null || description != null || iso != null ||
        aperture != null || exposureSeconds != null

    private companion object {
        private const val serialVersionUID = 1L
    }
}

/** Поле патча, к которому относится проблема. */
enum class PatchField { MAKE, MODEL, AUTHOR, COPYRIGHT, DESCRIPTION, DATE_TAKEN, DATE_DIGITIZED }

/** Проблема в патче. Текст для пользователя подбирает интерфейс по коду (и полю, если оно есть). */
data class PatchIssue(val code: Code, val field: PatchField? = null) {
    enum class Code {
        GPS_REMOVE_AND_SET, GPS_INCOMPLETE, LATITUDE_RANGE, LONGITUDE_RANGE,
        ISO_RANGE, APERTURE_RANGE, EXPOSURE_RANGE,
        /** Недопустимые символы или длина больше 512; [field] указывает поле. */
        TEXT_INVALID,
        /** Формат даты ГГГГ:ММ:ДД ЧЧ:ММ:СС; [field] указывает поле. */
        DATE_FORMAT,
        NO_CHANGES,
    }
}

/** Патч не прошёл проверку. */
class InvalidPatchException(val issues: List<PatchIssue>) : IllegalArgumentException(issues.joinToString { it.code.name })

object MetadataPatchValidator {
    private val exifDateTime = DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

    fun validate(patch: MetadataPatch): List<PatchIssue> = buildList {
        if (patch.removeGps && (patch.latitude != null || patch.longitude != null)) {
            add(PatchIssue(PatchIssue.Code.GPS_REMOVE_AND_SET))
        }
        if ((patch.latitude == null) != (patch.longitude == null)) {
            add(PatchIssue(PatchIssue.Code.GPS_INCOMPLETE))
        }
        if (patch.latitude != null && (!patch.latitude.isFinite() || patch.latitude !in -90.0..90.0)) {
            add(PatchIssue(PatchIssue.Code.LATITUDE_RANGE))
        }
        if (patch.longitude != null && (!patch.longitude.isFinite() || patch.longitude !in -180.0..180.0)) {
            add(PatchIssue(PatchIssue.Code.LONGITUDE_RANGE))
        }
        if (patch.iso != null && patch.iso !in 1..204800) add(PatchIssue(PatchIssue.Code.ISO_RANGE))
        if (patch.aperture != null && (!patch.aperture.isFinite() || patch.aperture !in 0.5..128.0)) {
            add(PatchIssue(PatchIssue.Code.APERTURE_RANGE))
        }
        if (patch.exposureSeconds != null && (!patch.exposureSeconds.isFinite() || patch.exposureSeconds !in 0.000001..3600.0)) {
            add(PatchIssue(PatchIssue.Code.EXPOSURE_RANGE))
        }
        for ((field, value) in listOf(PatchField.MAKE to patch.make, PatchField.MODEL to patch.model,
            PatchField.AUTHOR to patch.author, PatchField.COPYRIGHT to patch.copyright, PatchField.DESCRIPTION to patch.description)) {
            if (value != null && (value.length > 512 || value.any { it == '\u0000' || (it.code < 32 && it != '\n' && it != '\t') })) {
                add(PatchIssue(PatchIssue.Code.TEXT_INVALID, field))
            }
        }
        for ((field, value) in listOf(PatchField.DATE_TAKEN to patch.dateTaken, PatchField.DATE_DIGITIZED to patch.dateDigitized)) {
            if (value != null) {
                try {
                    require(value.length == 19)
                    LocalDateTime.parse(value, exifDateTime)
                } catch (_: DateTimeParseException) { add(PatchIssue(PatchIssue.Code.DATE_FORMAT, field)) }
                catch (_: IllegalArgumentException) { add(PatchIssue(PatchIssue.Code.DATE_FORMAT, field)) }
            }
        }
        if (!patch.hasChanges) add(PatchIssue(PatchIssue.Code.NO_CHANGES))
    }
}

data class MetadataWriteResult(
    val bytesWritten: Long,
    val downscaled: Boolean,
    val format: String,
    val verified: Boolean,
)

data class BatchProgress(
    val completed: Int = 0,
    val total: Int = 0,
    val current: String = "",
    val succeeded: Int = 0,
    val failed: Int = 0,
    val errors: List<BatchFileError> = emptyList(),
    val running: Boolean = false,
)

/** Ошибка одного файла пакета. Текст ошибки локализует интерфейс по [error]. */
data class BatchFileError(val file: String, val error: Throwable)
