package com.moodfox.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moodfox.R
import com.moodfox.data.local.PreferencesManager
import com.moodfox.ui.theme.*
import kotlinx.coroutines.launch

private val PAGE_COUNT = 4

@Composable
fun WelcomeScreen(
    preferencesManager: PreferencesManager,
    onFinished: () -> Unit,
) {
    val colors = LocalAppColors.current
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    val complete: () -> Unit = {
        scope.launch {
            preferencesManager.setOnboardingComplete(true)
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        // Skip link — visible on all pages
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
                    0 -> OnboardingPage1()
                    1 -> OnboardingPage2()
                    2 -> OnboardingPage3()
                    3 -> OnboardingPage4(preferencesManager = preferencesManager)
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

@Composable
private fun OnboardingPage1() {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Helper character placeholder
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(colors.cardSurface),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🦊", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 64.sp))
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.onboarding_page1_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_page1_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
            listOf("😭", "😢", "😕", "😐", "🙂", "😊", "🤩").forEachIndexed { i, emoji ->
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

// needed for .sp in onboarding pages
private val Int.sp get() = this.toFloat().sp
private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
