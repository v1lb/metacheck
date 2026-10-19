package com.example.metacheck.domain.metadata

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/** Nullable attributes mean "leave the existing EXIF tag unchanged". */
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
) {
    val hasChanges: Boolean get() = removeGps || latitude != null || longitude != null ||
        dateTaken != null || dateDigitized != null || make != null || model != null ||
        author != null || copyright != null || description != null || iso != null ||
        aperture != null || exposureSeconds != null
}

object MetadataPatchValidator {
    private val exifDateTime = DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

    fun validate(patch: MetadataPatch): List<String> = buildList {
        if (patch.removeGps && (patch.latitude != null || patch.longitude != null)) {
            add("Нельзя одновременно удалить и заменить координаты")
        }
        if ((patch.latitude == null) != (patch.longitude == null)) {
            add("Широта и долгота должны быть заданы вместе")
        }
        if (patch.latitude != null && (!patch.latitude.isFinite() || patch.latitude !in -90.0..90.0)) {
            add("Широта должна быть от −90 до 90")
        }
        if (patch.longitude != null && (!patch.longitude.isFinite() || patch.longitude !in -180.0..180.0)) {
            add("Долгота должна быть от −180 до 180")
        }
        if (patch.iso != null && patch.iso !in 1..204800) add("ISO должно быть от 1 до 204800")
        if (patch.aperture != null && (!patch.aperture.isFinite() || patch.aperture !in 0.5..128.0)) {
            add("Диафрагма должна быть от f/0.5 до f/128")
        }
        if (patch.exposureSeconds != null && (!patch.exposureSeconds.isFinite() || patch.exposureSeconds !in 0.000001..3600.0)) {
            add("Выдержка должна быть от 1/1000000 до 3600 секунд")
        }
        for ((key, value) in listOf("Производитель" to patch.make, "Модель" to patch.model,
            "Автор" to patch.author, "Copyright" to patch.copyright, "Описание" to patch.description)) {
            if (value != null && (value.length > 512 || value.any { it == '\u0000' || (it.code < 32 && it != '\n' && it != '\t') })) {
                add("$key: недопустимые символы или длина более 512")
            }
        }
        for ((key, value) in listOf("Дата съёмки" to patch.dateTaken, "Дата оцифровки" to patch.dateDigitized)) {
            if (value != null) {
                try {
                    require(value.length == 19)
                    LocalDateTime.parse(value, exifDateTime)
                } catch (_: DateTimeParseException) { add("$key: формат ГГГГ:ММ:ДД ЧЧ:ММ:СС") }
                catch (_: IllegalArgumentException) { add("$key: формат ГГГГ:ММ:ДД ЧЧ:ММ:СС") }
            }
        }
        if (!patch.hasChanges) add("Выберите хотя бы одно изменение")
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
    val errors: List<String> = emptyList(),
    val running: Boolean = false,
)
