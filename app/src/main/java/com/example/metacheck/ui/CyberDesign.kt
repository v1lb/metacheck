package com.example.metacheck.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** Native lightweight grid with theme-aware text and accents; no WebView or GPU blur. */
@Composable
fun CyberBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.red < .15f
    val background = scheme.background
    val highlight = scheme.primary
    val purple = scheme.secondary
    val mid = if (dark) Color(0xFF0D1420) else Color(0xFFF4F8FC)
    Box(modifier.fillMaxSize().background(background)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(mid, background, background)))
            drawCircle(
                brush = Brush.radialGradient(listOf(purple.copy(alpha = if (dark) .20f else .065f), Color.Transparent),
                    center = Offset(size.width * .88f, size.height * .11f), radius = size.width * .8f),
                radius = size.width * .8f, center = Offset(size.width * .88f, size.height * .11f),
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(highlight.copy(alpha = if (dark) .10f else .045f), Color.Transparent),
                    center = Offset(size.width * .07f, size.height * .6f), radius = size.width * .86f),
                radius = size.width * .86f, center = Offset(size.width * .07f, size.height * .6f),
            )
            val pitch = 32.dp.toPx()
            val stroke = .7.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(highlight.copy(alpha = if (dark) .07f else .09f), Offset(x, 0f), Offset(x, size.height), stroke)
                x += pitch
            }
            var y = 0f
            while (y < size.height) {
                drawLine(highlight.copy(alpha = if (dark) .07f else .09f), Offset(0f, y), Offset(size.width, y), stroke)
                y += pitch
            }
        }
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) { content() }
    }
}

@Composable
fun GlassPanel(modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary,
               content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(23.dp)
    Box(modifier.clip(shape)
        .background(Brush.linearGradient(listOf(scheme.surface.copy(alpha = .97f),
            scheme.surfaceVariant.copy(alpha = .91f))))
        .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = .53f),
            scheme.outline.copy(alpha = .22f), scheme.outline.copy(alpha = .55f))), shape)) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) { content() }
    }
}

@Composable
fun CyberEyebrow(text: String, modifier: Modifier = Modifier,
                 color: Color = MaterialTheme.colorScheme.primary) {
    LocalizedText(text, modifier = modifier, color = color,
        style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun NeonButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary)) {
        LocalizedText(text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CyberOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                       enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .55f))) {
        LocalizedText(text, color = MaterialTheme.colorScheme.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CyberEyebrow(text)
        Canvas(Modifier.weight(1f).height(1.dp)) {
            drawRect(Brush.horizontalGradient(listOf(accent.copy(alpha = .5f), Color.Transparent)))
        }
    }
}

@Composable
fun MetricPanel(value: String, label: String, modifier: Modifier = Modifier,
                accent: Color = MaterialTheme.colorScheme.primary) {
    GlassPanel(modifier, accent) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            LocalizedText(value, color = accent, style = MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            LocalizedText(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
fun PulseIndicator(color: Color = MaterialTheme.colorScheme.tertiary) {
    val transition = rememberInfiniteTransition(label = "scanner-pulse")
    val opacity by transition.animateFloat(initialValue = .32f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "pulse-opacity")
    Canvas(Modifier.size(14.dp)) {
        drawCircle(color.copy(alpha = opacity * .3f), radius = size.minDimension / 2f)
        drawCircle(color, radius = size.minDimension * .26f)
    }
}

@Composable
fun ScanningBars(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Canvas(modifier) {
        val fractions = listOf(.32f,.53f,.44f,.71f,.38f,.62f,.83f,.46f,.69f,.36f,.89f,.57f,.72f,.42f,.98f,.53f,.81f,.38f,.65f,.93f,.46f,.72f,.34f,.6f,.84f,.48f,.55f)
        val gap = size.width / fractions.size
        fractions.forEachIndexed { index, fraction ->
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(accent.copy(alpha = .92f), secondary.copy(alpha = .72f))),
                topLeft = Offset(index * gap + gap * .18f, size.height * (1f - fraction)),
                size = Size(max(1f, gap * .46f), size.height * fraction),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
            )
        }
    }
}
