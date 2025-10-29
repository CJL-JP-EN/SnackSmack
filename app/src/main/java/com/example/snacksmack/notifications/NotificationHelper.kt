package com.example.snacksmack.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.snacksmack.notifications.NotificationHelper
import com.example.snacksmack.notifications.NotificationScheduler
import com.example.snacksmack.notifications.ReminderType

import kotlin.random.Random



object NotificationHelper {
    const val CHANNEL_ID = "snack_alerts_high"
    private const val ACTION_DISMISS = "com.example.snacksmack.ACTION_DISMISS"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Snack & Hydration Alerts", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority reminders to avoid snacking and drink water"
                enableVibration(true)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun pickMessage(
        type: ReminderType,
        useHarsh: Boolean
    ): String = when (type) {
        ReminderType.SNACK ->
            if (useHarsh) NotificationTexts.snackHarsh.random()
            else NotificationTexts.snackSupportive.random()
        ReminderType.HYDRATION ->
            NotificationTexts.hydration.random()
    }

    /** Show a heads-up notification. If persistent=true, user must tap OK action to dismiss. */
    fun show(
        context: Context,
        type: ReminderType,
        useHarsh: Boolean = true,
        persistent: Boolean = false
    ): Int {
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return -1

        val appCtx = context.applicationContext
        val dismissIntent = Intent(appCtx, DismissReceiver::class.java)
        val dismissPI = PendingIntent.getBroadcast(
            appCtx, 0, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val msg = pickMessage(type, useHarsh)
        val notif = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(if (type == ReminderType.HYDRATION) "Hydration Reminder" else "SnackSmack")
            .setContentText(msg)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(!persistent)
            .setOngoing(persistent)
            .addAction(android.R.drawable.checkbox_on_background, "OK", dismissPI)
            .build()

        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        NotificationManagerCompat.from(appCtx).notify(id, notif)
        return id
    }
}
