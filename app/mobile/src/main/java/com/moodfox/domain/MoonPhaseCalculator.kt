package com.moodfox.domain

import com.moodfox.data.local.db.MoonPhaseSnapshot
import java.time.Instant

/**
 * Pure astronomical calculation of moon phase from a timestamp.
 * Uses the synodic month (~29.53 days) and a known new-moon epoch.
 * No network, no permissions required.
 */
object MoonPhaseCalculator {

    private const val SYNODIC_MONTH = 29.53058770576 // days
    // Known new moon: 6 Jan 2000 18:14 UTC
    private const val KNOWN_NEW_MOON_MS = 947182440000L

    /**
     * Compute the moon phase for the given instant (defaults to now).
     */
    fun compute(timestamp: Long = System.currentTimeMillis()): MoonPhaseSnapshot {
        val daysSinceNew = (timestamp - KNOWN_NEW_MOON_MS) / (24.0 * 60 * 60 * 1000)
        val age = (daysSinceNew % SYNODIC_MONTH).let { if (it < 0) it + SYNODIC_MONTH else it }

        val fraction = age / SYNODIC_MONTH          // 0.0 = new, 0.5 = full
        val illumination = ((1 - kotlin.math.cos(2 * Math.PI * fraction)) / 2 * 100).toFloat()

        val phase = when {
            fraction < 0.0625  -> "New Moon"
            fraction < 0.1875  -> "Waxing Crescent"
            fraction < 0.3125  -> "First Quarter"
            fraction < 0.4375  -> "Waxing Gibbous"
            fraction < 0.5625  -> "Full Moon"
            fraction < 0.6875  -> "Waning Gibbous"
            fraction < 0.8125  -> "Third Quarter"
            fraction < 0.9375  -> "Waning Crescent"
            else               -> "New Moon"
        }

        return MoonPhaseSnapshot(
            timestamp    = timestamp,
            phase        = phase,
            illumination = illumination,
            age          = age.toFloat(),
        )
    }

    fun phaseEmoji(phase: String): String = when (phase) {
        "New Moon"         -> "🌑"
        "Waxing Crescent"  -> "🌒"
        "First Quarter"    -> "🌓"
        "Waxing Gibbous"   -> "🌔"
        "Full Moon"        -> "🌕"
        "Waning Gibbous"   -> "🌖"
        "Third Quarter"    -> "🌗"
        "Waning Crescent"  -> "🌘"
        else               -> "🌙"
    }
}
