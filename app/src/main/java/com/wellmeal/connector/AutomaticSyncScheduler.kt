package com.wellmeal.connector

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class AutomaticSyncScheduler(
    context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Cancels all scheduled production automatic sync slot jobs.
     */
    fun cancelAll() {
        for (i in 0 until MAX_SLOTS) {
            workManager.cancelUniqueWork(getSlotWorkName(i))
        }
    }

    /**
     * Reschedules production automatic sync slot jobs based on current settings and background permission.
     * Uses CANCEL_AND_REENQUEUE to apply updated times/constraints immediately.
     */
    fun reschedule(
        settings: SyncSettings,
        backgroundAccessGranted: Boolean
    ) {
        Log.d(TAG, "reschedule called enabled=${settings.automaticSyncEnabled} background=$backgroundAccessGranted times=${settings.syncTimes} wifiOnly=${settings.wifiOnly} avoidLowBattery=${settings.avoidLowBattery}")

        cancelAll()

        if (!settings.automaticSyncEnabled || !backgroundAccessGranted) {
            Log.d(TAG, "reschedule early return enabled=${settings.automaticSyncEnabled} background=$backgroundAccessGranted")
            return
        }

        val normalized = settings.normalized()
        val times = normalized.syncTimes

        times.forEachIndexed { index, time ->
            if (index < MAX_SLOTS) {
                scheduleSlot(
                    slotIndex = index,
                    time = time,
                    wifiOnly = normalized.wifiOnly,
                    avoidLowBattery = normalized.avoidLowBattery,
                    policy = ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                )
            }
        }
    }

    /**
     * Ensures production automatic sync slot jobs exist without resetting the daily countdown if already scheduled.
     * Called on app startup.
     */
    fun ensureScheduled(
        settings: SyncSettings,
        backgroundAccessGranted: Boolean
    ) {
        Log.d(TAG, "ensureScheduled called enabled=${settings.automaticSyncEnabled} background=$backgroundAccessGranted times=${settings.syncTimes}")

        if (!settings.automaticSyncEnabled || !backgroundAccessGranted) {
            Log.d(TAG, "ensureScheduled early return enabled=${settings.automaticSyncEnabled} background=$backgroundAccessGranted")
            cancelAll()
            return
        }

        val normalized = settings.normalized()
        val times = normalized.syncTimes

        // Cancel unused slots beyond configured frequency
        for (i in times.size until MAX_SLOTS) {
            workManager.cancelUniqueWork(getSlotWorkName(i))
        }

        times.forEachIndexed { index, time ->
            if (index < MAX_SLOTS) {
                scheduleSlot(
                    slotIndex = index,
                    time = time,
                    wifiOnly = normalized.wifiOnly,
                    avoidLowBattery = normalized.avoidLowBattery,
                    policy = ExistingPeriodicWorkPolicy.KEEP
                )
            }
        }
    }

    private fun scheduleSlot(
        slotIndex: Int,
        time: LocalTime,
        wifiOnly: Boolean,
        avoidLowBattery: Boolean,
        policy: ExistingPeriodicWorkPolicy
    ) {
        val initialDelay = calculateInitialDelay(time)
        val initialDelayMinutes = initialDelay.toMinutes()
        val workName = getSlotWorkName(slotIndex)

        val networkType = if (wifiOnly) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresBatteryNotLow(avoidLowBattery)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        Log.d(TAG, "enqueue slot=$slotIndex name=$workName initialDelayMinutes=$initialDelayMinutes policy=$policy wifiOnly=$wifiOnly avoidLowBattery=$avoidLowBattery")

        workManager.enqueueUniquePeriodicWork(
            workName,
            policy,
            workRequest
        )

        Log.d(TAG, "enqueued slot=$slotIndex name=$workName successfully")
    }

    /**
     * Calculates the initial delay duration from current device time to the next occurrence of preferred sync time.
     */
    private fun calculateInitialDelay(preferredTime: LocalTime): Duration {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)

        var target = now
            .withHour(preferredTime.hour)
            .withMinute(preferredTime.minute)
            .withSecond(0)
            .withNano(0)

        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }

        return Duration.between(now, target)
    }

    companion object {
        private const val TAG = "WellmealScheduler"
        const val MAX_SLOTS = 3
        private const val SLOT_WORK_NAME_PREFIX = "wellmeal_auto_sync_slot_"

        fun getSlotWorkName(index: Int): String {
            return "$SLOT_WORK_NAME_PREFIX$index"
        }
    }
}
