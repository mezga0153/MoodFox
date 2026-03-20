package com.moodfox.domain

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Schedule one-time notifications at each time in a JSON time array.
     * e.g. ["09:00","13:00","19:00"]
     * Each fires once; re-call after midnight to refresh for the next day.
     */
    fun scheduleAll(timesJson: String, quietStart: String, quietEnd: String) {
        cancelAll()
        val times = parseTimes(timesJson)
        val now   = ZonedDateTime.now()
        val qStart = LocalTime.parse(quietStart)
        val qEnd   = LocalTime.parse(quietEnd)

        times.forEachIndexed { idx, time ->
            // Skip if in quiet hours
            if (isQuiet(time, qStart, qEnd)) return@forEachIndexed

            var target = now.with(time)
            if (!target.isAfter(now)) target = target.plusDays(1)
            val delayMs = target.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()

            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag("reminder_$idx")
                .build()
            workManager.enqueue(request)
        }
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag("reminder_0")
        workManager.cancelAllWorkByTag("reminder_1")
        workManager.cancelAllWorkByTag("reminder_2")
        workManager.cancelAllWorkByTag("reminder_3")
        workManager.cancelAllWorkByTag("reminder_4")
    }

    private fun parseTimes(json: String): List<LocalTime> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull {
            runCatching { LocalTime.parse(arr.getString(it)) }.getOrNull()
        }
    } catch (_: Exception) { emptyList() }

    private fun isQuiet(time: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (start <= end) time >= start && time < end
        else time >= start || time < end
}

