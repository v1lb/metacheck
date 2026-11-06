package com.example.metacheck.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metacheck.R
import com.example.metacheck.ui.preferences.AppAppearance
import com.example.metacheck.ui.preferences.AppLanguage
import com.example.metacheck.ui.preferences.AppLocales
import com.example.metacheck.ui.preferences.UiPreferences
import com.example.metacheck.ui.text.UiText
import kotlinx.coroutines.launch

@Composable
fun AppearanceSettings(prefs: UiPreferences, onMessage: (UiText) -> Unit) {
    val selected by prefs.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Смена языка пересоздаёт Activity, поэтому прочитанного при композиции значения достаточно.
    val language = AppLocales.current()

    fun saveAppearance(appearance: AppAppearance) {
        scope.launch {
            if (!prefs.update(appearance = appearance)) onMessage(UiText.Res(R.string.appearance_save_failed))
        }
    }

    SectionLabel(stringResource(R.string.appearance_section))
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text(stringResource(R.string.appearance_language), color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium)
            LanguagePicker(language, { AppLocales.apply(it) }, maxHeight = 300.dp)
            Text(stringResource(R.string.appearance_language_current, languageSummary(language)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text(stringResource(R.string.appearance_language_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.appearance_theme), color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium)
            ThemePreviewCard(dark = true, selected = selected.appearance == AppAppearance.DARK,
                title = stringResource(R.string.appearance_theme_dark),
                subtitle = stringResource(R.string.appearance_theme_dark_sub)) {
                saveAppearance(AppAppearance.DARK)
            }
            ThemePreviewCard(dark = false, selected = selected.appearance == AppAppearance.LIGHT,
                title = stringResource(R.string.appearance_theme_light),
                subtitle = stringResource(R.string.appearance_theme_light_sub)) {
                saveAppearance(AppAppearance.LIGHT)
            }
            CyberOutlineButton(
                stringResource(if (selected.appearance == AppAppearance.SYSTEM) R.string.appearance_theme_system_selected
                    else R.string.appearance_theme_system),
                { saveAppearance(AppAppearance.SYSTEM) }, Modifier.fillMaxWidth())
        }
    }
}

/** «Русский» или «как в системе (English)». */
@Composable
internal fun languageSummary(language: AppLanguage): String =
    if (language == AppLanguage.SYSTEM) {
        stringResource(R.string.appearance_language_system_value, AppLocales.nativeName(AppLocales.systemLocale()))
    } else {
        stringResource(AppLocales.nameRes(language))
    }
