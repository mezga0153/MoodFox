package com.moodfox.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import com.moodfox.data.local.AppLogger
import com.moodfox.data.local.BackupManager
import com.moodfox.data.local.PreferencesManager
import com.moodfox.data.local.db.CauseCategoryDao
import com.moodfox.data.local.db.MoodEntryDao
import com.moodfox.data.local.db.WeatherSnapshotDao
import com.moodfox.data.remote.WeatherService
import com.moodfox.domain.ReminderScheduler
import com.moodfox.ui.theme.MoodFoxTheme
import com.moodfox.ui.theme.ThemePreset
import com.moodfox.ui.theme.buildAppColors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var moodEntryDao: MoodEntryDao
    @Inject lateinit var causeCategoryDao: CauseCategoryDao
    @Inject lateinit var weatherSnapshotDao: WeatherSnapshotDao
    @Inject lateinit var weatherService: WeatherService
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var appLogger: AppLogger
    @Inject lateinit var backupManager: BackupManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appLogger.user("App opened")
        enableEdgeToEdge()
        setContent {
            val themePresetName by preferencesManager.themePreset.collectAsState(initial = "PURPLE_DARK")
            val preset = try { ThemePreset.valueOf(themePresetName) } catch (_: Exception) { ThemePreset.PURPLE_DARK }
            val appColors = remember(preset) { buildAppColors(preset.accentHue, preset.mode) }

            LaunchedEffect(appColors.isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (appColors.isDark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (appColors.isDark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
            }

            MoodFoxTheme(appColors = appColors) {
                MoodFoxNavGraph(
                    preferencesManager = preferencesManager,
                    moodEntryDao = moodEntryDao,
                    causeCategoryDao = causeCategoryDao,
                    weatherSnapshotDao = weatherSnapshotDao,
                    weatherService = weatherService,
                    reminderScheduler = reminderScheduler,
                    appLogger = appLogger,
                    backupManager = backupManager,
                )
            }
        }
    }
}
