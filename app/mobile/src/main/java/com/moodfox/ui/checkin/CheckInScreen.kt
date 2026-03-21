package com.moodfox.ui.checkin

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.input.KeyboardType
import com.moodfox.R
import com.moodfox.data.local.db.CauseCategory
import com.moodfox.data.local.db.CauseCategoryDao
import com.moodfox.data.local.db.MoodEntry
import com.moodfox.data.local.db.MoodEntryDao
import com.moodfox.data.local.db.WeatherSnapshotDao
import com.moodfox.data.remote.WeatherService
import com.moodfox.ui.components.HelperBar
import com.moodfox.ui.components.localizedCauseName
import com.moodfox.ui.theme.AppColors
import com.moodfox.ui.theme.LocalAppColors
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.json.JSONArray

// ── Emoji for every step on the scale ────────────────────
private val SCALE_EMOJIS = mapOf(
    -10 to "😭",
    -9  to "😩",
    -8  to "😢",
    -7  to "😟",
    -6  to "😞",
    -5  to "😔",
    -4  to "😕",
    -3  to "🙁",
    -2  to "😐",
    -1  to "😑",
     0  to "🙂",
     1  to "😊",
     2  to "😌",
     3  to "😀",
     4  to "😄",
     5  to "😁",
     6  to "🤩",
     7  to "😎",
     8  to "🥳",
     9  to "😍",
    10  to "🤯",
)

private fun emojiForValue(value: Int): String = SCALE_EMOJIS[value.coerceIn(-10, 10)] ?: "🙂"

