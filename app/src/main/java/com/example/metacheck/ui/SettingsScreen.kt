package com.example.metacheck.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import com.example.metacheck.ui.LocalizedText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.metacheck.domain.api.ApiConfigValidator
import com.example.metacheck.domain.model.ProSource
import com.example.metacheck.ui.theme.CyberPalette

@Composable
fun SettingsScreen(
    onMessage: (String) -> Unit,
    uiPreferences: com.example.metacheck.ui.preferences.UiPreferences,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val proVm: ProViewModel = viewModel(factory = ProViewModel.Factory)
    val entitlement by proVm.entitlement.collectAsStateWithLifecycle()
    var endpoint by remember(settings.apiEndpoint) { mutableStateOf(settings.apiEndpoint) }
    var header by remember(settings.apiHeaderName) { mutableStateOf(settings.apiHeaderName) }
    var pin by remember(settings.certPin) { mutableStateOf(settings.certPin) }
    var keyInput by rememberSaveable { mutableStateOf("") }
    var clearConfirm by rememberSaveable { mutableStateOf(false) }
    var expandedApi by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 17.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            CyberEyebrow("CONTROL CENTER / LOCAL FIRST")
            Text("Центр управления", style = MaterialTheme.typography.headlineMedium)
            Text("Приватность, доступ и расширенные возможности", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppearanceSettings(uiPreferences, onMessage)
        GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.secondary) {
            Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CyberEyebrow("PLAN / ACCESS", color = MaterialTheme.colorScheme.secondary)
                Text(if (entitlement.isPro) "METACHECK PRO" else "METACHECK FREE", style = MaterialTheme.typography.titleLarge)
                Text(proStatusText(entitlement), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (entitlement.isPro) "У вас доступен расширенный экспорт и API." else
                    "Базовый анализ и очистка доступны бесплатно. Pro открывает PDF/JSON и до 50 записей в истории.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!entitlement.isPro) NeonButton("Возможности Pro", proVm::openPaywall, Modifier.fillMaxWidth())
                if (entitlement.source == ProSource.LICENSE) CyberOutlineButton("Отключить лицензию", proVm::removeLicense)
            }
        }
        SectionLabel("ЛОКАЛЬНАЯ ИСТОРИЯ")
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SwitchRow(
                    title = "Сохранять отчёты",
                    subtitle = "Шифрование через Android Keystore. Фотографии не копируются. Free: 10, Pro: 50.",
                    checked = settings.saveHistory,
                ) { enabled -> vm.update { it.copy(saveHistory = enabled) } }
                CyberOutlineButton("Удалить историю с телефона", { clearConfirm = true }, Modifier.fillMaxWidth())
            }
        }
        SectionLabel("СОБСТВЕННЫЙ API")
        GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.secondary) {
            Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                SwitchRow(
                    title = "Отправлять JSON-отчёт",
                    subtitle = "Только с подтверждением и только в Pro. Исходное изображение не передаётся.",
                    checked = settings.apiEnabled,
                    enabled = entitlement.isPro,
                ) { enabled -> vm.update { it.copy(apiEnabled = enabled) } }
                Row(Modifier.fillMaxWidth().clickable { expandedApi = !expandedApi }.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Настройки соединения", style = MaterialTheme.typography.titleMedium)
                    Text(if (expandedApi) "−" else "+", color = MaterialTheme.colorScheme.primary)
                }
                if (expandedApi) {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it; vm.update { s -> s.copy(apiEndpoint = it) } },
                        label = { Text("Адрес API (HTTPS)") },
                        singleLine = true, enabled = settings.apiEnabled,
                        isError = endpoint.isNotBlank() && ApiConfigValidator.validateUrl(endpoint) != null,
                        supportingText = { ApiConfigValidator.validateUrl(endpoint)?.let { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = header,
                        onValueChange = { header = it; vm.update { s -> s.copy(apiHeaderName = it) } },
                        label = { Text("Имя заголовка авторизации") },
                        singleLine = true, enabled = settings.apiEnabled,
                        isError = ApiConfigValidator.validateHeaderName(header) != null,
                        supportingText = { ApiConfigValidator.validateHeaderName(header)?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text(if (settings.hasApiKey) "Заменить сохранённый ключ" else "Ключ API") },
                        singleLine = true, enabled = settings.apiEnabled,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supportingText = { Text("Сохраняется зашифрованным; значение нельзя прочитать из настроек.") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeonButton("Сохранить ключ", {
                            if (vm.saveKey(keyInput)) { keyInput = ""; onMessage("Ключ сохранён") }
                            else onMessage("Ошибка сохранения ключа")
                        }, Modifier.weight(1f), enabled = settings.apiEnabled && keyInput.isNotBlank())
                        CyberOutlineButton("Удалить", {
                            vm.deleteKey(); keyInput = ""; onMessage("Ключ удалён")
                        }, Modifier.weight(1f), enabled = settings.hasApiKey)
                    }
                    SwitchRow("Отправлять GPS", "По умолчанию геолокация из JSON исключена.",
                        settings.apiSendGps, settings.apiEnabled) { enabled -> vm.update { it.copy(apiSendGps = enabled) } }
                    SwitchRow("Только TLS 1.3", "Работает с совместимыми серверами на Android 10+.",
                        settings.tls13Only, settings.apiEnabled) { enabled -> vm.update { it.copy(tls13Only = enabled) } }
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it; vm.update { s -> s.copy(certPin = it) } },
                        label = { Text("SHA-256 публичного ключа (base64)") },
                        singleLine = true, enabled = settings.apiEnabled,
                        isError = ApiConfigValidator.validatePin(pin) != null,
                        supportingText = { Text(ApiConfigValidator.validatePin(pin) ?: "Необязательный pin сертификата сервера.") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.tertiary) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                CyberEyebrow("SECURITY STATUS", color = MaterialTheme.colorScheme.tertiary)
                Text("Хранение только на устройстве", style = MaterialTheme.typography.titleMedium)
                Text("Сеть используется только при явно включённом внешнем API или запросах к Google Play для покупки Pro.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (clearConfirm) AlertDialog(
        onDismissRequest = { clearConfirm = false },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(26.dp),
        title = { Text("Удалить все отчёты?") },
        text = { Text("Это действие удалит сохранённые отчёты без возможности восстановления.") },
        confirmButton = { TextButton(onClick = {
            clearConfirm = false; vm.clearHistory(); onMessage("Запрошена очистка истории")
        }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("Отмена") } },
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
