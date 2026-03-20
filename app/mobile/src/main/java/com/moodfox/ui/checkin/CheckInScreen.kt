package com.moodfox.ui.checkin

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.moodfox.R
import com.moodfox.data.local.db.CauseCategory
import com.moodfox.data.local.db.CauseCategoryDao
import com.moodfox.data.local.db.MoodEntry
import com.moodfox.data.local.db.MoodEntryDao
import com.moodfox.data.local.db.WeatherSnapshotDao
import com.moodfox.data.remote.WeatherService
import com.moodfox.ui.components.HelperBar
import com.moodfox.ui.theme.AppColors
import com.moodfox.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.abs

// ── Emoji anchors (5 key points) ────────────────────────
private val SCALE_EMOJIS = mapOf(
    -10 to "😭", -5 to "😔", 0 to "😐", 5 to "😊", 10 to "🤩",
)

private fun emojiForValue(value: Int): String =
    SCALE_EMOJIS.entries.minByOrNull { abs(it.key - value) }?.value ?: "😐"

// ── Mood color ────────────────────────────────────────────
@Composable
private fun moodColor(value: Int, colors: AppColors): Color = when {
    value > 2  -> colors.secondary
    value < -2 -> colors.tertiary
    else       -> colors.primary
}

// ── Main screen ───────────────────────────────────────────
@Composable
fun CheckInScreen(
    moodEntryDao: MoodEntryDao,
    causeCategoryDao: CauseCategoryDao,
    weatherSnapshotDao: WeatherSnapshotDao,
    weatherService: WeatherService,
    weatherEnabled: Boolean,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface),
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
                                        timestamp = System.currentTimeMillis(),
                                        moodValue = moodValue,
                                        causeIds  = causeJson,
                                        note      = note.trimEnd().ifEmpty { null },
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
            Spacer(Modifier.height(12.dp))
            // Calm zone pill
            Surface(
                shape  = RoundedCornerShape(50.dp),
                color  = colors.accent.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.28f)),
            ) {
                Text(
                    text     = stringResource(R.string.checkin_target_label),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    style    = MaterialTheme.typography.labelMedium,
                    color    = colors.accent,
                )
            }
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

        // Numeric labels
        val numericTicks = listOf(-10, -5, -2, 0, 2, 5, 10)
        Row(
            modifier              = Modifier.fillMaxWidth().padding(top = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            numericTicks.forEach { tick ->
                val isActive = tick == value
                val label    = if (tick > 0) "+$tick" else "$tick"
                Box(
                    modifier         = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = label,
                        fontSize   = if (isActive) 13.sp else 11.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color      = if (isActive) moodColor(value, colors)
                                     else colors.onSurfaceVariant.copy(alpha = 0.55f),
                        textAlign  = TextAlign.Center,
                    )
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
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(
                text     = stringResource(R.string.checkin_causes_label),
                style    = MaterialTheme.typography.labelLarge,
                color    = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            chunked.forEach { row ->
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
            if (hiddenCount > 0 || showAll) {
                TextButton(
                    onClick        = onToggleAll,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                    colors         = ButtonDefaults.textButtonColors(contentColor = colors.primary),
                ) {
                    Text(
                        text  = if (showAll) "Show less" else "+$hiddenCount more",
                        style = MaterialTheme.typography.labelLarge,
                    )
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
            .padding(horizontal = 6.dp, vertical = 10.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = category.emoji, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text      = category.name,
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
            AnimatedVisibility(visible = showNote) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value         = note,
                        onValueChange = onNoteChange,
                        placeholder   = {
                            Text(stringResource(R.string.checkin_note_hint), color = colors.onSurfaceVariant)
                        },
                        modifier      = Modifier.fillMaxWidth(),
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


