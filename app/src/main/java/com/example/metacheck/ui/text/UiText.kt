package com.example.metacheck.ui.text

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource

/**
 * Текст для показа пользователю, который ViewModel может отдать, не зная языка интерфейса.
 * Превращается в строку только в UI ([asString]), поэтому после смены языка сообщение
 * показывается на новом языке.
 */
sealed interface UiText {
    /** Строковый ресурс. Аргументы могут быть сами [UiText]: они разворачиваются рекурсивно. */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText {
        constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())
    }

    /** Текст, который не переводится: имя файла, ответ сервера, значение метаданных. */
    data class Plain(val text: String) : UiText

    /** Несколько частей через разделитель (например, «причина; причина»). */
    data class Joined(val parts: List<UiText>, val separator: String = " ") : UiText
}

fun UiText.asString(context: Context): String = when (this) {
    is UiText.Plain -> text
    is UiText.Joined -> parts.joinToString(separator) { it.asString(context) }
    is UiText.Res -> if (args.isEmpty()) {
        context.getString(id)
    } else {
        context.getString(id, *args.map { if (it is UiText) it.asString(context) else it }.toTypedArray())
    }
}

/** Через stringResource: строка обновляется при смене конфигурации (языка) без пересоздания. */
@Composable
@ReadOnlyComposable
fun UiText.asString(): String = when (this) {
    is UiText.Plain -> text
    is UiText.Joined -> parts.map { it.asString() }.joinToString(separator)
    is UiText.Res -> if (args.isEmpty()) {
        stringResource(id)
    } else {
        stringResource(id, *args.map { if (it is UiText) it.asString() else it }.toTypedArray())
    }
}
