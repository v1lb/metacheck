package com.example.metacheck.domain

import java.io.IOException

/**
 * Причина отказа операции, понятная пользователю. Текст подбирает интерфейс по коду
 * (ui/text/ErrorTexts), поэтому домен и data-слой не содержат фраз ни на одном языке.
 */
enum class FailureCode {
    // Источник и цель
    INVALID_URI,
    SAME_AS_SOURCE,
    SAME_AS_BACKUP,
    CANNOT_READ_SOURCE,
    CANNOT_OPEN_OUTPUT,
    NO_SAFE_OVERWRITE,
    CANNOT_CREATE_IN_FOLDER,
    SOURCE_TOO_LARGE,
    /** args: тип, который сообщил провайдер (может быть пустым). */
    FORMAT_UNCONFIRMED,

    // Форматы
    UNSUPPORTED_IMAGE,
    EDIT_JPEG_ONLY,
    OVERWRITE_EDIT_JPEG_ONLY,
    OVERWRITE_STRIP_JPEG_PNG_ONLY,
    WIPE_GPS_JPEG_PNG_ONLY,

    // Обработка
    DECODE_FAILED,
    ENCODE_FAILED,
    OUT_OF_MEMORY,
    EMPTY_RESULT,

    // Проверки результата
    CLEAN_CHECK_FAILED,
    /** args: перечень найденного, см. [HiddenPart]. */
    HIDDEN_DATA_FOUND,
    GPS_NOT_REMOVED,
    GPS_NOT_WRITTEN,
    GPS_MISMATCH,
    /** args: имя тега EXIF. */
    EXIF_FIELD_UNVERIFIED,
    SAVED_FILE_UNVERIFIED,
    EDIT_UNVERIFIED,

    // Перезапись оригинала
    BACKUP_UNVERIFIED,
    REPLACED_FILE_UNVERIFIED,
    RESTORE_UNVERIFIED,
    /** cause: исходная ошибка замены. */
    REPLACE_CANCELLED_RESTORED,
    /** cause: ошибка восстановления. */
    REPLACE_FAILED_RESTORE_FAILED,

    // Пакет
    BATCH_SIZE,

    // История и экспорт
    HISTORY_UNAVAILABLE,

    // Хранилище ключей
    KEYSTORE_UNAVAILABLE,
}

/** Скрытые части JPEG, найденные проверкой. Передаются в [FailureCode.HIDDEN_DATA_FOUND] именами. */
enum class HiddenPart { XMP, IPTC, COMMENT, MPF, TRAILER, EXIF_THUMBNAIL }

/**
 * Ошибка операции с кодом для пользователя. Наследует IOException, чтобы существующая обработка
 * ошибок ввода-вывода работала как раньше. [message] — только код, без текста на каком-либо языке.
 */
class AppFailure(
    val code: FailureCode,
    val args: List<String> = emptyList(),
    cause: Throwable? = null,
) : IOException(code.name + if (args.isEmpty()) "" else args.joinToString(prefix = ": "), cause)

/** Аналог require(), но с кодом для пользователя. */
inline fun ensure(condition: Boolean, code: FailureCode, vararg args: String) {
    if (!condition) throw AppFailure(code, args.toList())
}
