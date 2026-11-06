package com.example.metacheck.ui

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Просьба оценить приложение (Google Play In-App Review). Показывается один раз, после [ASK_AFTER]
 * удачных проверенных очисток, на главном экране: человек уже получил пользу. Google сам ограничивает частоту показа
 * и может не показать окно вовсе, поэтому результат не проверяется и ни на что не влияет.
 */
object ReviewPrompt {
    private const val PREFS = "review_prompt"
    private const val KEY_COUNT = "successful_cleans"
    private const val KEY_ASKED = "asked"
    const val ASK_AFTER = 3

    /** Удачная проверенная очистка: только считаем, окно не показываем. */
    fun recordClean(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return
        prefs.edit().putInt(KEY_COUNT, prefs.getInt(KEY_COUNT, 0) + 1).apply()
    }

    /** Вызывается на главном экране. Один раз за всё время, когда набралось [ASK_AFTER] очисток. */
    fun maybeAsk(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false) || prefs.getInt(KEY_COUNT, 0) < ASK_AFTER) return
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        runCatching {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { request ->
                if (request.isSuccessful && !activity.isFinishing && !activity.isDestroyed) {
                    manager.launchReviewFlow(activity, request.result)
                }
            }
        }
    }
}
