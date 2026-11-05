package com.example.metacheck.ui

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Просьба оценить приложение (Google Play In-App Review). Показывается один раз, после [ASK_AFTER]
 * удачных проверенных очисток: человек уже получил пользу. Google сам ограничивает частоту показа
 * и может не показать окно вовсе, поэтому результат не проверяется и ни на что не влияет.
 */
object ReviewPrompt {
    private const val PREFS = "review_prompt"
    private const val KEY_COUNT = "successful_cleans"
    private const val KEY_ASKED = "asked"
    const val ASK_AFTER = 3

    fun onCleanSucceeded(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return
        val count = prefs.getInt(KEY_COUNT, 0) + 1
        if (count < ASK_AFTER) {
            prefs.edit().putInt(KEY_COUNT, count).apply()
            return
        }
        prefs.edit().putInt(KEY_COUNT, count).putBoolean(KEY_ASKED, true).apply()
        runCatching {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { request ->
                if (request.isSuccessful && !activity.isFinishing) {
                    manager.launchReviewFlow(activity, request.result)
                }
            }
        }
    }
}
