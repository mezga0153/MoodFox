package com.moodfox.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.*
import androidx.core.os.LocaleListCompat
import com.moodfox.data.local.BackupManager
import com.moodfox.data.local.PreferencesManager
import com.moodfox.data.local.db.CauseCategoryDao
import com.moodfox.data.local.db.MoodEntryDao
import com.moodfox.data.local.db.MoonPhaseSnapshotDao
import com.moodfox.data.local.db.WeatherSnapshotDao
import com.moodfox.data.remote.WeatherService
import com.moodfox.domain.ReminderScheduler
import com.moodfox.ui.theme.MoodFoxTheme
import com.moodfox.ui.theme.ThemePreset
import com.moodfox.ui.theme.buildAppColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var moodEntryDao: MoodEntryDao
    @Inject lateinit var causeCategoryDao: CauseCategoryDao
    @Inject lateinit var weatherSnapshotDao: WeatherSnapshotDao
    @Inject lateinit var moonPhaseSnapshotDao: MoonPhaseSnapshotDao
    @Inject lateinit var weatherService: WeatherService
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var backupManager: BackupManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // On first launch language is "". Auto-pick system language (en/sl/hu) or fall back to en.
        val supportedLanguages = listOf("en", "sl", "hu")
        val savedLanguage = runBlocking { preferencesManager.language.first() }
        if (savedLanguage.isEmpty()) {
            val systemLang = resources.configuration.locales[0].language
            val picked = if (systemLang in supportedLanguages) systemLang else "en"
            runBlocking { preferencesManager.setLanguage(picked) }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(picked))
        }

        enableEdgeToEdge()
        setContent {
            val themePresetName by preferencesManager.themePreset.collectAsState(initial = "PURPLE_DARK")
            val preset = try { ThemePreset.valueOf(themePresetName) } catch (_: Exception) { ThemePreset.PURPLE_DARK }
            val appColors = remember(preset) { buildAppColors(preset.accentHue, preset.mode, preset.satScale) }

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
                    moonPhaseSnapshotDao = moonPhaseSnapshotDao,
                    weatherService = weatherService,
                    reminderScheduler = reminderScheduler,
                    backupManager = backupManager,
                )
            }
        }
    }
}
