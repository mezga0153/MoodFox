package com.moodfox.domain

import com.moodfox.data.local.db.MoonPhaseSnapshot

/**
 * Converts a MoonPhaseSnapshot into a quality score on the -10..+10 scale,
 * matching the mood scale used throughout the app.
 */
object MoonPhaseScorer {

    fun score(snap: MoonPhaseSnapshot): Int {
        // Phase-based component (dominant signal)
        val phaseScore = when (snap.phase) {
            "New Moon"         -> -3f
            "Waxing Crescent"  ->  0f
            "First Quarter"    -> +2f
            "Waxing Gibbous"   -> +3f
            "Full Moon"        -> +1f
            "Waning Gibbous"   ->  0f
            "Third Quarter"    -> -1f
            "Waning Crescent"  -> -2f
            else               ->  0f
        }

        // Illumination component: 0% → -1, 50% → 0, 100% → +1
        val illumBonus = (snap.illumination / 100f) * 2f - 1f

        return (phaseScore + illumBonus).toInt().coerceIn(-10, 10)
    }
}
