package com.example.metacheck.domain.metadata

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Сдвиг даты EXIF вида «2024:06:15 19:42:10». */
object DateShift {
    /** Больше года сдвигать незачем: это уже не часовой пояс, а ошибка ввода. */
    const val MAX_SECONDS = 366L * 24 * 3600

    private val format = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /** null, если дата пустая или нестандартная (например, «0000:00:00 00:00:00»). */
    fun shift(value: String, seconds: Long): String? = try {
        val trimmed = value.trim().take(19)
        if (trimmed.length != 19) null else LocalDateTime.parse(trimmed, format).plusSeconds(seconds).format(format)
    } catch (_: DateTimeParseException) {
        null
    }

    /** Сдвиг из полей формы: знак, часы, минуты. null, если ввод неверный. */
    fun seconds(negative: Boolean, hours: String, minutes: String): Long? {
        val h = hours.trim().ifEmpty { "0" }.toLongOrNull() ?: return null
        val m = minutes.trim().ifEmpty { "0" }.toLongOrNull() ?: return null
        if (h < 0 || m < 0 || m > 59) return null
        val total = h * 3600 + m * 60
        if (total == 0L || total > MAX_SECONDS) return null
        return if (negative) -total else total
    }
}
