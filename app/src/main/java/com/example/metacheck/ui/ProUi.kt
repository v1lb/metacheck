package com.example.metacheck.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.example.metacheck.ui.LocalizedText as Text
import androidx.compose.material3.TextButton
import com.example.metacheck.ui.theme.CyberPalette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metacheck.domain.model.Entitlement
import com.example.metacheck.domain.model.ProSource
import java.text.DateFormat
import java.util.Date

@Composable
fun PaywallDialog(vm: ProViewModel) {
    val price by vm.price.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var keyInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = vm::dismissPaywall,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(26.dp),
        title = { Text("MetaCheck Pro", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CyberEyebrow("FREE  →  PRO", color = MaterialTheme.colorScheme.primary)
                ScanningBars(Modifier.fillMaxWidth().height(38.dp))
                Text("Free: анализ и GPS Privacy Shield, просмотр EXIF, 75 обработок фото в сутки (UTC), 10 отчётов в истории.")
                Text("Pro: безлимитная очистка, пакетная обработка до 50 фото, PDF/JSON, 50 отчётов, API.")
                Text("Разовая покупка через Google Play, без подписки. Цена показывается только если доступен товар pro_lifetime в Play Console.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Ключ Pro, если он у тебя есть") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { vm.redeem(keyInput) }, enabled = keyInput.isNotBlank()) {
                    Text("Применить ключ")
                }
                info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = { context.findActivity()?.let(vm::buy) }, enabled = price != null) {
                Text(if (price != null) "Купить за $price" else "Покупка недоступна")
            }
        },
        dismissButton = { TextButton(onClick = vm::dismissPaywall) { Text("Закрыть") } },
    )
}

/** Строка о состоянии Pro для экрана настроек. */
fun proStatusText(e: Entitlement): String = when (e.source) {
    ProSource.NONE -> "Бесплатная версия"
    ProSource.PLAY -> "Pro активен (покупка в Google Play)"
    ProSource.LICENSE -> {
        val until = e.licenseExpiresAt?.let { " до " + DateFormat.getDateInstance().format(Date(it * 1000)) } ?: ""
        "Pro активен по ключу$until"
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
