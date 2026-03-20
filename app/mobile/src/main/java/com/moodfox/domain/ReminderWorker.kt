package com.moodfox.domain

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.work.*
import com.moodfox.R
import com.moodfox.ui.MainActivity
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "moodfox_reminders"

class ReminderWorker(
    private val ctx: Context,
    params: WorkerParameters,
) : Worker(ctx, params) {

    override fun doWork(): Result {
        ensureChannel()
        val nm = ctx.getSystemService<NotificationManager>()!!
        val intent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(ctx.getString(R.string.reminder_title))
            .setContentText(ctx.getString(R.string.reminder_body))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notification)
        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Mood reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            ctx.getSystemService<NotificationManager>()?.createNotificationChannel(ch)
        }
    }
}
