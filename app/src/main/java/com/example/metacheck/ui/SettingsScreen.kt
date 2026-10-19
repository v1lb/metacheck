package com.example.metacheck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.metacheck.domain.api.ApiConfigValidator
import com.example.metacheck.domain.model.ProSource

@Composable
fun SettingsScreen(
    onMessage: (String) -> Unit,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val proVm: ProViewModel = viewModel(factory = ProViewModel.Factory)
    val entitlement by proVm.entitlement.collectAsStateWithLifecycle()
    var endpoint by remember { mutableStateOf(s.apiEndpoint) }
    var header by remember { mutableStateOf(s.apiHeaderName) }
    var pin by remember { mutableStateOf(s.certPin) }
    var keyInput by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pro", style = MaterialTheme.typography.titleMedium)
        Text(proStatusText(entitlement), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!entitlement.isPro) {
                Button(onClick = { proVm.openPaywall() }) { Text("Открыть Pro") }
            }
            if (entitlement.source == ProSource.LICENSE) {
                OutlinedButton(onClick = { proVm.removeLicense() }) { Text("Удалить ключ Pro") }
            }
        }

        HorizontalDivider()

        Text("История", style = MaterialTheme.typography.titleMedium)
        SwitchRow(
            title = "Сохранять историю отчётов",
            subtitle = "Хранится только на устройстве в зашифрованном виде, копии фото не сохраняются. Бесплатно 10 последних отчётов, в Pro до 50.",
            checked = s.saveHistory,
        ) { v -> vm.update { it.copy(saveHistory = v) } }
        OutlinedButton(onClick = { confirmClear = true }) { Text("Очистить историю") }

        HorizontalDivider()

        Text("Внешний API", style = MaterialTheme.typography.titleMedium)
        SwitchRow(
            title = "Разрешить отправку отчёта",
            subtitle = "Доступно в Pro. Выключено: приложение не выходит в сеть. Отправляется только JSON-отчёт, изображение не уходит никогда.",
            checked = s.apiEnabled,
            enabled = entitlement.isPro,
        ) { v -> vm.update { it.copy(apiEnabled = v) } }

        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it; vm.update { s -> s.copy(apiEndpoint = it) } },
            label = { Text("Адрес API (https://…)") },
            singleLine = true,
            enabled = s.apiEnabled,
            isError = endpoint.isNotBlank() && ApiConfigValidator.validateUrl(endpoint) != null,
            supportingText = {
                if (endpoint.isNotBlank()) ApiConfigValidator.validateUrl(endpoint)?.let { Text(it) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = header,
            onValueChange = { header = it; vm.update { s -> s.copy(apiHeaderName = it) } },
            label = { Text("Заголовок для ключа") },
            singleLine = true,
            enabled = s.apiEnabled,
            isError = ApiConfigValidator.validateHeaderName(header) != null,
            supportingText = { ApiConfigValidator.validateHeaderName(header)?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text(if (s.hasApiKey) "Новый ключ (сохранённый скрыт)" else "Ключ API целиком, например Bearer …") },
            singleLine = true,
            enabled = s.apiEnabled,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = { Text("Хранится зашифрованным в Android Keystore.") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = s.apiEnabled && keyInput.isNotBlank(),
                onClick = {
                    if (vm.saveKey(keyInput)) {
                        keyInput = ""
                        onMessage("Ключ сохранён")
                    } else {
                        onMessage("Не удалось сохранить ключ")
                    }
                },
            ) { Text("Сохранить ключ") }
            OutlinedButton(
                enabled = s.hasApiKey,
                onClick = {
                    vm.deleteKey()
                    onMessage("Ключ удалён")
                },
            ) { Text("Удалить ключ") }
        }
        if (s.hasApiKey) {
            Text("Ключ сохранён.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SwitchRow(
            title = "Отправлять координаты GPS",
            subtitle = "По умолчанию выключено: в отчёте на сервер координат не будет.",
            checked = s.apiSendGps,
            enabled = s.apiEnabled,
        ) { v -> vm.update { it.copy(apiSendGps = v) } }
        SwitchRow(
            title = "Только TLS 1.3",
            subtitle = "Строже, но работает на Android 10+ и только с серверами, которые поддерживают TLS 1.3.",
            checked = s.tls13Only,
            enabled = s.apiEnabled,
        ) { v -> vm.update { it.copy(tls13Only = v) } }
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it; vm.update { s -> s.copy(certPin = it) } },
            label = { Text("Пин сертификата SHA-256 (необязательно)") },
            singleLine = true,
            enabled = s.apiEnabled,
            isError = ApiConfigValidator.validatePin(pin) != null,
            supportingText = {
                Text(ApiConfigValidator.validatePin(pin) ?: "Отпечаток открытого ключа в base64. Смена сертификата на сервере без смены пина сломает доступ.")
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить историю?") },
            text = { Text("Все сохранённые отчёты будут удалены с устройства.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearHistory()
                    onMessage("История очищена")
                }) { Text("Очистить") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
