package com.example.metacheck.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * Копирование отчёта в буфер обмена с пометкой «чувствительные данные» (аудит, п. 27).
 *
 * В отчёте бывают GPS-координаты, имя автора и серийные номера. Android 13+ показывает
 * скопированный текст во всплывающем превью, а клавиатуры — в подсказках. С пометкой
 * IS_SENSITIVE система скрывает текст в превью (вместо него точки). На Android 12 и ниже
 * флаг работает через то же строковое значение extra, его учитывают некоторые клавиатуры.
 */
internal object SensitiveClipboard {

    private const val EXTRA_IS_SENSITIVE_COMPAT = "android.content.extra.IS_SENSITIVE"

    /** Возвращает true, если система сама показала подтверждение (Android 13+): своё сообщение не нужно. */
    fun copy(context: Context, label: String, text: String): Boolean {
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply {
            val key = if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else EXTRA_IS_SENSITIVE_COMPAT
            putBoolean(key, true)
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        return Build.VERSION.SDK_INT >= 33
    }
}
