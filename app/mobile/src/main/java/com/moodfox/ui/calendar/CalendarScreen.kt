package com.moodfox.ui.calendar

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodfox.R
import com.moodfox.data.local.db.CauseCategory
import com.moodfox.data.local.db.CauseCategoryDao
import com.moodfox.data.local.db.MoodEntry
import com.moodfox.data.local.db.MoodEntryDao
import com.moodfox.ui.components.localizedCauseName
import com.moodfox.ui.theme.AppColors
import com.moodfox.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.time.*
import java.time.format.TextStyle
import java.util.*

// ── Data helpers ──────────────────────────────────────────

private data class DayStats(
    val date: LocalDate,
    val entries: List<MoodEntry>,
    val avg: Float,
    val min: Int,
    val max: Int,
)

private fun moodColor(avg: Float, colors: AppColors): Color = when {
    avg >  2f -> colors.secondary
    avg < -2f -> colors.tertiary
    else      -> colors.primary
}

// Interpolate from surface (no entries) to fully saturated mood colour
private fun cellColor(avg: Float, count: Int, colors: AppColors): Color {
    if (count == 0) return Color.Transparent
    val base = moodColor(avg, colors)
    val alpha = (0.25f + (count.coerceAtMost(5) / 5f) * 0.60f).coerceIn(0f, 0.85f)
    return base.copy(alpha = alpha)
}

// ── Screen ────────────────────────────────────────────────

private val SCALE_EMOJIS = mapOf(
    -10 to "😭", -9 to "😭", -8 to "😭",
    -7 to "😔",  -6 to "😔", -5 to "😔",
    -4 to "😕",  -3 to "😕", -2 to "😕",
    -1 to "😐",   0 to "😐",  1 to "😐",
     2 to "🙂",   3 to "🙂",  4 to "🙂",
     5 to "😊",   6 to "😊",  7 to "😊",
     8 to "🤩",   9 to "🤩", 10 to "🤩",
)

