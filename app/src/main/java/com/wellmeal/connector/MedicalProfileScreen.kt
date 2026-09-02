package com.wellmeal.connector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MedicalProfileScreen(
    healthProfile: HealthProfile?,
    dietaryRestrictions: List<String>,
    onAddDietaryRestriction: (String) -> Unit,
    onRemoveDietaryRestriction: (String) -> Unit
) {
    var dietaryRestrictionInput by remember {
        mutableStateOf("")
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
            text = "Medical Profile",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Allergies Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Allergies",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                val allergies = healthProfile?.allergies ?: emptyList()
                if (allergies.isEmpty()) {
                    Text("No allergies recorded")
                } else {
                    allergies.forEach { allergy ->
                        val details = buildString {
                            append(allergy.name)
                            if (!allergy.category.isNullOrBlank()) {
                                append(" (${allergy.category})")
                            }
                            if (!allergy.severity.isNullOrBlank()) {
                                append(" - ${allergy.severity}")
                            }
                        }
                        Text(details)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Medications Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Medications",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                val medications = healthProfile?.medications ?: emptyList()
                if (medications.isEmpty()) {
                    Text("No medications recorded")
                } else {
                    medications.forEach { medication ->
                        val details = buildString {
                            append(medication.name)
                            if (!medication.status.isNullOrBlank()) {
                                append(" (${medication.status})")
                            }
                        }
                        Text(details)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dietary Restrictions Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Dietary Restrictions",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Add dietary restrictions that are not available from Health Connect."
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = dietaryRestrictionInput,
                    onValueChange = {
                        dietaryRestrictionInput = it
                    },
                    label = {
                        Text("Restriction")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    enabled = dietaryRestrictionInput.trim().isNotEmpty(),
                    onClick = {
                        val input = dietaryRestrictionInput.trim()
                        if (input.isNotEmpty()) {
                            onAddDietaryRestriction(input)
                            dietaryRestrictionInput = ""
                        }
                    }
                ) {
                    Text("Add Restriction")
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (dietaryRestrictions.isEmpty()) {
                    Text("No dietary restrictions")
                } else {
                    dietaryRestrictions.forEach { restriction ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(restriction)

                            Button(
                                onClick = {
                                    onRemoveDietaryRestriction(restriction)
                                }
                            ) {
                                Text("Remove")
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
