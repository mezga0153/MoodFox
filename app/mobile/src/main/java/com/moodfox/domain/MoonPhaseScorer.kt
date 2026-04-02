package com.moodfox.domain

import com.moodfox.data.local.db.MoonPhaseSnapshot

/**
 * Converts a MoonPhaseSnapshot into a quality score on the -10..+10 scale,
 * matching the mood scale used throughout the app.
 */
object MoonPhaseScorer {

    fun score(snap: MoonPhaseSnapshot): Int {
        // Maps each phase directly onto the intended -10..+10 mood scale:
        // New Moon (dark) = -10, Full Moon (bright) = +10.
        return when (snap.phase) {
            "New Moon"         -> -10
            "Waxing Crescent"  ->  -5
            "First Quarter"    ->   0
            "Waxing Gibbous"   ->   5
            "Full Moon"        ->  10
            "Waning Gibbous"   ->   5
            "Third Quarter"    ->   0
            "Waning Crescent"  ->  -5
            else               ->   0
        }
    }
}
