package com.moodfox.ui.settings

import android.content.Intent
import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import com.moodfox.R
import com.moodfox.data.local.AppLogger
import com.moodfox.data.local.BackupManager
import com.moodfox.data.local.PreferencesManager
import com.moodfox.data.local.db.CauseCategory
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.moodfox.data.local.db.CauseCategoryDao
import com.moodfox.domain.ReminderScheduler
import com.moodfox.ui.theme.*
import kotlinx.coroutines.launch

// ── Settings screen ───────────────────────────────────────

@Composable
fun SettingsScreen(
    preferencesManager: PreferencesManager,
    reminderScheduler: ReminderScheduler,
    moodEntryDao: com.moodfox.data.local.db.MoodEntryDao,
    backupManager: BackupManager,
    onNavigateToCategories: () -> Unit,
    onNavigateToLogViewer: () -> Unit,
    onNavigateToHowItWorks: () -> Unit,
) {
    val colors = LocalAppColors.current
    val scope  = rememberCoroutineScope()
    val context = LocalContext.current
    val themePresetName by preferencesManager.themePreset.collectAsState(initial = "PURPLE_DARK")
    val weatherEnabled  by preferencesManager.weatherEnabled.collectAsState(initial = false)
    val remindersEnabled by preferencesManager.remindersEnabled.collectAsState(initial = false)
    val reminderTimes   by preferencesManager.reminderTimes.collectAsState(initial = "[\"09:00\",\"13:00\",\"19:00\"]")
    val quietStart      by preferencesManager.quietHoursStart.collectAsState(initial = "22:00")
    val quietEnd        by preferencesManager.quietHoursEnd.collectAsState(initial = "08:00")
    val animationsEnabled by preferencesManager.animationsEnabled.collectAsState(initial = true)
    val reduceMotion    by preferencesManager.reduceMotion.collectAsState(initial = false)
    val language        by preferencesManager.language.collectAsState(initial = "")

    val allEntries by moodEntryDao.getAll().collectAsState(initial = emptyList())
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    val screenGradient = Brush.verticalGradient(
        listOf(colors.primary.copy(alpha = 0.12f), colors.surface),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenGradient)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text  = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
        )

        val currentPreset = try { ThemePreset.valueOf(themePresetName) } catch (_: Exception) { ThemePreset.PURPLE_DARK }
        val currentMode   = currentPreset.mode

        // ── Theme ─────────────────────────────────────────────
        SettingsSection(stringResource(R.string.settings_theme), colors) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToneChip(
                    label    = stringResource(R.string.theme_dark),
                    selected = currentMode == ThemeMode.DARK,
                    colors   = colors,
                ) {
                    val target = ThemePreset.entries.first { it.accentHue == currentPreset.accentHue && it.mode == ThemeMode.DARK }
                    scope.launch { preferencesManager.setThemePreset(target.name) }
                }
                ToneChip(
                    label    = stringResource(R.string.theme_light),
                    selected = currentMode == ThemeMode.LIGHT,
                    colors   = colors,
                ) {
                    val target = ThemePreset.entries.first { it.accentHue == currentPreset.accentHue && it.mode == ThemeMode.LIGHT }
                    scope.launch { preferencesManager.setThemePreset(target.name) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier              = Modifier.fillMaxWidth(),
            ) {
                ThemePreset.entries.groupBy { it.accentHue }.forEach { (hue, presets) ->
                    val selected     = currentPreset.accentHue == hue
                    val previewColor = buildAppColors(hue, currentMode).primary
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .then(
                                if (selected) Modifier.border(2.5.dp, colors.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable {
                                val target = presets.first { it.mode == currentMode }
                                scope.launch { preferencesManager.setThemePreset(target.name) }
                            },
                    )
                }
            }
        }

        // ── Language ──────────────────────────────────────────
        SettingsSection(stringResource(R.string.settings_language), colors) {
            LanguagePicker(
                current  = language,
                colors   = colors,
                onSelect = { tag ->
                    scope.launch {
                        preferencesManager.setLanguage(tag)   // persist first
                        val localeList = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                                         else LocaleListCompat.forLanguageTags(tag)
                        AppCompatDelegate.setApplicationLocales(localeList)
                    }
                },
            )
        }

        // ── Appearance ────────────────────────────────────────
        SettingsSection(stringResource(R.string.settings_animations), colors) {
            SettingsToggle(
                label   = stringResource(R.string.settings_animations),
                checked = animationsEnabled,
                icon    = Icons.Filled.Animation,
                colors  = colors,
                onChange = { scope.launch { preferencesManager.setAnimationsEnabled(it) } },
            )
            SettingsToggle(
                label   = stringResource(R.string.settings_reduce_motion),
                checked = reduceMotion,
                icon    = Icons.Filled.MotionPhotosOff,
                colors  = colors,
                onChange = { scope.launch { preferencesManager.setReduceMotion(it) } },
            )
        }

        // ── Reminders ─────────────────────────────────────────
        SettingsSection(stringResource(R.string.settings_reminders), colors) {
            // Notification-permission launcher (Android 13+)
            val notifPermLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) {
                        scope.launch {
                            preferencesManager.setRemindersEnabled(true)
                            reminderScheduler.scheduleAll(reminderTimes, quietStart, quietEnd)
                        }
                    }
                }
            } else null

            SettingsToggle(
                label   = stringResource(R.string.settings_reminders_enabled),
                checked = remindersEnabled,
                icon    = Icons.Filled.NotificationsActive,
                colors  = colors,
                onChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifPermLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        scope.launch {
                            preferencesManager.setRemindersEnabled(enabled)
                            if (enabled) reminderScheduler.scheduleAll(reminderTimes, quietStart, quietEnd)
                            else reminderScheduler.cancelAll()
                        }
                    }
                },
            )

            if (remindersEnabled) {
                Spacer(Modifier.height(8.dp))

                // Parse current times
                val parsedTimes = remember(reminderTimes) {
                    try {
                        val arr = org.json.JSONArray(reminderTimes)
                        (0 until arr.length()).map { arr.getString(it) }
                    } catch (_: Exception) { listOf("09:00") }
                }

                // Time chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    parsedTimes.forEach { t ->
                        Surface(
                            shape  = RoundedCornerShape(20.dp),
                            color  = colors.primary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f)),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            ) {
                                Text(t, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                                if (parsedTimes.size > 1) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                val newList = parsedTimes.filter { it != t }
                                                val newJson = org.json.JSONArray(newList).toString()
                                                preferencesManager.setReminderTimes(newJson)
                                                reminderScheduler.scheduleAll(newJson, quietStart, quietEnd)
                                            }
                                        },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Remove", tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                    }
                                } else {
                                    Spacer(Modifier.width(8.dp))
                                }
                            }
                        }
                    }

                    // Add time button (limit 8 slots)
                    if (parsedTimes.size < 8) {
                        Surface(
                            shape  = RoundedCornerShape(20.dp),
                            color  = colors.cardSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.outline.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable {
                                val defaultH = 12; val defaultM = 0
                                TimePickerDialog(context, { _, h, m ->
                                    scope.launch {
                                        val timeStr  = "%02d:%02d".format(h, m)
                                        val newList  = (parsedTimes + timeStr).distinct().sorted()
                                        val newJson  = org.json.JSONArray(newList).toString()
                                        preferencesManager.setReminderTimes(newJson)
                                        reminderScheduler.scheduleAll(newJson, quietStart, quietEnd)
                                    }
                                }, defaultH, defaultM, true).show()
                            },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.settings_reminders_add_time), style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Quiet hours row
                var showQuietStartPicker by remember { mutableStateOf(false) }
                var showQuietEndPicker   by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.BedtimeOff, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = stringResource(R.string.settings_quiet_hours),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showQuietStartPicker = true }) {
                        Text(quietStart, color = colors.primary)
                    }
                    Text("–", color = colors.onSurfaceVariant)
                    TextButton(onClick = { showQuietEndPicker = true }) {
                        Text(quietEnd, color = colors.primary)
                    }
                }

                if (showQuietStartPicker) {
                    val (h, m) = quietStart.split(":").map { it.toIntOrNull() ?: 0 }
                    TimePickerDialog(context, { _, hour, min ->
                        scope.launch {
                            val v = "%02d:%02d".format(hour, min)
                            preferencesManager.setQuietHoursStart(v)
                            reminderScheduler.scheduleAll(reminderTimes, v, quietEnd)
                        }
                        showQuietStartPicker = false
                    }, h, m, true).also {
                        it.setOnDismissListener { showQuietStartPicker = false }
                    }.show()
                }
                if (showQuietEndPicker) {
                    val (h, m) = quietEnd.split(":").map { it.toIntOrNull() ?: 0 }
                    TimePickerDialog(context, { _, hour, min ->
                        scope.launch {
                            val v = "%02d:%02d".format(hour, min)
                            preferencesManager.setQuietHoursEnd(v)
                            reminderScheduler.scheduleAll(reminderTimes, quietStart, v)
                        }
                        showQuietEndPicker = false
                    }, h, m, true).also {
                        it.setOnDismissListener { showQuietEndPicker = false }
                    }.show()
                }
            }
        }

        // ── Weather ───────────────────────────────────────────
        SettingsSection(stringResource(R.string.settings_weather), colors) {
            SettingsToggle(
                label   = stringResource(R.string.settings_weather_enabled),
                checked = weatherEnabled,
                icon    = Icons.Filled.WbSunny,
                colors  = colors,
                onChange = { scope.launch { preferencesManager.setWeatherEnabled(it) } },
            )
        }

        // ── Causes ────────────────────────────────────────────
        SettingsSection(stringResource(R.string.settings_categories), colors) {
            SettingsNavRow(
                label   = stringResource(R.string.settings_categories),
                icon    = Icons.Filled.Category,
                colors  = colors,
                onClick = onNavigateToCategories,
            )
        }

        // ── Backup ────────────────────────────────────────────
        SettingsSection(stringResource(R.string.settings_backup), colors) {
            SettingsNavRow(
                label   = stringResource(R.string.backup_export_csv),
                icon    = Icons.Filled.FileDownload,
                colors  = colors,
                onClick = {
                    scope.launch {
                        val intent = backupManager.exportCsv(allEntries)
                        shareLauncher.launch(intent)
                    }
                },
            )
            SettingsNavRow(
                label   = stringResource(R.string.backup_export_json),
                icon    = Icons.Filled.FileDownload,
                colors  = colors,
                onClick = {
                    scope.launch {
                        val intent = backupManager.exportJson(allEntries)
                        shareLauncher.launch(intent)
                    }
                },
            )
        }

        // ── About ─────────────────────────────────────────────
        SettingsSection(stringResource(R.string.settings_about), colors) {
            // Version
            val versionName = remember {
                try { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                catch (_: Exception) { "0.2.0" }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = stringResource(R.string.about_version),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                )
                Text(
                    text  = versionName ?: "0.2.0",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            // How it works
            SettingsNavRow(
                label   = stringResource(R.string.settings_how_it_works),
                icon    = Icons.Filled.Info,
                colors  = colors,
                onClick = onNavigateToHowItWorks,
            )
            // Open source licenses
            SettingsNavRow(
                label   = stringResource(R.string.about_licenses),
                icon    = Icons.Filled.Description,
                colors  = colors,
                onClick = {
                    context.startActivity(
                        Intent(context, com.google.android.gms.oss.licenses.OssLicensesMenuActivity::class.java)
                    )
                },
            )
            // Debug logs
            SettingsNavRow(
                label   = stringResource(R.string.settings_debug_logs),
                icon    = Icons.Filled.BugReport,
                colors  = colors,
                onClick = onNavigateToLogViewer,
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Category manager ──────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerScreen(
    causeCategoryDao: CauseCategoryDao,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val scope  = rememberCoroutineScope()
    val dbCategories by causeCategoryDao.getAll().collectAsState(initial = emptyList())

    // Local mutable list for immediate drag feedback
    var localList by remember(dbCategories) { mutableStateOf(dbCategories) }

    var showAddDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        localList = localList.toMutableList().also { it.add(to.index, it.removeAt(from.index)) }
    }

    // Persist new order whenever localList changes (debounced by coroutine)
    LaunchedEffect(localList) {
        causeCategoryDao.updateSortOrders(
            localList.mapIndexed { index, cat -> cat.id to index }
        )
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title), color = colors.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.primary)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, null, tint = colors.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            items(localList, key = { it.id }) { cat ->
                ReorderableItem(reorderState, key = cat.id) { isDragging ->
                    val elevation = if (isDragging) 4.dp else 0.dp
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation)
                            .background(if (isDragging) colors.cardSurface else Color.Transparent),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Drag handle — long-press to drag
                            Icon(
                                imageVector = Icons.Filled.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier
                                    .draggableHandle(
                                        onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                        onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    )
                                    .padding(end = 8.dp),
                            )
                            Text(cat.emoji, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                cat.name,
                                style    = MaterialTheme.typography.bodyLarge,
                                color    = if (cat.isActive) colors.onSurface else colors.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked         = cat.isActive,
                                onCheckedChange = { scope.launch { causeCategoryDao.update(cat.copy(isActive = !cat.isActive)) } },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor   = colors.primary,
                                    checkedTrackColor   = colors.primaryContainer,
                                    uncheckedThumbColor = colors.onSurfaceVariant,
                                    uncheckedTrackColor = colors.outline,
                                ),
                            )
                            if (!cat.isDefault) {
                                IconButton(onClick = { scope.launch { causeCategoryDao.delete(cat) } }) {
                                    Icon(Icons.Filled.Delete, null, tint = colors.error)
                                }
                            }
                        }
                        HorizontalDivider(color = colors.outline)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm  = { name, emoji ->
                scope.launch {
                    val next = (localList.maxOfOrNull { it.sortOrder } ?: 0) + 1
                    causeCategoryDao.insert(CauseCategory(name = name, emoji = emoji, sortOrder = next, isDefault = false))
                }
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun AddCategoryDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name  by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    val colors = LocalAppColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = colors.cardSurface,
        title = { Text("Add cause", color = colors.onSurface) },
        text = {
            Column {
                OutlinedTextField(
                    value = emoji, onValueChange = { emoji = it },
                    label = { Text("Emoji", color = colors.onSurfaceVariant) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outline,
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name", color = colors.onSurfaceVariant) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outline,
                        focusedTextColor = colors.onSurface,
                        unfocusedTextColor = colors.onSurface,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { if (name.isNotBlank() && emoji.isNotBlank()) onConfirm(name.trim(), emoji.trim()) },
                enabled  = name.isNotBlank() && emoji.isNotBlank(),
            ) { Text("Add", color = colors.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.onSurfaceVariant) }
        },
    )
}

// ── Log viewer ────────────────────────────────────────────

@Composable
fun LogViewerScreen(
    appLogger: AppLogger,
    onBack: () -> Unit,
) {
    val colors  = LocalAppColors.current
    val logText = remember { appLogger.read() }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Debug log", color = colors.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colors.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text  = logText.ifEmpty { "No log entries." },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

// ── Shared composables ────────────────────────────────────

@Composable
private fun ToneChip(label: String, selected: Boolean, colors: AppColors, onClick: () -> Unit) {
    val bg     = if (selected) colors.primary else colors.cardSurface
    val border = if (selected) colors.primary else colors.outline
    val text   = if (selected) (if (colors.isDark) Color.Black.copy(alpha = 0.85f) else Color.White)
                 else colors.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = text)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguagePicker(current: String, colors: AppColors, onSelect: (String) -> Unit) {
    data class Lang(val tag: String, val labelRes: Int)
    val langs = listOf(
        Lang("en", R.string.language_en),
        Lang("sl", R.string.language_sl),
        Lang("hu", R.string.language_hu),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
    ) {
        langs.forEach { lang ->
            ToneChip(
                label    = stringResource(lang.labelRes),
                selected = current == lang.tag,
                colors   = colors,
            ) { onSelect(lang.tag) }
        }
    }
}

@Composable
private fun SettingsSection(title: String, colors: AppColors, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardSurface)
            .padding(16.dp),
    ) {
        Text(
            text  = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun SectionHeader(label: String, colors: AppColors) {
    Text(
        text  = label,
        style = MaterialTheme.typography.titleMedium,
        color = colors.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    icon: ImageVector,
    colors: AppColors,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface, modifier = Modifier.weight(1f))
        Switch(
            checked  = checked,
            onCheckedChange = onChange,
            colors   = SwitchDefaults.colors(
                checkedThumbColor   = colors.primary,
                checkedTrackColor   = colors.primaryContainer,
                uncheckedThumbColor = colors.onSurfaceVariant,
                uncheckedTrackColor = colors.outline,
            ),
        )
    }
}

@Composable
private fun SettingsNavRow(
    label: String,
    icon: ImageVector,
    colors: AppColors,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = colors.onSurfaceVariant)
    }
}
