package com.moodfox.data.local

import com.moodfox.data.local.db.CauseCategoryDao
import com.moodfox.data.local.db.MoodEntry
import com.moodfox.data.local.db.MoodEntryDao
import com.moodfox.data.local.db.MoonPhaseSnapshotDao
import com.moodfox.data.local.db.WeatherSnapshot
import com.moodfox.data.local.db.WeatherSnapshotDao
import com.moodfox.domain.MoonPhaseCalculator
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

private val conditions = listOf("Clear", "Cloudy", "Overcast", "Partly cloudy", "Rainy", "Drizzle", "Sunny", "Foggy")
private val cities     = listOf("Ljubljana", "Maribor", "Koper", "Celje", "Kranj")
private val sampleNotes = listOf(
    "Feeling pretty good today.",
    "Bit tired but okay.",
    "Stressed about work.",
    "Had a great workout.",
    "Didn't sleep well.",
    "Nice dinner with family.",
    "Really productive day.",
    "Overwhelmed with tasks.",
    "Relaxing evening.",
    "Feeling anxious for no reason.",
    null, null, null, null, null, // most entries have no note
)

suspend fun seedDummyData(
    moodEntryDao: MoodEntryDao,
    causeCategoryDao: CauseCategoryDao,
    weatherSnapshotDao: WeatherSnapshotDao,
    moonPhaseSnapshotDao: MoonPhaseSnapshotDao,
) {
    val causeIds = causeCategoryDao.getAllList()
        .filter { it.isActive }
        .map { it.id }

    val today = LocalDate.now()
    val zone  = ZoneId.systemDefault()
    val rng   = Random(42)

    // Possible check-in times throughout the day
    val timeSlots = listOf(
        LocalTime.of(7, 30),
        LocalTime.of(10, 15),
        LocalTime.of(13, 0),
        LocalTime.of(16, 45),
        LocalTime.of(20, 30),
    )

    for (daysAgo in 0..29) {
        val date = today.minusDays(daysAgo.toLong())

        // One weather snapshot around midday for this day
        val condition = conditions.random(rng)
        val snapshotId = weatherSnapshotDao.insert(
            WeatherSnapshot(
                timestamp    = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                city         = cities.random(rng),
                temperatureC = (rng.nextFloat() * 30f) - 5f,
                condition    = condition,
                isRaining    = condition in listOf("Rainy", "Drizzle"),
                humidity     = rng.nextFloat() * 60f + 30f,
            )
        )

        // Moon phase snapshot (computed from date)
        val moonTs = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val moonSnap = MoonPhaseCalculator.compute(moonTs)
        val moonSnapshotId = moonPhaseSnapshotDao.insert(moonSnap)

        // 2–5 entries per day
        val entryCount = rng.nextInt(2, 6)
        val times = timeSlots.shuffled(rng).take(entryCount).sorted()

        times.forEach { time ->
            val causeCount = if (causeIds.isEmpty()) 0 else rng.nextInt(0, 3)
            val selectedCauses = if (causeIds.isEmpty()) emptyList() else causeIds.shuffled(rng).take(causeCount)

            moodEntryDao.insert(
                MoodEntry(
                    timestamp           = date.atTime(time).atZone(zone).toInstant().toEpochMilli(),
                    moodValue           = rng.nextInt(-10, 11),
                    causeIds            = JSONArray(selectedCauses).toString(),
                    note                = sampleNotes.random(rng),
                    weatherSnapshotId   = if (rng.nextFloat() < 0.4f) snapshotId else null,
                    moonPhaseSnapshotId = moonSnapshotId,
                )
            )
        }
    }
}
