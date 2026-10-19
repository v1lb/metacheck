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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.metacheck.ui.theme.CyberPalette
import kotlin.math.max

/** Native Compose grid: no WebView or network, fixed rendering cost independent of history size. */
@Composable
fun CyberBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(CyberPalette.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0D1420), CyberPalette.Base, CyberPalette.Black)))
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(CyberPalette.Purple.copy(alpha = .22f), Color.Transparent),
                    center = Offset(size.width * .87f, size.height * .10f),
                    radius = size.width * .78f,
                ),
                radius = size.width * .78f,
                center = Offset(size.width * .87f, size.height * .10f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(CyberPalette.Cyan.copy(alpha = .12f), Color.Transparent),
                    center = Offset(size.width * .07f, size.height * .56f),
                    radius = size.width * .85f,
                ),
                radius = size.width * .85f,
                center = Offset(size.width * .07f, size.height * .56f),
            )
            val pitch = 30.dp.toPx()
            val stroke = 0.7.dp.toPx()
            var x = 0f
            while (x <= size.width) {
                drawLine(CyberPalette.Cyan.copy(alpha = .065f), Offset(x, 0f), Offset(x, size.height), stroke)
                x += pitch
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(CyberPalette.Cyan.copy(alpha = .065f), Offset(0f, y), Offset(size.width, y), stroke)
                y += pitch
            }
        }
        content()
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    accent: Color = CyberPalette.Cyan,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(23.dp)
    Box(
        modifier = modifier.clip(shape)
            .background(Brush.linearGradient(listOf(CyberPalette.GlassLight, CyberPalette.Glass)))
            .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = .46f), Color.White.copy(alpha = .06f), CyberPalette.Line)), shape)
    ) { content() }
}

@Composable
fun CyberEyebrow(text: String, modifier: Modifier = Modifier, color: Color = CyberPalette.Cyan) {
    Text(text, modifier = modifier, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun NeonButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, modifier = modifier, enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CyberPalette.Cyan, contentColor = Color(0xFF06121A)),
    ) {
        Text(text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CyberOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick, modifier = modifier, enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CyberPalette.Cyan.copy(alpha = .38f)),
    ) { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CyberEyebrow(text)
        Canvas(Modifier.weight(1f).height(1.dp)) {
            drawRect(Brush.horizontalGradient(listOf(CyberPalette.Cyan.copy(alpha = .35f), Color.Transparent)))
        }
    }
}

@Composable
fun MetricPanel(value: String, label: String, modifier: Modifier = Modifier, accent: Color = CyberPalette.Cyan) {
    GlassPanel(modifier = modifier, accent = accent) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, color = accent, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, color = CyberPalette.Muted, maxLines = 2)
        }
    }
}

@Composable
fun PulseIndicator(color: Color = CyberPalette.Green) {
    val transition = rememberInfiniteTransition(label = "scanner-pulse")
    val opacity by transition.animateFloat(
        initialValue = .30f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1500), repeatMode = RepeatMode.Reverse),
        label = "pulse-opacity",
    )
    Canvas(Modifier.size(14.dp)) {
        drawCircle(color.copy(alpha = opacity * .25f), radius = size.minDimension / 2f)
        drawCircle(color, radius = size.minDimension * .25f)
    }
}

@Composable
fun ScanningBars(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val total = 27
        val gap = size.width / total
        val fractions = listOf(.32f,.53f,.44f,.71f,.38f,.62f,.83f,.46f,.69f,.36f,.89f,.57f,.72f,.42f,.98f,.53f,.81f,.38f,.65f,.93f,.46f,.72f,.34f,.6f,.84f,.48f,.55f)
        fractions.forEachIndexed { index, fraction ->
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(CyberPalette.Cyan.copy(alpha = .88f), CyberPalette.Purple.copy(alpha = .55f))),
                topLeft = Offset(index * gap + gap * .18f, size.height * (1 - fraction)),
                size = Size(max(1f, gap * .46f), size.height * fraction),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
            )
        }
    }
}
