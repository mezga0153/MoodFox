package com.moodfox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.moodfox.R
import com.moodfox.data.local.AppLogger
import com.moodfox.data.local.BackupManager
import com.moodfox.data.local.PreferencesManager
import com.moodfox.data.local.db.CauseCategoryDao
import com.moodfox.data.local.db.MoodEntryDao
import com.moodfox.data.local.db.WeatherSnapshotDao
import com.moodfox.data.remote.WeatherService
import com.moodfox.domain.ReminderScheduler
import com.moodfox.ui.analysis.AnalysisScreen
import com.moodfox.ui.calendar.CalendarScreen
import com.moodfox.ui.checkin.CheckInScreen
import com.moodfox.ui.onboarding.WelcomeScreen
import com.moodfox.ui.settings.CategoryManagerScreen
import com.moodfox.ui.settings.LogViewerScreen
import com.moodfox.ui.settings.SettingsScreen
import com.moodfox.ui.theme.LocalAppColors

private val bottomTabs = listOf(
    Triple("checkin",  Icons.Filled.Mood,        R.string.tab_checkin),
    Triple("calendar", Icons.Filled.CalendarMonth, R.string.tab_calendar),
    Triple("analysis", Icons.Filled.BarChart,     R.string.tab_analysis),
    Triple("settings", Icons.Filled.Settings,     R.string.tab_settings),
)

private val hiddenBottomBarRoutes = setOf("welcome", "how_it_works", "settings/categories", "settings/log_viewer")

@Composable
fun MoodFoxNavGraph(
    preferencesManager: PreferencesManager,
    moodEntryDao: MoodEntryDao,
    causeCategoryDao: CauseCategoryDao,
    weatherSnapshotDao: WeatherSnapshotDao,
    weatherService: WeatherService,
    reminderScheduler: ReminderScheduler,
    appLogger: AppLogger,
    backupManager: BackupManager,
) {
    val colors = LocalAppColors.current
    val navController = rememberNavController()

    val onboardingComplete by preferencesManager.onboardingComplete.collectAsState(initial = null)
    val weatherEnabled by preferencesManager.weatherEnabled.collectAsState(initial = false)

    // Wait until the flag is loaded before deciding start destination
    if (onboardingComplete == null) return

    val startDestination = if (onboardingComplete == true) "checkin" else "welcome"

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute !in hiddenBottomBarRoutes

    Scaffold(
        containerColor = colors.surface,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = colors.cardSurface) {
                    bottomTabs.forEach { (route, icon, labelRes) ->
                        NavigationBarItem(
                            selected = navBackStackEntry?.destination?.hierarchy?.any { it.route == route } == true,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = stringResource(labelRes)) },
                            label = { Text(stringResource(labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.primary,
                                selectedTextColor = colors.primary,
                                indicatorColor = colors.primaryContainer,
                                unselectedIconColor = colors.onSurfaceVariant,
                                unselectedTextColor = colors.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("welcome") {
                WelcomeScreen(
                    preferencesManager = preferencesManager,
                    reminderScheduler  = reminderScheduler,
                    onFinished = {
                        navController.navigate("checkin") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    },
                )
            }
            composable("checkin") {
                CheckInScreen(
                    moodEntryDao = moodEntryDao,
                    causeCategoryDao = causeCategoryDao,
                    weatherSnapshotDao = weatherSnapshotDao,
                    weatherService = weatherService,
                    weatherEnabled = weatherEnabled,
                )
            }
            composable("calendar") {
                CalendarScreen(
                    moodEntryDao       = moodEntryDao,
                    causeCategoryDao   = causeCategoryDao,
                    weatherSnapshotDao = weatherSnapshotDao,
                    weatherService     = weatherService,
                    weatherEnabled     = weatherEnabled,
                )
            }
            composable("analysis") {
                AnalysisScreen(
                    moodEntryDao       = moodEntryDao,
                    causeCategoryDao   = causeCategoryDao,
                    weatherSnapshotDao = weatherSnapshotDao,
                )
            }
            composable("settings") {
                SettingsScreen(
                    preferencesManager = preferencesManager,
                    reminderScheduler  = reminderScheduler,
                    backupManager      = backupManager,
                    onNavigateToCategories = { navController.navigate("settings/categories") },
                    onNavigateToLogViewer = { navController.navigate("settings/log_viewer") },
                    onNavigateToHowItWorks = { navController.navigate("how_it_works") },
                )
            }
            composable("how_it_works") {
                WelcomeScreen(
                    preferencesManager = preferencesManager,
                    reminderScheduler  = reminderScheduler,
                    isReview = true,
                    onFinished = { navController.popBackStack() },
                )
            }
            composable("settings/categories") {
                CategoryManagerScreen(
                    causeCategoryDao = causeCategoryDao,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("settings/log_viewer") {
                LogViewerScreen(
                    appLogger = appLogger,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
