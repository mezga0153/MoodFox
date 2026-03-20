package com.moodfox.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.moodfox.R
import com.moodfox.data.local.AppLogger
import com.moodfox.data.local.BackupManager
import com.moodfox.data.local.PreferencesManager
import com.moodfox.data.local.db.CauseCategory
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
) {
    val colors = LocalAppColors.current
    val scope  = rememberCoroutineScope()

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
    val context    = androidx.compose.ui.platform.LocalContext.current
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text  = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(20.dp))

        // ── Theme section ────────────────────────────────
        SectionHeader(stringResource(R.string.settings_theme), colors)
        Spacer(Modifier.height(10.dp))

        val currentPreset = try { ThemePreset.valueOf(themePresetName) } catch (_: Exception) { ThemePreset.PURPLE_DARK }
        val currentMode   = currentPreset.mode

        // Dark / Light chips
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

        // Color swatches
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

        Spacer(Modifier.height(20.dp))

        // ── Language ─────────────────────────────────────
        SectionHeader(stringResource(R.string.settings_language), colors)
        Spacer(Modifier.height(10.dp))
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

        Spacer(Modifier.height(20.dp))

        // ── Appearance ───────────────────────────────────
        SectionHeader(stringResource(R.string.settings_animations), colors)
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

        Spacer(Modifier.height(20.dp))

        // ── Reminders ────────────────────────────────────
        SectionHeader(stringResource(R.string.settings_reminders), colors)
        SettingsToggle(
            label   = stringResource(R.string.settings_reminders_enabled),
            checked = remindersEnabled,
            icon    = Icons.Filled.NotificationsActive,
            colors  = colors,
            onChange = { enabled ->
                scope.launch {
                    preferencesManager.setRemindersEnabled(enabled)
                    if (enabled) reminderScheduler.scheduleAll(reminderTimes, quietStart, quietEnd)
                    else reminderScheduler.cancelAll()
                }
            },
        )

        Spacer(Modifier.height(20.dp))

        // ── Weather ──────────────────────────────────────
        SectionHeader(stringResource(R.string.settings_weather), colors)
        SettingsToggle(
            label   = stringResource(R.string.settings_weather_enabled),
            checked = weatherEnabled,
            icon    = Icons.Filled.WbSunny,
            colors  = colors,
            onChange = { scope.launch { preferencesManager.setWeatherEnabled(it) } },
        )

        Spacer(Modifier.height(20.dp))

        // ── Categories ───────────────────────────────────
        SectionHeader(stringResource(R.string.settings_categories), colors)
        SettingsNavRow(
            label   = stringResource(R.string.settings_categories),
            icon    = Icons.Filled.Category,
            colors  = colors,
            onClick = onNavigateToCategories,
        )

        Spacer(Modifier.height(20.dp))
        // ── Backup / export ──────────────────────
        SectionHeader(stringResource(R.string.settings_backup), colors)
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

        Spacer(Modifier.height(20.dp))
        // ── Debug ────────────────────────────────────────
        SettingsNavRow(
            label   = "Debug log",
            icon    = Icons.Filled.BugReport,
            colors  = colors,
            onClick = onNavigateToLogViewer,
        )

        Spacer(Modifier.height(24.dp))
    }
}

// ── Category manager ──────────────────────────────────────

@Composable
fun CategoryManagerScreen(
    causeCategoryDao: CauseCategoryDao,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val scope  = rememberCoroutineScope()
    val categories by causeCategoryDao.getAll().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            categories.forEach { cat ->
                CategoryRow(
                    category = cat,
                    colors   = colors,
                    onToggleActive = {
                        scope.launch { causeCategoryDao.update(cat.copy(isActive = !cat.isActive)) }
                    },
                    onDelete = {
                        scope.launch { causeCategoryDao.delete(cat) }
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            onDismiss  = { showAddDialog = false },
            onConfirm  = { name, emoji ->
                scope.launch {
                    val next = (categories.maxOfOrNull { it.sortOrder } ?: 0) + 1
                    causeCategoryDao.insert(CauseCategory(name = name, emoji = emoji, sortOrder = next, isDefault = false))
                }
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun CategoryRow(
    category: CauseCategory,
    colors: AppColors,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(category.emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        Text(
            category.name,
            style  = MaterialTheme.typography.bodyLarge,
            color  = if (category.isActive) colors.onSurface else colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked  = category.isActive,
            onCheckedChange = { onToggleActive() },
            colors   = SwitchDefaults.colors(
                checkedThumbColor       = colors.primary,
                checkedTrackColor       = colors.primaryContainer,
                uncheckedThumbColor     = colors.onSurfaceVariant,
                uncheckedTrackColor     = colors.outline,
            ),
        )
        if (!category.isDefault) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, null, tint = colors.error)
            }
        }
    }
    HorizontalDivider(color = colors.outline)
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
        Lang("",   R.string.language_system),
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
