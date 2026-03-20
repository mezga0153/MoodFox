package com.moodfox.ui.analysis

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moodfox.R
import com.moodfox.data.local.db.MoodEntryDao
import com.moodfox.ui.theme.LocalAppColors

@Composable
fun AnalysisScreen(moodEntryDao: MoodEntryDao, causeCategoryDao: com.moodfox.data.local.db.CauseCategoryDao) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.analysis_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
        )
        // TODO Phase 12: charts, rolling average, stability card
    }
}
