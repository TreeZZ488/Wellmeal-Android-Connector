package com.wellmeal.connector

import java.time.LocalDate

data class DailyHealthSnapshot(
    val date: LocalDate,
    val steps: Long?,
    val heartRateAverage: Long?,
    val heartRateMinimum: Long?,
    val heartRateMaximum: Long?,
    val sleepMinutes: Long?,
    val exerciseMinutes: Long?
)

data class HealthAllergy(
    val name: String,
    val category: String? = null,
    val severity: String? = null
)

data class Medication(
    val name: String,
    val status: String? = null
)

data class HealthProfile(
    val allergies: List<HealthAllergy> = emptyList(),
    val dietaryRestrictions: List<String> = emptyList(),
    val medications: List<Medication> = emptyList()
)
