package com.example.snacksmack.notifications

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate

object SnackStatsPrefs {
    private const val PREFS = "snack_prefs"
    private const val KEY_DAILY_MAP = "dailyCounts" // Map<Long, IntArray> = epochDay -> [protein,salty,sweet,healthy]
    private val gson = Gson()

    fun todayCounts(context: Context): IntArray {
        val map = loadMap(context)
        return map[LocalDate.now().toEpochDay()] ?: intArrayOf(0,0,0,0)
    }

    fun todayTotal(context: Context): Int = todayCounts(context).sum()

    private fun loadMap(context: Context): Map<Long, IntArray> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_DAILY_MAP, null) ?: return emptyMap()
        val type = object : TypeToken<Map<Long, IntArray>>(){}.type
        return gson.fromJson(json, type)
    }
}
