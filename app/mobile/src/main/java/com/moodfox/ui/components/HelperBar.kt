package com.moodfox.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodfox.ui.theme.AppColors

private val REACTIONS: Map<ClosedRange<Int>, List<String>> = mapOf(
    (-10..-7) to listOf("That sounds really rough. 🦊 I'm here.", "Tough day. It's okay to feel this."),
    (-6..-3)  to listOf("A bit low today. That's valid. 🦊", "Not your best stretch — it passes."),
    (-2..2)   to listOf("Calm and steady. 🦊 That's the goal.", "Balanced and grounded. Keep going."),
    (3..6)    to listOf("Feeling good! 🦊 Ride it mindfully.", "Nice — don't forget to note why."),
    (7..10)   to listOf("High energy! 🦊 Savour this moment.", "Great mood — what made today good?"),
)

/**
 * A small helper bar that shows a contextual reaction message after a check-in save.
 */
@Composable
fun HelperBar(
    moodValue: Int,
    visible: Boolean,
    colors: AppColors,
    modifier: Modifier = Modifier,
) {
    val message = remember(moodValue) {
        REACTIONS.entries.firstOrNull { moodValue in it.key }?.value?.random()
            ?: "Logged. 🦊"
    }

    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn() + slideInVertically { it / 2 },
        exit    = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text      = message,
                style     = MaterialTheme.typography.bodyLarge,
                color     = colors.onSurface,
                textAlign = TextAlign.Center,
                fontSize  = 15.sp,
            )
        }
    }
}
