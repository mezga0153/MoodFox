package com.moodfox.domain

import com.moodfox.data.local.db.MoodEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure functions for mood statistics. No side effects, fully testable.
 */
object MoodStats {

    // ── Rolling average (EMA, α=0.3) ─────────────────────────────────────────

    data class EmaPoint(val date: LocalDate, val ema: Float)

    fun rollingEma(entries: List<MoodEntry>, alpha: Float = 0.3f): List<EmaPoint> {
        if (entries.isEmpty()) return emptyList()
        val sorted = entries.sortedBy { it.timestamp }
        var ema = sorted.first().moodValue.toFloat()
        val result = mutableListOf<EmaPoint>()
        sorted.forEach { entry ->
            ema = alpha * entry.moodValue + (1 - alpha) * ema
            result += EmaPoint(entry.timestamp.toLocalDate(), ema)
        }
        return result
    }

    // ── Day-level aggregates ──────────────────────────────────────────────────

    data class DayAggregate(
        val date: LocalDate,
        val avg: Float,
        val min: Int,
        val max: Int,
        val count: Int,
    )

    fun aggregateByDay(entries: List<MoodEntry>): List<DayAggregate> =
        entries
            .groupBy { it.timestamp.toLocalDate() }
            .map { (date, es) ->
                DayAggregate(
                    date  = date,
                    avg   = es.map { it.moodValue }.average().toFloat(),
                    min   = es.minOf { it.moodValue },
                    max   = es.maxOf { it.moodValue },
                    count = es.size,
                )
            }
            .sortedBy { it.date }

    // ── Period summary ────────────────────────────────────────────────────────

    data class PeriodSummary(
        val count: Int,
        val mean: Float,
        val stdDev: Float,
        val volatility: Float,  // mean abs day-to-day swing
        val inBand: Boolean,    // long-term avg between -2 and +2
        val trend: Float,       // slope of daily averages (units/day)
    )

    fun periodSummary(entries: List<MoodEntry>): PeriodSummary? {
        if (entries.isEmpty()) return null
        val values = entries.map { it.moodValue.toFloat() }
        val mean   = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        val stdDev   = sqrt(variance)

        val days = aggregateByDay(entries)
        val volatility = if (days.size < 2) 0f else
            days.zipWithNext { a, b -> abs(b.avg - a.avg) }.average().toFloat()

        val trend = linearTrend(days.map { it.avg })

        return PeriodSummary(
            count      = entries.size,
            mean       = mean,
            stdDev     = stdDev,
            volatility = volatility,
            inBand     = mean in -2f..2f,
            trend      = trend,
        )
    }

    // ── Cause frequency ───────────────────────────────────────────────────────

    data class CauseFreq(val categoryId: Long, val count: Int, val avgMood: Float)

    fun causeFrequencies(entries: List<MoodEntry>): List<CauseFreq> {
        val grouped = mutableMapOf<Long, MutableList<Int>>()
        entries.forEach { entry ->
            parseCauseIds(entry.causeIds).forEach { id ->
                grouped.getOrPut(id) { mutableListOf() }.add(entry.moodValue)
            }
        }
        return grouped.map { (id, moods) ->
            CauseFreq(id, moods.size, moods.average().toFloat())
        }.sortedByDescending { it.count }
    }

    // ── Time-of-day pattern ───────────────────────────────────────────────────

    enum class TimeOfDay { MORNING, AFTERNOON, EVENING, NIGHT }

    data class TimeOfDayAvg(val bucket: TimeOfDay, val avg: Float, val count: Int)

    fun byTimeOfDay(entries: List<MoodEntry>): List<TimeOfDayAvg> {
        val zone = ZoneId.systemDefault()
        val buckets = TimeOfDay.entries.associateWith { mutableListOf<Int>() }
        entries.forEach { entry ->
            val hour  = Instant.ofEpochMilli(entry.timestamp).atZone(zone).hour
            val bucket = when (hour) {
                in 5..11  -> TimeOfDay.MORNING
                in 12..16 -> TimeOfDay.AFTERNOON
                in 17..21 -> TimeOfDay.EVENING
                else       -> TimeOfDay.NIGHT
            }
            buckets[bucket]!!.add(entry.moodValue)
        }
        return buckets
            .filter { it.value.isNotEmpty() }
            .map { (bucket, moods) ->
                TimeOfDayAvg(bucket, moods.average().toFloat(), moods.size)
            }
            .sortedBy { it.bucket.ordinal }
    }