private fun moodEmoji(value: Int) = SCALE_EMOJIS[value.coerceIn(-10, 10)] ?: "😐"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(moodEntryDao: MoodEntryDao, causeCategoryDao: CauseCategoryDao) {
    val colors = LocalAppColors.current
    val today  = LocalDate.now()

    var displayMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDay  by remember { mutableStateOf<LocalDate?>(null) }

    val allCategories by causeCategoryDao.getAll().collectAsState(initial = emptyList())
    val categoryMap: Map<Long, CauseCategory> = remember(allCategories) {
        allCategories.associateBy { it.id }
    }

    // Load entries for ±1 month window so navigating stays fast
    val windowStart = displayMonth.minusMonths(1).atDay(1)
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val windowEnd   = displayMonth.plusMonths(1).atEndOfMonth()
        .atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val entries by moodEntryDao.getByDateRange(windowStart, windowEnd)
        .collectAsState(initial = emptyList())

    // Group by local date
    val byDate: Map<LocalDate, List<MoodEntry>> = remember(entries) {
        entries.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }

    val monthStats: Map<LocalDate, DayStats> = remember(byDate, displayMonth) {
        val first = displayMonth.atDay(1)
        val last  = displayMonth.atEndOfMonth()
        (0..Period.between(first, last.plusDays(1)).days - 1).associate { offset ->
            val d = first.plusDays(offset.toLong())
            val es = byDate[d] ?: emptyList()
            val avg = if (es.isEmpty()) 0f else es.map { it.moodValue }.average().toFloat()
            d to DayStats(d, es, avg, es.minOfOrNull { it.moodValue } ?: 0, es.maxOfOrNull { it.moodValue } ?: 0)
        }
    }

    val selectedDayEntries = selectedDay?.let { byDate[it] } ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        // ── Month navigation ─────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { displayMonth = displayMonth.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = colors.primary)
            }
            Text(
                text = "${displayMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${displayMonth.year}",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(
                onClick = { displayMonth = displayMonth.plusMonths(1) },
                enabled = displayMonth < YearMonth.from(today),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (displayMonth < YearMonth.from(today)) colors.primary else colors.outline,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Weekday header ───────────────────────────────
        val dayNames = (1..7).map {
            DayOfWeek.of(it).getDisplayName(TextStyle.NARROW, Locale.getDefault())
        }
        Row(Modifier.fillMaxWidth()) {
            dayNames.forEach { name ->
                Text(
                    text      = name,
                    modifier  = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style     = MaterialTheme.typography.labelLarge,
                    color     = colors.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── Grid ─────────────────────────────────────────
        CalendarGrid(
            month       = displayMonth,
            today       = today,
            monthStats  = monthStats,
            selectedDay = selectedDay,
            colors      = colors,
            onDayClick  = { day ->
                selectedDay = if (selectedDay == day) null else day
            },
        )

        // ── Day detail sheet ─────────────────────────────
        if (selectedDay != null && selectedDayEntries.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            DayDetailCard(
                date        = selectedDay!!,
                stats       = monthStats[selectedDay!!],
                entries     = selectedDayEntries,
                categoryMap = categoryMap,
                colors      = colors,
            )
        } else if (selectedDay != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text  = stringResource(R.string.calendar_no_entries),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Calendar grid ─────────────────────────────────────────

@Composable
private fun CalendarGrid(
    month: YearMonth,
    today: LocalDate,
    monthStats: Map<LocalDate, DayStats>,
    selectedDay: LocalDate?,
    colors: AppColors,
    onDayClick: (LocalDate) -> Unit,
) {
    val firstDay   = month.atDay(1)
    // Monday-based: Monday=1 … Sunday=7
    val startOffset = (firstDay.dayOfWeek.value - 1)  // 0..6
    val daysInMonth = month.lengthOfMonth()
    val totalCells  = startOffset + daysInMonth
    val rows        = (totalCells + 6) / 7

    Column(Modifier.fillMaxWidth()) {
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - startOffset + 1
                    if (dayNum < 1 || dayNum > daysInMonth) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date  = month.atDay(dayNum)
                        val stats = monthStats[date]
                        val bg    = cellColor(stats?.avg ?: 0f, stats?.entries?.size ?: 0, colors)
                        val isSelected = date == selectedDay
                        val isToday    = date == today
                        val isFuture   = date > today

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> colors.primary.copy(alpha = 0.25f)
                                        else       -> bg
                                    }
                                )
                                .border(
                                    width  = if (isSelected) 2.dp else if (isToday) 1.5.dp else 0.dp,
                                    color  = if (isSelected) colors.primary else if (isToday) colors.primary.copy(0.5f) else Color.Transparent,
                                    shape  = CircleShape,
                                )
                                .clickable(enabled = !isFuture) { onDayClick(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text  = "$dayNum",
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    isFuture   -> colors.onSurfaceVariant.copy(alpha = 0.3f)
                                    isSelected -> colors.primary
                                    isToday    -> colors.onSurface
                                    (stats?.entries?.size ?: 0) > 0 -> colors.onSurface
                                    else       -> colors.onSurfaceVariant
                                },
                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Day detail card ───────────────────────────────────────

@Composable
private fun DayDetailCard(
    date: LocalDate,
    stats: DayStats?,
    entries: List<MoodEntry>,
    categoryMap: Map<Long, CauseCategory>,
    colors: AppColors,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.cardSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header row: date + stats
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text  = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                if (stats != null && entries.isNotEmpty()) {
                    val avgColor = moodColor(stats.avg, colors)
                    Text(
                        text  = stringResource(R.string.day_detail_avg, stats.avg),
                        style = MaterialTheme.typography.titleMedium,
                        color = avgColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (stats != null && entries.size > 1) {
                Text(
                    text  = stringResource(R.string.day_detail_range, stats.min, stats.max),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = colors.outline)
            Spacer(Modifier.height(8.dp))

            // Entry list
            entries.sortedBy { it.timestamp }.forEach { entry ->
                EntryRow(entry = entry, categoryMap = categoryMap, colors = colors)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryRow(
    entry: MoodEntry,
    categoryMap: Map<Long, CauseCategory>,
    colors: AppColors,
) {
    val time = Instant.ofEpochMilli(entry.timestamp)
        .atZone(ZoneId.systemDefault()).toLocalTime()
    val timeStr = "%02d:%02d".format(time.hour, time.minute)
    val moodStr = if (entry.moodValue >= 0) "+${entry.moodValue}" else "${entry.moodValue}"
    val moodCol = when {
        entry.moodValue > 2  -> colors.secondary
        entry.moodValue < -2 -> colors.tertiary
        else                  -> colors.primary
    }

    // Parse cause IDs from JSON
    val causes: List<CauseCategory> = remember(entry.causeIds, categoryMap) {
        val arr = JSONArray(entry.causeIds)
        (0 until arr.length()).mapNotNull { categoryMap[arr.getLong(it)] }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(10.dp),
    ) {
        // ── Top row: time · emoji · score ────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text  = timeStr,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.width(44.dp),
            )
            Text(
                text     = moodEmoji(entry.moodValue),
                fontSize = 22.sp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text       = moodStr,
                style      = MaterialTheme.typography.titleMedium,
                color      = moodCol,
                fontWeight = FontWeight.Bold,
            )
        }

        // ── Cause chips ───────────────────────────────────
        if (causes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement   = Arrangement.spacedBy(6.dp),
            ) {
                causes.forEach { cat ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = moodCol.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, moodCol.copy(alpha = 0.35f)),
                    ) {
                        Text(
                            text     = "${cat.emoji} ${localizedCauseName(cat)}",
                            style    = MaterialTheme.typography.labelMedium,
                            color    = colors.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        // ── Note ──────────────────────────────────────────
        if (!entry.note.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text  = entry.note,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
        }
    }
}
