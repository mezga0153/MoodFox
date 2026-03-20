package com.moodfox.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moodfox.R
import com.moodfox.data.local.PreferencesManager
import com.moodfox.ui.theme.LocalAppColors

@Composable
fun SettingsScreen(
    preferencesManager: PreferencesManager,
    onNavigateToCategories: () -> Unit,
    onNavigateToLogViewer: () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
        )
        // TODO Phase 13: full settings screen
    }
}

@Composable
fun CategoryManagerScreen(
    causeCategoryDao: com.moodfox.data.local.db.CauseCategoryDao,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.categories_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
        )
        // TODO Phase 13: category manager
    }
}

@Composable
fun LogViewerScreen(
    appLogger: com.moodfox.data.local.AppLogger,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val logText = remember { appLogger.read() }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = logText, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
