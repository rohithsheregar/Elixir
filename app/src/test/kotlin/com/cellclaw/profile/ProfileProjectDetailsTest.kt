package com.cellclaw.profile

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ProfileProjectDetailsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    private fun sampleProject(
        title: String = "Elixir Android Assistant",
        confirmed: Boolean = true
    ) = ProjectDetail(
        id = "proj-123",
        title = title,
        problem = "College placement applications require repetitive form filling and un-tailored resumes.",
        role = "Lead Android Architect & Core SWE",
        tools = listOf("Kotlin", "Jetpack Compose", "Coroutines", "Room", "Hilt"),
        challenge = "Ensuring zero-fabrication ground truth while extracting keywords and automating Chrome forms via Accessibility Service without root.",
        verifiedResults = "Reduced application prep time by 85% and guaranteed 100% verified factual accuracy across 50+ applications.",
        shortStory = "During campus placements, students faced rigid form deadlines with high error rates. Built an on-device assistant that verifies eligibility locally and automates form fills under explicit user supervision.",
        confirmed = confirmed
    )

    @Test
    fun testProjectDetailCreationAndValidation() {
        val proj = sampleProject()
        assertEquals("Elixir Android Assistant", proj.title)
        assertEquals("Lead Android Architect & Core SWE", proj.role)
        assertEquals(5, proj.tools.size)
        assertTrue(proj.confirmed)
        assertTrue(proj.toSummary().contains("Elixir Android Assistant"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testProjectDetailRejectsBlankTitle() {
        sampleProject(title = "   ")
    }

    @Test
    fun testProjectDetailToProfileFactsConversion() {
        val proj = sampleProject()
        val facts = proj.toProfileFacts("base-proj")

        assertTrue("Must contain project_title", facts.any { it.field == "project_title" && it.value == "Elixir Android Assistant" })
        assertTrue("Must contain project_role", facts.any { it.field == "project_role" && it.value.contains("Lead Android") })
        assertTrue("Must contain project_problem", facts.any { it.field == "project_problem" && it.value.contains("repetitive form filling") })
        assertTrue("Must contain project_challenge", facts.any { it.field == "project_challenge" && it.value.contains("zero-fabrication") })
        assertTrue("Must contain project_results", facts.any { it.field == "project_results" && it.value.contains("Reduced application prep time") })
        assertTrue("Must contain project_tools", facts.any { it.field == "project_tools" && it.value.contains("Kotlin, Jetpack Compose") })
        assertTrue("Must contain project_story", facts.any { it.field == "project_story" && it.value.contains("During campus placements") })

        // Every fact must preserve the confirmed flag
        assertTrue("All converted facts must be confirmed", facts.all { it.confirmed })
    }

    @Test
    fun testSerializationAndRoundTripping() {
        val initialProfile = CandidateProfile(
            name = ProfileFact("name-1", "name", "Rohith", ProfileFactSource.USER_ENTERED, true, Instant.now()),
            projectDetails = listOf(sampleProject())
        )

        val serialized = json.encodeToString(initialProfile)
        assertTrue(serialized.contains("Elixir Android Assistant"))
        assertTrue(serialized.contains("verifiedResults"))

        val deserialized = json.decodeFromString<CandidateProfile>(serialized)
        assertEquals(1, deserialized.projectDetails.size)
        val proj = deserialized.projectDetails[0]
        assertEquals("Elixir Android Assistant", proj.title)
        assertEquals(5, proj.tools.size)
    }

    @Test
    fun testBackwardCompatibilityWithLegacyProfileJson() {
        // Legacy JSON without projectDetails field
        val legacyJson = """
            {
                "skills": [],
                "projects": [],
                "internships": [],
                "certificates": [],
                "links": [],
                "reusableAnswers": []
            }
        """.trimIndent()

        val parsed = json.decodeFromString<CandidateProfile>(legacyJson)
        assertNotNull(parsed)
        assertTrue("Legacy profile must parse with empty projectDetails", parsed.projectDetails.isEmpty())
    }

    @Test
    fun testProfileManagerProjectOperations() = runTest {
        val repo = InMemoryProfileRepository()
        val manager = ProfileManager(repo)

        val proj = sampleProject()
        manager.saveProjectDetail(proj)

        val savedProfile = repo.getProfile()
        assertNotNull(savedProfile)
        assertEquals(1, savedProfile!!.projectDetails.size)
        assertEquals("Elixir Android Assistant", savedProfile.projectDetails[0].title)

        // allFacts includes the converted project facts
        val allFacts = savedProfile.allFacts()
        assertTrue("allFacts must include project_role", allFacts.any { it.field == "project_role" })

        // Delete project
        manager.deleteProjectDetail(proj.id)
        val afterDelete = repo.getProfile()
        assertTrue("Project must be removed", afterDelete!!.projectDetails.isEmpty())
    }
}

