package com.cellclaw.profile

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeImporterTest {
    private val timestamp = Instant.parse("2026-01-01T00:00:00Z")
    private val importer = ResumeImporter(now = { timestamp }, newId = { "synthetic-id" })

    @Test fun `explicit synthetic resume labels become unconfirmed proposals`() {
        val previousName = ProfileFact(
            id = "name-1",
            field = "name",
            value = "Asha Test",
            source = ProfileFactSource.USER_ENTERED,
            confirmed = true,
            updatedAt = timestamp
        )

        val proposals = importer.import(
            """
            Name: Asha Updated
            Email: asha@example.invalid
            CGPA: 8.7 / 10
            Skills: Kotlin, Android, SQL
            GitHub: https://github.example.invalid/asha
            """.trimIndent(),
            CandidateProfile(name = previousName)
        )

        assertEquals(previousName, proposals.single { it.proposed.field == "name" }.current)
        assertEquals(setOf("name", "email", "cgpa", "cgpaScale", "skill", "link"), proposals.map { it.proposed.field }.toSet())
        assertEquals(setOf("Kotlin", "Android", "SQL"), proposals.filter { it.proposed.field == "skill" }.map { it.proposed.value }.toSet())
        assertTrue(proposals.all { it.proposed.source == ProfileFactSource.IMPORTED_FROM_RESUME })
        assertTrue(proposals.all { !it.proposed.confirmed })
        assertTrue(proposals.all { it.proposed.updatedAt == timestamp })
    }

    @Test fun `unlabelled resume prose does not fabricate profile facts`() {
        val proposals = importer.import("Built Android apps with Kotlin and worked on distributed systems.")

        assertTrue(proposals.isEmpty())
    }

    @Test fun `blank labelled values are ignored`() {
        val proposals = importer.import("Name:   \nSkills:   ")

        assertTrue(proposals.isEmpty())
    }
}
