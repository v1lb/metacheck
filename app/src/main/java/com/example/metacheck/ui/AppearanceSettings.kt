package com.example.metacheck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metacheck.ui.preferences.AppAppearance
import com.example.metacheck.ui.preferences.AppLanguage
import com.example.metacheck.ui.preferences.UiPreferences

@Composable
fun AppearanceSettings(prefs: UiPreferences, onMessage: (String) -> Unit) {
    val selected by prefs.state.collectAsStateWithLifecycle()
    val en = LocalUiLanguage.current == AppLanguage.EN
    val scope = rememberCoroutineScope()
    SectionLabel(if (en) "LANGUAGE & APPEARANCE" else "ЯЗЫК И ОФОРМЛЕНИЕ")
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text(if (en) "Interface language" else "Язык интерфейса", color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                CyberOutlineButton(if (en) "System" else "Система", {
                    scope.launch { if (!prefs.update(language = AppLanguage.SYSTEM)) onMessage(if (en) "Could not save" else "Ошибка сохранения") }
                }, Modifier.weight(1f))
                CyberOutlineButton("Русский", {
                    scope.launch { if (!prefs.update(language = AppLanguage.RU)) onMessage(if (en) "Could not save" else "Ошибка сохранения") }
                }, Modifier.weight(1f))
                CyberOutlineButton("English", {
                    scope.launch { if (!prefs.update(language = AppLanguage.EN)) onMessage(if (en) "Could not save" else "Ошибка сохранения") }
                }, Modifier.weight(1f))
            }
            Text(if (en) "Current: ${selected.language.name}" else "Выбрано: ${selected.language.name}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            Text(if (en) "Appearance" else "Тема", color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium)
            ThemePreviewCard(dark = true, selected = selected.appearance == AppAppearance.DARK,
                title = if (en) "Cyber Grid Dark" else "Тёмная Cyber Grid", subtitle = "NEON INTELLIGENCE") {
                scope.launch { if (!prefs.update(appearance = AppAppearance.DARK)) onMessage(if (en) "Could not save" else "Ошибка сохранения") }
            }
            ThemePreviewCard(dark = false, selected = selected.appearance == AppAppearance.LIGHT,
                title = if (en) "Clean Cyber Light" else "Светлая Clean Cyber",
                subtitle = if (en) "Daylight contrast" else "Контраст при дневном освещении") {
                scope.launch { if (!prefs.update(appearance = AppAppearance.LIGHT)) onMessage(if (en) "Could not save" else "Ошибка сохранения") }
            }
            CyberOutlineButton(if (en) "Follow device theme" else "Тема как в системе", {
                scope.launch { if (!prefs.update(appearance = AppAppearance.SYSTEM)) onMessage(if (en) "Could not save" else "Ошибка сохранения") }
            }, Modifier.fillMaxWidth())
        }
    }
}
