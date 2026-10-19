package com.example.metacheck.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metacheck.ui.preferences.AppAppearance
import com.example.metacheck.ui.preferences.AppLanguage
import com.example.metacheck.ui.preferences.UiPreferences
import com.example.metacheck.ui.theme.CyberPalette
import kotlinx.coroutines.launch

/** Full-screen first-run experience; navigation and preferences are independent of image processing. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingFlow(prefs: UiPreferences) {
    val selected by prefs.state.collectAsStateWithLifecycle()
    val en = LocalUiLanguage.current == AppLanguage.EN
    var step by rememberSaveable { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun save(language: AppLanguage? = null,
             appearance: AppAppearance? = null,
             complete: Boolean? = null) {
        scope.launch {
            failure = !prefs.update(language = language, appearance = appearance, completed = complete)
        }
    }
    CyberBackdrop {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("◈  MetaCheck", color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge)
                Text("0${step + 1} / 03", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge)
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInHorizontally(tween(240)) { it / 5 }) togetherWith
                        (fadeOut(tween(160)) + slideOutHorizontally(tween(180)) { -it / 5 })
                },
                label = "onboarding-steps",
            ) { page ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    when (page) {
                        0 -> {
                            Spacer(Modifier.height(24.dp))
                            NeonMark()
                            Text("METACHECK / IMAGE INTELLIGENCE", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium)
                            Text(if (en) "See beyond\nthe image" else "Увидеть больше,\nчем изображение",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.headlineLarge,
                                textAlign = TextAlign.Center)
                            Text(if (en) "Privacy-first image metadata analysis. Your photos stay on this device."
                                else "Проверка метаданных и технических признаков изображения. Фото остаются на вашем устройстве.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            GlassPanel(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    CyberEyebrow("EXIF  /  XMP  /  AI SIGNALS")
                                    ScanningBars(Modifier.fillMaxWidth().height(66.dp))
                                    Text(if (en) "Metadata analysis is not a pixel-level AI detector and cannot certify authenticity."
                                        else "Анализ метаданных не является нейросетевой проверкой пикселей и не доказывает подлинность.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            NeonButton(if (en) "Continue →" else "Продолжить →", { step = 1 }, Modifier.fillMaxWidth())
                        }
                        1 -> {
                            Spacer(Modifier.height(14.dp))
                            CyberEyebrow("02 / LANGUAGE")
                            Text(if (en) "Choose your language" else "Выберите язык",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.headlineMedium)
                            Text(if (en) "The interface updates immediately. You can change the language later in Settings."
                                else "Интерфейс переключается сразу. Язык можно поменять позже в настройках.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            SelectTile("Русский", "RU / Русский", selected.language == AppLanguage.RU) {
                                save(language = AppLanguage.RU)
                            }
                            SelectTile("English", "EN / English", selected.language == AppLanguage.EN) {
                                save(language = AppLanguage.EN)
                            }
                            NeonButton(if (en) "Continue →" else "Продолжить →", { step = 2 }, Modifier.fillMaxWidth())
                            CyberOutlineButton(if (en) "Skip · use device language and theme" else "Пропустить · системные язык и тема",
                                { save(language = AppLanguage.SYSTEM, appearance = AppAppearance.SYSTEM, complete = true) }, Modifier.fillMaxWidth())
                        }
                        else -> {
                            Spacer(Modifier.height(14.dp))
                            CyberEyebrow("03 / PERSONALIZE")
                            Text(if (en) "Choose a theme" else "Выберите тему",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.headlineMedium)
                            Text(if (en) "Tap a preview to apply it instantly. Both themes keep text readable."
                                else "Нажмите на миниатюру: тема применится сразу. Обе темы рассчитаны на читаемость.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            ThemePreviewCard(dark = true, selected = selected.appearance == AppAppearance.DARK,
                                title = if (en) "Cyber Grid Dark" else "Тёмная · Cyber Grid",
                                subtitle = if (en) "Neon Intelligence" else "Неоновая аналитика") {
                                save(appearance = AppAppearance.DARK)
                            }
                            ThemePreviewCard(dark = false, selected = selected.appearance == AppAppearance.LIGHT,
                                title = if (en) "Clean Cyber Light" else "Светлая · Clean Cyber",
                                subtitle = if (en) "High-contrast daylight" else "Высокий контраст днём") {
                                save(appearance = AppAppearance.LIGHT)
                            }
                            NeonButton(if (en) "Finish setup ✓" else "Завершить ✓",
                                { save(complete = true) }, Modifier.fillMaxWidth())
                            CyberOutlineButton(if (en) "Skip · use device language and theme" else "Пропустить · системные язык и тема",
                                { save(language = AppLanguage.SYSTEM, appearance = AppAppearance.SYSTEM, complete = true) }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            if (failure) {
                Text(if (en) "Could not save preferences. Check available storage and try again."
                    else "Не удалось сохранить настройки. Проверьте свободное место и повторите.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun NeonMark() {
    val transition = rememberInfiniteTransition(label = "logo-glow")
    val pulse by transition.animateFloat(.38f, .9f,
        infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "logo-opacity")
    Box(Modifier.size(128.dp).background(
        Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = pulse * .3f), Color.Transparent)),
        RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
        Text("◈", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineLarge)
    }
}

@Composable
private fun SelectTile(label: String, description: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(20.dp)
    Row(Modifier.fillMaxWidth().clip(shape)
        .border(BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = .5f)), shape)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f))
        .clickable(onClick = onClick).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (selected) "◉" else "○", color = accent,
            style = MaterialTheme.typography.titleLarge)
    }
}

/** A selectable, high-fidelity mini UI. Tapping any part changes the live app theme. */
@Composable
fun ThemePreviewCard(dark: Boolean, selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    val base = if (dark) Color(0xFF0A0C10) else Color(0xFFF4F8FC)
    val pane = if (dark) Color(0xFF172436) else Color.White
    val text = if (dark) Color.White else Color(0xFF0A2233)
    val accent = if (dark) Color(0xFF00F3FF) else Color(0xFF006579)
    val outline = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().clip(shape)
        .border(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else outline.copy(alpha = .55f), shape)
        .clickable(onClick = onClick)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = .86f))
        .padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(title, color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(if (selected) "✓" else "○", color = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .background(base).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("◈  MetaCheck    /    EXIF", color = text, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp))
                    .background(pane).border(1.dp, accent.copy(alpha = .3f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.CenterStart) {
                    Text("  AI · 0", color = text, style = MaterialTheme.typography.labelLarge)
                }
                Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp))
                    .background(pane).border(1.dp, accent.copy(alpha = .3f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.CenterStart) {
                    Text("  GPS · ✓", color = text, style = MaterialTheme.typography.labelLarge)
                }
            }
            Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(5.dp))
                .background(accent.copy(alpha = .65f)))
        }
    }
}
