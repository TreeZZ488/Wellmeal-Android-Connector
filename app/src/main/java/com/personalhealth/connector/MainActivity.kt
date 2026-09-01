package com.personalhealth.connector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import kotlinx.coroutines.launch
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi

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

    val personalHealthRecordAvailable = remember {
        healthConnectClient.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }

    // Define the Health Connect permissions used by V0.1.
    val permissions = remember {
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

    var grantedPermissions by remember {
        mutableStateOf<Set<String>>(emptySet())
    }

    // Launcher for Health Connect permission requests.
    val permissionLauncher =
        rememberLauncherForActivityResult(
            PermissionController
                .createRequestPermissionResultContract()
        ) { result ->

            grantedPermissions = result
        }

    // Read the current permission state when the screen starts.
    LaunchedEffect(Unit) {

        grantedPermissions =
            healthConnectClient
                .permissionController
                .getGrantedPermissions()
    }

    val allGranted =
        grantedPermissions.containsAll(permissions)

    val repository = remember {
        HealthConnectRepository(context)
    }

    val jsonExporter = remember {
        HealthJsonExporter(context)
    }

    val scope = rememberCoroutineScope()

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

        Spacer(modifier = Modifier.height(24.dp))

        Text("Health Connect: Available")

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            if (personalHealthRecordAvailable) {
                "Personal Health Record: Available"
            } else {
                "Personal Health Record: Unavailable"
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            if (allGranted) {
                "Health permissions: Granted"
            } else {
                "Health permissions: Not granted"
            }
        )

        Text(
            "Granted: ${grantedPermissions.size} / ${permissions.size}"
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                permissionLauncher.launch(permissions)
            }
        ) {
            Text("Request Health Permissions")
        }

        Spacer(modifier = Modifier.height(28.dp))

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

        // Read yesterday's Health Connect summary.
        Button(
            enabled = allGranted,
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

        // Export the current snapshot to an internal JSON file.
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