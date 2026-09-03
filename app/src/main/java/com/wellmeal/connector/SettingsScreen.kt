package com.wellmeal.connector

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalPersonalHealthRecordApi::class)
@Composable
fun SettingsScreen(
    context: Context,
    syncSettings: SyncSettings,
    onSyncSettingsChanged: (SyncSettings) -> Unit,
    backgroundReadAvailable: Boolean,
    backgroundReadGranted: Boolean,
    onLaunchBackgroundPermission: () -> Unit,
    notificationPermissionGranted: Boolean,
    onLaunchNotificationPermission: () -> Unit,
    authManager: MicrosoftAuthManager,
    oneDriveUploader: OneDriveUploader,
    healthEmailSender: HealthEmailSender = remember { HealthEmailSender() },
    personalHealthRecordAvailable: Boolean,
    fitnessAllGranted: Boolean,
    fitnessGrantedCount: Int,
    fitnessPermissionsSize: Int,
    onLaunchFitnessPermission: () -> Unit,
    medicalAllGranted: Boolean,
    medicalGrantedCount: Int,
    medicalPermissionsSize: Int,
    onLaunchMedicalPermission: () -> Unit,
    medicalRepository: MedicalProfileRepository,
    medicalProfileParser: MedicalProfileParser,
    healthProfile: HealthProfile?,
    onHealthProfileUpdated: (HealthProfile) -> Unit,
    dietaryRestrictions: List<String>,
    profileJsonExporter: HealthProfileJsonExporter,
    medicalResult: String?,
    onMedicalResultUpdated: (String?) -> Unit,
    uploadResult: String?,
    onUploadResultUpdated: (String?) -> Unit,
    repository: HealthConnectRepository,
    jsonExporter: HealthJsonExporter,
    snapshot: DailyHealthSnapshot?,
    onSnapshotUpdated: (DailyHealthSnapshot?) -> Unit,
    dataLoaded: Boolean,
    onDataLoadedUpdated: (Boolean) -> Unit,
    loadError: String?,
    onLoadErrorUpdated: (String?) -> Unit,
    exportResult: String?,
    onExportResultUpdated: (String?) -> Unit,
    scope: CoroutineScope
) {
    val currentUser = authManager.currentUser
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    val workInfoList by remember(context) {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow("wellmeal_auto_sync_test")
    }.collectAsState(initial = emptyList())

    val workInfo = workInfoList.firstOrNull()
    val testStatusText = when (workInfo?.state) {
        WorkInfo.State.ENQUEUED -> "Automatic sync test: Enqueued"
        WorkInfo.State.RUNNING -> "Automatic sync test: Running"
        WorkInfo.State.SUCCEEDED -> "Automatic sync test: Succeeded"
        WorkInfo.State.FAILED -> "Automatic sync test: Failed"
        WorkInfo.State.BLOCKED -> "Automatic sync test: Enqueued"
        WorkInfo.State.CANCELLED -> "Automatic sync test: Cancelled"
        null -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Automatic Sync Settings Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Automatic Sync",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Automatic Sync Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Automatic Sync",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = syncSettings.automaticSyncEnabled,
                        onCheckedChange = { isChecked ->
                            onSyncSettingsChanged(
                                syncSettings.copy(automaticSyncEnabled = isChecked).normalized()
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Background Health Access Status & Action
                Text(
                    text = "Background Access",
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (backgroundReadAvailable) {
                    if (backgroundReadGranted) {
                        Text(
                            text = "Background health access: Granted",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = "Background health access: Required",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (syncSettings.automaticSyncEnabled) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Automatic sync cannot run until background health access is granted.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onLaunchBackgroundPermission
                        ) {
                            Text("Grant Background Access")
                        }
                    }
                } else {
                    Text(
                        text = "Background health access: Not supported on this device",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sync Notifications Section
                Text(
                    text = "Sync Notifications",
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (notificationPermissionGranted) {
                    Text(
                        text = "Notifications: Allowed",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "Notifications: Not allowed",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onLaunchNotificationPermission
                    ) {
                        Text("Allow Notifications")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sync Frequency Chips
                Text(
                    text = "Sync Frequency",
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                val currentCount = syncSettings.syncTimes.size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currentCount == 1,
                        onClick = {
                            val newTimes = updateSyncTimeCount(1, syncSettings.syncTimes)
                            onSyncSettingsChanged(syncSettings.copy(syncTimes = newTimes).normalized())
                        },
                        label = { Text("Once daily") }
                    )

                    FilterChip(
                        selected = currentCount == 2,
                        onClick = {
                            val newTimes = updateSyncTimeCount(2, syncSettings.syncTimes)
                            onSyncSettingsChanged(syncSettings.copy(syncTimes = newTimes).normalized())
                        },
                        label = { Text("Twice daily") }
                    )

                    FilterChip(
                        selected = currentCount == 3,
                        onClick = {
                            val newTimes = updateSyncTimeCount(3, syncSettings.syncTimes)
                            onSyncSettingsChanged(syncSettings.copy(syncTimes = newTimes).normalized())
                        },
                        label = { Text("3 times daily") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Configured Sync Times Selectors
                Text(
                    text = "Sync Times",
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                syncSettings.syncTimes.forEachIndexed { index, time ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Time ${index + 1}: ${time.format(timeFormatter)}")

                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        val newTime = LocalTime.of(hourOfDay, minute)
                                        val updatedTimes = syncSettings.syncTimes.toMutableList()
                                        updatedTimes[index] = newTime

                                        // Prevent duplicates
                                        if (updatedTimes.distinct().size == updatedTimes.size) {
                                            onSyncSettingsChanged(
                                                syncSettings.copy(syncTimes = updatedTimes).normalized()
                                            )
                                        }
                                    },
                                    time.hour,
                                    time.minute,
                                    true
                                ).show()
                            }
                        ) {
                            Text("Edit Time")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Network Preference
                Text(
                    text = "Network Preference",
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sync on Wi-Fi only")
                    Switch(
                        checked = syncSettings.wifiOnly,
                        onCheckedChange = { isChecked ->
                            onSyncSettingsChanged(
                                syncSettings.copy(wifiOnly = isChecked).normalized()
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Battery Preference
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Avoid syncing when battery is low",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = syncSettings.avoidLowBattery,
                        onCheckedChange = { isChecked ->
                            onSyncSettingsChanged(
                                syncSettings.copy(avoidLowBattery = isChecked).normalized()
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // System conditions note
                Text(
                    text = "Scheduled sync times are approximate and may run later depending on Android system conditions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Schedule Status Summary
                Text(
                    text = "Schedule Status",
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(4.dp))

                val scheduleStatusText = when {
                    !syncSettings.automaticSyncEnabled -> "Automatic sync is disabled."
                    backgroundReadAvailable && !backgroundReadGranted -> "Schedule inactive until background access is granted."
                    else -> {
                        val formattedTimes = syncSettings.syncTimes.joinToString(", ") {
                            it.format(timeFormatter)
                        }
                        "Scheduled: $formattedTimes daily"
                    }
                }

                Text(
                    text = scheduleStatusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!syncSettings.automaticSyncEnabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else if (backgroundReadAvailable && !backgroundReadGranted) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Daily Health Email Settings Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Daily Health Email",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Daily Health Email sends a copy of your daily health summary to the configured email address. Your existing OneDrive health archive is not affected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Email Enable Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable daily health email",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = syncSettings.dailyHealthEmailEnabled,
                        onCheckedChange = { isChecked ->
                            onSyncSettingsChanged(
                                syncSettings.copy(dailyHealthEmailEnabled = isChecked).normalized()
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Recipient OutlinedTextField
                OutlinedTextField(
                    value = syncSettings.emailRecipient,
                    onValueChange = { newRecipient ->
                        onSyncSettingsChanged(
                            syncSettings.copy(emailRecipient = newRecipient).normalized()
                        )
                    },
                    label = { Text("Recipient Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                var testEmailResult by remember { mutableStateOf<String?>(null) }
                var isTestingEmail by remember { mutableStateOf(false) }

                Button(
                    enabled = !isTestingEmail,
                    onClick = {
                        val recipient = syncSettings.emailRecipient.trim()
                        if (recipient.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(recipient).matches()) {
                            testEmailResult = "Please configure a valid recipient email address."
                            return@Button
                        }

                        val user = authManager.currentUser
                        if (user == null) {
                            testEmailResult = "Microsoft account not signed in."
                            return@Button
                        }

                        val activity = context as? Activity
                        if (activity == null) {
                            testEmailResult = "Activity context not available."
                            return@Button
                        }

                        isTestingEmail = true
                        testEmailResult = "Preparing test email..."

                        fun executeSendMail(token: String) {
                            scope.launch {
                                try {
                                    val date = java.time.LocalDate.now().minusDays(1)
                                    val dailyFile = File(context.filesDir, "exports/health-$date.json")
                                    val profileFile = File(context.filesDir, "exports/profile.json")

                                    val dummySnapshot = snapshot ?: DailyHealthSnapshot(
                                        date = date,
                                        steps = null,
                                        heartRateAverage = null,
                                        heartRateMinimum = null,
                                        heartRateMaximum = null,
                                        sleepMinutes = null,
                                        exerciseMinutes = null
                                    )

                                    val bodyText = healthEmailSender.buildEmailBody(dummySnapshot, healthProfile)
                                    val sendRes = healthEmailSender.sendDailyHealthEmail(
                                        accessToken = token,
                                        recipientEmail = recipient,
                                        date = date,
                                        bodyText = bodyText,
                                        dailyFile = if (dailyFile.exists()) dailyFile else null,
                                        profileFile = if (profileFile.exists()) profileFile else null
                                    )

                                    testEmailResult = if (sendRes.isSuccess) {
                                        "Test email sent successfully to $recipient"
                                    } else {
                                        "Test email failed: ${sendRes.exceptionOrNull()?.message}"
                                    }
                                } catch (e: Exception) {
                                    testEmailResult = "Test email failed: ${e.message}"
                                } finally {
                                    isTestingEmail = false
                                }
                            }
                        }

                        // Try silent token first
                        authManager.acquireMailTokenSilent(
                            onSuccess = { authResult ->
                                executeSendMail(authResult.accessToken)
                            },
                            onError = { _ ->
                                // Prompt for interactive consent if silent token fails
                                authManager.acquireMailTokenInteractive(
                                    activity = activity,
                                    onSuccess = { authResult ->
                                        executeSendMail(authResult.accessToken)
                                    },
                                    onError = { interactiveError ->
                                        testEmailResult = "Mail.Send consent required: ${interactiveError.message}"
                                        isTestingEmail = false
                                    }
                                )
                            }
                        )
                    }
                ) {
                    Text("Send Test Health Email")
                }

                testEmailResult?.let { result ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(result)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        HorizontalDivider(modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))

        // ----------------------------------------------------------------
        // DEVELOPER TOOLS SECTION
        // ----------------------------------------------------------------
        Text(
            text = "Developer Tools",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Health Connect: Available")

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            if (personalHealthRecordAvailable) {
                "Personal Health Record: Available"
            } else {
                "Personal Health Record: Unavailable"
            }
        )

        // WorkManager section
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "WorkManager",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "wellmeal_auto_sync_test",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }
        ) {
            Text("Run Automatic Sync Test")
        }

        testStatusText?.let { status ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(status)
        }

        // Microsoft Account section
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Microsoft Account",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (currentUser == null) {
            Text("Status: Not signed in")

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val activity = context as? Activity
                    if (activity != null) {
                        authManager.signIn(activity)
                    }
                }
            ) {
                Text("Sign in with Microsoft")
            }
        } else {
            Text("Signed in: ${currentUser.username}")

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    authManager.signOut()
                }
            ) {
                Text("Sign out")
            }
        }

        authManager.authError?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text("Auth error: $error")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            enabled = authManager.currentUser != null,
            onClick = {
                val profileFile = File(context.filesDir, "exports/profile.json")

                if (!profileFile.exists()) {
                    onUploadResultUpdated("profile.json not found")
                    return@Button
                }

                onUploadResultUpdated("Uploading profile.json...")

                authManager.acquireTokenSilent(
                    scopes = listOf("Files.ReadWrite.AppFolder"),
                    onSuccess = { authResult ->
                        scope.launch {
                            val result = oneDriveUploader.uploadToAppFolder(
                                accessToken = authResult.accessToken,
                                file = profileFile,
                                remoteFilename = "profile.json"
                            )

                            onUploadResultUpdated(
                                if (result.isSuccess) {
                                    "OneDrive upload successful"
                                } else {
                                    "Upload failed: ${result.exceptionOrNull()?.message}"
                                }
                            )
                        }
                    },
                    onError = { exception ->
                        onUploadResultUpdated("Token error: ${exception.message}")
                    }
                )
            }
        ) {
            Text("Upload Profile to OneDrive")
        }

        uploadResult?.let { resultText ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(resultText)
        }

        // Fitness permission section
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Fitness Permissions",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            if (fitnessAllGranted) {
                "Health permissions: Granted"
            } else {
                "Health permissions: Not granted"
            }
        )

        Text("Granted: $fitnessGrantedCount / $fitnessPermissionsSize")

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onLaunchFitnessPermission
        ) {
            Text("Request Health Permissions")
        }

        // Optional medical profile permission section
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Optional Medical Profile",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            if (medicalAllGranted) {
                "Medical permissions: Granted"
            } else {
                "Medical permissions: Not granted"
            }
        )

        Text("Granted: $medicalGrantedCount / $medicalPermissionsSize")

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            enabled = personalHealthRecordAvailable,
            onClick = onLaunchMedicalPermission
        ) {
            Text("Request Medical Permissions")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            enabled = personalHealthRecordAvailable && medicalAllGranted,
            onClick = {
                scope.launch {
                    try {
                        val allergies = medicalRepository.readAllergies()
                        val medications = medicalRepository.readMedications()

                        val profile = medicalProfileParser.parse(
                            allergies = allergies,
                            medications = medications,
                            dietaryRestrictions = dietaryRestrictions
                        )

                        onHealthProfileUpdated(profile)

                        val allergyNames = profile.allergies
                            .joinToString(", ") { it.name }
                            .ifBlank { "None" }

                        val medicationNames = profile.medications
                            .joinToString(", ") { it.name }
                            .ifBlank { "None" }

                        val dietaryRestrictionNames = profile.dietaryRestrictions
                            .joinToString(", ")
                            .ifBlank { "None" }

                        onMedicalResultUpdated(
                            "Allergies: $allergyNames\n" +
                                "Medications: $medicationNames\n" +
                                "Dietary restrictions: $dietaryRestrictionNames"
                        )
                    } catch (e: Exception) {
                        onMedicalResultUpdated(
                            "Medical read failed: ${e.message ?: "Unknown error"}"
                        )
                    }
                }
            }
        ) {
            Text("Read Medical Profile")
        }

        medicalResult?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            enabled = healthProfile != null,
            onClick = {
                val currentProfile = healthProfile?.copy(
                    dietaryRestrictions = dietaryRestrictions
                )

                if (currentProfile != null) {
                    try {
                        val file = profileJsonExporter.exportProfile(currentProfile)
                        onMedicalResultUpdated("Profile exported: ${file.name}")
                    } catch (e: Exception) {
                        onMedicalResultUpdated("Profile export failed: ${e.message ?: "Unknown error"}")
                    }
                }
            }
        ) {
            Text("Export Profile JSON")
        }

        // Daily health summary section
        Spacer(modifier = Modifier.height(24.dp))

        if (!dataLoaded) {
            Text("Yesterday data: Not loaded")
        } else {
            val data = snapshot
            if (loadError != null) {
                Text("Error: $loadError")
            } else {
                Text("Date: ${data?.date ?: "Unknown"}")

                Spacer(modifier = Modifier.height(8.dp))

                Text("Steps: ${data?.steps?.toString() ?: "No data"}")
                Text("Average HR: ${data?.heartRateAverage?.let { "$it bpm" } ?: "No data"}")
                Text("Minimum HR: ${data?.heartRateMinimum?.let { "$it bpm" } ?: "No data"}")
                Text("Maximum HR: ${data?.heartRateMaximum?.let { "$it bpm" } ?: "No data"}")
                Text("Sleep: ${data?.sleepMinutes?.let { "$it min" } ?: "No data"}")
                Text("Exercise: ${data?.exerciseMinutes?.let { "$it min" } ?: "No data"}")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            enabled = fitnessAllGranted,
            onClick = {
                scope.launch {
                    onLoadErrorUpdated(null)
                    onExportResultUpdated(null)

                    try {
                        val summary = repository.getYesterdaySummary()
                        onSnapshotUpdated(summary)
                    } catch (e: Exception) {
                        onSnapshotUpdated(null)
                        onLoadErrorUpdated(e.message ?: "Unknown error")
                    }

                    onDataLoadedUpdated(true)
                }
            }
        ) {
            Text("Read Yesterday Summary")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            enabled = snapshot != null,
            onClick = {
                val currentSnapshot = snapshot
                if (currentSnapshot != null) {
                    try {
                        val file = jsonExporter.exportDailyHealth(currentSnapshot)
                        onExportResultUpdated("Exported: ${file.name}")
                    } catch (e: Exception) {
                        onExportResultUpdated("Export failed: ${e.message ?: "Unknown error"}")
                    }
                }
            }
        ) {
            Text("Export JSON")
        }

        exportResult?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it)
        }
    }
}

/**
 * Updates sync time count while preserving existing times where possible.
 */
private fun updateSyncTimeCount(targetCount: Int, currentTimes: List<LocalTime>): List<LocalTime> {
    val defaultTimes = when (targetCount) {
        1 -> listOf(LocalTime.of(8, 0))
        2 -> listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
        else -> listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0))
    }

    if (currentTimes.isEmpty()) return defaultTimes

    val result = mutableListOf<LocalTime>()
    for (i in 0 until targetCount) {
        if (i < currentTimes.size) {
            result.add(currentTimes[i])
        } else {
            val candidate = defaultTimes[i]
            if (!result.contains(candidate)) {
                result.add(candidate)
            } else {
                val fallback = defaultTimes.firstOrNull { !result.contains(it) }
                    ?: LocalTime.of((candidate.hour + 2) % 24, candidate.minute)
                result.add(fallback)
            }
        }
    }
    return result.distinct().sorted()
}
