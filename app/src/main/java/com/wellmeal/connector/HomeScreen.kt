package com.wellmeal.connector

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    fitnessAllGranted: Boolean,
    authManager: MicrosoftAuthManager,
    isSyncing: Boolean,
    lastSyncResult: SyncResult?,
    healthProfile: HealthProfile?,
    dietaryRestrictions: List<String>,
    onSyncNow: () -> Unit
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
        // App Title
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Connection Status Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Connection Status",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                val hcStatus = if (fitnessAllGranted) {
                    "Connected"
                } else {
                    "Needs permission"
                }
                Text("Health Connect: $hcStatus")

                Spacer(modifier = Modifier.height(4.dp))

                val msStatus = if (currentUser != null) {
                    "Connected (${currentUser.username})"
                } else {
                    "Not connected"
                }
                Text("Microsoft Account: $msStatus")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sync Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Sync",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    enabled = currentUser != null && fitnessAllGranted && !isSyncing,
                    onClick = onSyncNow
                ) {
                    Text("Sync Now")
                }

                if (isSyncing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Syncing...")
                } else {
                    lastSyncResult?.let { result ->
                        Spacer(modifier = Modifier.height(8.dp))
                        val resultMessage = when {
                            result.error != null -> {
                                "Sync failed\nError: ${result.error}"
                            }
                            result.profileStatus == ProfileSyncStatus.FAILED -> {
                                "Sync completed with warnings\nDaily: ${result.date}\nProfile: failed"
                            }
                            else -> {
                                "Sync successful\nDaily: ${result.date}\nProfile: ${result.profileStatus.name.lowercase()}"
                            }
                        }
                        Text(resultMessage)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Compact Medical Profile Summary Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Medical Profile Summary",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                val allergyCount = healthProfile?.allergies?.size ?: 0
                val medicationCount = healthProfile?.medications?.size ?: 0
                val dietaryCount = dietaryRestrictions.size

                Text("Allergies: $allergyCount")
                Text("Medications: $medicationCount")
                Text("Dietary Restrictions: $dietaryCount")
            }
        }
    }
}
