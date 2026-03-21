package com.moodfox.ui.onboarding

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.moodfox.R
import com.moodfox.data.local.PreferencesManager
import com.moodfox.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val PAGE_COUNT = 3

@Composable
fun WelcomeScreen(
    preferencesManager: PreferencesManager,
    onFinished: () -> Unit,
    isReview: Boolean = false,
) {
    val colors = LocalAppColors.current
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    val background = Brush.verticalGradient(
        listOf(colors.primary.copy(alpha = 0.12f), colors.surface),
    )

    val complete: () -> Unit = {
        scope.launch {
            if (!isReview) preferencesManager.setOnboardingComplete(true)
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        // Skip / Done link — visible on all pages
        TextButton(
            onClick = complete,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.button_skip),
                color = colors.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.08f))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> OnboardingPage0(preferencesManager = preferencesManager, scope = scope)
                    1 -> OnboardingPage1()
                    2 -> OnboardingPage4(preferencesManager = preferencesManager)
                }
            }

            // Page dots
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(PAGE_COUNT) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (pagerState.currentPage == index) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index) colors.primary
                                else colors.outline,
                            ),
                    )
                }
            }

            // Next / Get Started button
            Button(
                onClick = {
                    if (pagerState.currentPage < PAGE_COUNT - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        complete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 32.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) {
                Text(
                    text = if (pagerState.currentPage < PAGE_COUNT - 1)
                        stringResource(R.string.button_next)
                    else
                        stringResource(R.string.button_get_started),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingPage0(preferencesManager: PreferencesManager, scope: CoroutineScope) {
    val colors = LocalAppColors.current
    val themePresetName by preferencesManager.themePreset.collectAsState(initial = "PURPLE_DARK")
    val language by preferencesManager.language.collectAsState(initial = "")

    val currentPreset = try { ThemePreset.valueOf(themePresetName) } catch (_: Exception) { ThemePreset.PURPLE_DARK }
    val currentMode = currentPreset.mode

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Fox hero image
        Image(
            painter = painterResource(R.drawable.fox),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "MoodFox",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.onboarding_page0_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(36.dp))

        // Language row — centered
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement   = Arrangement.spacedBy(8.dp),
        ) {
            data class Lang(val tag: String, val labelRes: Int)
            val langs = listOf(
                Lang("en", R.string.language_en),
                Lang("sl", R.string.language_sl),
                Lang("hu", R.string.language_hu),
            )
            langs.forEach { lang ->
                WelcomeToneChip(
                    label    = stringResource(lang.labelRes),
                    selected = language == lang.tag,
                    colors   = colors,
                ) {
                    scope.launch {
                        preferencesManager.setLanguage(lang.tag)
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Dark / Light row — centered
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (currentMode == ThemeMode.DARK) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WelcomeToneChip(
                label    = stringResource(R.string.theme_dark),
                selected = currentMode == ThemeMode.DARK,
                colors   = colors,
            ) {
                val target = ThemePreset.entries.first { it.accentHue == currentPreset.accentHue && it.mode == ThemeMode.DARK }
                scope.launch { preferencesManager.setThemePreset(target.name) }
            }
            WelcomeToneChip(
                label    = stringResource(R.string.theme_light),
                selected = currentMode == ThemeMode.LIGHT,
                colors   = colors,
            ) {
                val target = ThemePreset.entries.first { it.accentHue == currentPreset.accentHue && it.mode == ThemeMode.LIGHT }
                scope.launch { preferencesManager.setThemePreset(target.name) }
            }
        }
    }
}

@Composable
private fun OnboardingPage1() {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.fox),
            contentDescription = null,
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.onboarding_page1_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_page1_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        // Slider info card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardSurface)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "😭  ←  😐  →  🤩",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(6.dp))
            // Gradient track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(colors.tertiary, colors.outline, colors.secondary),
                        )
                    ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "-10", style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
                Text(text = "0",   style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(text = "+10", style = MaterialTheme.typography.labelSmall, color = colors.secondary)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Slide left or right to rate how you feel. Aim to stay between −2 and +2.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Cause selection info card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardSurface)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("😴 Sleep", "🏃 Exercise", "💼 Work").forEach { label ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.primary.copy(alpha = 0.2f))
                            .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.primary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap the causes that influenced your mood. Add your own in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OnboardingPage2() {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "📊", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 64.sp))
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.onboarding_page2_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_page2_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        // Demo bar showing the scale anchors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("😭", "😢", "😕", "😐", "🙂", "😊", "🤩").forEach { emoji ->
                Text(text = emoji, style = MaterialTheme.typography.titleLarge)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("-10", "-5", "-2", "0", "+2", "+5", "+10").forEach { label ->
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OnboardingPage3() {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "🎯", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 64.sp))
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.onboarding_page3_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_page3_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        // Visual band illustration
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.cardSurface),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.2f)
                    .fillMaxHeight(0.5f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accent.copy(alpha = 0.35f)),
            )
            Text(
                text = "−2  ✦  +2",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun OnboardingPage4(preferencesManager: PreferencesManager) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "🎨", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 64.sp))
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.onboarding_page4_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_page4_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        // Color theme swatch grid
        val currentPresetName by preferencesManager.themePreset.collectAsState(initial = "PURPLE_DARK")
        com.moodfox.ui.theme.ThemePreset.entries.chunked(5).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                row.forEach { preset ->
                    val presetColors = buildAppColors(preset.accentHue, preset.mode)
                    val selected = preset.name == currentPresetName
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(presetColors.primary)
                            .then(
                                if (selected) Modifier.padding(3.dp)
                                    .clip(CircleShape)
                                    .background(presetColors.surface)
                                else Modifier
                            )
                            .clickable {
                                scope.launch { preferencesManager.setThemePreset(preset.name) }
                            },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun WelcomeToneChip(label: String, selected: Boolean, colors: AppColors, onClick: () -> Unit) {
    val bg     = if (selected) colors.primary else colors.cardSurface
    val border = if (selected) colors.primary else colors.outline
    val text   = if (selected) (if (colors.isDark) Color.Black.copy(alpha = 0.85f) else Color.White)
                 else colors.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = text)
    }
}

// needed for .sp in onboarding pages
private val Int.sp get() = this.toFloat().sp
private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
