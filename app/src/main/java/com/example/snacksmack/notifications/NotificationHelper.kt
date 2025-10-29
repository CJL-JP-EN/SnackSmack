package com.example.snacksmack.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.snacksmack.R
import kotlin.random.Random

object NotificationHelper {

    // Single high-priority channel used for all alerts
    private const val CHANNEL_ID = "snacksmack_alerts"
    private const val CHANNEL_NAME = "SnackSmack Alerts"
    private const val CHANNEL_DESC = "High-priority reminders for snacks and hydration"

    /**
     * Create (or ensure) the high-priority channel exists.
     * Safe to call multiple times.
     */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val importance = NotificationManager.IMPORTANCE_HIGH
                val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                    enableLights(true)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    /**
     * Backward-compatible alias some of your screens call.
     * Keeps older code working.
     */
    fun createHighPriorityChannel(context: Context) = createChannel(context)

    /**
     * Show a notification for the given reminder type.
     *
     * @param useHarsh if true, picks from harsh snack lines (only applies to SNACK).
     * @param persistent if true, makes it ongoing (user can dismiss via action).
     */
    fun show(
        context: Context,
        type: ReminderType,
        useHarsh: Boolean,
        persistent: Boolean
    ) {
        createChannel(context) // ensure channel exists

        val (title, text) = pickContent(type, useHarsh)

        // Optional: tap intent to open app (MainActivity). If you don't have it or don't want it,
        // you can remove the contentIntent bits below.
        val contentIntent = context.packageManager?.getLaunchIntentForPackage(context.packageName)
        val contentPI = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Optional: “Dismiss” action via broadcast (if you implemented DismissReceiver).
        val dismissIntent = Intent(context, DismissReceiver::class.java)
        val dismissPI = PendingIntent.getBroadcast(
            context,
            0,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // simple built-in icon
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentPI)
            .setAutoCancel(!persistent)
            .setOngoing(persistent)

        if (persistent) {
            builder.addAction(
                /* icon = */ 0,
                /* title = */ "Dismiss",
                /* intent = */ dismissPI
            )
        }

        val id = notificationId(type)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    /**
     * Used by your HomeScreen’s manual trigger.
     */
    fun showRandomSnackAlert(context: Context, useHarsh: Boolean) {
        show(context, ReminderType.SNACK, useHarsh, persistent = false)
    }

    // ---- helpers ----

    private fun pickContent(type: ReminderType, useHarsh: Boolean): Pair<String, String> {
        return when (type) {
            ReminderType.SNACK -> {
                val list = if (useHarsh) NotificationTexts.snackHarsh else NotificationTexts.snackSupportive
                "SnackSmack" to list.randomSafe()
            }
            ReminderType.HYDRATION -> {
                "Hydration Reminder" to NotificationTexts.hydration.randomSafe()
            }
        }
    }

    private fun <T> List<T>.randomSafe(): T =
        if (isEmpty()) throw IllegalStateException("Notification text list is empty")
        else this[Random.nextInt(size)]

    private fun notificationId(type: ReminderType): Int {
        // A simple type-based stable prefix + random tail to avoid collisions in rapid repeats
        val base = when (type) {
            ReminderType.SNACK -> 1000
            ReminderType.HYDRATION -> 2000 // typo? ensure matches enum; corrected below
        }
        // NOTE: fix enum spelling below; using proper ordinal-safe approach instead:
        // return type.ordinal * 10_000 + (System.currentTimeMillis() % 10_000).toInt()
        // But many folks like stable ids per type. We'll keep base + random:
        return base + Random.nextInt(500)
    }
}