// ── Mood color ────────────────────────────────────────────
@Composable
private fun moodColor(value: Int, colors: AppColors): Color = when {
    value > 2  -> colors.secondary
    value < -2 -> colors.tertiary
    else       -> colors.primary
}
private fun conditionEmoji(condition: String) = when {
    "clear"   in condition.lowercase()                               -> "☀️"
    "cloud"   in condition.lowercase() ||
    "partly"  in condition.lowercase()                               -> "⛅"
    "rain"    in condition.lowercase() ||
    "drizzle" in condition.lowercase() ||
    "shower"  in condition.lowercase()                               -> "🌧️"
    "snow"    in condition.lowercase()                               -> "❄️"
    "thunder" in condition.lowercase()                               -> "⛈️"
    "fog"     in condition.lowercase()                               -> "🌫️"
    else                                                              -> "🌤️"
}
// ── Main screen ───────────────────────────────────────────
@Composable
fun CheckInScreen(
    moodEntryDao: MoodEntryDao,
    causeCategoryDao: CauseCategoryDao,
    weatherSnapshotDao: WeatherSnapshotDao,
    weatherService: WeatherService,
    weatherEnabled: Boolean,
    manualCity: String?,
) {
    val colors = LocalAppColors.current
    val scope  = rememberCoroutineScope()

    val categories by causeCategoryDao.getActive().collectAsState(initial = emptyList())

    var moodValue      by remember { mutableIntStateOf(0) }
    var selectedCauses by remember { mutableStateOf(setOf<Long>()) }
    var note           by remember { mutableStateOf("") }
    var saved          by remember { mutableStateOf(false) }
    var showNote       by remember { mutableStateOf(false) }
    var showAllCauses  by remember { mutableStateOf(false) }
    var lastSavedMood  by remember { mutableIntStateOf(0) }

    val displayColor by animateColorAsState(moodColor(moodValue, colors), tween(250), "dcolor")

    val context = LocalContext.current
    var weatherSnapshotId    by remember { mutableStateOf<Long?>(null) }
    var weatherDisplay        by remember { mutableStateOf<String?>(null) }
    var detectedCondition     by remember { mutableStateOf<String?>(null) }
    var detectedTempC         by remember { mutableStateOf<Float?>(null) }
    var showWeatherOverride   by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.any { it }) {
            scope.launch {
                val loc  = weatherService.getLastKnownLocation(context) ?: return@launch
                val snap = weatherService.fetchCurrent(loc.latitude, loc.longitude) ?: return@launch
                weatherSnapshotId    = weatherSnapshotDao.insert(snap)
                detectedCondition    = snap.condition
                detectedTempC        = snap.temperatureC
                weatherDisplay       = "${conditionEmoji(snap.condition)} ${snap.condition} ${snap.temperatureC.toInt()}°C"
            }
        }
    }

    LaunchedEffect(weatherEnabled) {
        if (!weatherEnabled) return@LaunchedEffect
        // Manual city takes priority over GPS
        if (!manualCity.isNullOrBlank()) {
            scope.launch {
                val snap = weatherService.fetchByCity(manualCity) ?: return@launch
                weatherSnapshotId = weatherSnapshotDao.insert(snap)
                detectedCondition = snap.condition
                detectedTempC     = snap.temperatureC
                weatherDisplay    = "${conditionEmoji(snap.condition)} ${snap.condition} ${snap.temperatureC.toInt()}°C"
            }
            return@LaunchedEffect
        }
        val hasPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            val loc  = weatherService.getLastKnownLocation(context) ?: return@LaunchedEffect
            val snap = weatherService.fetchCurrent(loc.latitude, loc.longitude) ?: return@LaunchedEffect
            weatherSnapshotId = weatherSnapshotDao.insert(snap)
            detectedCondition = snap.condition
            detectedTempC     = snap.temperatureC
            weatherDisplay    = "${conditionEmoji(snap.condition)} ${snap.condition} ${snap.temperatureC.toInt()}°C"
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    LaunchedEffect(saved) {
        if (saved) {
            kotlinx.coroutines.delay(2000)
            moodValue      = 0
            selectedCauses = emptySet()
            note           = ""
            showNote       = false
            showAllCauses  = false
            saved          = false
        }
    }

    val screenGradient = Brush.verticalGradient(
        listOf(colors.primary.copy(alpha = 0.12f), colors.surface),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenGradient)
            .imePadding(),
    ) {
        // ── Scrollable content ────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 28.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MoodHero(moodValue = moodValue, displayColor = displayColor, colors = colors)

            Spacer(Modifier.height(24.dp))

            SliderCard(value = moodValue, onChange = { moodValue = it }, colors = colors)

            Spacer(Modifier.height(16.dp))

            if (categories.isNotEmpty()) {
                CausesCard(
                    categories  = categories,
                    selected    = selectedCauses,
                    onToggle    = { id ->
                        selectedCauses = if (id in selectedCauses) selectedCauses - id
                                        else selectedCauses + id
                    },
                    showAll     = showAllCauses,
                    onToggleAll = { showAllCauses = !showAllCauses },
                    colors      = colors,
                )
                Spacer(Modifier.height(16.dp))
            }

            NoteCard(
                note         = note,
                showNote     = showNote,
                onToggle     = { showNote = !showNote },
                onNoteChange = { if (it.length <= 300) note = it },
                colors       = colors,
            )

            HelperBar(
                moodValue = lastSavedMood,
                visible   = saved,
                colors    = colors,
                modifier  = Modifier.padding(top = 16.dp),
            )

            if (weatherEnabled && weatherDisplay != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape    = RoundedCornerShape(20.dp),
                    color    = colors.cardSurface,
                    modifier = Modifier.clickable { showWeatherOverride = true },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        Text(
                            text  = weatherDisplay!!,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector        = Icons.Filled.EditNote,
                            contentDescription = "Override weather",
                            tint               = colors.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier           = Modifier.size(14.dp),
                        )
                    }
                }
            }

            if (showWeatherOverride) {
                WeatherOverrideSheet(
                    colors             = colors,
                    initialCondition   = detectedCondition,
                    initialTempC       = detectedTempC,
                    onDismiss          = { showWeatherOverride = false },
                    onConfirm = { condition, tempC ->
                        showWeatherOverride = false
                        scope.launch {
                            val snap = com.moodfox.data.local.db.WeatherSnapshot(
                                timestamp    = System.currentTimeMillis(),
                                city         = "Manual",
                                temperatureC = tempC,
                                condition    = condition,
                                isRaining    = "rain" in condition.lowercase() || "drizzle" in condition.lowercase(),
                                humidity     = 0f,
                            )
                            weatherSnapshotId = weatherSnapshotDao.insert(snap)
                            weatherDisplay = "${conditionEmoji(condition)} $condition ${tempC.toInt()}°C"
                        }
                    },
                )
            }
        }

        // ── Sticky save button ────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, colors.surface, colors.surface),
                    )
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            AnimatedContent(
                targetState    = saved,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) },
                label          = "saveState",
            ) { isSaved ->
                if (isSaved) {
                    Text(
                        text       = stringResource(R.string.checkin_saved),
                        modifier   = Modifier.fillMaxWidth(),
                        textAlign  = TextAlign.Center,
                        style      = MaterialTheme.typography.titleMedium,
                        color      = colors.secondary,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Button(
                        onClick  = {
                            scope.launch {
                                val causeJson = JSONArray().apply {
                                    selectedCauses.forEach { put(it) }
                                }.toString()
                                moodEntryDao.insert(
                                    MoodEntry(
                                        timestamp         = System.currentTimeMillis(),
                                        moodValue         = moodValue,
                                        causeIds          = causeJson,
                                        note              = note.trimEnd().ifEmpty { null },
                                        weatherSnapshotId = weatherSnapshotId,
                                    )
                                )
                                lastSavedMood = moodValue
                                saved = true
                            }
                        },
                        colors   = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        shape    = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text(
                            text       = stringResource(R.string.checkin_save),
                            style      = MaterialTheme.typography.titleMedium,
                            color      = if (colors.isDark) Color.Black.copy(alpha = 0.85f) else Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

// ── Mood hero ─────────────────────────────────────────────
@Composable
private fun MoodHero(moodValue: Int, displayColor: Color, colors: AppColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text  = stringResource(R.string.checkin_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        AnimatedContent(
            targetState    = emojiForValue(moodValue),
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label          = "heroEmoji",
        ) { emoji ->
            Text(text = emoji, fontSize = 80.sp)
        }
    }
}

// ── Slider card ───────────────────────────────────────────
@Composable
private fun SliderCard(value: Int, onChange: (Int) -> Unit, colors: AppColors) {
    Surface(
        shape    = RoundedCornerShape(20.dp),
        color    = colors.cardSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MoodScaleSlider(value = value, onChange = onChange, colors = colors)
        }
    }
}

// ── Mood scale slider ─────────────────────────────────────
@Composable
private fun MoodScaleSlider(value: Int, onChange: (Int) -> Unit, colors: AppColors) {
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbColor by animateColorAsState(moodColor(value, colors), tween(200), label = "thumb")
    val trackGradient = Brush.horizontalGradient(
        listOf(colors.tertiary, colors.primary, colors.secondary)
    )
    val fraction = (value + 10f) / 20f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(trackWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (trackWidthPx > 0f) {
                            val raw = ((offset.x / trackWidthPx) * 20f - 10f)
                            onChange(raw.toInt().coerceIn(-10, 10))
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (trackWidthPx > 0f) {
                            val raw = ((change.position.x / trackWidthPx) * 20f - 10f)
                            onChange(raw.toInt().coerceIn(-10, 10))
                        }
                    },
                )
            }
            .pointerInput(trackWidthPx) {
                detectTapGestures { offset ->
                    if (trackWidthPx > 0f) {
                        val raw = ((offset.x / trackWidthPx) * 20f - 10f)
                        onChange(raw.toInt().coerceIn(-10, 10))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackY = size.height * 0.42f
            val trackH = with(density) { 10.dp.toPx() }

            // Track
            drawRoundRect(
                brush        = trackGradient,
                topLeft      = Offset(0f, trackY - trackH / 2),
                size         = Size(size.width, trackH),
                cornerRadius = CornerRadius(trackH / 2),
            )

            // Calm zone highlight
            val b2Start = (8f / 20f) * size.width
            val b2End   = (12f / 20f) * size.width
            drawRoundRect(
                color        = colors.accent.copy(alpha = 0.20f),
                topLeft      = Offset(b2Start, trackY - trackH / 2 - 2f),
                size         = Size(b2End - b2Start, trackH + 4f),
                cornerRadius = CornerRadius(6f),
            )

            // Subtle ticks at key points only
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

            // Thumb
            val thumbX = fraction * size.width
            // glow halo
            drawCircle(
                color  = thumbColor.copy(alpha = 0.20f),
                radius = with(density) { 22.dp.toPx() },
                center = Offset(thumbX, trackY),
            )
            // border ring
            drawCircle(
                color  = colors.cardSurface,
                radius = with(density) { 16.dp.toPx() },
                center = Offset(thumbX, trackY),
            )
            // fill
            drawCircle(
                color  = thumbColor,
                radius = with(density) { 12.dp.toPx() },
                center = Offset(thumbX, trackY),
            )
        }

        // Numeric labels — positioned to match their actual location on the track
        val numericTicks = listOf(-10, -5, -2, 0, 2, 5, 10)
        Layout(
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
            content = {
                numericTicks.forEach { tick ->
                    val isActive = tick == value
                    val label    = if (tick > 0) "+$tick" else "$tick"
                    Text(
                        text       = label,
                        fontSize   = if (isActive) 13.sp else 11.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color      = if (isActive) moodColor(value, colors)
                                     else colors.onSurfaceVariant.copy(alpha = 0.55f),
                        textAlign  = TextAlign.Center,
                    )
                }
            },
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(androidx.compose.ui.unit.Constraints()) }
            val height = placeables.maxOf { it.height }
            layout(constraints.maxWidth, height) {
                numericTicks.forEachIndexed { i, tick ->
                    val fraction = (tick + 10f) / 20f
                    val centerX  = (fraction * constraints.maxWidth).toInt()
                    val x = (centerX - placeables[i].width / 2)
                        .coerceIn(0, constraints.maxWidth - placeables[i].width)
                    placeables[i].placeRelative(x, 0)
                }
            }
        }
    }
}

// ── Causes card ───────────────────────────────────────────
@Composable
private fun CausesCard(
    categories: List<CauseCategory>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    showAll: Boolean,
    onToggleAll: () -> Unit,
    colors: AppColors,
) {
    val visibleCats = if (showAll) categories else categories.take(6)
    val hiddenCount = (categories.size - 6).coerceAtLeast(0)
    val chunked     = visibleCats.chunked(3)

    Surface(
        shape    = RoundedCornerShape(20.dp),
        color    = colors.cardSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = stringResource(R.string.checkin_causes_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
                if (hiddenCount > 0 || showAll) {
                    TextButton(
                        onClick        = onToggleAll,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        colors         = ButtonDefaults.textButtonColors(contentColor = colors.primary),
                    ) {
                        Text(
                            text  = if (showAll) "Show less" else "+$hiddenCount more",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            chunked.forEach { row ->
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { cat ->
                        CauseChip(
                            category = cat,
                            selected = cat.id in selected,
                            onClick  = { onToggle(cat.id) },
                            colors   = colors,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CauseChip(
    category: CauseCategory,
    selected: Boolean,
    onClick: () -> Unit,
    colors: AppColors,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) colors.primary else colors.outline
    val bgColor     = if (selected) colors.primaryContainer else Color.Transparent
    val textColor   = if (selected) colors.primary else colors.onSurfaceVariant

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 7.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = category.emoji, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text      = localizedCauseName(category),
                style     = MaterialTheme.typography.labelSmall,
                color     = textColor,
                textAlign = TextAlign.Center,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Note card ─────────────────────────────────────────────
@Composable
private fun NoteCard(
    note: String,
    showNote: Boolean,
    onToggle: () -> Unit,
    onNoteChange: (String) -> Unit,
    colors: AppColors,
) {
    val noteScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    Surface(
        shape    = RoundedCornerShape(20.dp),
        color    = colors.cardSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
            ) {
                Icon(
                    imageVector        = Icons.Filled.Edit,
                    contentDescription = null,
                    tint               = colors.onSurfaceVariant,
                    modifier           = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text     = if (note.isBlank()) stringResource(R.string.checkin_note_add) else note,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = if (note.isBlank()) colors.onSurfaceVariant else colors.onSurface,
                    maxLines = if (showNote) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(showNote) {
                if (showNote) focusRequester.requestFocus()
            }
            AnimatedVisibility(visible = showNote) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    LaunchedEffect(note) {
                        bringIntoViewRequester.bringIntoView()
                    }
                    OutlinedTextField(
                        value         = note,
                        onValueChange = onNoteChange,
                        placeholder   = {
                            Text(stringResource(R.string.checkin_note_hint), color = colors.onSurfaceVariant)
                        },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .bringIntoViewRequester(bringIntoViewRequester),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = colors.primary,
                            unfocusedBorderColor = colors.outline,
                            focusedTextColor     = colors.onSurface,
                            unfocusedTextColor   = colors.onSurface,
                            cursorColor          = colors.primary,
                        ),
                        maxLines      = 5,
                        shape         = RoundedCornerShape(12.dp),
                    )
                }
            }
        }
    }
}

// ── Weather override sheet ────────────────────────────────
private val WEATHER_CONDITIONS = listOf(
    "Clear", "Sunny", "Partly cloudy", "Cloudy", "Overcast",
    "Drizzle", "Rainy", "Showers", "Thunderstorm", "Snowy", "Foggy",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherOverrideSheet(
    colors: AppColors,
    initialCondition: String?,
    initialTempC: Float?,
    onDismiss: () -> Unit,
    onConfirm: (condition: String, tempC: Float) -> Unit,
) {
    var selectedCondition by remember {
        mutableStateOf(initialCondition?.let { ic ->
            WEATHER_CONDITIONS.firstOrNull { it.equals(ic, ignoreCase = true) } ?: WEATHER_CONDITIONS[0]
        } ?: WEATHER_CONDITIONS[0])
    }
    var tempText by remember { mutableStateOf(initialTempC?.toInt()?.toString() ?: "20") }

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        containerColor    = colors.cardSurface,
        contentColor      = colors.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text       = "Set weather manually",
                style      = MaterialTheme.typography.titleMedium,
                color      = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
            )

            // Condition chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
            ) {
                WEATHER_CONDITIONS.forEach { cond ->
                    val selected = cond == selectedCondition
                    Surface(
                        shape    = RoundedCornerShape(20.dp),
                        color    = if (selected) colors.primary.copy(alpha = 0.2f) else colors.cardSurface,
                        border   = BorderStroke(1.dp, if (selected) colors.primary else colors.outline.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable { selectedCondition = cond },
                    ) {
                        Text(
                            text     = "${conditionEmoji(cond)} $cond",
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = if (selected) colors.primary else colors.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            // Temperature input
            OutlinedTextField(
                value         = tempText,
                onValueChange = { if (it.length <= 5) tempText = it },
                label         = { Text("Temperature (°C)", color = colors.onSurfaceVariant) },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = colors.primary,
                    unfocusedBorderColor = colors.outline,
                    focusedTextColor     = colors.onSurface,
                    unfocusedTextColor   = colors.onSurface,
                    cursorColor          = colors.primary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick  = {
                    val temp = tempText.toFloatOrNull() ?: 20f
                    onConfirm(selectedCondition, temp)
                },
                colors   = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape    = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    text       = "Apply",
                    style      = MaterialTheme.typography.titleSmall,
                    color      = if (colors.isDark) Color.Black.copy(alpha = 0.85f) else Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
