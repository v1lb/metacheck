package com.example.metacheck.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
            MetaCheckTheme {
                MainScreen(viewModel)
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
