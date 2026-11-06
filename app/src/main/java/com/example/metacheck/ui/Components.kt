package com.example.metacheck.ui

import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import com.example.metacheck.ui.theme.MonoText
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.pluralStringResource
import com.example.metacheck.R
import com.example.metacheck.ui.text.rememberMetaTexts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.metacheck.domain.model.MetaItem

private const val MAX_VALUE_CHARS = 6000

/**
 * Раздел метаданных: заголовок с числом полей и линия, под ней строки «подпись — значение».
 * Подписи и значения полей локализуются здесь, по ключам ([MetaItem.key]); значения без ключа — как есть.
 */
@Composable
fun SectionCard(title: String, items: List<MetaItem>, initiallyExpanded: Boolean = false) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(top = 10.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            Text("${items.size}", style = MonoText.tag, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = pluralStringResource(R.plurals.section_fields_collapsed, items.size, items.size),
                tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 6.dp))
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        if (expanded) {
            items.forEach { ItemRow(it) }
        }
    }
}

@Composable
private fun ItemRow(item: MetaItem) {
    val texts = rememberMetaTexts()
    val label = texts.itemLabel(item)
    val full = texts.itemValue(item)
    val value = if (full.length > MAX_VALUE_CHARS) full.take(MAX_VALUE_CHARS) + "…" else full
    FieldRow(label, value)
}

/** Отдельное сообщение: лист с рамкой цвета смысла и заголовком. */
@Composable
fun BannerCard(title: String, body: String, container: Color, content: Color) {
    Column(
        Modifier.fillMaxWidth()
            .border(1.5.dp, content, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = content)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
