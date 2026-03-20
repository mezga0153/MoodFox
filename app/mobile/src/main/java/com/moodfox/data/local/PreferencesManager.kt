package com.moodfox.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_SELECTED_HELPER        = stringPreferencesKey("selected_helper")
        private val KEY_HELPER_ENABLED         = booleanPreferencesKey("helper_enabled")
        private val KEY_ANIMATIONS_ENABLED     = booleanPreferencesKey("animations_enabled")
        private val KEY_REDUCE_MOTION          = booleanPreferencesKey("reduce_motion")
        private val KEY_WEATHER_ENABLED        = booleanPreferencesKey("weather_enabled")
        private val KEY_MANUAL_CITY            = stringPreferencesKey("manual_city")
        private val KEY_REMINDERS_ENABLED      = booleanPreferencesKey("reminders_enabled")
        private val KEY_REMINDER_TIMES         = stringPreferencesKey("reminder_times")
        private val KEY_QUIET_HOURS_START      = stringPreferencesKey("quiet_hours_start")
        private val KEY_QUIET_HOURS_END        = stringPreferencesKey("quiet_hours_end")
        private val KEY_THEME_PRESET           = stringPreferencesKey("theme_preset")
        private val KEY_ANALYSIS_DEFAULT_RANGE = intPreferencesKey("analysis_default_range")
        private val KEY_ONBOARDING_COMPLETE    = booleanPreferencesKey("onboarding_complete")
    }

    val selectedHelper: Flow<String> = context.dataStore.data.map { it[KEY_SELECTED_HELPER] ?: "fox" }
    val helperEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_HELPER_ENABLED] ?: true }
    val animationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_ANIMATIONS_ENABLED] ?: true }
    val reduceMotion: Flow<Boolean> = context.dataStore.data.map { it[KEY_REDUCE_MOTION] ?: false }
    val weatherEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_WEATHER_ENABLED] ?: false }
    val manualCity: Flow<String?> = context.dataStore.data.map { it[KEY_MANUAL_CITY] }
    val remindersEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_REMINDERS_ENABLED] ?: false }
    val reminderTimes: Flow<String> = context.dataStore.data.map { it[KEY_REMINDER_TIMES] ?: "[\"09:00\",\"13:00\",\"19:00\"]" }
    val quietHoursStart: Flow<String> = context.dataStore.data.map { it[KEY_QUIET_HOURS_START] ?: "22:00" }
    val quietHoursEnd: Flow<String> = context.dataStore.data.map { it[KEY_QUIET_HOURS_END] ?: "08:00" }
    val themePreset: Flow<String> = context.dataStore.data.map { it[KEY_THEME_PRESET] ?: "PURPLE_DARK" }
    val analysisDefaultRange: Flow<Int> = context.dataStore.data.map { it[KEY_ANALYSIS_DEFAULT_RANGE] ?: 30 }
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDING_COMPLETE] ?: false }

    suspend fun setSelectedHelper(value: String) = context.dataStore.edit { it[KEY_SELECTED_HELPER] = value }
    suspend fun setHelperEnabled(value: Boolean) = context.dataStore.edit { it[KEY_HELPER_ENABLED] = value }
    suspend fun setAnimationsEnabled(value: Boolean) = context.dataStore.edit { it[KEY_ANIMATIONS_ENABLED] = value }
    suspend fun setReduceMotion(value: Boolean) = context.dataStore.edit { it[KEY_REDUCE_MOTION] = value }
    suspend fun setWeatherEnabled(value: Boolean) = context.dataStore.edit { it[KEY_WEATHER_ENABLED] = value }
    suspend fun setManualCity(value: String?) = context.dataStore.edit {
        if (value != null) it[KEY_MANUAL_CITY] = value else it.remove(KEY_MANUAL_CITY)
    }
    suspend fun setRemindersEnabled(value: Boolean) = context.dataStore.edit { it[KEY_REMINDERS_ENABLED] = value }
    suspend fun setReminderTimes(value: String) = context.dataStore.edit { it[KEY_REMINDER_TIMES] = value }
    suspend fun setQuietHoursStart(value: String) = context.dataStore.edit { it[KEY_QUIET_HOURS_START] = value }
    suspend fun setQuietHoursEnd(value: String) = context.dataStore.edit { it[KEY_QUIET_HOURS_END] = value }
    suspend fun setThemePreset(value: String) = context.dataStore.edit { it[KEY_THEME_PRESET] = value }
    suspend fun setAnalysisDefaultRange(value: Int) = context.dataStore.edit { it[KEY_ANALYSIS_DEFAULT_RANGE] = value }
    suspend fun setOnboardingComplete(value: Boolean) = context.dataStore.edit { it[KEY_ONBOARDING_COMPLETE] = value }
}
