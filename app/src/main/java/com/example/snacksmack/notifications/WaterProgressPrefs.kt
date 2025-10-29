package com.example.snacksmack.notifications

import android.content.Context
import androidx.core.content.edit

object WaterProgressPrefs {
    private const val PREFS = "water_progress"
    private const val KEY_GOAL = "goal_oz"
    private const val KEY_CONSUMED = "consumed_oz"

    fun goalOz(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_GOAL, 64) // default 64oz

    fun consumedOz(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CONSUMED, 0)

    fun setGoalOz(ctx: Context, v: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putInt(KEY_GOAL, v) }
    }

    fun setConsumedOz(ctx: Context, v: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putInt(KEY_CONSUMED, v) }
    }
}