    // ── Weather & mood correlation ────────────────────────────────────────────

    data class ConditionMood(val condition: String, val avg: Float, val count: Int)

    data class WeatherScoreBucket(val label: String, val avgMood: Float, val count: Int)

    data class WeatherAnalysis(
        val rainyAvg: Float?,
        val dryAvg: Float?,
        val byCondition: List<ConditionMood>,
        val scoreCorrelation: Float?,            // Pearson r between weather score and mood
        val byScoreBucket: List<WeatherScoreBucket>,  // Good / Neutral / Poor buckets
    )

    fun weatherAnalysis(
        entries: List<MoodEntry>,
        snapshots: Map<Long, com.moodfox.data.local.db.WeatherSnapshot>,
    ): WeatherAnalysis {
        val withSnap = entries.mapNotNull { e ->
            val snap = e.weatherSnapshotId?.let { snapshots[it] } ?: return@mapNotNull null
            e to snap
        }
        val rainyMoods = withSnap.filter { (_, s) -> s.isRaining }.map { (e, _) -> e.moodValue.toFloat() }
        val dryMoods   = withSnap.filter { (_, s) -> !s.isRaining }.map { (e, _) -> e.moodValue.toFloat() }

        val grouped = withSnap
            .groupBy { (_, s) -> s.condition }
            .map { (cond, pairs) ->
                ConditionMood(
                    condition = cond,
                    avg       = pairs.map { (e, _) -> e.moodValue.toFloat() }.average().toFloat(),
                    count     = pairs.size,
                )
            }
            .sortedByDescending { it.count }

        // Score-based correlation
        val scoredPairs = withSnap.map { (e, s) ->
            WeatherScorer.score(s).toFloat() to e.moodValue.toFloat()
        }
        val correlation = pearsonR(scoredPairs.map { it.first }, scoredPairs.map { it.second })

        val bucketOrder = listOf("Good", "Neutral", "Poor")
        val byBucket = scoredPairs
            .groupBy { (score, _) ->
                when {
                    score >= 3f  -> "Good"
                    score >= -2f -> "Neutral"
                    else         -> "Poor"
                }
            }
            .map { (label, pairs) ->
                WeatherScoreBucket(
                    label   = label,
                    avgMood = pairs.map { it.second }.average().toFloat(),
                    count   = pairs.size,
                )
            }
            .sortedBy { bucketOrder.indexOf(it.label) }

        return WeatherAnalysis(
            rainyAvg         = if (rainyMoods.isEmpty()) null else rainyMoods.average().toFloat(),
            dryAvg           = if (dryMoods.isEmpty()) null else dryMoods.average().toFloat(),
            byCondition      = grouped,
            scoreCorrelation = correlation,
            byScoreBucket    = byBucket,
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun pearsonR(xs: List<Float>, ys: List<Float>): Float? {
        if (xs.size < 5) return null
        val mx = xs.average().toFloat()
        val my = ys.average().toFloat()
        val num = xs.zip(ys).sumOf { (x, y) -> ((x - mx) * (y - my)).toDouble() }.toFloat()
        val dx  = sqrt(xs.sumOf { ((it - mx) * (it - mx)).toDouble() }.toFloat())
        val dy  = sqrt(ys.sumOf { ((it - my) * (it - my)).toDouble() }.toFloat())
        return if (dx == 0f || dy == 0f) null else (num / (dx * dy)).coerceIn(-1f, 1f)
    }

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun parseCauseIds(json: String): List<Long> = try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { arr.getLong(it) }
    } catch (_: Exception) { emptyList() }

    /** Simple linear regression slope over a list of floats (index = x-axis). */
    private fun linearTrend(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val n   = values.size.toFloat()
        val xMean = (n - 1) / 2f
        val yMean = values.average().toFloat()
        val num   = values.indices.sumOf { i -> ((i - xMean) * (values[i] - yMean)).toDouble() }.toFloat()
        val den   = values.indices.sumOf { i -> ((i - xMean) * (i - xMean)).toDouble() }.toFloat()
        return if (den == 0f) 0f else num / den
    }
}
