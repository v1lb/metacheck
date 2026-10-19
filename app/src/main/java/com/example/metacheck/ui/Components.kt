package com.example.metacheck.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.example.metacheck.ui.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.metacheck.domain.model.MetaItem
import java.util.Locale

private const val MAX_VALUE_CHARS = 6000

@Composable
fun SectionCard(title: String, items: List<MetaItem>, initiallyExpanded: Boolean = false) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .48f)),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f))
                Text(if (expanded) "−" else "+", color = MaterialTheme.colorScheme.primary)
            }
            if (!expanded) {
                Text("${items.size} полей  ·  Нажмите, чтобы раскрыть",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .22f))
                    ItemRow(item)
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: MetaItem) {
    val value = if (item.value.length > MAX_VALUE_CHARS) item.value.take(MAX_VALUE_CHARS) + "…" else item.value
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    if (value.length > 42 || value.contains('\n')) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(item.label, style = MaterialTheme.typography.labelMedium, color = labelColor)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(item.label, style = MaterialTheme.typography.labelMedium, color = labelColor,
                modifier = Modifier.weight(.46f))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(.54f))
        }
    }
}

@Composable
fun BannerCard(title: String, body: String, container: Color, content: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, content.copy(alpha = .25f)),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

fun formatSize(bytes: Long): String = when {
    bytes < 0 -> "неизвестно"
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> String.format(Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0))
}
