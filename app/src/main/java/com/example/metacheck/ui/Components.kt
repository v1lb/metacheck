package com.example.metacheck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.metacheck.domain.model.MetaItem
import java.util.Locale

private const val MAX_VALUE_CHARS = 6000

@Composable
fun SectionCard(title: String, items: List<MetaItem>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            items.forEach { ItemRow(it) }
        }
    }
}

@Composable
private fun ItemRow(item: MetaItem) {
    val value = if (item.value.length > MAX_VALUE_CHARS) item.value.take(MAX_VALUE_CHARS) + "…" else item.value
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    if (value.length > 60 || value.contains('\n')) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.label, style = MaterialTheme.typography.bodySmall, color = labelColor)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                item.label,
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
                modifier = Modifier.weight(0.4f),
            )
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
        }
    }
}

@Composable
fun BannerCard(title: String, body: String, container: Color, content: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
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
