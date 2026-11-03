package com.example.metacheck.domain.api

import java.net.URI

/** Проблема в настройках API. Текст для пользователя подбирает интерфейс по коду. */
enum class ApiConfigIssue {
    URL_EMPTY, URL_INVALID, URL_NOT_HTTPS, URL_NO_HOST, URL_HAS_CREDENTIALS,
    HEADER_INVALID,
    PIN_INVALID,
}

/** Проверка настроек API. Возвращает проблему или null, если всё в порядке. */
object ApiConfigValidator {
    private val headerRegex = Regex("^[A-Za-z0-9-]{1,64}$")
    private val pinRegex = Regex("^[A-Za-z0-9+/]{43}=$")

    fun validateUrl(raw: String): ApiConfigIssue? {
        val s = raw.trim()
        if (s.isEmpty()) return ApiConfigIssue.URL_EMPTY
        if (s.length > 2048 || s.any { it.isWhitespace() }) return ApiConfigIssue.URL_INVALID
        val uri = try {
            URI(s)
        } catch (e: Exception) {
            return ApiConfigIssue.URL_INVALID
        }
        if (!"https".equals(uri.scheme, ignoreCase = true)) return ApiConfigIssue.URL_NOT_HTTPS
        if (uri.host.isNullOrBlank()) return ApiConfigIssue.URL_NO_HOST
        if (uri.userInfo != null) return ApiConfigIssue.URL_HAS_CREDENTIALS
        return null
    }

    fun validateHeaderName(raw: String): ApiConfigIssue? =
        if (headerRegex.matches(raw.trim())) null else ApiConfigIssue.HEADER_INVALID

    /** Пустой пин допустим: пиннинг необязателен. */
    fun validatePin(raw: String): ApiConfigIssue? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        return if (pinRegex.matches(s)) null else ApiConfigIssue.PIN_INVALID
    }
}
