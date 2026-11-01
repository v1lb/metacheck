package com.example.metacheck.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.metacheck.ui.LocalizedText as Text
import com.example.metacheck.ui.i18n.localize
import com.example.metacheck.ui.preferences.AppLanguage

/** Версия приложения и ссылки на документы. Политика конфиденциальности должна быть доступна и внутри приложения. */
@Composable
fun AboutPanel() {
    val context = LocalContext.current
    val language = LocalUiLanguage.current
    val english = language == AppLanguage.EN
    var licensesOpen by rememberSaveable { mutableStateOf(false) }
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun openLink(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(context, localize("Не удалось открыть ссылку", language), Toast.LENGTH_SHORT).show()
        }
    }

    GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.secondary) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CyberEyebrow("ABOUT", color = MaterialTheme.colorScheme.secondary)
            Text("О приложении", style = MaterialTheme.typography.titleMedium)
            if (versionName.isNotBlank()) {
                Text(
                    "MetaCheck " + versionName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { openLink(if (english) LegalLinks.PRIVACY_EN else LegalLinks.PRIVACY_RU) }) {
                Text("Политика конфиденциальности")
            }
            TextButton(onClick = { openLink(if (english) LegalLinks.TERMS_EN else LegalLinks.TERMS_RU) }) {
                Text("Условия использования")
            }
            TextButton(onClick = { openLink(LegalLinks.SUPPORT) }) { Text("Поддержка и вопросы") }
            TextButton(onClick = { licensesOpen = true }) { Text("Лицензии открытого кода") }
        }
    }

    if (licensesOpen) AlertDialog(
        onDismissRequest = { licensesOpen = false },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(26.dp),
        title = { Text("Лицензии открытого кода") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(LicenseNotices.TEXT, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { licensesOpen = false }) { Text("Закрыть") } },
    )
}
