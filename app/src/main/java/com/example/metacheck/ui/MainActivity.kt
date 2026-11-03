package com.example.metacheck.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.example.metacheck.ui.preferences.StartDestination
import com.example.metacheck.ui.preferences.UiPreferences
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.metacheck.ui.preferences.AppLocales
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metacheck.MetaCheckApplication
import com.example.metacheck.ui.preferences.UiSelection
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import com.example.metacheck.ui.theme.MetaCheckTheme

/**
 * AppCompatActivity, а не ComponentActivity: только так AppCompat применяет язык приложения
 * на Android 8–12 (AppCompatDelegate.setApplicationLocales). Тема в res/values — AppCompat.
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uiPrefs = (application as MetaCheckApplication).container.uiPreferences
        holdFirstFrameUntilLoaded(uiPrefs)
        migrateLegacyLanguage(uiPrefs)
        // Повторно не анализируем после поворота экрана: состояние уже в ViewModel.
        if (savedInstanceState == null) handleSharedImage(intent)
        setContent {
            val selection by uiPrefs.state.collectAsStateWithLifecycle()
            val loaded by uiPrefs.loaded.collectAsStateWithLifecycle()
            val dark = UiSelection.effectiveDark(selection.appearance, isSystemInDarkTheme())
            MetaCheckTheme(darkTheme = dark) {
                when (UiSelection.startDestination(loaded, selection.completed)) {
                    // Пустой фон, а не онбординг: виден, только если DataStore читается дольше MAX_SPLASH_MS.
                    StartDestination.LOADING ->
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    StartDestination.ONBOARDING -> OnboardingFlow(uiPrefs)
                    StartDestination.MAIN -> MainScreen(viewModel, uiPreferences = uiPrefs)
                }
            }
        }
    }

    /**
     * Держим системный splash (Android 12+) или фон окна (Android 8–11), пока настройки не прочитаны.
     * Документированный способ через OnPreDrawListener, без новой зависимости.
     * Ограничение по времени, чтобы зависший диск не оставил пользователя на splash навсегда.
     */
    private fun holdFirstFrameUntilLoaded(uiPrefs: UiPreferences) {
        if (uiPrefs.loaded.value) return
        val startedAt = SystemClock.uptimeMillis()
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val ready = uiPrefs.loaded.value || SystemClock.uptimeMillis() - startedAt > MAX_SPLASH_MS
                if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                return ready
            }
        })
    }

    /**
     * В прежних версиях язык хранился в DataStore и применялся только внутри Compose.
     * Переносим явный выбор (RU/EN) в AppCompat один раз, затем удаляем старое значение.
     * Если язык уже задан в системных настройках, он важнее: старое значение просто удаляется.
     */
    private fun migrateLegacyLanguage(uiPrefs: UiPreferences) {
        lifecycleScope.launch {
            uiPrefs.loaded.first { it }
            val legacy = uiPrefs.state.value.legacyLanguage ?: return@launch
            val target = UiSelection.legacyLanguageToApply(legacy, AppLocales.isUnset())
            // Сначала удаляем (в scope UiPreferences): apply() пересоздаст Activity и отменит этот scope.
            uiPrefs.clearLegacyLanguage()
            if (target != null) AppLocales.apply(target)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedImage(intent)
    }

    /** Приём «Поделиться». Тип проверяем сами, а схему URI (только content://) проверяет репозиторий. */
    private fun handleSharedImage(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        viewModel.onImagePicked(uri)
    }

    private companion object {
        const val MAX_SPLASH_MS = 1_500L
    }
}
