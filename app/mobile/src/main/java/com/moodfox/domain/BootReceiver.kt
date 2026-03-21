package com.moodfox.domain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moodfox.data.local.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val enabled = preferencesManager.remindersEnabled.first()
                if (enabled) {
                    val times      = preferencesManager.reminderTimes.first()
                    val quietStart = preferencesManager.quietHoursStart.first()
                    val quietEnd   = preferencesManager.quietHoursEnd.first()
                    reminderScheduler.scheduleAll(times, quietStart, quietEnd)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
