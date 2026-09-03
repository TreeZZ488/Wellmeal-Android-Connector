package com.wellmeal.connector

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class SyncOutcome {
    SUCCESS,
    PARTIAL,
    FAILED
}

enum class SyncTrigger {
    MANUAL,
    AUTOMATIC
}

data class SyncHistoryEntry(
    val completedAt: String,
    val date: LocalDate,
    val trigger: SyncTrigger,
    val outcome: SyncOutcome,
    val dailyUploaded: Boolean,
    val latestUploaded: Boolean = false,
    val profileStatus: ProfileSyncStatus,
    val retryScheduled: Boolean = false,
    val error: String? = null,
    val profileError: String? = null
)

class SyncHistoryStore(
    private val context: Context
) {

    private val historyFile: File
        get() = File(context.filesDir, HISTORY_FILE_NAME)

    /**
     * Loads all sync history entries, removing expired (>30 days) items.
     * Returned list is sorted newest first.
     */
    fun loadHistory(): List<SyncHistoryEntry> {
        val file = historyFile
        if (!file.exists()) {
            return emptyList()
        }

        return try {
            val jsonText = file.readText()
            if (jsonText.isBlank()) return emptyList()

            val jsonArray = JSONArray(jsonText)
            val entries = mutableListOf<SyncHistoryEntry>()
            val now = Instant.now()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val entry = obj.toSyncHistoryEntry() ?: continue

                if (!isExpired(entry, now)) {
                    entries.add(entry)
                }
            }

            val sortedEntries = entries.sortedByDescending { it.completedAt }

            if (sortedEntries.size < jsonArray.length()) {
                saveHistory(sortedEntries)
            }

            sortedEntries
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Appends a new sync history entry, prunes expired items, and saves atomically.
     */
    fun appendEntry(entry: SyncHistoryEntry) {
        try {
            val existing = loadHistory()
            val now = Instant.now()

            val updated = (listOf(entry) + existing)
                .filterNot { isExpired(it, now) }
                .sortedByDescending { it.completedAt }

            saveHistory(updated)
        } catch (_: Exception) {
            // Fail gracefully without crashing
        }
    }

    /**
     * Gets the most recent sync history entry or null if empty.
     */
    fun getLatestEntry(): SyncHistoryEntry? {
        return loadHistory().firstOrNull()
    }

    /**
     * Atomically saves history entries to sync_history.json using a temp file.
     */
    private fun saveHistory(entries: List<SyncHistoryEntry>) {
        try {
            val jsonArray = JSONArray()
            entries.forEach { entry ->
                jsonArray.put(entry.toJson())
            }

            val tempFile = File(context.filesDir, "$HISTORY_FILE_NAME.tmp")
            tempFile.writeText(jsonArray.toString(2))

            if (tempFile.exists()) {
                tempFile.renameTo(historyFile)
            }
        } catch (_: Exception) {
            // Fail gracefully
        }
    }

    private fun isExpired(entry: SyncHistoryEntry, now: Instant): Boolean {
        return try {
            val entryInstant = Instant.parse(entry.completedAt)
            val thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS)
            entryInstant.isBefore(thirtyDaysAgo)
        } catch (_: Exception) {
            val thirtyDaysAgoDate = LocalDate.now().minusDays(30)
            entry.date.isBefore(thirtyDaysAgoDate)
        }
    }

    private fun SyncHistoryEntry.toJson(): JSONObject {
        return JSONObject().apply {
            put("completedAt", completedAt)
            put("date", date.toString())
            put("trigger", trigger.name)
            put("outcome", outcome.name)
            put("dailyUploaded", dailyUploaded)
            put("latestUploaded", latestUploaded)
            put("profileStatus", profileStatus.name)
            put("retryScheduled", retryScheduled)
            putNullable("error", error)
            putNullable("profileError", profileError)
        }
    }

    private fun JSONObject.toSyncHistoryEntry(): SyncHistoryEntry? {
        return try {
            val dailyUploaded = getBoolean("dailyUploaded")
            // Backward-compatible fallback for legacy records
            val latestUploaded = optBoolean("latestUploaded", dailyUploaded)
            val retryScheduled = optBoolean("retryScheduled", false)

            SyncHistoryEntry(
                completedAt = getString("completedAt"),
                date = LocalDate.parse(getString("date")),
                trigger = SyncTrigger.valueOf(getString("trigger")),
                outcome = SyncOutcome.valueOf(getString("outcome")),
                dailyUploaded = dailyUploaded,
                latestUploaded = latestUploaded,
                profileStatus = ProfileSyncStatus.valueOf(getString("profileStatus")),
                retryScheduled = retryScheduled,
                error = optStringOrNull("error"),
                profileError = optStringOrNull("profileError")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
        return this
    }

    companion object {
        private const val HISTORY_FILE_NAME = "sync_history.json"
    }
}
