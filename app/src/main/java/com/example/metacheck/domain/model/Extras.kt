package com.example.metacheck.domain.model

data class HistoryEntry(
    val id: String,
    val createdAt: Long,
    val fileName: String,
    val format: ImageFormat,
    val hasAiEvidence: Boolean,
    val hasGps: Boolean,
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

data class CleanResult(val downscaled: Boolean)

/** Сообщение предназначено для показа пользователю и не содержит секретов. */
class ApiException(message: String) : Exception(message)

class TooLargeException : Exception("Изображение слишком большое")
