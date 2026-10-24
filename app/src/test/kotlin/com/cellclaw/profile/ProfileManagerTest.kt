package com.cellclaw.profile

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileManagerTest {
    private val timestamp = Instant.parse("2026-01-01T00:00:00Z")

    @Test fun `unconfirmed and missing facts are UNKNOWN`() = runTest {
        val repository = InMemoryProfileRepository()
        repository.saveProfile(CandidateProfile(email = fact("email", "test@example.invalid", confirmed = false)))
        val manager = ProfileManager(repository)

        assertEquals(ConfirmedFactResult.Unknown, manager.getConfirmedFact("email"))
        assertEquals(ConfirmedFactResult.Unknown, manager.getConfirmedFact("cgpa"))
    }

    @Test fun `proposal shows diff and becomes usable only after confirmation`() = runTest {
        val repository = InMemoryProfileRepository()
        val previous = fact("name", "Asha Test", confirmed = true)
        repository.saveProfile(CandidateProfile(name = previous))
        val manager = ProfileManager(repository)
        val proposal = manager.proposeFactUpdate(
            previous,
            fact("name", "Asha Updated", confirmed = true)
        )

        assertEquals(previous, proposal.current)
        assertFalse(proposal.proposed.confirmed)
        assertEquals("Asha Updated", proposal.proposed.value)
        assertEquals("Asha Test", (manager.getConfirmedFact("name") as ConfirmedFactResult.Confirmed).fact.value)

        val confirmed = manager.confirmProposal(proposal)

        assertTrue(confirmed.confirmed)
        assertEquals(
            "Asha Updated",
            (manager.getConfirmedFact("name") as ConfirmedFactResult.Confirmed).fact.value
        )
    }

    @Test fun `exportProfileJson returns null when no profile exists`() = runTest {
        val repository = InMemoryProfileRepository()
        val manager = ProfileManager(repository)

        assertEquals(null, manager.exportProfileJson())
    }

    @Test fun `exportProfileJson exports full profile and round trip restores facts`() = runTest {
        val repository = InMemoryProfileRepository()
        val profile = CandidateProfile(
            name = fact("name", "Ravi Kumar", confirmed = true),
            email = fact("email", "ravi@example.invalid", confirmed = true),
            skills = listOf(
                fact("skill", "Kotlin", confirmed = true),
                fact("skill", "Python", confirmed = false)
            )
        )
        repository.saveProfile(profile)
        val manager = ProfileManager(repository)

        val exportedJson = manager.exportProfileJson()
        org.junit.Assert.assertNotNull(exportedJson)
        assertTrue(exportedJson!!.contains("Ravi Kumar"))
        assertTrue(exportedJson.contains("Kotlin"))
        assertTrue(exportedJson.contains("Python"))

        val restored = kotlinx.serialization.json.Json.decodeFromString<CandidateProfile>(exportedJson)
        assertEquals(profile, restored)
    }

    @Test fun `exportProfileJson with confirmedOnly excludes unconfirmed facts`() = runTest {
        val repository = InMemoryProfileRepository()
        val profile = CandidateProfile(
            name = fact("name", "Ravi Kumar", confirmed = true),
            email = fact("email", "unconfirmed@example.invalid", confirmed = false),
            skills = listOf(
                fact("skill", "Kotlin", confirmed = true),
                fact("skill", "Rust", confirmed = false)
            )
        )
        repository.saveProfile(profile)
        val manager = ProfileManager(repository)

        val exportedJson = manager.exportProfileJson(confirmedOnly = true)
        org.junit.Assert.assertNotNull(exportedJson)
        assertTrue(exportedJson!!.contains("Ravi Kumar"))
        assertTrue(exportedJson.contains("Kotlin"))
        assertFalse(exportedJson.contains("unconfirmed@example.invalid"))
        assertFalse(exportedJson.contains("Rust"))

        val restored = kotlinx.serialization.json.Json.decodeFromString<CandidateProfile>(exportedJson)
        org.junit.Assert.assertNull(restored.email)
        assertEquals(1, restored.skills.size)
        assertEquals("Kotlin", restored.skills.single().value)
    }

    @Test fun `fullWipe clears repository and resets all facts to UNKNOWN`() = runTest {
        val repository = InMemoryProfileRepository()
        val profile = CandidateProfile(
            name = fact("name", "Ravi Kumar", confirmed = true),
            cgpa = fact("cgpa", "8.9", confirmed = true)
        )
        repository.saveProfile(profile)
        val manager = ProfileManager(repository)

        assertEquals("Ravi Kumar", (manager.getConfirmedFact("name") as ConfirmedFactResult.Confirmed).fact.value)

        manager.fullWipe()

        org.junit.Assert.assertNull(repository.getProfile())
        assertEquals(ConfirmedFactResult.Unknown, manager.getConfirmedFact("name"))
        assertEquals(ConfirmedFactResult.Unknown, manager.getConfirmedFact("cgpa"))
        org.junit.Assert.assertNull(manager.exportProfileJson())
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
