package com.wellmeal.connector

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalPersonalHealthRecordApi::class)
@Composable
fun SettingsScreen(
    context: Context,
    authManager: MicrosoftAuthManager,
    oneDriveUploader: OneDriveUploader,
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

        // Placeholder User Settings Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Sync Settings",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Automatic sync: Coming soon")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Sync schedule: Coming soon")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Network preference: Coming soon")
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
