package com.moodfox.ui.calendar

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import com.moodfox.R
import com.moodfox.data.local.db.CauseCategory
import com.moodfox.data.local.db.CauseCategoryDao
import com.moodfox.data.local.db.MoodEntry
import com.moodfox.data.local.db.MoodEntryDao
import com.moodfox.ui.components.localizedCauseName
import com.moodfox.ui.theme.AppColors
import com.moodfox.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    val scope  = rememberCoroutineScope()

    var displayMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDay  by remember { mutableStateOf<LocalDate?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var listView     by remember { mutableStateOf(false) }

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
        (0 until displayMonth.lengthOfMonth()).associate { offset ->
            val d = first.plusDays(offset.toLong())
            val es = byDate[d] ?: emptyList()
            val avg = if (es.isEmpty()) 0f else es.map { it.moodValue }.average().toFloat()
            d to DayStats(d, es, avg, es.minOfOrNull { it.moodValue } ?: 0, es.maxOfOrNull { it.moodValue } ?: 0)
        }
    }

    val selectedDayEntries = selectedDay?.let { byDate[it] } ?: emptyList()

    val screenGradient = Brush.verticalGradient(
        listOf(colors.primary.copy(alpha = 0.12f), colors.surface),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenGradient)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Grid / List toggle
                IconButton(onClick = { listView = !listView }) {
                    Icon(
                        imageVector = if (listView) Icons.Filled.CalendarMonth else Icons.Filled.ViewList,
                        contentDescription = if (listView) "Grid view" else "List view",
                        tint = colors.primary,
                    )
                }
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
        }

        Spacer(Modifier.height(8.dp))

        if (listView) {
            // ── List view ─────────────────────────────────
            CalendarListView(
                month       = displayMonth,
                today       = today,
                monthStats  = monthStats,
                categoryMap = categoryMap,
                colors      = colors,
            )
        } else {
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

        // ── Add entry FAB ────────────────────────────────
        if (selectedDay != null && selectedDay!! < today) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showAddSheet = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                border = BorderStroke(1.dp, colors.primary),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add entry", color = colors.primary)
            }
        }
        } // end else (grid view)
    }

    // ── Add entry bottom sheet ───────────────────────────
    if (showAddSheet && selectedDay != null) {
        AddEntrySheet(
            date             = selectedDay!!,
            categories       = allCategories.filter { it.isActive },
            onDismiss        = { showAddSheet = false },
            onSave           = { entry ->
                scope.launch {
                    moodEntryDao.insert(entry)
                    showAddSheet = false
                }
            },
            colors           = colors,
        )
    }
}

// ── Calendar list view ────────────────────────────────────

