package com.example.metacheck.ui

import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
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
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.example.metacheck.R
import com.example.metacheck.ui.text.ErrorTexts
import com.example.metacheck.ui.text.UiText
import com.example.metacheck.ui.text.asString
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
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
import com.example.metacheck.domain.metadata.DailyCleanQuota
import com.example.metacheck.domain.model.ProSource
import com.example.metacheck.ui.theme.CyberPalette

@Composable
fun SettingsScreen(
    onMessage: (UiText) -> Unit,
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
            CyberEyebrow(stringResource(R.string.settings_eyebrow))
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.settings_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppearanceSettings(uiPreferences, onMessage)
        GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.secondary) {
            Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CyberEyebrow(stringResource(R.string.settings_plan_eyebrow), color = MaterialTheme.colorScheme.secondary)
                Text(stringResource(if (entitlement.isPro) R.string.dash_pro_title else R.string.settings_plan_free), style = MaterialTheme.typography.titleLarge)
                Text(proStatusText(entitlement), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (entitlement.isPro) stringResource(R.string.settings_plan_pro_text)
                    else stringResource(R.string.settings_plan_free_text, DailyCleanQuota.FREE_DAILY_LIMIT),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!entitlement.isPro) NeonButton(stringResource(R.string.settings_pro_features), proVm::openPaywall, Modifier.fillMaxWidth())
                if (entitlement.source == ProSource.LICENSE) CyberOutlineButton(stringResource(R.string.settings_remove_license), proVm::removeLicense)
            }
        }
        val tipPrice by proVm.tipPrice.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { proVm.loadTipPrice() }
        // Кнопка появляется, только когда товар поддержки создан в Play Console и Play ответил ценой.
        tipPrice?.let { shownTipPrice ->
            val tipContext = LocalContext.current
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.tip_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.tip_text), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CyberOutlineButton(stringResource(R.string.tip_button, shownTipPrice),
                        { tipContext.findActivity()?.let(proVm::tip) }, Modifier.fillMaxWidth())
                }
            }
        }
        SectionLabel(stringResource(R.string.settings_section_history))
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SwitchRow(
                    title = stringResource(R.string.settings_save_history),
                    subtitle = stringResource(R.string.settings_save_history_text),
                    checked = settings.saveHistory,
                ) { enabled -> vm.update { it.copy(saveHistory = enabled) } }
                CyberOutlineButton(stringResource(R.string.settings_clear_history), { clearConfirm = true }, Modifier.fillMaxWidth())
            }
        }
        SectionLabel(stringResource(R.string.settings_section_api))
        GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.secondary) {
            Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                SwitchRow(
                    title = stringResource(R.string.settings_api_enabled),
                    subtitle = stringResource(R.string.settings_api_enabled_text),
                    checked = settings.apiEnabled,
                    enabled = entitlement.isPro,
                ) { enabled -> vm.update { it.copy(apiEnabled = enabled) } }
                Row(Modifier.fillMaxWidth().clickable { expandedApi = !expandedApi }.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_api_connection), style = MaterialTheme.typography.titleMedium)
                    Icon(if (expandedApi) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                if (expandedApi) {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it; vm.update { s -> s.copy(apiEndpoint = it) } },
                        label = { Text(stringResource(R.string.settings_api_url)) },
                        singleLine = true, enabled = settings.apiEnabled,
                        isError = endpoint.isNotBlank() && ApiConfigValidator.validateUrl(endpoint) != null,
                        supportingText = { ApiConfigValidator.validateUrl(endpoint)?.let { Text(ErrorTexts.configIssue(it).asString()) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = header,
                        onValueChange = { header = it; vm.update { s -> s.copy(apiHeaderName = it) } },
                        label = { Text(stringResource(R.string.settings_api_header)) },
                        singleLine = true, enabled = settings.apiEnabled,
                        isError = ApiConfigValidator.validateHeaderName(header) != null,
                        supportingText = { ApiConfigValidator.validateHeaderName(header)?.let { Text(ErrorTexts.configIssue(it).asString()) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text(stringResource(if (settings.hasApiKey) R.string.settings_api_key_replace else R.string.settings_api_key)) },
                        singleLine = true, enabled = settings.apiEnabled,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supportingText = { Text(stringResource(R.string.settings_api_key_note)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeonButton(stringResource(R.string.settings_api_key_save), {
                            vm.saveKey(keyInput) { ok ->
                                if (ok) { keyInput = ""; onMessage(UiText.Res(R.string.settings_msg_key_saved)) }
                                else onMessage(UiText.Res(R.string.settings_msg_key_save_failed))
                            }
                        }, Modifier.weight(1f), enabled = settings.apiEnabled && keyInput.isNotBlank())
                        CyberOutlineButton(stringResource(R.string.common_delete), {
                            vm.deleteKey(); keyInput = ""; onMessage(UiText.Res(R.string.settings_msg_key_deleted))
                        }, Modifier.weight(1f), enabled = settings.hasApiKey)
                    }
                    SwitchRow(stringResource(R.string.settings_api_send_gps), stringResource(R.string.settings_api_send_gps_text),
                        settings.apiSendGps, settings.apiEnabled) { enabled -> vm.update { it.copy(apiSendGps = enabled) } }
                    SwitchRow(stringResource(R.string.settings_api_tls13), stringResource(R.string.settings_api_tls13_text),
                        settings.tls13Only, settings.apiEnabled) { enabled -> vm.update { it.copy(tls13Only = enabled) } }
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it; vm.update { s -> s.copy(certPin = it) } },
                        label = { Text(stringResource(R.string.settings_api_pin)) },
                        singleLine = true, enabled = settings.apiEnabled,
                        isError = ApiConfigValidator.validatePin(pin) != null,
                        supportingText = { Text(ApiConfigValidator.validatePin(pin)?.let { ErrorTexts.configIssue(it).asString() } ?: stringResource(R.string.settings_api_pin_note)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        GlassPanel(Modifier.fillMaxWidth(), MaterialTheme.colorScheme.tertiary) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                CyberEyebrow(stringResource(R.string.settings_security_eyebrow), color = MaterialTheme.colorScheme.tertiary)
                Text(stringResource(R.string.settings_security_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_security_text),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AboutPanel()
    }

    if (clearConfirm) AlertDialog(
        onDismissRequest = { clearConfirm = false },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(26.dp),
        title = { Text(stringResource(R.string.settings_clear_title)) },
        text = { Text(stringResource(R.string.settings_clear_text)) },
        confirmButton = { TextButton(onClick = {
            clearConfirm = false; vm.clearHistory { ok ->
                onMessage(UiText.Res(if (ok) R.string.settings_msg_history_cleared else R.string.settings_msg_history_clear_failed))
            }
        }) { Text(stringResource(R.string.common_delete)) } },
        dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text(stringResource(R.string.common_cancel)) } },
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
