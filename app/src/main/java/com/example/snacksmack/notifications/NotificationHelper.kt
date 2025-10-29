// app/src/main/java/com/example/snacksmack/notifications/NotificationHelper.kt
package com.example.snacksmack.notifications

import android.Manifest
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    private const val CHANNEL_ID_HIGH = "snacksmack.high"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_HIGH,
                "High Priority Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "SnackSmack reminders and hydration nudges"
                enableVibration(true)
                enableLights(true)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    /** Generic entry used by NotificationReceiver */
    fun show(
        context: Context,
        type: ReminderType,
        useHarsh: Boolean,
        persistent: Boolean
    ) {
        when (type) {
            ReminderType.SNACK     -> showRandom(context, useHarsh, persistent)
            ReminderType.HYDRATION -> showHydration(context)
        }
    }

    // Kept for direct calls elsewhere (e.g., buttons you used to have)
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showRandom(
        context: Context,
        useHarsh: Boolean,
        persistent: Boolean = false
    ) {
        val title = "SnackSmack Reminder"
        val body = if (useHarsh)
            NotificationTexts.snackHarsh.random()
        else
            NotificationTexts.snackSupportive.random()

        val actions = mutableListOf<NotificationCompat.Action>()
        val ongoing = if (persistent) {
            val dismissIntent = Intent(context, DismissReceiver::class.java)
            val dismissPi = PendingIntent.getBroadcast(
                context, 1001, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            actions += NotificationCompat.Action(0, "Dismiss", dismissPi)
            true
        } else false

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_HIGH)
            .setSmallIcon(R.drawable.ic_dialog_info) // built-in icon
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(!persistent)
            .setOngoing(ongoing)
            .apply { actions.forEach { addAction(it) } }
            .build()

        NotificationManagerCompat.from(context)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showHydration(context: Context) {
        val title = "Hydration Reminder"
        val body = NotificationTexts.hydration.random()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_HIGH)
            .setSmallIcon(R.drawable.ic_dialog_info) // built-in icon
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

    /** Optional: alias for older call sites */
    fun showRandomSnackAlert(context: Context, useHarsh: Boolean) =
        showRandom(context, useHarsh, persistent = false)
}
