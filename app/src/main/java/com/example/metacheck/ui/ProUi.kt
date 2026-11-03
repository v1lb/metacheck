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
import com.example.metacheck.data.license.LicenseKey
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.example.metacheck.R
import com.example.metacheck.domain.metadata.DailyCleanQuota
import com.example.metacheck.ui.text.asString
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
    val entitlement by vm.entitlement.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var keyInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = vm::dismissPaywall,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(26.dp),
        title = { Text(stringResource(R.string.pro_title), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CyberEyebrow(stringResource(R.string.pro_eyebrow), color = MaterialTheme.colorScheme.primary)
                ScanningBars(Modifier.fillMaxWidth().height(38.dp))
                Text(stringResource(R.string.pro_free_features, DailyCleanQuota.FREE_DAILY_LIMIT))
                Text(stringResource(R.string.pro_pro_features))
                Text(stringResource(R.string.pro_one_time),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Поле ключа показываем только если в сборку вшит открытый ключ проверки (LicenseKey).
                // Иначе любой ввод заканчивался бы ошибкой «ключи Pro в этой сборке не включены».
                if (LicenseKey.PUBLIC_KEY_BASE64.isNotBlank()) {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text(stringResource(R.string.pro_key_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { vm.redeem(keyInput) }, enabled = keyInput.isNotBlank()) {
                        Text(stringResource(R.string.pro_key_apply))
                    }
                }
                if (entitlement.purchasePending) {
                    Text(stringResource(R.string.pro_payment_pending),
                        color = MaterialTheme.colorScheme.primary)
                }
                info?.let { Text(it.asString(), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = { context.findActivity()?.let(vm::buy) },
                // Второй платёж поверх ожидающего Play всё равно отклонит.
                enabled = price != null && !entitlement.purchasePending,
            ) {
                Text(
                    when {
                        entitlement.purchasePending -> stringResource(R.string.pro_buy_pending)
                        price != null -> stringResource(R.string.pro_buy_for, price!!)
                        else -> stringResource(R.string.pro_buy_unavailable)
                    },
                )
            }
        },
        dismissButton = { TextButton(onClick = vm::dismissPaywall) { Text(stringResource(R.string.common_close)) } },
    )
}

/** Строка о состоянии Pro для экрана настроек. Дата в формате языка интерфейса, а не системы. */
@Composable
fun proStatusText(e: Entitlement): String = when (e.source) {
    ProSource.NONE -> stringResource(if (e.purchasePending) R.string.pro_status_free_pending else R.string.pro_status_free)
    ProSource.PLAY -> stringResource(R.string.pro_status_play)
    ProSource.LICENSE -> {
        val locale = LocalConfiguration.current.locales[0]
        val expires = e.licenseExpiresAt
        if (expires == null) stringResource(R.string.pro_status_license)
        else stringResource(R.string.pro_status_license_until,
            DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(expires * 1000)))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
