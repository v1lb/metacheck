package com.example.metacheck.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.metacheck.R
import com.example.metacheck.ui.preferences.AppLanguage
import com.example.metacheck.ui.preferences.AppLocales
import java.util.Locale

/**
 * Прокручиваемый список языков в стиле приложения. Высота ограничена, поэтому список можно класть
 * внутрь экрана с verticalScroll: прокручивается сам список, а не весь экран.
 */
@Composable
fun LanguagePicker(selected: AppLanguage, onSelect: (AppLanguage) -> Unit, maxHeight: Dp = 340.dp) {
    val options = AppLanguage.entries
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (options.indexOf(selected) - 1).coerceAtLeast(0))
    val shape = RoundedCornerShape(6.dp)
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).clip(shape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f)), shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .6f)),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(options, key = { it.name }) { option ->
            LanguageRow(option, option == selected) { onSelect(option) }
        }
    }
}

@Composable
private fun LanguageRow(option: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(4.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .border(BorderStroke(if (selected) 2.dp else 1.dp,
                if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = .25f)), shape)
            .background(if (selected) accent.copy(alpha = .10f) else MaterialTheme.colorScheme.surface.copy(alpha = .7f))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(AppLocales.nameRes(option)), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(languageHint(option), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null, tint = accent)
    }
}

/** Под названием: язык устройства для «Как в системе», иначе название языка на текущем языке интерфейса. */
@Composable
private fun languageHint(option: AppLanguage): String {
    val tag = option.tag ?: return stringResource(R.string.onboarding_language_system_detail,
        AppLocales.nativeName(AppLocales.systemLocale()))
    val uiLocale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return Locale.forLanguageTag(tag).getDisplayName(uiLocale).replaceFirstChar { it.titlecase(uiLocale) }
}
