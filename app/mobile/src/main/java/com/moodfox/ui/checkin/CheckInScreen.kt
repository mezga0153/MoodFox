package com.moodfox.ui.checkin

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.moodfox.R
import com.moodfox.data.local.db.CauseCategory
import com.moodfox.data.local.db.CauseCategoryDao
import com.moodfox.data.local.db.MoodEntry
import com.moodfox.data.local.db.MoodEntryDao
import com.moodfox.data.local.db.WeatherSnapshotDao
import com.moodfox.data.remote.WeatherService
import com.moodfox.ui.components.HelperBar
import com.moodfox.ui.theme.LocalAppColors
import com.moodfox.ui.theme.AppColors
import kotlinx.coroutines.launch
import org.json.JSONArray

// ── Emoji anchors for the scale ───────────────────────────
private val SCALE_EMOJIS = mapOf(
    -10 to "😭",
    -5  to "😔",
    -2  to "😕",
     0  to "😐",
     2  to "🙂",
     5  to "😊",
    10  to "🤩",
)

// ── Helper: mood value → mixed accent color ───────────────
@Composable
private fun moodColor(value: Int, colors: com.moodfox.ui.theme.AppColors): Color = when {
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

    var moodValue    by remember { mutableIntStateOf(0) }
    var selectedCauses by remember { mutableStateOf(setOf<Long>()) }
    var note         by remember { mutableStateOf("") }
    var saved        by remember { mutableStateOf(false) }
    var showNote     by remember { mutableStateOf(false) }
    var lastSavedMood by remember { mutableIntStateOf(0) }

    // After save: briefly show feedback then reset
    LaunchedEffect(saved) {
        if (saved) {
            kotlinx.coroutines.delay(2000)
            moodValue     = 0
            selectedCauses = emptySet()
            note          = ""
            showNote      = false
            saved         = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        // ── Header ──────────────────────────────────────
        Text(
            text = stringResource(R.string.checkin_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        // ── Current value display ────────────────────────
        val displayColor = moodColor(moodValue, colors)
        val emojiForValue = SCALE_EMOJIS.entries
            .minByOrNull { kotlin.math.abs(it.key - moodValue) }?.value ?: "😐"

        AnimatedMoodDisplay(moodValue = moodValue, emoji = emojiForValue, color = displayColor)

        Spacer(Modifier.height(28.dp))

        // ── Mood scale ───────────────────────────────────
        MoodScaleSlider(
            value    = moodValue,
            onChange = { moodValue = it },
            colors   = colors,
        )

        Spacer(Modifier.height(8.dp))

        // ── Band labels ──────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("-10", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Text(
                text = stringResource(R.string.checkin_target_label),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
            )
            Text("+10", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        }

        Spacer(Modifier.height(28.dp))

        // ── Causes ───────────────────────────────────────
        if (categories.isNotEmpty()) {
            Text(
                text = stringResource(R.string.checkin_causes_label),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
            CauseChipRow(
                categories     = categories,
                selected       = selectedCauses,
                onToggle       = { id ->
                    selectedCauses = if (id in selectedCauses) selectedCauses - id else selectedCauses + id
                },
                colors         = colors,
            )
            Spacer(Modifier.height(16.dp))
        }

        // ── Note toggle ──────────────────────────────────
        TextButton(
            onClick = { showNote = !showNote },
            colors  = ButtonDefaults.textButtonColors(contentColor = colors.primary),
        ) {
            Text(
                if (showNote) stringResource(R.string.checkin_note_hide)
                else stringResource(R.string.checkin_note_add),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        if (showNote) {
            OutlinedTextField(
                value         = note,
                onValueChange = { if (it.length <= 300) note = it },
                placeholder   = { Text(stringResource(R.string.checkin_note_hint), color = colors.onSurfaceVariant) },
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor    = colors.primary,
                    unfocusedBorderColor  = colors.outline,
                    focusedTextColor      = colors.onSurface,
                    unfocusedTextColor    = colors.onSurface,
                    cursorColor           = colors.primary,
                ),
                maxLines      = 4,
                shape         = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(12.dp))

        // ── Save button ──────────────────────────────────
        if (saved) {
            Text(
                text = stringResource(R.string.checkin_saved),
                style = MaterialTheme.typography.titleMedium,
                color = colors.secondary,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Button(
                onClick = {
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
                colors  = ButtonDefaults.buttonColors(containerColor = displayColor),
                shape   = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text  = stringResource(R.string.checkin_save),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (colors.isDark) Color.Black.copy(alpha = 0.85f) else Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // ── Helper reaction ──────────────────────────────
        HelperBar(
            moodValue = lastSavedMood,
            visible   = saved,
            colors    = colors,
            modifier  = Modifier.padding(top = 12.dp),
        )

        Spacer(Modifier.height(24.dp))
    }
}

// ── Animated mood value + emoji display ───────────────────
@Composable
private fun AnimatedMoodDisplay(moodValue: Int, emoji: String, color: Color) {
    val animColor by animateColorAsState(color, animationSpec = tween(250), label = "moodColor")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text  = emoji,
            fontSize = 52.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = if (moodValue >= 0) "+$moodValue" else "$moodValue",
            style = MaterialTheme.typography.headlineLarge,
            color = animColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Mood scale slider ─────────────────────────────────────
@Composable
private fun MoodScaleSlider(
    value: Int,
    onChange: (Int) -> Unit,
    colors: com.moodfox.ui.theme.AppColors,
) {
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbColor by animateColorAsState(moodColor(value, colors), tween(200), label = "thumb")

    // Gradient: tertiary(left) → primary(center) → secondary(right)
    val trackGradient = Brush.horizontalGradient(
        colors = listOf(colors.tertiary, colors.primary, colors.secondary)
    )

    // Fraction of current value in 0..1
    val fraction = (value + 10f) / 20f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(trackWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (trackWidthPx > 0f) {
                            val raw = ((offset.x / trackWidthPx) * 20f - 10f).coerceIn(-10f, 10f)
                            onChange(raw.toInt().coerceIn(-10, 10))
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (trackWidthPx > 0f) {
                            val raw = ((change.position.x / trackWidthPx) * 20f - 10f).coerceIn(-10f, 10f)
                            onChange(raw.toInt().coerceIn(-10, 10))
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackY = size.height / 2f
            val trackHeight = with(density) { 8.dp.toPx() }

            // Track
            drawRoundRect(
                brush        = trackGradient,
                topLeft      = Offset(0f, trackY - trackHeight / 2),
                size         = androidx.compose.ui.geometry.Size(size.width, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2),
            )

            // Target band: -2 to +2 tinted strip
            val bandStart = (2f / 20f) * size.width   // +2 fraction
            val bandEnd   = (18f / 20f) * size.width  // -2 = 8/20, +2 = 12/20
            val b2Start   = (8f / 20f) * size.width
            val b2End     = (12f / 20f) * size.width
            drawRoundRect(
                color        = colors.accent.copy(alpha = 0.25f),
                topLeft      = Offset(b2Start, trackY - trackHeight / 2 - 2f),
                size         = androidx.compose.ui.geometry.Size(b2End - b2Start, trackHeight + 4f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f),
            )

            // Tick marks at -10,-5,-2,0,+2,+5,+10
            val ticks = listOf(-10, -5, -2, 0, 2, 5, 10)
            ticks.forEach { tick ->
                val x = ((tick + 10f) / 20f) * size.width
                drawLine(
                    color       = colors.onSurfaceVariant.copy(alpha = 0.5f),
                    start       = Offset(x, trackY - 14f),
                    end         = Offset(x, trackY + 14f),
                    strokeWidth = with(density) { 1.5.dp.toPx() },
                    cap         = StrokeCap.Round,
                )
            }

            // Thumb
            val thumbX = fraction * size.width
            drawCircle(
                color  = colors.surface,
                radius = with(density) { 14.dp.toPx() },
                center = Offset(thumbX, trackY),
            )
            drawCircle(
                color  = thumbColor,
                radius = with(density) { 11.dp.toPx() },
                center = Offset(thumbX, trackY),
            )
        }

        // Emoji labels along bottom
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SCALE_EMOJIS.keys.sorted().forEach { tick ->
                val isActive = tick == value
                Text(
                    text     = SCALE_EMOJIS[tick] ?: "",
                    fontSize = if (isActive) 20.sp else 14.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Cause chip row ────────────────────────────────────────
@Composable
private fun CauseChipRow(
    categories: List<CauseCategory>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    colors: com.moodfox.ui.theme.AppColors,
) {
    val chunked = categories.chunked(3)
    chunked.forEach { row ->
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            row.forEach { cat ->
                val isSelected  = cat.id in selected
                val borderColor = if (isSelected) colors.primary else colors.outline
                val bgColor     = if (isSelected) colors.primaryContainer else colors.cardSurface
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(bgColor)
                        .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                        .clickable { onToggle(cat.id) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    Text(
                        text      = "${cat.emoji} ${cat.name}",
                        style     = MaterialTheme.typography.labelLarge,
                        color     = if (isSelected) colors.primary else colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines  = 2,
                    )
                }
            }
            // fill empty cells in the last row so weights stay even
            repeat(3 - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}


