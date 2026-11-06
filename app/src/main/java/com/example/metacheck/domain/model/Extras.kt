package com.example.metacheck.domain.model

data class HistoryEntry(
    val id: String,
    val createdAt: Long,
    val fileName: String,
    val format: ImageFormat,
    val hasAiEvidence: Boolean,
    val hasGps: Boolean,
    /** Сколько личных фактов выдаёт файл (место, серийный номер, автор…), как в отчёте. */
    val exposureCount: Int = 0,
)

data class AppSettings(
    val saveHistory: Boolean = true,
    val apiEnabled: Boolean = false,
    val apiEndpoint: String = "",
    val apiHeaderName: String = "Authorization",
    val apiSendGps: Boolean = false,
    val tls13Only: Boolean = false,
    /** SHA-256 отпечаток открытого ключа сертификата в base64 (необязательно). */
    val certPin: String = "",
    val hasApiKey: Boolean = false,
)

data class ApiResult(val statusCode: Int, val body: String)

/** Причина ошибки отправки отчёта. Текст для пользователя подбирает интерфейс по коду. */
enum class ApiError {
    DISABLED, CERTIFICATE, TLS_HANDSHAKE, HOST_NOT_FOUND, TIMEOUT, BAD_SETTINGS, KEY_UNREADABLE, REQUEST_FAILED,
    /** Настройки не прошли проверку, подробности в [ApiException.configIssue]. */
    INVALID_CONFIG,
}

/** Не содержит секретов: только код причины (и проблему настроек, если она есть). */
class ApiException(
    val code: ApiError,
    val configIssue: com.example.metacheck.domain.api.ApiConfigIssue? = null,
) : Exception(code.name)

class TooLargeException : Exception("Image is too large")
