package com.moodfox.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.moodfox.R
import com.moodfox.data.local.db.CauseCategory

// Maps default category emoji → string resource ID so names are localised.
// Custom (user-added) categories fall back to cat.name.
private val EMOJI_TO_RES: Map<String, Int> = mapOf(
    "💼" to R.string.cause_work,
    "🏠" to R.string.cause_family,
    "💑" to R.string.cause_relationship,
    "👥" to R.string.cause_friends,
    "😴" to R.string.cause_sleep,
    "🏥" to R.string.cause_health,
    "🏃" to R.string.cause_exercise,
    "🍎" to R.string.cause_food,
    "🌤" to R.string.cause_weather,
    "💶" to R.string.cause_money,
    "🔄" to R.string.cause_routine,
    "😰" to R.string.cause_stress,
    "🏆" to R.string.cause_achievement,
    "🛋" to R.string.cause_rest,
    "🤷" to R.string.cause_nothing,
    "➕" to R.string.cause_other,
)

@Composable
fun localizedCauseName(cat: CauseCategory): String {
    val resId = EMOJI_TO_RES[cat.emoji] ?: return cat.name
    return stringResource(resId)
}
