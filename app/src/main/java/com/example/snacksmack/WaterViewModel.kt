package com.example.snacksmack

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate

class WaterViewModel(
    app: Application,
    private val state: SavedStateHandle
) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // constants
    val cupIncrementOz: Int = 8

    // state (persisted)
    var goalOz by mutableStateOf(prefs.getInt(KEY_GOAL, 64))
        private set

    private var consumptionData by mutableStateOf(loadConsumptionData())

    var consumedOz by mutableStateOf(consumptionData[getTodayId()] ?: 0)
        private set

    private var todayId: Long = getTodayId()

    val progress: Float
        get() = if (goalOz <= 0) 0f else (consumedOz.toFloat() / goalOz).coerceIn(0f, 1f)

    init {
        resetIfNewDay()
    }

    /** Call this once per resume or app open to ensure fresh data each day. */
    fun resetIfNewDay() {
        val today = getTodayId()
        if (today != todayId) {
            todayId = today
            consumedOz = consumptionData[todayId] ?: 0
            persist()
        }
    }

    fun addCup() {
        val next = consumedOz + cupIncrementOz
        val newVal = if (goalOz > 0) next.coerceAtMost(goalOz) else next
        if (newVal != consumedOz) {
            consumedOz = newVal
            updateConsumptionForToday()
        }
    }

    fun removeCup() {
        val newVal = (consumedOz - cupIncrementOz).coerceAtLeast(0)
        if (newVal != consumedOz) {
            consumedOz = newVal
            updateConsumptionForToday()
        }
    }

    fun updateGoalOz(newGoalOz: Int) {
        val stepped = roundToStep(newGoalOz, cupIncrementOz).coerceAtLeast(cupIncrementOz)
        if (stepped != goalOz) {
            goalOz = stepped
            if (consumedOz > goalOz) {
                consumedOz = goalOz
                updateConsumptionForToday()
            } else {
                persist()
            }
        }
    }

    fun getWeeklyData(startOfWeek: LocalDate): Map<LocalDate, Int> {
        return (0..6).associate { dayIndex ->
            val date = startOfWeek.plusDays(dayIndex.toLong())
            val dayId = date.toEpochDay()
            date to (consumptionData[dayId] ?: 0)
        }
    }

    fun getMonthData(startOfMonth: LocalDate): Map<LocalDate, Int> {
        val month = startOfMonth.month
        return (0 until startOfMonth.lengthOfMonth()).associate { i ->
            val date = startOfMonth.plusDays(i.toLong())
            val dayId = date.toEpochDay()
            date to (consumptionData[dayId] ?: 0)
        }
    }

    fun shouldPromptGoalOnce(): Boolean =
        !prefs.getBoolean(KEY_GOAL_PROMPT_SEEN, false)

    fun markGoalPromptSeen() {
        prefs.edit().putBoolean(KEY_GOAL_PROMPT_SEEN, true).apply()
    }

    private fun updateConsumptionForToday() {
        val mutableData = consumptionData.toMutableMap()
        mutableData[todayId] = consumedOz
        consumptionData = mutableData
        persist()
    }

    private fun persist() {
        prefs.edit()
            .putInt(KEY_GOAL, goalOz)
            .putString(KEY_CONSUMPTION_DATA, gson.toJson(consumptionData))
            .apply()

        state[KEY_GOAL] = goalOz
        state[KEY_CONSUMED] = consumedOz // For UI to update
    }

    private fun loadConsumptionData(): Map<Long, Int> {
        val json = prefs.getString(KEY_CONSUMPTION_DATA, null)
        return if (json != null) {
            val type = object : TypeToken<Map<Long, Int>>() {}.type
            gson.fromJson(json, type)
        } else {
            // Try to migrate from old format
            val oldConsumed = prefs.getInt(KEY_CONSUMED, 0)
            val oldDayId = prefs.getLong(KEY_DAY_ID, getTodayId())
            if (oldConsumed > 0) {
                mapOf(oldDayId to oldConsumed)
            } else {
                emptyMap()
            }
        }
    }

    private fun getTodayId(): Long = LocalDate.now().toEpochDay()

    private fun roundToStep(value: Int, step: Int): Int {
        val rem = value % step
        return if (rem == 0) value else value + (step - rem)
    }

    companion object {
        private const val PREFS_NAME = "water_prefs"
        private const val KEY_GOAL = "goalOz"
        private const val KEY_CONSUMED = "consumedOz" // Still used for saved state handle
        private const val KEY_DAY_ID = "dayId" // Only used for migration now
        private const val KEY_GOAL_PROMPT_SEEN = "goalPromptSeen"
        private const val KEY_CONSUMPTION_DATA = "consumptionData"
    }
}
