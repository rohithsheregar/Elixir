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

    private fun fact(field: String, value: String, confirmed: Boolean) = ProfileFact(
        id = "$field-1",
        field = field,
        value = value,
        source = ProfileFactSource.USER_ENTERED,
        confirmed = confirmed,
        updatedAt = timestamp
    )
}