@Composable
private fun CalendarListView(
    month: YearMonth,
    today: LocalDate,
    monthStats: Map<LocalDate, DayStats>,
    categoryMap: Map<Long, CauseCategory>,
    colors: AppColors,
) {
    val daysWithEntries = remember(monthStats) {
        (1..month.lengthOfMonth())
            .map { month.atDay(it) }
            .filter { (monthStats[it]?.entries?.size ?: 0) > 0 && it <= today }
            .reversed()
    }
    var expandedDay by remember { mutableStateOf<LocalDate?>(null) }

    if (daysWithEntries.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
            Text(
                text  = "No entries this month",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        return
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(daysWithEntries, key = { it.toString() }) { date ->
            val stats    = monthStats[date]!!
            val isExpanded = expandedDay == date
            val avgColor = moodColor(stats.avg, colors)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.cardSurface)
                    .clickable { expandedDay = if (isExpanded) null else date },
            ) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Date
                    Column(modifier = Modifier.width(52.dp)) {
                        Text(
                            text  = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text  = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    // Avg score bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.outline.copy(alpha = 0.3f)),
                    ) {
                        val fraction = ((stats.avg + 10f) / 20f).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .clip(RoundedCornerShape(3.dp))
                                .background(avgColor),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    // Avg label + emoji
                    Text(
                        text  = if (stats.avg >= 0f) "+%.1f".format(stats.avg) else "%.1f".format(stats.avg),
                        style = MaterialTheme.typography.labelLarge,
                        color = avgColor,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text  = moodEmoji(stats.avg.toInt()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                // Expanded: show individual entries
                if (isExpanded) {
                    HorizontalDivider(color = colors.outline.copy(alpha = 0.4f))
                    stats.entries.sortedBy { it.timestamp }.forEach { entry ->
                        val time = Instant.ofEpochMilli(entry.timestamp)
                            .atZone(ZoneId.systemDefault()).toLocalTime()
                        val timeStr = "%02d:%02d".format(time.hour, time.minute)
                        val entryColor = moodColor(entry.moodValue.toFloat(), colors)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text  = timeStr,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.width(40.dp),
                            )
                            Text(
                                text  = moodEmoji(entry.moodValue),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text  = if (entry.moodValue >= 0) "+${entry.moodValue}" else "${entry.moodValue}",
                                style = MaterialTheme.typography.labelLarge,
                                color = entryColor,
                                fontWeight = FontWeight.Bold,
                            )
                            if (entry.note != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text     = entry.note,
                                    style    = MaterialTheme.typography.bodySmall,
                                    color    = colors.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
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
                                .clickable(enabled = !isFuture) { onDayClick(date) },
                        ) {
                            // Circle background + border (clipped independently)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
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
                                    ),
                            )
                            // Day number — shift up when overlays shown
                            val hasEntries = (stats?.entries?.size ?: 0) > 0
                            Text(
                                text  = "$dayNum",
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    isFuture   -> colors.onSurfaceVariant.copy(alpha = 0.3f)
                                    isSelected -> colors.primary
                                    isToday    -> colors.onSurface
                                    hasEntries -> colors.onSurface
                                    else       -> colors.onSurfaceVariant
                                },
                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier   = Modifier
                                    .align(Alignment.Center)
                                    .offset(y = if (hasEntries) (-4).dp else 0.dp),
                            )
                            // Bottom-left: avg score, Bottom-right: emoji (unclipped)
                            if (hasEntries && stats != null) {
                                val avgLabel = if (stats.avg >= 0f) "+%.0f".format(stats.avg)
                                               else "%.0f".format(stats.avg)
                                Text(
                                    text       = avgLabel,
                                    fontSize   = 10.sp,
                                    color      = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 10.sp,
                                    modifier   = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 3.dp, bottom = 2.dp),
                                )
                                Text(
                                    text       = moodEmoji(stats.avg.toInt()),
                                    fontSize   = 10.sp,
                                    lineHeight = 10.sp,
                                    modifier   = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 3.dp, bottom = 2.dp),
                                )
                            }
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

// ── Add entry bottom sheet ────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddEntrySheet(
    date: LocalDate,
    categories: List<CauseCategory>,
    onDismiss: () -> Unit,
    onSave: (MoodEntry) -> Unit,
    colors: AppColors,
) {
    var moodValue      by remember { mutableIntStateOf(0) }
    var selectedCauses by remember { mutableStateOf(setOf<Long>()) }
    var note           by remember { mutableStateOf("") }
    var showNote       by remember { mutableStateOf(false) }
    var showAllCauses  by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Default time: 12:00 or current time if date == today
    val now = LocalTime.now()
    var selectedHour   by remember { mutableIntStateOf(12) }
    var selectedMinute by remember { mutableIntStateOf(0) }

    val timePickerState = rememberTimePickerState(
        initialHour   = selectedHour,
        initialMinute = selectedMinute,
        is24Hour      = true,
    )

    val dateLabel = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${date.year}"
    val timeLabel = "%02d:%02d".format(selectedHour, selectedMinute)

    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbColor = when {
        moodValue > 2  -> colors.secondary
        moodValue < -2 -> colors.tertiary
        else           -> colors.primary
    }
    val trackGradient = Brush.horizontalGradient(listOf(colors.tertiary, colors.primary, colors.secondary))
    val fraction = (moodValue + 10f) / 20f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = colors.cardSurface,
        dragHandle       = { BottomSheetDefaults.DragHandle(color = colors.outline) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Text(
                text       = "Add entry — $dateLabel",
                style      = MaterialTheme.typography.titleMedium,
                color      = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))

            // Time picker row
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier              = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showTimePicker = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text  = "🕐 $timeLabel",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Emoji + score
            val emoji = moodEmoji(moodValue)
            val scoreLabel = if (moodValue >= 0) "+$moodValue" else "$moodValue"
            Text(text = emoji, fontSize = 56.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text       = scoreLabel,
                style      = MaterialTheme.typography.headlineSmall,
                color      = thumbColor,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))

            // Slider
            Surface(shape = RoundedCornerShape(20.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .onSizeChanged { trackWidthPx = it.width.toFloat() }
                            .pointerInput(trackWidthPx) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        if (trackWidthPx > 0f)
                                            moodValue = ((offset.x / trackWidthPx) * 20f - 10f).toInt().coerceIn(-10, 10)
                                    },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        if (trackWidthPx > 0f)
                                            moodValue = ((change.position.x / trackWidthPx) * 20f - 10f).toInt().coerceIn(-10, 10)
                                    },
                                )
                            }
                            .pointerInput(trackWidthPx) {
                                detectTapGestures { offset ->
                                    if (trackWidthPx > 0f)
                                        moodValue = ((offset.x / trackWidthPx) * 20f - 10f).toInt().coerceIn(-10, 10)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val trackY = size.height * 0.42f
                            val trackH = with(density) { 10.dp.toPx() }
                            drawRoundRect(
                                brush        = trackGradient,
                                topLeft      = Offset(0f, trackY - trackH / 2),
                                size         = Size(size.width, trackH),
                                cornerRadius = CornerRadius(trackH / 2),
                            )
                            val b2Start = (8f / 20f) * size.width
                            val b2End   = (12f / 20f) * size.width
                            drawRoundRect(
                                color        = colors.accent.copy(alpha = 0.20f),
                                topLeft      = Offset(b2Start, trackY - trackH / 2 - 2f),
                                size         = Size(b2End - b2Start, trackH + 4f),
                                cornerRadius = CornerRadius(6f),
                            )
                            listOf(-10, -5, 0, 5, 10).forEach { tick ->
                                val x = ((tick + 10f) / 20f) * size.width
                                drawLine(
                                    color       = colors.onSurfaceVariant.copy(alpha = 0.18f),
                                    start       = Offset(x, trackY - 10f),
                                    end         = Offset(x, trackY + 10f),
                                    strokeWidth = with(density) { 1.5.dp.toPx() },
                                    cap         = StrokeCap.Round,
                                )
                            }
                            val thumbX = fraction * size.width
                            drawCircle(color = thumbColor.copy(alpha = 0.20f), radius = with(density) { 22.dp.toPx() }, center = Offset(thumbX, trackY))
                            drawCircle(color = colors.cardSurface, radius = with(density) { 16.dp.toPx() }, center = Offset(thumbX, trackY))
                            drawCircle(color = thumbColor, radius = with(density) { 12.dp.toPx() }, center = Offset(thumbX, trackY))
                        }
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(top = 40.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            listOf(-10, -5, -2, 0, 2, 5, 10).forEach { tick ->
                                val active = tick == moodValue
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text(
                                        text       = if (tick > 0) "+$tick" else "$tick",
                                        fontSize   = if (active) 13.sp else 11.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                        color      = if (active) thumbColor else colors.onSurfaceVariant.copy(alpha = 0.55f),
                                        textAlign  = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Causes
            if (categories.isNotEmpty()) {
                val visibleCats = if (showAllCauses) categories else categories.take(6)
                val hiddenCount = (categories.size - 6).coerceAtLeast(0)
                Surface(shape = RoundedCornerShape(20.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text     = stringResource(R.string.checkin_causes_label),
                            style    = MaterialTheme.typography.labelLarge,
                            color    = colors.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        visibleCats.chunked(3).forEach { row ->
                            Row(
                                modifier              = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { cat ->
                                    val sel = cat.id in selectedCauses
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (sel) colors.primaryContainer else Color.Transparent)
                                            .border(1.dp, if (sel) colors.primary else colors.outline, RoundedCornerShape(12.dp))
                                            .clickable {
                                                selectedCauses = if (cat.id in selectedCauses) selectedCauses - cat.id else selectedCauses + cat.id
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(text = cat.emoji, fontSize = 20.sp)
                                            Text(
                                                text      = localizedCauseName(cat),
                                                style     = MaterialTheme.typography.labelSmall,
                                                color     = if (sel) colors.primary else colors.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                maxLines  = 1,
                                            )
                                        }
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        if (hiddenCount > 0 || showAllCauses) {
                            TextButton(
                                onClick        = { showAllCauses = !showAllCauses },
                                contentPadding = PaddingValues(0.dp),
                                colors         = ButtonDefaults.textButtonColors(contentColor = colors.primary),
                            ) {
                                Text(if (showAllCauses) "Show less" else "+$hiddenCount more", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Note toggle
            TextButton(
                onClick = { showNote = !showNote },
                colors  = ButtonDefaults.textButtonColors(contentColor = colors.primary),
            ) {
                Text(if (showNote) stringResource(R.string.checkin_note_hide) else stringResource(R.string.checkin_note_add))
            }
            if (showNote) {
                OutlinedTextField(
                    value         = note,
                    onValueChange = { if (it.length <= 300) note = it },
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text(stringResource(R.string.checkin_note_hint), color = colors.onSurfaceVariant) },
                    minLines      = 3,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = colors.primary,
                        unfocusedBorderColor = colors.outline,
                        focusedTextColor     = colors.onSurface,
                        unfocusedTextColor   = colors.onSurface,
                    ),
                )
                Spacer(Modifier.height(12.dp))
            }

            // Save
            Button(
                onClick = {
                    val dt = LocalDateTime.of(date, LocalTime.of(selectedHour, selectedMinute))
                    val ts = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val causeJson = JSONArray().apply { selectedCauses.forEach { put(it) } }.toString()
                    onSave(MoodEntry(timestamp = ts, moodValue = moodValue, causeIds = causeJson, note = note.trimEnd().ifEmpty { null }))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) {
                Text(stringResource(R.string.button_save), style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    // Time picker dialog
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor   = colors.cardSurface,
            confirmButton = {
                TextButton(onClick = {
                    selectedHour   = timePickerState.hour
                    selectedMinute = timePickerState.minute
                    showTimePicker = false
                }) { Text(stringResource(R.string.button_ok), color = colors.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.button_cancel), color = colors.onSurfaceVariant)
                }
            },
            text = {
                TimePicker(
                    state  = timePickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor          = colors.surface,
                        clockDialSelectedContentColor = colors.cardSurface,
                        clockDialUnselectedContentColor = colors.onSurface,
                        selectorColor           = colors.primary,
                        containerColor          = colors.cardSurface,
                        periodSelectorBorderColor = colors.outline,
                        timeSelectorSelectedContainerColor = colors.primaryContainer,
                        timeSelectorUnselectedContainerColor = colors.surface,
                        timeSelectorSelectedContentColor = colors.primary,
                        timeSelectorUnselectedContentColor = colors.onSurface,
                    ),
                )
            },
        )
    }
}
