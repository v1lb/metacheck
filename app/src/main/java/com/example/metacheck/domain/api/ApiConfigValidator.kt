package com.example.metacheck.domain.api

import java.net.URI

/** Проверка настроек API. Возвращает текст ошибки для пользователя или null, если всё в порядке. */
object ApiConfigValidator {
    private val headerRegex = Regex("^[A-Za-z0-9-]{1,64}$")
    private val pinRegex = Regex("^[A-Za-z0-9+/]{43}=$")

    fun validateUrl(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return "Укажи адрес API"
        if (s.length > 2048 || s.any { it.isWhitespace() }) return "Некорректный адрес"
        val uri = try {
            URI(s)
        } catch (e: Exception) {
            return "Некорректный адрес"
        }
        if (!"https".equals(uri.scheme, ignoreCase = true)) return "Разрешён только https://"
        if (uri.host.isNullOrBlank()) return "В адресе нет хоста"
        if (uri.userInfo != null) return "Логин и пароль в адресе запрещены, используй заголовок"
        return null
    }

    fun validateHeaderName(raw: String): String? =
        if (headerRegex.matches(raw.trim())) null else "Только латинские буквы, цифры и дефис"

    /** Пустой пин допустим: пиннинг необязателен. */
    fun validatePin(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        return if (pinRegex.matches(s)) null else "Нужен SHA-256 в base64 (44 символа, заканчивается на =)"
    }
}
