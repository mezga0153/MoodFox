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
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.res.painterResource
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
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.moodfox.data.local.db.MoodEntry
import com.moodfox.data.local.db.MoodEntryDao
import com.moodfox.data.local.db.WeatherSnapshotDao
import com.moodfox.data.remote.WeatherService
import com.moodfox.ui.checkin.CausesCard
import com.moodfox.ui.checkin.NoteCard
import com.moodfox.ui.checkin.SliderCard
import com.moodfox.ui.checkin.characterDrawableForValue
import com.moodfox.ui.checkin.foxDrawableForValue
import com.moodfox.ui.checkin.emojiForValue
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

private fun conditionEmoji(condition: String): String {
    val c = condition.lowercase()
    return when {
        "thunder" in c              -> "⛈️"
        "snow"    in c              -> "❄️"
        "fog"     in c || "mist" in c -> "🌫️"
        "rain"    in c || "drizzle" in c || "shower" in c -> "🌧️"
        "clear"   in c || "sunny" in c -> "☀️"
        "partly"  in c || "overcast" in c -> "⛅"
        "cloud"   in c              -> "⛅"
        else                        -> "🌤️"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(
    moodEntryDao: MoodEntryDao,
    causeCategoryDao: CauseCategoryDao,
    weatherSnapshotDao: WeatherSnapshotDao,
    weatherService: WeatherService,
    weatherEnabled: Boolean,
    characterMode: String = "fox",
) {
    val colors = LocalAppColors.current
    val today  = LocalDate.now()
    val scope  = rememberCoroutineScope()

    var displayMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDay  by remember { mutableStateOf<LocalDate?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }

    val context           = LocalContext.current
    var pendingSnapshotId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(showAddSheet) {
        if (!showAddSheet || !weatherEnabled) return@LaunchedEffect
        val hasPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return@LaunchedEffect
        val loc  = weatherService.getLastKnownLocation(context) ?: return@LaunchedEffect
        val snap = weatherService.fetchCurrent(loc.latitude, loc.longitude) ?: return@LaunchedEffect
        pendingSnapshotId = weatherSnapshotDao.insert(snap)
    }

    val allCategories by causeCategoryDao.getAll().collectAsState(initial = emptyList())
    val categoryMap: Map<Long, CauseCategory> = remember(allCategories) {
        allCategories.associateBy { it.id }
    }

    val allSnapshots by weatherSnapshotDao.getAll().collectAsState(initial = emptyList())
    val snapshotMap: Map<Long, com.moodfox.data.local.db.WeatherSnapshot> = remember(allSnapshots) {
        allSnapshots.associateBy { it.id }
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

        // ── List view ─────────────────────────────────────
        CalendarListView(
            month         = displayMonth,
            today         = today,
            monthStats    = monthStats,
            allCategories = allCategories,
            categoryMap   = categoryMap,
            snapshotMap   = snapshotMap,
            colors        = colors,
            characterMode = characterMode,
            onAddEntry    = { day -> selectedDay = day; showAddSheet = true },
            onDeleteEntry = { entry -> scope.launch { moodEntryDao.delete(entry) } },
        )
    }

    // ── Add entry bottom sheet ───────────────────────────
    if (showAddSheet && selectedDay != null) {
        AddEntrySheet(
            date             = selectedDay!!,
            categories       = allCategories.filter { it.isActive },
            onDismiss        = { showAddSheet = false },
            onSave           = { entry ->
                scope.launch {
                    moodEntryDao.insert(entry.copy(weatherSnapshotId = pendingSnapshotId))
                    pendingSnapshotId = null
                    showAddSheet = false
                }
            },
            colors           = colors,
            characterMode    = characterMode,
        )
    }
}

// ── Calendar list view ────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarListView(
    month: YearMonth,
    today: LocalDate,
    monthStats: Map<LocalDate, DayStats>,
    allCategories: List<CauseCategory>,
    categoryMap: Map<Long, CauseCategory>,
    snapshotMap: Map<Long, com.moodfox.data.local.db.WeatherSnapshot>,
    colors: AppColors,
    characterMode: String,
    onAddEntry: (LocalDate) -> Unit,
    onDeleteEntry: (MoodEntry) -> Unit,
) {
    val days = remember(month, today) {
        (1..month.lengthOfMonth())
            .map { month.atDay(it) }
            .filter { it <= today }
            .reversed()
    }

    data class WeekGroup(val weekStart: LocalDate, val days: List<LocalDate>)
    val byWeek = remember(days) {
        days.groupBy { date ->
            date.minusDays((date.dayOfWeek.value - 1).toLong())
        }.entries
            .sortedByDescending { it.key }
            .map { (start, ds) -> WeekGroup(start, ds.sortedDescending()) }
    }

    var expandedDay by remember { mutableStateOf<LocalDate?>(null) }
    val fmt = java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        byWeek.forEach { week ->
            val weekEnd  = week.weekStart.plusDays(6)
            val endLabel = if (weekEnd.month == month.month) "${weekEnd.dayOfMonth}" else weekEnd.format(fmt)
            val header   = "${week.weekStart.format(fmt)}–$endLabel"

            item(key = "week-${week.weekStart}") {
                Text(
                    text     = header,
                    style    = MaterialTheme.typography.labelMedium,
                    color    = colors.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                )
            }

            items(week.days, key = { it.toString() }) { date ->
                val stats          = monthStats[date]
                val hasEntries     = (stats?.entries?.size ?: 0) > 0
                val isExpanded     = expandedDay == date
                var expandedNotes  by remember { mutableStateOf(setOf<Long>()) }
                val avgColor   = if (hasEntries) moodColor(stats!!.avg, colors) else colors.outline

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.cardSurface),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = hasEntries) { expandedDay = if (isExpanded) null else date }
                            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.width(44.dp)) {
                            Text(
                                text       = date.dayOfMonth.toString(),
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color      = if (hasEntries) colors.onSurface else colors.onSurfaceVariant,
                            )
                            Text(
                                text  = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        if (hasEntries) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(colors.outline.copy(alpha = 0.25f)),
                            ) {
                                val fraction = ((stats!!.avg + 10f) / 20f).coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(avgColor),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text       = if (stats!!.avg >= 0f) "+%.1f".format(stats.avg) else "%.1f".format(stats.avg),
                                style      = MaterialTheme.typography.labelLarge,
                                color      = avgColor,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(4.dp))
                            if (characterMode != "emoji") {
                                Image(
                                    painter = painterResource(characterDrawableForValue(characterMode, stats!!.avg.toInt())),
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                )
                            } else {
                                Text(text = moodEmoji(stats!!.avg.toInt()), fontSize = 28.sp)
                            }
                            Icon(
                                imageVector        = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint               = colors.onSurfaceVariant,
                                modifier           = Modifier.size(18.dp),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                            Text(
                                text  = "—",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                        }
                        IconButton(onClick = { onAddEntry(date) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = "Add entry", tint = colors.primary, modifier = Modifier.size(18.dp))
                        }
                    }

                    if (isExpanded && stats != null) {
                        HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                        stats.entries.sortedByDescending { it.timestamp }.forEach { entry ->
                            val time         = Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).toLocalTime()
                            val timeStr      = "%02d:%02d".format(time.hour, time.minute)
                            val ec           = moodColor(entry.moodValue.toFloat(), colors)
                            val hasNote      = !entry.note.isNullOrBlank()
                            val noteExpanded = entry.id in expandedNotes
                            val entryCauses  = remember(entry.causeIds, categoryMap) {
                                val arr = JSONArray(entry.causeIds)
                                (0 until arr.length()).mapNotNull { categoryMap[arr.getLong(it)] }
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                val snap = entry.weatherSnapshotId?.let { snapshotMap[it] }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(timeStr, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.width(40.dp))
                                    if (characterMode != "emoji") {
                                        Image(
                                            painter = painterResource(characterDrawableForValue(characterMode, entry.moodValue)),
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                        )
                                    } else {
                                        Text(moodEmoji(entry.moodValue), fontSize = 36.sp)
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text       = if (entry.moodValue >= 0) "+${entry.moodValue}" else "${entry.moodValue}",
                                        style      = MaterialTheme.typography.labelLarge,
                                        color      = ec,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    FlowRow(
                                        modifier              = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalArrangement   = Arrangement.spacedBy(3.dp),
                                    ) {
                                        entryCauses.forEach { cat ->
                                            Surface(
                                                shape  = RoundedCornerShape(20.dp),
                                                color  = ec.copy(alpha = 0.12f),
                                                border = BorderStroke(1.dp, ec.copy(alpha = 0.3f)),
                                            ) {
                                                Text(
                                                    text     = "${cat.emoji} ${localizedCauseName(cat)}",
                                                    style    = MaterialTheme.typography.labelSmall,
                                                    color    = colors.onSurface,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                        if (snap != null) {
                                            Surface(
                                                shape = RoundedCornerShape(20.dp),
                                                color = colors.outline.copy(alpha = 0.15f),
                                            ) {
                                                Text(
                                                    text     = "${conditionEmoji(snap.condition)} ${snap.temperatureC.toInt()}°C",
                                                    style    = MaterialTheme.typography.labelSmall,
                                                    color    = colors.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                    if (hasNote) {
                                        IconButton(
                                            onClick  = { expandedNotes = if (noteExpanded) expandedNotes - entry.id else expandedNotes + entry.id },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Comment,
                                                contentDescription = "Note",
                                                tint     = if (noteExpanded) colors.primary else colors.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                    IconButton(onClick = { onDeleteEntry(entry) }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = colors.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                                // Note (on click of comment icon)
                                if (hasNote && noteExpanded) {
                                    Text(
                                        text     = entry.note.orEmpty(),
                                        style    = MaterialTheme.typography.bodySmall,
                                        color    = colors.onSurface,
                                        modifier = Modifier.padding(start = 52.dp, end = 12.dp, bottom = 8.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
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
    characterMode: String,
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

    val thumbColor = when {
        moodValue > 2  -> colors.secondary
        moodValue < -2 -> colors.tertiary
        else           -> colors.primary
    }
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = colors.cardSurface,
        dragHandle       = { BottomSheetDefaults.DragHandle(color = colors.outline) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
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

            // Character + score
            if (characterMode != "emoji") {
                Image(
                    painter = painterResource(characterDrawableForValue(characterMode, moodValue)),
                    contentDescription = "Mood $moodValue",
                    modifier = Modifier.size(100.dp),
                )
            } else {
                Text(text = emojiForValue(moodValue), fontSize = 56.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text       = if (moodValue >= 0) "+$moodValue" else "$moodValue",
                style      = MaterialTheme.typography.headlineSmall,
                color      = thumbColor,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))

            // Slider (shared with check-in)
            SliderCard(value = moodValue, onChange = { moodValue = it }, colors = colors)

            Spacer(Modifier.height(16.dp))

            // Causes (shared with check-in)
            if (categories.isNotEmpty()) {
                CausesCard(
                    categories  = categories,
                    selected    = selectedCauses,
                    onToggle    = { id -> selectedCauses = if (id in selectedCauses) selectedCauses - id else selectedCauses + id },
                    showAll     = showAllCauses,
                    onToggleAll = { showAllCauses = !showAllCauses },
                    colors      = colors,
                )
                Spacer(Modifier.height(12.dp))
            }

            // Note (shared with check-in)
            NoteCard(
                note         = note,
                showNote     = showNote,
                onToggle     = { showNote = !showNote },
                onNoteChange = { if (it.length <= 300) note = it },
                scrollState  = scrollState,
                colors       = colors,
            )

            Spacer(Modifier.height(16.dp))

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
