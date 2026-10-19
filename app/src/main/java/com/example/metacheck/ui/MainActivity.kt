package com.example.metacheck.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metacheck.ui.preferences.UiPreferences
import com.example.metacheck.ui.preferences.UiSelection
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import com.example.metacheck.ui.theme.MetaCheckTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Повторно не анализируем после поворота экрана: состояние уже в ViewModel.
        if (savedInstanceState == null) handleSharedImage(intent)
        setContent {
            val uiPrefs = remember { UiPreferences(applicationContext) }
            val selection by uiPrefs.state.collectAsStateWithLifecycle()
            val config = LocalConfiguration.current
            val systemLanguage = config.locales[0]?.language ?: "en"
            val language = UiSelection.effectiveLanguage(selection.language, systemLanguage)
            val dark = UiSelection.effectiveDark(selection.appearance, isSystemInDarkTheme())
            CompositionLocalProvider(LocalUiLanguage provides language) {
                MetaCheckTheme(darkTheme = dark) {
                    if (!selection.completed) {
                        OnboardingFlow(uiPrefs)
                    } else {
                        MainScreen(viewModel, uiPreferences = uiPrefs)
                    }
                }
            }
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
}
