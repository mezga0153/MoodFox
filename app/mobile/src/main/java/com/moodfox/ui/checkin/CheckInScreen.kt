package com.moodfox.ui.checkin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moodfox.R
import com.moodfox.data.local.db.CauseCategoryDao
import com.moodfox.data.local.db.MoodEntryDao
import com.moodfox.data.local.db.WeatherSnapshotDao
import com.moodfox.data.remote.WeatherService
import com.moodfox.ui.theme.LocalAppColors

@Composable
fun CheckInScreen(
    moodEntryDao: MoodEntryDao,
    causeCategoryDao: CauseCategoryDao,
    weatherSnapshotDao: WeatherSnapshotDao,
    weatherService: WeatherService,
    weatherEnabled: Boolean,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.checkin_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
        )
        // TODO Phase 9: implement full check-in UI
    }
}
