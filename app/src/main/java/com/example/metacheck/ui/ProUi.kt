package com.example.metacheck.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
        title = { Text("MetaCheck Pro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Анализ, копирование и очистка метаданных остаются бесплатными. Pro добавляет:\n" +
                        "• экспорт отчётов в PDF и JSON\n" +
                        "• историю до 50 отчётов (бесплатно 10)\n" +
                        "• отправку отчёта в твой API\n\n" +
                        "Оплата разовая, без подписки.",
                )
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
            Button(onClick = { context.findActivity()?.let(vm::buy) }) {
                Text(if (price != null) "Купить за $price" else "Купить")
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
