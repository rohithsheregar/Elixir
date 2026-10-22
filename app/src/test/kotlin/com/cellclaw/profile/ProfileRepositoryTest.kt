package com.cellclaw.profile

import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileRepositoryTest {
    private val timestamp = Instant.parse("2026-01-01T00:00:00Z")

    @Test fun `in-memory repository returns the profile that was saved`() = runTest {
        val repository = InMemoryProfileRepository()
        val profile = CandidateProfile(
            name = fact("name", "Asha Test", confirmed = true),
            skills = listOf(fact("skill", "Kotlin", confirmed = true))
        )

        repository.saveProfile(profile)

        assertEquals(profile, repository.getProfile())
    }

    @Test fun `saving again replaces the old profile and delete removes it`() = runTest {
        val repository = InMemoryProfileRepository()
        repository.saveProfile(CandidateProfile(email = fact("email", "old@example.invalid", confirmed = true)))
        val replacement = CandidateProfile(email = fact("email", "new@example.invalid", confirmed = true))

        repository.saveProfile(replacement)
        assertEquals(replacement, repository.getProfile())

        repository.deleteProfile()
        assertNull(repository.getProfile())
    }

    @Test fun `profile JSON round trip preserves confirmation and timestamp`() {
        val profile = CandidateProfile(
            projects = listOf(fact("project", "Synthetic project", confirmed = false))
        )

        val restored = Json.decodeFromString<CandidateProfile>(Json.encodeToString(profile))

        assertEquals(profile, restored)
        assertEquals(false, restored.projects.single().confirmed)
    }

    private fun fact(field: String, value: String, confirmed: Boolean) = ProfileFact(
        id = "$field-1",
        field = field,
        value = value,
        source = ProfileFactSource.USER_ENTERED,
        confirmed = confirmed,
        updatedAt = timestamp
    )
}
