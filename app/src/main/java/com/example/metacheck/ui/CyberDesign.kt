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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metacheck.ui.theme.MonoText
import com.example.metacheck.ui.theme.PlexMono
import kotlin.math.max

/*
 * Общие элементы стиля «протокол осмотра». Имена остались от прежнего оформления, чтобы не трогать
 * все экраны: CyberBackdrop — просто фон бумаги, GlassPanel — лист с тонкой рамкой и т. д.
 */

/** Ровный фон без сетки, свечения и градиентов. */
@Composable
fun CyberBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(scheme.background)) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) { content() }
    }
}

/**
 * Лист: плоский блок с тонкой рамкой. [accent] окрашивает рамку, только если это сигнальный цвет
 * (ошибка); прочие акценты прежнего оформления дают нейтральную линию.
 */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary,
               content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(6.dp)
    val strong = accent == scheme.error
    val border = if (strong) BorderStroke(1.5.dp, accent) else BorderStroke(1.dp, scheme.outlineVariant)
    Box(modifier.clip(shape).background(scheme.surfaceVariant).border(border, shape)) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) { content() }
    }
}

/** Служебная подпись: моноширинная, приглушённая. Цвет — только когда он что-то значит. */
@Composable
fun CyberEyebrow(text: String, modifier: Modifier = Modifier,
                 color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val shown = if (color == MaterialTheme.colorScheme.primary) MaterialTheme.colorScheme.onSurfaceVariant else color
    Text(text, modifier = modifier, color = shown, style = MonoText.tag, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Главная кнопка экрана: залита чернилами. */
@Composable
fun NeonButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(onClick = onClick, modifier = modifier.defaultMinSize(minHeight = 52.dp), enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Второстепенная кнопка: рамка чернилами, без заливки. */
@Composable
fun CyberOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                       enabled: Boolean = true, icon: ImageVector? = null) {
    val ink = MaterialTheme.colorScheme.onSurface
    OutlinedButton(onClick = onClick, modifier = modifier.defaultMinSize(minHeight = 48.dp), enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, if (enabled) ink else ink.copy(alpha = .3f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ink)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Заголовок раздела: полужирный текст и линия чернилами во всю ширину под ним. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            if (trailing != null) Text(trailing, style = MonoText.tag, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
    }
}

/** Числовое поле: значение моноширинным, подпись под ним. */
@Composable
fun MetricPanel(value: String, label: String, modifier: Modifier = Modifier,
                accent: Color = MaterialTheme.colorScheme.primary) {
    GlassPanel(modifier) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, color = if (accent == MaterialTheme.colorScheme.primary) MaterialTheme.colorScheme.onSurface else accent,
                style = MonoText.file.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/**
 * Штамп: текст в рамке сигнального цвета, слегка повёрнут, как отметка на бланке.
 * [small] — метка в строке списка (без поворота).
 */
@Composable
fun Stamp(lines: List<String>, color: Color, modifier: Modifier = Modifier, small: Boolean = false) {
    if (small) {
        Box(modifier.border(1.5.dp, color, RoundedCornerShape(3.dp)).padding(horizontal = 7.dp, vertical = 3.dp)) {
            Text(lines.joinToString(" "), color = color, style = MonoText.tag, maxLines = 1)
        }
        return
    }
    Column(
        modifier.rotate(-2f).border(2.5.dp, color, RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        lines.forEachIndexed { index, line ->
            Text(
                line, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = if (index == 0 && lines.size > 1) MonoText.tag.copy(letterSpacing = 1.sp)
                else MonoText.file.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                    lineHeight = MaterialTheme.typography.headlineSmall.lineHeight, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/** Пунктирная рамка «бланка», куда кладут файл. */
fun Modifier.dashedFrame(color: Color, width: Dp = 1.5.dp, radius: Dp = 6.dp): Modifier = drawBehind {
    val stroke = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))),
    )
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

/** Индикатор анализа: штрихи чернилами, как отметки на линейке. */
@Composable
fun ScanningBars(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val transition = rememberInfiniteTransition(label = "scan")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "scan-phase")
    Canvas(modifier) {
        val fractions = listOf(.32f,.53f,.44f,.71f,.38f,.62f,.83f,.46f,.69f,.36f,.89f,.57f,.72f,.42f,.98f,.53f,.81f,.38f,.65f,.93f,.46f,.72f,.34f,.6f,.84f,.48f,.55f)
        val gap = size.width / fractions.size
        val active = (phase * fractions.size).toInt()
        fractions.forEachIndexed { index, fraction ->
            drawRect(
                color = if (index <= active) ink else ink.copy(alpha = .18f),
                topLeft = Offset(index * gap + gap * .3f, size.height * (1f - fraction)),
                size = Size(max(1f, gap * .3f), size.height * fraction),
            )
        }
    }
}

/** Строка «подпись — значение» с тонкой линией снизу; значение моноширинным. */
@Composable
fun FieldRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier.fillMaxWidth()) {
        if (value.length > 34 || value.contains('\n')) {
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MonoText.value, color = valueColor)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(.5f))
                Text(value, style = MonoText.value, color = valueColor, modifier = Modifier.weight(.5f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Для совместимости со старыми вызовами: моноширинное семейство. */
val MonoFamily = PlexMono
