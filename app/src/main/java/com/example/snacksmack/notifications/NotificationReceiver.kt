package com.example.snacksmack.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build

import java.util.Calendar

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val typeStr = intent.getStringExtra("type") ?: ReminderType.SNACK.name
            val type = runCatching { ReminderType.valueOf(typeStr) }.getOrElse { ReminderType.SNACK }
            val useHarsh = intent.getBooleanExtra("useHarsh", true)
            val persistent = intent.getBooleanExtra("persistent", false)

            val repeatDaily = intent.getBooleanExtra("repeatDaily", false)
            val hour = intent.getIntExtra("hour24", -1)
            val minute = intent.getIntExtra("minute", -1)
            val twoHourlyStride = intent.getBooleanExtra("twoHourlyStride", false)

            // Skip hydration if daily goal already met
            if (type == ReminderType.HYDRATION) {
                val goal = WaterProgressPrefs.goalOz(context)
                val consumed = WaterProgressPrefs.consumedOz(context)
                if (consumed >= goal) {
                    // Still reschedule the next slot/day so cadence resumes tomorrow
                    if (repeatDaily) rescheduleNext(context, type, useHarsh, persistent, hour, minute, twoHourlyStride)
                    return
                }
            }

            NotificationHelper.createChannel(context)
            NotificationHelper.show(context, type, useHarsh, persistent)

            // Reschedule the next occurrence (exact)
            if (repeatDaily) {
                rescheduleNext(context, type, useHarsh, persistent, hour, minute, twoHourlyStride)
            }

        } catch (t: Throwable) {
            Log.e("SnackSmack", "NotificationReceiver failed", t)
        }
    }

    private fun rescheduleNext(
        context: Context,
        type: ReminderType,
        useHarsh: Boolean,
        persistent: Boolean,
        hour: Int,
        minute: Int,
        twoHourlyStride: Boolean
    ) {
        if (hour !in 0..23 || minute !in 0..59) return

        val next = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)

            if (twoHourlyStride) {
                // advance by +2 hours until in the future
                while (timeInMillis <= System.currentTimeMillis()) {
                    add(java.util.Calendar.HOUR_OF_DAY, 2)
                }
                // align minute (should already be 0, but keep explicit)
                set(java.util.Calendar.MINUTE, minute)
            } else {
                // always schedule tomorrow at the same time
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }

        // same rc scheme as scheduler: ordinal * 10000 + HHmm
        val rc = type.ordinal * 10000 + (hour * 100) + minute

        val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val pi = android.app.PendingIntent.getBroadcast(
            context.applicationContext,
            rc,
            android.content.Intent(context.applicationContext, NotificationReceiver::class.java).apply {
                putExtra("type", type.name)
                putExtra("useHarsh", useHarsh)
                putExtra("persistent", persistent)
                putExtra("repeatDaily", true)
                putExtra("hour24", hour)
                putExtra("minute", minute)
                putExtra("twoHourlyStride", twoHourlyStride)
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (android.os.Build.VERSION.SDK_INT >= 23) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
        )

        when {
            android.os.Build.VERSION.SDK_INT >= 23 ->
                am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
            android.os.Build.VERSION.SDK_INT >= 19 ->
                am.setExact(android.app.AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
            else ->
                am.set(android.app.AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
        }
    }


    // Small helper to reuse the same extras when rescheduling from receiver
    private object PendingIntentFactory {
        fun forNext(
            context: Context,
            requestCode: Int,
            type: ReminderType,
            useHarsh: Boolean,
            persistent: Boolean,
            repeatDaily: Boolean,
            hour: Int,
            minute: Int,
            twoHourlyStride: Boolean
        ) = android.app.PendingIntent.getBroadcast(
            context.applicationContext,
            requestCode,
            Intent(context.applicationContext, NotificationReceiver::class.java).apply {
                putExtra("type", type.name)
                putExtra("useHarsh", useHarsh)
                putExtra("persistent", persistent)
                putExtra("repeatDaily", repeatDaily)
                putExtra("hour24", hour)
                putExtra("minute", minute)
                putExtra("twoHourlyStride", twoHourlyStride)
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 23) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
        )
    }
}
