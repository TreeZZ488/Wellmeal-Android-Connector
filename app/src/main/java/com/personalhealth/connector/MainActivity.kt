package com.personalhealth.connector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                HealthConnectScreen()
            }
        }
    }
}

@OptIn(ExperimentalPersonalHealthRecordApi::class)
@Composable
fun HealthConnectScreen() {

    val context = LocalContext.current

    // Check Health Connect availability.
    val sdkStatus = HealthConnectClient.getSdkStatus(context)

    if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Personal Health Connector",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text("Health Connect: Unavailable")
        }

        return
    }

    // Create the Health Connect client.
    val healthConnectClient = remember {
        HealthConnectClient.getOrCreate(context)
    }

    // Check Personal Health Record support.
    val personalHealthRecordAvailable = remember {
        healthConnectClient.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }

    // Define fitness permissions.
    val fitnessPermissions = remember {
        setOf(
            HealthPermission.getReadPermission(
                StepsRecord::class
            ),
            HealthPermission.getReadPermission(
                HeartRateRecord::class
            ),
            HealthPermission.getReadPermission(
                SleepSessionRecord::class
            ),
            HealthPermission.getReadPermission(
                ExerciseSessionRecord::class
            )
        )
    }

    // Define optional medical profile permissions.
    val medicalPermissions = remember {
        setOf(
            HealthPermission
                .PERMISSION_READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,

            HealthPermission
                .PERMISSION_READ_MEDICAL_DATA_MEDICATIONS
        )
    }

    // Store all currently granted Health Connect permissions.
    var grantedPermissions by remember {
        mutableStateOf<Set<String>>(emptySet())
    }

    // Request fitness permissions.
    val fitnessPermissionLauncher =
        rememberLauncherForActivityResult(
            PermissionController
                .createRequestPermissionResultContract()
        ) { result ->

            grantedPermissions =
                grantedPermissions + result
        }

    // Request optional medical profile permissions.
    val medicalPermissionLauncher =
        rememberLauncherForActivityResult(
            PermissionController
                .createRequestPermissionResultContract()
        ) { result ->

            grantedPermissions =
                grantedPermissions + result
        }

    // Read the existing permission state when the app starts.
    LaunchedEffect(Unit) {

        grantedPermissions =
            healthConnectClient
                .permissionController
                .getGrantedPermissions()
    }

    val fitnessGrantedCount =
        fitnessPermissions.count {
            grantedPermissions.contains(it)
        }

    val fitnessAllGranted =
        grantedPermissions.containsAll(
            fitnessPermissions
        )

    val medicalGrantedCount =
        medicalPermissions.count {
            grantedPermissions.contains(it)
        }

    val medicalAllGranted =
        grantedPermissions.containsAll(
            medicalPermissions
        )

    // Create data services.
    val repository = remember {
        HealthConnectRepository(context)
    }

    val jsonExporter = remember {
        HealthJsonExporter(context)
    }

    val scope = rememberCoroutineScope()

    // Store the currently loaded daily health snapshot.
    var snapshot by remember {
        mutableStateOf<DailyHealthSnapshot?>(null)
    }

    var dataLoaded by remember {
        mutableStateOf(false)
    }

    var loadError by remember {
        mutableStateOf<String?>(null)
    }

    var exportResult by remember {
        mutableStateOf<String?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Personal Health Connector",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("Health Connect: Available")

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            if (personalHealthRecordAvailable) {
                "Personal Health Record: Available"
            } else {
                "Personal Health Record: Unavailable"
            }
        )

        // Fitness permission section.
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

        Text(
            "Granted: $fitnessGrantedCount / ${fitnessPermissions.size}"
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                fitnessPermissionLauncher.launch(
                    fitnessPermissions
                )
            }
        ) {
            Text("Request Health Permissions")
        }

        // Optional medical profile permission section.
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

        Text(
            "Granted: $medicalGrantedCount / ${medicalPermissions.size}"
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            enabled = personalHealthRecordAvailable,
            onClick = {
                medicalPermissionLauncher.launch(
                    medicalPermissions
                )
            }
        ) {
            Text("Request Medical Permissions")
        }

        // Daily health summary section.
        Spacer(modifier = Modifier.height(24.dp))

        if (!dataLoaded) {

            Text("Yesterday data: Not loaded")

        } else {

            val data = snapshot

            if (loadError != null) {

                Text("Error: $loadError")

            } else {

                Text(
                    text = "Date: ${data?.date ?: "Unknown"}"
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Steps: ${
                        data?.steps?.toString()
                            ?: "No data"
                    }"
                )

                Text(
                    "Average HR: ${
                        data?.heartRateAverage
                            ?.let { "$it bpm" }
                            ?: "No data"
                    }"
                )

                Text(
                    "Minimum HR: ${
                        data?.heartRateMinimum
                            ?.let { "$it bpm" }
                            ?: "No data"
                    }"
                )

                Text(
                    "Maximum HR: ${
                        data?.heartRateMaximum
                            ?.let { "$it bpm" }
                            ?: "No data"
                    }"
                )

                Text(
                    "Sleep: ${
                        data?.sleepMinutes
                            ?.let { "$it min" }
                            ?: "No data"
                    }"
                )

                Text(
                    "Exercise: ${
                        data?.exerciseMinutes
                            ?.let { "$it min" }
                            ?: "No data"
                    }"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Read yesterday's aggregated Health Connect data.
        Button(
            enabled = fitnessAllGranted,
            onClick = {

                scope.launch {

                    loadError = null
                    exportResult = null

                    try {

                        snapshot =
                            repository
                                .getYesterdaySummary()

                    } catch (e: Exception) {

                        snapshot = null

                        loadError =
                            e.message ?: "Unknown error"
                    }

                    dataLoaded = true
                }
            }
        ) {
            Text("Read Yesterday Summary")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Export the loaded snapshot to JSON.
        Button(
            enabled = snapshot != null,
            onClick = {

                val currentSnapshot = snapshot

                if (currentSnapshot != null) {

                    try {

                        val file =
                            jsonExporter.exportDailyHealth(
                                snapshot = currentSnapshot
                            )

                        exportResult =
                            "Exported: ${file.name}"

                    } catch (e: Exception) {

                        exportResult =
                            "Export failed: ${
                                e.message ?: "Unknown error"
                            }"
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