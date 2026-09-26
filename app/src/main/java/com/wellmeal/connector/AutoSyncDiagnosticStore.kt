package com.wellmeal.connector

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class SyncDiagnosticStage {
    WORKER_STARTED,
    MSAL_INITIALIZING,
    MSAL_READY,
    READING_HEALTH_DATA,
    EXPORTING_JSON,
    ACQUIRING_ONEDRIVE_TOKEN,
    CHECKING_ONEDRIVE_FOLDER,
    UPLOADING_DAILY,
    UPLOADING_LATEST,
    READING_YESTERDAY,
    ACQUIRING_MAIL_TOKEN,
    SENDING_EMAIL,
    WRITING_HISTORY,
    COMPLETED
}

data class AutoSyncDiagnosticData(
    val startedAt: String = "N/A",
    val lastUpdatedAt: String = "N/A",
    val currentStage: SyncDiagnosticStage = SyncDiagnosticStage.WORKER_STARTED,
    val completed: Boolean = false,
    val lastError: String? = null,
    val stopReason: String? = null
)

class AutoSyncDiagnosticStore(
    context: Context
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Records the start of an automatic SyncWorker execution.
     */
    fun recordStarted() {
        try {
            val now = formatTimestamp(Instant.now())
            preferences.edit()
                .putString(KEY_STARTED_AT, now)
                .putString(KEY_LAST_UPDATED_AT, now)
                .putString(KEY_CURRENT_STAGE, SyncDiagnosticStage.WORKER_STARTED.name)
                .putBoolean(KEY_COMPLETED, false)
                .remove(KEY_LAST_ERROR)
                .remove(KEY_STOP_REASON)
                .apply()
        } catch (_: Exception) {
            // Never throw or break sync
        }
    }

    /**
     * Updates the current stage of automatic sync execution.
     */
    fun recordStage(stage: SyncDiagnosticStage) {
        try {
            val now = formatTimestamp(Instant.now())
            preferences.edit()
                .putString(KEY_LAST_UPDATED_AT, now)
                .putString(KEY_CURRENT_STAGE, stage.name)
                .apply()
        } catch (_: Exception) {
            // Never throw or break sync
        }
    }

    /**
     * Persists the final completion status and error details.
     */
    fun recordCompleted(success: Boolean, error: String? = null) {
        try {
            val now = formatTimestamp(Instant.now())
            val editor = preferences.edit()
                .putString(KEY_LAST_UPDATED_AT, now)
                .putBoolean(KEY_COMPLETED, success)

            if (success) {
                editor.putString(KEY_CURRENT_STAGE, SyncDiagnosticStage.COMPLETED.name)
            }
            if (error != null) {
                editor.putString(KEY_LAST_ERROR, error)
            } else if (success) {
                editor.remove(KEY_LAST_ERROR)
            }
            editor.apply()
        } catch (_: Exception) {
            // Never throw or break sync
        }
    }

    /**
     * Persists WorkManager stop reason when Worker is stopped.
     */
    fun recordStopped(reasonText: String) {
        try {
            val now = formatTimestamp(Instant.now())
            preferences.edit()
                .putString(KEY_LAST_UPDATED_AT, now)
                .putString(KEY_STOP_REASON, reasonText)
                .apply()
        } catch (_: Exception) {
            // Never throw or break sync
        }
    }

    /**
     * Loads the persisted diagnostic data safely.
     */
    fun loadData(): AutoSyncDiagnosticData {
        return try {
            val startedAt = preferences.getString(KEY_STARTED_AT, "N/A") ?: "N/A"
            val lastUpdatedAt = preferences.getString(KEY_LAST_UPDATED_AT, "N/A") ?: "N/A"
            val stageStr = preferences.getString(KEY_CURRENT_STAGE, SyncDiagnosticStage.WORKER_STARTED.name)
            val stage = try {
                SyncDiagnosticStage.valueOf(stageStr ?: SyncDiagnosticStage.WORKER_STARTED.name)
            } catch (_: Exception) {
                SyncDiagnosticStage.WORKER_STARTED
            }
            val completed = preferences.getBoolean(KEY_COMPLETED, false)
            val lastError = preferences.getString(KEY_LAST_ERROR, null)
            val stopReason = preferences.getString(KEY_STOP_REASON, null)

            AutoSyncDiagnosticData(
                startedAt = startedAt,
                lastUpdatedAt = lastUpdatedAt,
                currentStage = stage,
                completed = completed,
                lastError = lastError,
                stopReason = stopReason
            )
        } catch (_: Exception) {
            AutoSyncDiagnosticData()
        }
    }

    private fun formatTimestamp(instant: Instant): String {
        return try {
            val zone = ZoneId.systemDefault()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
            formatter.format(instant.atZone(zone))
        } catch (_: Exception) {
            instant.toString()
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "auto_sync_diagnostic_preferences"
        private const val KEY_STARTED_AT = "started_at"
        private const val KEY_LAST_UPDATED_AT = "last_updated_at"
        private const val KEY_CURRENT_STAGE = "current_stage"
        private const val KEY_COMPLETED = "completed"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_STOP_REASON = "stop_reason"
    }
}
