package com.wellmeal.connector

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import androidx.health.connect.client.records.MedicalResource
import androidx.health.connect.client.request.ReadMedicalResourcesInitialRequest
import androidx.health.connect.client.request.ReadMedicalResourcesPageRequest
import androidx.health.connect.client.request.ReadMedicalResourcesRequest

@OptIn(ExperimentalPersonalHealthRecordApi::class)
class MedicalProfileRepository(
    context: Context
) {

    private val client =
        HealthConnectClient.getOrCreate(context)

    suspend fun readAllergies(): List<MedicalResource> {
        return readAllPages(
            MedicalResource.MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES
        )
    }

    suspend fun readMedications(): List<MedicalResource> {
        return readAllPages(
            MedicalResource.MEDICAL_RESOURCE_TYPE_MEDICATIONS
        )
    }

    private suspend fun readAllPages(
        medicalResourceType: Int
    ): List<MedicalResource> {

        val result = mutableListOf<MedicalResource>()

        val initialRequest: ReadMedicalResourcesRequest =
            ReadMedicalResourcesInitialRequest(
                medicalResourceType = medicalResourceType,
                medicalDataSourceIds = emptySet(),
                pageSize = 100
            )

        var request: ReadMedicalResourcesRequest =
            initialRequest

        while (true) {

            val response =
                client.readMedicalResources(request)

            result.addAll(
                response.medicalResources
            )

            val nextPageToken =
                response.nextPageToken
                    ?: break

            request =
                ReadMedicalResourcesPageRequest(
                    pageToken = nextPageToken,
                    pageSize = 100
                )
        }

        return result
    }
}
