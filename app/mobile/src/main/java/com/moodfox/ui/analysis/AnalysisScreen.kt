package com.moodfox.ui.analysis

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodfox.R
import com.moodfox.data.local.db.CauseCategoryDao
import com.moodfox.data.local.db.MoodEntry
import com.moodfox.data.local.db.MoodEntryDao
import com.moodfox.data.local.db.MoonPhaseSnapshotDao
import com.moodfox.data.local.db.WeatherSnapshotDao
import com.moodfox.domain.MoodStats
import com.moodfox.domain.MoonPhaseCalculator
import com.moodfox.ui.theme.AppColors
import com.moodfox.ui.theme.LocalAppColors
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// ── Range selector ────────────────────────────────────────
private enum class Range(val labelRes: Int, val days: Int) {
    D7( R.string.analysis_range_7d,  7),
    D30(R.string.analysis_range_30d, 30),
    D90(R.string.analysis_range_90d, 90),
}

// ── Screen ────────────────────────────────────────────────
@Composable
fun AnalysisScreen(
    moodEntryDao: MoodEntryDao,
    causeCategoryDao: CauseCategoryDao,
    weatherSnapshotDao: WeatherSnapshotDao,
    moonPhaseSnapshotDao: MoonPhaseSnapshotDao,
) {
    val colors = LocalAppColors.current
    var range  by remember { mutableStateOf(Range.D30) }

    val now   = Instant.now()
    val from  = now.minus(range.days.toLong(), ChronoUnit.DAYS).toEpochMilli()
    val to    = now.toEpochMilli()

    val entries    by moodEntryDao.getByDateRange(from, to).collectAsState(initial = emptyList())
    val categories by causeCategoryDao.getActive().collectAsState(initial = emptyList())
    val allSnapshots by weatherSnapshotDao.getAll().collectAsState(initial = emptyList())
    val snapshotMap = remember(allSnapshots) { allSnapshots.associateBy { it.id } }
    val allMoonSnapshots by moonPhaseSnapshotDao.getAll().collectAsState(initial = emptyList())
    val moonSnapshotMap = remember(allMoonSnapshots) { allMoonSnapshots.associateBy { it.id } }

    val summary     = remember(entries) { MoodStats.periodSummary(entries) }
    val ema         = remember(entries) { MoodStats.rollingEma(entries) }
    val days        = remember(entries) { MoodStats.aggregateByDay(entries) }
    val byTime      = remember(entries) { MoodStats.byTimeOfDay(entries) }
    val causes      = remember(entries) { MoodStats.causeFrequencies(entries) }
    val weatherStat = remember(entries, snapshotMap) { MoodStats.weatherAnalysis(entries, snapshotMap) }
    val moonStat    = remember(entries, moonSnapshotMap) { MoodStats.moonPhaseAnalysis(entries, moonSnapshotMap) }

    val screenGradient = Brush.verticalGradient(
        listOf(colors.primary.copy(alpha = 0.12f), colors.surface),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenGradient)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text  = stringResource(R.string.analysis_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(12.dp))

        // ── Range tabs ───────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.cardSurface),
        ) {
            Range.entries.forEach { r ->
                val selected = r == range
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.primary else Color.Transparent)
                        .clickable { range = r }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = stringResource(r.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) colors.surface else colors.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (summary == null || entries.size < 3) {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                Text(
                    text  = stringResource(R.string.analysis_no_data),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        // ── Stability card ───────────────────────────────
        StabilityCard(summary = summary, range = range, colors = colors)

        Spacer(Modifier.height(16.dp))

        // ── Mood line chart ──────────────────────────────
        if (days.isNotEmpty()) {
            SectionTitle(R.string.analysis_rolling_avg, colors)
            Spacer(Modifier.height(8.dp))
            MoodLineChart(
                days      = days,
                ema       = ema,
                colors    = colors,
            )
            Spacer(Modifier.height(16.dp))
        }

        // ── Time of day ──────────────────────────────────
        if (byTime.isNotEmpty()) {
            SectionTitle(R.string.analysis_volatility, colors)
            Spacer(Modifier.height(8.dp))
            TimeOfDayBars(buckets = byTime, colors = colors)
            Spacer(Modifier.height(16.dp))
        }

        // ── Observations ─────────────────────────────────
        ObservationCard(summary = summary, range = range, colors = colors)

        // ── Top causes ───────────────────────────────────
        if (causes.isNotEmpty() && categories.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            val catMap = categories.associateBy { it.id }
            TopCauses(causes = causes.take(5), catMap = catMap, colors = colors)
        }

        // ── Weather & mood ────────────────────────────────
        val hasWeather = weatherStat.byCondition.isNotEmpty()
        if (hasWeather) {
            Spacer(Modifier.height(16.dp))
            SectionTitle(R.string.analysis_weather_title, colors)
            Spacer(Modifier.height(8.dp))
            WeatherMoodSection(stat = weatherStat, colors = colors)
        }

        // ── Moon phase & mood ─────────────────────────────
        val hasMoon = moonStat.byPhase.isNotEmpty()
        if (hasMoon) {
            Spacer(Modifier.height(16.dp))
            SectionTitle(R.string.analysis_moon_title, colors)
            Spacer(Modifier.height(8.dp))
            MoonPhaseMoodSection(stat = moonStat, colors = colors)
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Stability card ────────────────────────────────────────

@Composable
private fun StabilityCard(summary: MoodStats.PeriodSummary, range: Range, colors: AppColors) {
    val stabilityLabel = when {
        summary.volatility < 1.5f -> stringResource(R.string.analysis_stability_calm)
        summary.volatility < 3f   -> stringResource(R.string.analysis_stability_moderate)
        else                       -> stringResource(R.string.analysis_stability_turbulent)
    }
    val stabilityColor = when {
        summary.volatility < 1.5f -> colors.secondary
        summary.volatility < 3f   -> colors.primary
        else                       -> colors.tertiary
    }
    val bandLabel = stringResource(
        if (summary.inBand) R.string.analysis_in_band else R.string.analysis_out_of_band
    )
    val bandColor = if (summary.inBand) colors.secondary else colors.tertiary
    val meanStr   = if (summary.mean >= 0) "+%.1f".format(summary.mean) else "%.1f".format(summary.mean)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.cardSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatCell(label = "${range.days}d avg", value = meanStr,
                color = if (summary.inBand) colors.secondary else colors.primary, colors = colors)
            VerticalDivider(color = colors.outline, modifier = Modifier.height(48.dp))
            StatCell(label = stabilityLabel, value = "%.1f".format(summary.volatility),
                color = stabilityColor, colors = colors)
            VerticalDivider(color = colors.outline, modifier = Modifier.height(48.dp))
            StatCell(label = bandLabel, value = "${summary.count}×",
                color = bandColor, colors = colors)
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, color: Color, colors: AppColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelLarge,  color = colors.onSurfaceVariant)
    }
}

// ── Mood line chart ───────────────────────────────────────

@Composable
private fun MoodLineChart(
    days: List<MoodStats.DayAggregate>,
    ema: List<MoodStats.EmaPoint>,
    colors: AppColors,
) {
    val primary = colors.primary
    val accent  = colors.accent
    val outline = colors.outline
    val onSurfaceVariant = colors.onSurfaceVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        if (days.isEmpty()) return@Canvas
        val padL = 8.dp.toPx(); val padR = 8.dp.toPx()
        val padT = 8.dp.toPx(); val padB = 8.dp.toPx()
        val w = size.width - padL - padR
        val h = size.height - padT - padB

        val minV = -10f; val maxV = 10f
        fun xOf(i: Int, n: Int): Float = padL + if (n <= 1) w / 2 else i * w / (n - 1)
        fun yOf(v: Float): Float = padT + h - (v - minV) / (maxV - minV) * h

        // Target band fill: -2 to +2
        drawRect(
            color   = accent.copy(alpha = 0.12f),
            topLeft = Offset(padL, yOf(2f)),
            size    = androidx.compose.ui.geometry.Size(w, yOf(-2f) - yOf(2f)),
        )

        // Zero line
        drawLine(outline.copy(alpha = 0.4f), Offset(padL, yOf(0f)), Offset(padL + w, yOf(0f)),
            strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))

        // Daily avg line
        if (days.size >= 2) {
            val avgPath = Path()
            days.forEachIndexed { i, d ->
                val x = xOf(i, days.size); val y = yOf(d.avg)
                if (i == 0) avgPath.moveTo(x, y) else avgPath.lineTo(x, y)
            }
            drawPath(avgPath, primary.copy(alpha = 0.4f), style = Stroke(width = 1.5.dp.toPx(),
                cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // EMA line
        val emaByDate = ema.associate { it.date to it.ema }
        val emaValues = days.mapNotNull { d -> emaByDate[d.date]?.let { d to it } }
        if (emaValues.size >= 2) {
            val emaPath = Path()
            emaValues.forEachIndexed { i, (d, v) ->
                val x = xOf(days.indexOf(d), days.size); val y = yOf(v)
                if (i == 0) emaPath.moveTo(x, y) else emaPath.lineTo(x, y)
            }
            drawPath(emaPath, primary, style = Stroke(width = 2.5.dp.toPx(),
                cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// ── Time-of-day bars ──────────────────────────────────────

@Composable
private fun TimeOfDayBars(buckets: List<MoodStats.TimeOfDayAvg>, colors: AppColors) {
    val labels = mapOf(
        MoodStats.TimeOfDay.MORNING   to "🌅 Morning",
        MoodStats.TimeOfDay.AFTERNOON to "☀️ Afternoon",
        MoodStats.TimeOfDay.EVENING   to "🌆 Evening",
        MoodStats.TimeOfDay.NIGHT     to "🌙 Night",
    )
    Column(Modifier.fillMaxWidth()) {
        buckets.forEach { b ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    labels[b.bucket] ?: b.bucket.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.width(110.dp),
                )
                val barFraction = ((b.avg + 10f) / 20f).coerceIn(0f, 1f)
                val barColor = when {
                    b.avg > 2f  -> colors.secondary
                    b.avg < -2f -> colors.tertiary
                    else         -> colors.primary
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(colors.outline)
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(barFraction)
                            .clip(RoundedCornerShape(5.dp))
                            .background(barColor)
                    )
                }
                Spacer(Modifier.width(8.dp))
                val avgStr = if (b.avg >= 0) "+%.1f".format(b.avg) else "%.1f".format(b.avg)
                Text(avgStr, style = MaterialTheme.typography.labelLarge,
                    color = barColor, modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.End)
            }
        }
    }
}

// ── Observation card ──────────────────────────────────────

@Composable
private fun ObservationCard(summary: MoodStats.PeriodSummary, range: Range, colors: AppColors) {
    val obs = buildString {
        val meanStr = if (summary.mean >= 0) "+%.1f".format(summary.mean) else "%.1f".format(summary.mean)
        append(
            when {
                summary.inBand  -> "Your ${range.days}-day average is $meanStr — within the calm zone."
                summary.mean > 2 -> "Your ${range.days}-day average is $meanStr — a bit high. Sustainable?"
                else              -> "Your ${range.days}-day average is $meanStr — a bit below the stable range."
            }
        )
        if (summary.volatility >= 3f) {
            append("\n\nYou have several large swings this period. The average looks fine but the ride is rough.")
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.cardSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text     = obs,
            style    = MaterialTheme.typography.bodyLarge,
            color    = colors.onSurface,
            modifier = Modifier.padding(16.dp),
        )
    }
}

// ── Top causes ────────────────────────────────────────────

@Composable
private fun TopCauses(
    causes: List<MoodStats.CauseFreq>,
    catMap: Map<Long, com.moodfox.data.local.db.CauseCategory>,
    colors: AppColors,
) {
    Column(Modifier.fillMaxWidth()) {
        causes.forEach { cf ->
            val cat = catMap[cf.categoryId] ?: return@forEach
            val moodCol = when {
                cf.avgMood > 2f  -> colors.secondary
                cf.avgMood < -2f -> colors.tertiary
                else              -> colors.primary
            }
            val avgStr = if (cf.avgMood >= 0) "+%.1f".format(cf.avgMood) else "%.1f".format(cf.avgMood)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${cat.emoji} ${cat.name}", style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface, modifier = Modifier.weight(1f))
                Text("${cf.count}×", style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant, modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.End)
                Spacer(Modifier.width(8.dp))
                Text(avgStr, style = MaterialTheme.typography.labelLarge, color = moodCol,
                    modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────

@Composable
private fun SectionTitle(res: Int, colors: AppColors) {
    Text(
        text  = stringResource(res),
        style = MaterialTheme.typography.titleMedium,
        color = colors.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
}

// ── Weather & mood section ────────────────────────────────

@Composable
private fun WeatherMoodSection(stat: MoodStats.WeatherAnalysis, colors: AppColors) {

    fun moodStr(v: Float?) = if (v == null) "—" else if (v >= 0) "+%.1f".format(v) else "%.1f".format(v)
    fun conditionEmoji(c: String) = when {
        "thunder" in c.lowercase()             -> "⛈️"
        "snow"    in c.lowercase()             -> "❄️"
        "fog"     in c.lowercase() || "mist" in c.lowercase() -> "🌫️"
        "rain"    in c.lowercase() || "drizzle" in c.lowercase() || "shower" in c.lowercase() -> "🌧️"
        "clear"   in c.lowercase() || "sunny" in c.lowercase() -> "☀️"
        "partly"  in c.lowercase() || "overcast" in c.lowercase() -> "⛅"
        "cloud"   in c.lowercase()             -> "⛅"
        else                                   -> "🌤️"
    }

    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = colors.cardSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Rainy vs dry comparison
            if (stat.rainyAvg != null || stat.dryAvg != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (stat.rainyAvg != null) {
                        StatCell(
                            label  = stringResource(R.string.analysis_weather_rainy),
                            value  = moodStr(stat.rainyAvg),
                            color  = colors.primary,
                            colors = colors,
                        )
                    }
                    if (stat.rainyAvg != null && stat.dryAvg != null) {
                        VerticalDivider(color = colors.outline, modifier = Modifier.height(48.dp))
                    }
                    if (stat.dryAvg != null) {
                        StatCell(
                            label  = stringResource(R.string.analysis_weather_dry),
                            value  = moodStr(stat.dryAvg),
                            color  = colors.secondary,
                            colors = colors,
                        )
                    }
                }
                if (stat.byCondition.isNotEmpty() || stat.byScoreBucket.isNotEmpty()) {
                    HorizontalDivider(
                        color    = colors.outline.copy(alpha = 0.3f),
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }

            // Score buckets: Good / Neutral / Poor
            if (stat.byScoreBucket.isNotEmpty()) {
                val bucketEmoji = mapOf("Good" to "🌤️", "Neutral" to "🌥️", "Poor" to "🌧️")
                stat.byScoreBucket.forEach { b ->
                    val barFraction = ((b.avgMood + 10f) / 20f).coerceIn(0f, 1f)
                    val barColor    = when {
                        b.avgMood > 2f  -> colors.secondary
                        b.avgMood < -2f -> colors.tertiary
                        else            -> colors.primary
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${bucketEmoji[b.label] ?: ""} ${b.label} (${b.count}×)",
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = colors.onSurface,
                            modifier = Modifier.width(130.dp),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(colors.outline)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(barFraction)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(barColor)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            moodStr(b.avgMood),
                            style     = MaterialTheme.typography.labelLarge,
                            color     = barColor,
                            modifier  = Modifier.width(36.dp),
                            textAlign = TextAlign.End,
                        )
                    }
                }

                // Correlation line
                val r = stat.scoreCorrelation
                if (r != null) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))
                    val strength = when {
                        kotlin.math.abs(r) >= 0.5f  -> "strong"
                        kotlin.math.abs(r) >= 0.25f -> "moderate"
                        else                         -> "weak"
                    }
                    val dir = if (r >= 0) "positive" else "negative"
                    val rStr = if (r >= 0) "+%.2f".format(r) else "%.2f".format(r)
                    Text(
                        "Weather score shows $strength $dir correlation with mood (r = $rStr).",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }

                if (stat.byCondition.isNotEmpty()) {
                    HorizontalDivider(
                        color    = colors.outline.copy(alpha = 0.3f),
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }

            // Per-condition rows
            stat.byCondition.take(6).forEach { cond ->
                val barFraction = ((cond.avg + 10f) / 20f).coerceIn(0f, 1f)
                val barColor    = when {
                    cond.avg > 2f  -> colors.secondary
                    cond.avg < -2f -> colors.tertiary
                    else            -> colors.primary
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${conditionEmoji(cond.condition)} ${cond.condition}",
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = colors.onSurface,
                        modifier = Modifier.width(130.dp),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(colors.outline)
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(barFraction)
                                .clip(RoundedCornerShape(5.dp))
                                .background(barColor)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        moodStr(cond.avg),
                        style    = MaterialTheme.typography.labelLarge,
                        color    = barColor,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

// ── Moon phase & mood section ─────────────────────────────

@Composable
private fun MoonPhaseMoodSection(stat: MoodStats.MoonPhaseAnalysis, colors: AppColors) {

    fun moodStr(v: Float) = if (v >= 0) "+%.1f".format(v) else "%.1f".format(v)

    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = colors.cardSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Per-phase bars
            stat.byPhase.forEach { pm ->
                val barFraction = ((pm.avg + 10f) / 20f).coerceIn(0f, 1f)
                val barColor = when {
                    pm.avg > 2f  -> colors.secondary
                    pm.avg < -2f -> colors.tertiary
                    else         -> colors.primary
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${MoonPhaseCalculator.phaseEmoji(pm.phase)} ${pm.phase} (${pm.count}×)",
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = colors.onSurface,
                        modifier = Modifier.width(170.dp),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(colors.outline)
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(barFraction)
                                .clip(RoundedCornerShape(5.dp))
                                .background(barColor)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        moodStr(pm.avg),
                        style    = MaterialTheme.typography.labelLarge,
                        color    = barColor,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }

            // Correlation line
            val r = stat.scoreCorrelation
            if (r != null) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                Spacer(Modifier.height(8.dp))
                val strength = when {
                    kotlin.math.abs(r) >= 0.5f  -> "strong"
                    kotlin.math.abs(r) >= 0.25f -> "moderate"
                    else                         -> "weak"
                }
                val dir = if (r >= 0) "positive" else "negative"
                val rStr = if (r >= 0) "+%.2f".format(r) else "%.2f".format(r)
                Text(
                    "Moon phase shows $strength $dir correlation with mood (r = $rStr).",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
