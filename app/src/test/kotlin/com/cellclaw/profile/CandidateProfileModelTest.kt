package com.cellclaw.profile

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateProfileModelTest {
    private val timestamp = Instant.parse("2026-01-01T00:00:00Z")

    @Test fun `profile retains confirmed synthetic facts`() {
        val name = ProfileFact("name-1", "name", "Asha Test", ProfileFactSource.USER_ENTERED, true, timestamp)
        val kotlin = ProfileFact("skill-1", "skill", "Kotlin", ProfileFactSource.USER_ENTERED, true, timestamp)
        val profile = CandidateProfile(name = name, skills = listOf(kotlin))
        assertEquals("Asha Test", profile.name?.value)
        assertTrue(profile.skills.single().confirmed)
    }

    @Test fun `defaults do not invent facts`() {
        val profile = CandidateProfile()
        assertEquals(null, profile.cgpa)
        assertTrue(profile.projects.isEmpty())
        assertFalse(ProfileFact("x", "email", "test@example.invalid", ProfileFactSource.IMPORTED_FROM_RESUME, false, timestamp).confirmed)
    }
}
