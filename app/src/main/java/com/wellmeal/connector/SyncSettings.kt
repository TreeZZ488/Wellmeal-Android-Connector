package com.wellmeal.connector

import java.time.LocalTime

data class SyncSettings(
    val automaticSyncEnabled: Boolean = false,
    val syncTimes: List<LocalTime> = listOf(
        LocalTime.of(8, 0),
        LocalTime.of(20, 0)
    ),
    val wifiOnly: Boolean = false,
    val avoidLowBattery: Boolean = true
) {
    /**
     * Ensures times are unique, between 1 and 3 items, and sorted ascending.
     */
    fun normalized(): SyncSettings {
        val uniqueSorted = syncTimes.distinct().sorted()
        val clamped = when {
            uniqueSorted.isEmpty() -> listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
            uniqueSorted.size > 3 -> uniqueSorted.take(3)
            else -> uniqueSorted
        }
        return copy(syncTimes = clamped)
    }
}
