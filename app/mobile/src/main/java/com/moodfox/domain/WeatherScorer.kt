package com.moodfox.domain

import com.moodfox.data.local.db.WeatherSnapshot

/**
 * Converts a WeatherSnapshot into a quality score on the -10..+10 scale,
 * matching the mood scale used throughout the app.
 */
object WeatherScorer {

    fun score(snap: WeatherSnapshot): Int {
        var s = 0f

        // Temperature: comfortable 14–24 °C is the sweet spot
        s += when {
            snap.temperatureC < 0f   -> -3f
            snap.temperatureC < 8f   -> -2f
            snap.temperatureC < 14f  -> -1f
            snap.temperatureC <= 24f -> +2f
            snap.temperatureC <= 30f -> +1f
            else                     -> -2f   // very hot
        }

        // Condition string
        val c = snap.condition.lowercase()
        s += when {
            "thunder" in c                                     -> -3f
            "rain"    in c || "drizzle" in c || "shower" in c -> -2f
            "snow"    in c                                     -> -1f
            "fog"     in c || "mist"    in c                   -> -1f
            "overcast" in c                                    -> -1f
            "partly"  in c || "cloud"   in c                   ->  0f
            "clear"   in c || "sunny"   in c                   -> +2f
            else                                               ->  0f
        }

        // High humidity penalty
        if (snap.humidity > 80f) s -= 1f
        if (snap.humidity > 90f) s -= 1f

        return s.toInt().coerceIn(-10, 10)
    }
}
