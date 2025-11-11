package com.example.snacksmack

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate

enum class SnackType { PROTEIN, SALTY, SWEET, HEALTHY }

/**
 * Stores per-day counts for 4 snack categories.
 * Persistence: SharedPreferences (JSON Map<Long, IntArray(4)>)
 */
class SnackViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    // Map<epochDay, IntArray(size=4)>
    private var dayData: MutableMap<Long, IntArray> = load()
    private var todayId: Long = LocalDate.now().toEpochDay()

    var countsToday by mutableStateOf(dayData.getOrPut(todayId) { IntArray(4) }.copyOf())
        private set

    val totalToday: Int
        get() = countsToday.sum()

    fun addSnack(type: SnackType) {
        ensureToday()
        val idx = type.ordinal
        val arr = dayData[todayId] ?: IntArray(4).also { dayData[todayId] = it }
        arr[idx] += 1
        countsToday = arr.copyOf()
        persist()
    }

    fun removeSnack(type: SnackType) {
        ensureToday()
        val idx = type.ordinal
        val arr = dayData[todayId] ?: IntArray(4).also { dayData[todayId] = it }
        if (arr[idx] > 0) arr[idx] -= 1
        countsToday = arr.copyOf()
        persist()
    }

    /** For current day UI; returns a snapshot IntArray(size=4). */
    fun getTodayCounts(): IntArray = countsToday.copyOf()

    /** Weekly mini helper: Map<LocalDate, IntArray(4)> for [startOfWeek..+6]. */
    fun getWeekPerCategory(startOfWeek: LocalDate): Map<LocalDate, IntArray> {
        return (0..6).associate { i ->
            val date = startOfWeek.plusDays(i.toLong())
            val id = date.toEpochDay()
            date to (dayData[id]?.copyOf() ?: IntArray(4))
        }
    }

    /** Call on resume/open just like WaterVM to roll into a fresh day. */
    fun resetIfNewDay() {
        val now = LocalDate.now().toEpochDay()
        if (now != todayId) {
            todayId = now
            countsToday = dayData.getOrPut(todayId) { IntArray(4) }.copyOf()
            persist()
        }
    }

    private fun ensureToday() {
        val now = LocalDate.now().toEpochDay()
        if (now != todayId) {
            todayId = now
            if (!dayData.containsKey(todayId)) dayData[todayId] = IntArray(4)
        }
    }

    private fun persist() {
        prefs.edit().putString(KEY_DATA, gson.toJson(dayData)).apply()
    }

    private fun load(): MutableMap<Long, IntArray> {
        val json = prefs.getString(KEY_DATA, null) ?: return mutableMapOf()
        val type = object : TypeToken<Map<Long, List<Int>>>() {}.type
        val raw: Map<Long, List<Int>> = gson.fromJson(json, type)
        return raw.mapValues { (_, v) ->
            IntArray(4) { i -> v.getOrNull(i) ?: 0 }
        }.toMutableMap()
    }

    companion object {
        private const val PREFS = "snack_prefs"
        private const val KEY_DATA = "snack_data"
    }
}
