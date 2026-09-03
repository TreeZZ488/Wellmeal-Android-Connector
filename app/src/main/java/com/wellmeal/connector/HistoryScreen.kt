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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    syncHistoryStore: SyncHistoryStore
) {
    val historyEntries = remember { syncHistoryStore.loadHistory() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Sync History",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (historyEntries.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "No sync history yet.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            historyEntries.forEach { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        val outcomeText = when (entry.outcome) {
                            SyncOutcome.SUCCESS -> "Success"
                            SyncOutcome.PARTIAL -> "Partial"
                            SyncOutcome.FAILED -> "Failed"
                        }

                        Text(
                            text = outcomeText,
                            style = MaterialTheme.typography.titleMedium,
                            color = when (entry.outcome) {
                                SyncOutcome.SUCCESS -> MaterialTheme.colorScheme.primary
                                SyncOutcome.PARTIAL -> MaterialTheme.colorScheme.tertiary
                                SyncOutcome.FAILED -> MaterialTheme.colorScheme.error
                            }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = formatTimestamp(entry.completedAt),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Daily data: ${entry.date}")
                        Text("Daily: ${if (entry.dailyUploaded) "Uploaded" else "Failed"}")
                        Text("Latest: ${if (entry.latestUploaded) "Uploaded" else "Failed"}")
                        Text("Profile: ${entry.profileStatus.name.lowercase().replaceFirstChar { it.uppercase() }}")
                        Text("Trigger: ${entry.trigger.name.lowercase().replaceFirstChar { it.uppercase() }}")
                        if (entry.retryScheduled) {
                            Text("Retry scheduled: Yes")
                        }

                        if (entry.outcome != SyncOutcome.SUCCESS) {
                            val errorText = entry.error ?: entry.profileError
                            if (!errorText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Error: $errorText",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

private fun formatTimestamp(completedAt: String): String {
    return try {
        val instant = Instant.parse(completedAt)
        val zone = ZoneId.systemDefault()
        val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.US)
        formatter.format(instant.atZone(zone))
    } catch (_: Exception) {
        completedAt
    }
}
