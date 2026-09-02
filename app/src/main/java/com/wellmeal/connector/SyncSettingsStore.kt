package com.wellmeal.connector

import android.content.Context
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SyncSettingsStore(
    context: Context
) {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Loads the stored sync settings, falling back to defaults if missing or malformed.
     */
    fun load(): SyncSettings {
        return try {
            val enabled = preferences.getBoolean(KEY_AUTO_SYNC_ENABLED, false)
            val wifiOnly = preferences.getBoolean(KEY_WIFI_ONLY, false)
            val avoidLowBattery = preferences.getBoolean(KEY_AVOID_LOW_BATTERY, true)

            val rawTimes = preferences.getString(KEY_SYNC_TIMES, null)
            val times = if (!rawTimes.isNullOrBlank()) {
                rawTimes.split(",")
                    .mapNotNull {
                        try {
                            LocalTime.parse(it.trim(), TIME_FORMATTER)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    .distinct()
                    .sorted()
            } else {
                emptyList()
            }

            val validTimes = if (times.size in 1..3) {
                times
            } else {
                DEFAULT_TIMES
            }

            SyncSettings(
                automaticSyncEnabled = enabled,
                syncTimes = validTimes,
                wifiOnly = wifiOnly,
                avoidLowBattery = avoidLowBattery
            ).normalized()
        } catch (_: Exception) {
            SyncSettings().normalized()
        }
    }

    /**
     * Saves the sync settings to local SharedPreferences.
     */
    fun save(settings: SyncSettings) {
        val normalized = settings.normalized()
        try {
            val timeString = normalized.syncTimes.joinToString(",") {
                it.format(TIME_FORMATTER)
            }

            preferences.edit()
                .putBoolean(KEY_AUTO_SYNC_ENABLED, normalized.automaticSyncEnabled)
                .putString(KEY_SYNC_TIMES, timeString)
                .putBoolean(KEY_WIFI_ONLY, normalized.wifiOnly)
                .putBoolean(KEY_AVOID_LOW_BATTERY, normalized.avoidLowBattery)
                .apply()
        } catch (_: Exception) {
            // Fail gracefully
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "sync_settings_preferences"
        private const val KEY_AUTO_SYNC_ENABLED = "automatic_sync_enabled"
        private const val KEY_SYNC_TIMES = "sync_times"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_AVOID_LOW_BATTERY = "avoid_low_battery"

        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
        private val DEFAULT_TIMES = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
    }
}
