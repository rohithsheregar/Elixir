package com.cellclaw.profile

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persistence boundary for the one candidate profile. */
interface ProfileRepository {
    suspend fun getProfile(): CandidateProfile?

    suspend fun saveProfile(profile: CandidateProfile)

    suspend fun deleteProfile()
}

/** JVM-testable repository used by unit tests and callers that do not have Android storage. */
class InMemoryProfileRepository : ProfileRepository {
    private var profile: CandidateProfile? = null

    override suspend fun getProfile(): CandidateProfile? = profile

    override suspend fun saveProfile(profile: CandidateProfile) {
        this.profile = profile
    }

    override suspend fun deleteProfile() {
        profile = null
    }
}

/**
 * Stores the complete profile as JSON in Keystore-backed encrypted preferences.
 *
 * No candidate fact is written to Room: Room is reserved for non-sensitive metadata.
 */
class EncryptedSharedPreferencesProfileRepository(
    context: Context,
    private val json: Json = Json { encodeDefaults = true }
) : ProfileRepository {
    private val appContext = context.applicationContext

    private val preferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PROFILE_PREFERENCES,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun getProfile(): CandidateProfile? = preferences
        .getString(PROFILE_JSON_KEY, null)
        ?.let(json::decodeFromString)

    override suspend fun saveProfile(profile: CandidateProfile) {
        check(preferences.edit().putString(PROFILE_JSON_KEY, json.encodeToString(profile)).commit()) {
            "Unable to persist the encrypted candidate profile"
        }
    }

    override suspend fun deleteProfile() {
        check(preferences.edit().remove(PROFILE_JSON_KEY).commit()) {
            "Unable to delete the encrypted candidate profile"
        }
    }

    private companion object {
        const val PROFILE_PREFERENCES = "encrypted_candidate_profile"
        const val PROFILE_JSON_KEY = "candidate_profile_json"
    }
}
