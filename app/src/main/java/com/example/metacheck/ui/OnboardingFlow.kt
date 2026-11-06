package com.example.metacheck.ui

import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.layout.width
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
import com.example.metacheck.ui.preferences.AppLocales
import androidx.compose.ui.res.stringResource
import com.example.metacheck.R
import com.example.metacheck.ui.theme.Paper
import java.util.Locale
import com.example.metacheck.ui.preferences.UiPreferences
import kotlinx.coroutines.launch

/** Full-screen first-run experience; navigation and preferences are independent of image processing. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingFlow(prefs: UiPreferences) {
    val selected by prefs.state.collectAsStateWithLifecycle()
    // Смена языка пересоздаёт Activity (шаг сохраняется в rememberSaveable), поэтому достаточно чтения при композиции.
    val language = AppLocales.current()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun save(appearance: AppAppearance? = null, complete: Boolean? = null) {
        scope.launch {
            failure = !prefs.update(appearance = appearance, completed = complete)
        }
    }

    fun skip() {
        AppLocales.apply(AppLanguage.SYSTEM)
        save(appearance = AppAppearance.SYSTEM, complete = true)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("MetaCheck", color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge)
                }
                Text(stringResource(R.string.onboarding_step, step + 1, STEPS), color = MaterialTheme.colorScheme.primary,
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
                            Text(stringResource(R.string.onboarding_eyebrow_intro), color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium)
                            Text(stringResource(R.string.onboarding_intro_title),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.headlineLarge,
                                textAlign = TextAlign.Center)
                            Text(stringResource(R.string.onboarding_intro_text),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            GlassPanel(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    CyberEyebrow(stringResource(R.string.onboarding_intro_eyebrow_panel))
                                    Text(stringResource(R.string.onboarding_intro_disclaimer),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            NeonButton(stringResource(R.string.onboarding_continue), { step = 1 }, Modifier.fillMaxWidth())
                        }
                        1 -> {
                            Spacer(Modifier.height(14.dp))
                            CyberEyebrow(stringResource(R.string.onboarding_eyebrow_language))
                            Text(stringResource(R.string.onboarding_language_title),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.headlineMedium)
                            Text(stringResource(R.string.onboarding_language_text),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            LanguagePicker(language, { AppLocales.apply(it) })
                            NeonButton(stringResource(R.string.onboarding_continue), { step = 2 }, Modifier.fillMaxWidth())
                            CyberOutlineButton(stringResource(R.string.onboarding_skip), ::skip, Modifier.fillMaxWidth())
                        }
                        else -> {
                            Spacer(Modifier.height(14.dp))
                            CyberEyebrow(stringResource(R.string.onboarding_eyebrow_theme))
                            Text(stringResource(R.string.onboarding_theme_title),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.headlineMedium)
                            Text(stringResource(R.string.onboarding_theme_text),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            ThemePreviewCard(dark = true, selected = selected.appearance == AppAppearance.DARK,
                                title = stringResource(R.string.appearance_theme_dark),
                                subtitle = stringResource(R.string.appearance_theme_dark_sub)) {
                                save(appearance = AppAppearance.DARK)
                            }
                            ThemePreviewCard(dark = false, selected = selected.appearance == AppAppearance.LIGHT,
                                title = stringResource(R.string.appearance_theme_light),
                                subtitle = stringResource(R.string.appearance_theme_light_sub)) {
                                save(appearance = AppAppearance.LIGHT)
                            }
                            NeonButton(stringResource(R.string.onboarding_finish),
                                { save(complete = true) }, Modifier.fillMaxWidth())
                            CyberOutlineButton(stringResource(R.string.onboarding_skip), ::skip, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            if (failure) {
                Text(stringResource(R.string.onboarding_save_failed),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private const val STEPS = 3

/** Знак приложения на первом шаге: штамп «MC» в рамке, как отметка на бланке. */
@Composable
private fun NeonMark() {
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Stamp(listOf("METACHECK", "MC"), MaterialTheme.colorScheme.error)
    }
}

/** A selectable, high-fidelity mini UI. Tapping any part changes the live app theme. */
@Composable
fun ThemePreviewCard(dark: Boolean, selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    val base = if (dark) Paper.Dark else Paper.Light
    val pane = if (dark) Paper.DarkRaised else Paper.LightRaised
    val text = if (dark) Paper.DarkInk else Paper.Ink
    val accent = if (dark) Paper.DarkSignal else Paper.Signal
    val outline = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(6.dp)
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
            Icon(if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            .background(base).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("METACHECK", color = text, style = com.example.metacheck.ui.theme.MonoText.tag)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp))
                    .background(pane).border(1.dp, text.copy(alpha = .25f), RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.CenterStart) {
                    Text("  AI · 0", color = text, style = MaterialTheme.typography.labelLarge)
                }
                Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp))
                    .background(pane).border(1.dp, text.copy(alpha = .25f), RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.CenterStart) {
                    Text("  GPS · ✓", color = text, style = MaterialTheme.typography.labelLarge)
                }
            }
            Box(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(3.dp)).background(text),
                contentAlignment = Alignment.Center) {
                Box(Modifier.width(64.dp).height(4.dp).background(base))
            }
        }
    }
}
