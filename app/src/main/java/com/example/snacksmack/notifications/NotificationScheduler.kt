package com.example.snacksmack.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
import java.util.concurrent.TimeUnit
import com.example.snacksmack.notifications.ReminderType



object NotificationScheduler {

    // ---- Stable request codes so intents overwrite themselves ----
    private fun rcFor(type: ReminderType, hour: Int, minute: Int): Int =
        (type.ordinal * 10000) + (hour * 100) + minute   // 0..1 * 10000 + HHmm

    private fun rcForHydrationSlot(hour: Int, minute: Int) = rcFor(ReminderType.HYDRATION, hour, minute)
    private fun rcForSnack(hour: Int, minute: Int) = rcFor(ReminderType.SNACK, hour, minute)

    private fun pendingIntentFor(
        context: Context,
        requestCode: Int,
        type: ReminderType,
        useHarsh: Boolean,
        persistent: Boolean,
        repeatDaily: Boolean = false,
        hour24: Int = -1,
        minute: Int = -1,
        twoHourlyStride: Boolean = false    // hydration every 2 hours
    ): PendingIntent {
        val appCtx = context.applicationContext
        val i = Intent(appCtx, NotificationReceiver::class.java).apply {
            putExtra("type", type.name)
            putExtra("useHarsh", useHarsh)
            putExtra("persistent", persistent)
            putExtra("repeatDaily", repeatDaily)
            putExtra("hour24", hour24)
            putExtra("minute", minute)
            putExtra("twoHourlyStride", twoHourlyStride)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(appCtx, requestCode, i, flags)
    }

    private fun setExact(am: AlarmManager, triggerAt: Long, pi: PendingIntent) {
        when {
            Build.VERSION.SDK_INT >= 23 ->
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            Build.VERSION.SDK_INT >= 19 ->
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else ->
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    // ---------- One-shot at date+time (calendar events) ----------
    fun scheduleSnackAtDateTime(
        context: Context,
        year: Int, month0: Int, day: Int,
        hour24: Int, minute: Int,
        useHarsh: Boolean = true,
        persistent: Boolean = false
    ): Int {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month0)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour24)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val rc = (cal.timeInMillis % Int.MAX_VALUE).toInt()
        val pi = pendingIntentFor(appCtx, rc, ReminderType.SNACK, useHarsh, persistent, repeatDaily = false)
        setExact(am, cal.timeInMillis, pi)
        return rc
    }

    // ---------- Generic one-shot delay ----------
    fun scheduleAfterDelay(
        context: Context,
        delayMinutes: Long,
        type: ReminderType = ReminderType.SNACK,
        useHarsh: Boolean = true,
        persistent: Boolean = false
    ): Int {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes)
        val rc = (triggerAt % Int.MAX_VALUE).toInt()
        val pi = pendingIntentFor(appCtx, rc, type, useHarsh, persistent, repeatDaily = false)
        setExact(am, triggerAt, pi)
        return rc
    }

    // ---------- Daily exact snack reminders (e.g., 9:00, 13:00, 19:00) ----------
    fun scheduleDailySnackReminders(
        context: Context,
        times: List<Pair<Int, Int>> = listOf(9 to 0, 13 to 0, 19 to 0),
        useHarsh: Boolean = true,
        persistent: Boolean = false
    ): List<Int> {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return times.map { (hour, minute) ->
            val cal = nextOccurrence(hour, minute)
            val rc = rcForSnack(hour, minute)
            val pi = pendingIntentFor(
                appCtx, rc, ReminderType.SNACK, useHarsh, persistent,
                repeatDaily = true, hour24 = hour, minute = minute
            )
            setExact(am, cal.timeInMillis, pi)
            rc
        }
    }

    // ---------- Hydration every 2 hours during the day (exact, self-rescheduling) ----------
    fun scheduleHydrationEvery2Hours(
        context: Context,
        startHour: Int = 8,
        endHour: Int = 20,
        persistent: Boolean = false
    ): List<Int> {
        val hours = (startHour..endHour step 2).toList()
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return hours.map { hour ->
            val minute = 0
            val cal = nextOccurrence(hour, minute)
            val rc = rcForHydrationSlot(hour, minute)
            val pi = pendingIntentFor(
                appCtx, rc, ReminderType.HYDRATION, useHarsh = false, persistent = persistent,
                repeatDaily = true, hour24 = hour, minute = minute, twoHourlyStride = true
            )
            setExact(am, cal.timeInMillis, pi)
            rc
        }
    }

    private fun nextOccurrence(hour: Int, minute: Int): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }

    /** Cancel a specific scheduled alarm by requestCode */
    fun cancel(context: Context, requestCode: Int) {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // We don’t know all extras here; build the simplest matching PI signature:
        val dummy = PendingIntent.getBroadcast(
            appCtx, requestCode,
            Intent(appCtx, NotificationReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        am.cancel(dummy)
    }
}
