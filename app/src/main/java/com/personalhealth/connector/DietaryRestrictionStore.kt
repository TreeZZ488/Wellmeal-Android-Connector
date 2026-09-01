package com.personalhealth.connector

import android.content.Context

class DietaryRestrictionStore(
    context: Context
) {

    private val preferences =
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    fun load(): List<String> {

        return preferences
            .getStringSet(
                KEY_DIETARY_RESTRICTIONS,
                emptySet()
            )
            ?.toList()
            ?.sortedBy {
                it.lowercase()
            }
            ?: emptyList()
    }

    fun save(
        restrictions: List<String>
    ) {

        preferences
            .edit()
            .putStringSet(
                KEY_DIETARY_RESTRICTIONS,
                restrictions.toSet()
            )
            .apply()
    }

    companion object {

        private const val PREFERENCES_NAME =
            "medical_profile_preferences"

        private const val KEY_DIETARY_RESTRICTIONS =
            "dietary_restrictions"
    }
}
