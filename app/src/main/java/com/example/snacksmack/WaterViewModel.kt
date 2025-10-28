package com.example.snacksmack

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.max
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.apply

class WaterViewModel(
    app: Application,
    private val state: SavedStateHandle
) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("water_prefs", Context.MODE_PRIVATE)

    // ---- constants
    private val KEY_GOAL = "goalOz"
    private val KEY_CONSUMED = "consumedOz"
    private val KEY_EPOCH = "epochDay"
    val cupIncrementOz: Int = 8

    // ---- state (persisted)
    var goalOz by mutableStateOf(prefs.getInt(KEY_GOAL, 64))
        private set
    var consumedOz by mutableStateOf(prefs.getInt(KEY_CONSUMED, 0))
        private set
    private var epochDay: Long = prefs.getLong(KEY_EPOCH, todayEpoch())

    val progress: Float
        get() = if (goalOz <= 0) 0f else (consumedOz.toFloat() / goalOz).coerceIn(0f, 1f)

    init {
        // Reset immediately if the day changed while the app was closed
        if (todayEpoch() != epochDay) {
            consumedOz = 0
            epochDay = todayEpoch()
        }
        persist()

        // Then keep checking once per minute while the app is running
        viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                if (todayEpoch() != epochDay) {
                    consumedOz = 0
                    epochDay = todayEpoch()
                    persist()
                }
            }
        }
    }

    fun addCup() {
        val next = consumedOz + cupIncrementOz
        consumedOz = if (goalOz > 0) next.coerceAtMost(goalOz) else next
        persist()
    }

    fun removeCup() {
        consumedOz = (consumedOz - cupIncrementOz).coerceAtLeast(0)
        persist()
    }

    // Snap goal to 8oz steps so UI stays “by cups”
    fun updateGoalOz(newGoalOz: Int) {
        val stepped = roundToStep(newGoalOz, cupIncrementOz).coerceAtLeast(cupIncrementOz)
        goalOz = stepped
        if (consumedOz > goalOz) consumedOz = goalOz
        persist()
    }

    // ---- private helpers

    private fun persist() {
        prefs.edit()
            .putInt(KEY_GOAL, goalOz)
            .putInt(KEY_CONSUMED, consumedOz)
            .putLong(KEY_EPOCH, epochDay)
            .apply()

        state[KEY_GOAL] = goalOz
        state[KEY_CONSUMED] = consumedOz
        state[KEY_EPOCH] = epochDay
    }

    private fun todayEpoch(): Long = LocalDate.now().toEpochDay()

    private fun roundToStep(value: Int, step: Int): Int {
        val rem = value % step
        return if (rem == 0) value else value + (step - rem)
    }
    //promps only once
    fun shouldPromptGoalOnce(): Boolean {
        // only ask once unless never set before
        return !prefs.getBoolean("goalPromptSeen", false)
    }

    fun markGoalPromptSeen() {
        prefs.edit().putBoolean("goalPromptSeen", true).apply()
    }
}
