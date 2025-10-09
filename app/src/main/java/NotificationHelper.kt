package com.example.snacksmack

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
import kotlin.random.Random

object NotificationHelper {
    // Use a NEW channel ID with HIGH importance to enable heads-up banners
    private const val CHANNEL_ID = "snack_alerts_high"

    // Edit this list however you like
    private val harshMessages = listOf(
        "Hey, don't eat that snack today.",
        "A snack? Forget about that fatso.",
        "Hey fatty, don't eat that candy bar today.",
        "Oink Oink fatso, don't over eat.",
        "DO NOT SNACK!",
        "EAT A SALAD, IGNORE THE PIZZA"
    )

    // Optional: a supportive set you can switch to later
    private val supportiveMessages = listOf(
        "You’ve got this—skip the snack and crush your goals.",
        "Small choice, big win: water break instead!",
        "Hungry or just bored? Take 10 breaths.",
        "Future you says: thanks for passing on that snack.",
        "Protein > candy. You know the move.",
        "Walk it off: 2 minutes, then decide."
    )

    fun createHighPriorityChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Snack Alerts",
                NotificationManager.IMPORTANCE_HIGH // heads-up eligible
            ).apply {
                description = "High-priority alerts to stop snacking"
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun randomMessage(useHarsh: Boolean = true): String {
        val pool = if (useHarsh) harshMessages else supportiveMessages
        return pool[Random.nextInt(pool.size)]
    }

    fun showRandomSnackAlert(context: Context, useHarsh: Boolean = true) {
        // Android 13+ runtime permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission should be requested by the UI before calling this
            return
        }

        // Tap action: open the app (MainActivity or your entry Activity)
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentPI = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle("SnackSmack")
            .setContentText(randomMessage(useHarsh))
            .setContentIntent(contentPI)
            .setAutoCancel(true)
            // Heads-up banner knobs:
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // sound/vibrate for heads-up
            .build()

        // Unique ID so multiple notifications can coexist
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        NotificationManagerCompat.from(context).notify(notificationId, notif)
    }
}
