package com.cellclaw.ui

import com.cellclaw.eligibility.EligibilityEngine
import com.cellclaw.jd.JdDocumentReader
import com.cellclaw.jd.JdParser
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.InMemoryProfileRepository
import com.cellclaw.profile.ProfileFact
import com.cellclaw.profile.ProfileFactSource
import java.time.Instant
import com.cellclaw.tracker.InMemoryApplicationTracker
import com.cellclaw.ui.viewmodel.NewApplicationViewModel
import com.cellclaw.ui.viewmodel.ResumePreviewViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResumeTailoringWiringTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var profileRepo: InMemoryProfileRepository
    private lateinit var tracker: InMemoryApplicationTracker
    private lateinit var jdParser: JdParser
    private lateinit var eligibilityEngine: EligibilityEngine
    private lateinit var jdDocumentReader: JdDocumentReader

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        profileRepo = InMemoryProfileRepository()
        tracker = InMemoryApplicationTracker()
        jdParser = JdParser()
        eligibilityEngine = EligibilityEngine()
        jdDocumentReader = JdDocumentReader()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val timestamp = Instant.parse("2026-01-01T00:00:00Z")

    private fun fact(field: String, value: String, confirmed: Boolean = true, id: String = "$field-1") = ProfileFact(
        id = id,
        field = field,
        value = value,
        source = ProfileFactSource.USER_ENTERED,
        confirmed = confirmed,
        updatedAt = timestamp
    )

    private suspend fun seedCandidateProfile() {
        val profile = CandidateProfile(
            name = fact("name", "Jane Developer", confirmed = true, id = "fact-name"),
            email = fact("email", "jane@example.com", confirmed = true, id = "fact-email"),
            degree = fact("degree", "B.Tech Computer Science", confirmed = true, id = "fact-degree"),
            skills = listOf(
                fact("skill", "Kotlin", confirmed = true, id = "skill-1"),
                fact("skill", "Android", confirmed = true, id = "skill-2"),
                fact("skill", "Python", confirmed = true, id = "skill-3"),
                fact("skill", "Docker", confirmed = true, id = "skill-4"),
                fact("skill", "SQL", confirmed = true, id = "skill-5")
            )
        )
        profileRepo.saveProfile(profile)
    }

    @Test
    fun `parsed JdRequirement is persisted and loads into ResumePreviewViewModel to tailor skill ordering`() = runTest(testDispatcher) {
        seedCandidateProfile()

        val newAppViewModel = NewApplicationViewModel(
            applicationTracker = tracker,
            profileRepository = profileRepo,
            jdParser = jdParser,
            jdDocumentReader = jdDocumentReader,
            eligibilityEngine = eligibilityEngine
        )

        // Enter a JD that explicitly requires Python and Docker, and prefers SQL.
        // Untailored alphabetical order would be: Android, Docker, Kotlin, Python, SQL.
        // Tailored ordering must be:
        // Group 0 (Required): Docker, Python
        // Group 1 (Preferred): SQL
        // Group 2 (Unmentioned): Android, Kotlin
        val jdText = """
            Company: Acme Systems
            Role: Backend Infrastructure Engineer
            Required Skills: Python, Docker
            Preferred Skills: SQL
            Minimum CGPA: 7.0
        """.trimIndent()

        newAppViewModel.onJdTextChanged(jdText)
        newAppViewModel.parseCurrentJd()

        var createdAppId: String? = null
        newAppViewModel.createApplication { id ->
            createdAppId = id
        }
        testScheduler.advanceUntilIdle()

        assertNotNull("Application ID must be created", createdAppId)
        val appId = createdAppId!!

        val savedRecord = tracker.getApplication(appId)
        assertNotNull("ApplicationRecord must be persisted in tracker", savedRecord)
        assertTrue("Application notes must not be null", !savedRecord!!.notes.isNullOrBlank())

        // Now verify ResumePreviewViewModel loads the persisted JD requirements
        val resumePreviewViewModel = ResumePreviewViewModel(
            applicationTracker = tracker,
            profileRepository = profileRepo
        )

        resumePreviewViewModel.load(appId)
        testScheduler.advanceUntilIdle()

        val state = resumePreviewViewModel.uiState.value
        assertEquals("Resume preview must finish loading", false, state.isLoading)
        assertNull("Resume preview must not have error: ${state.errorMessage}", state.errorMessage)
        assertNotNull("Generated resume content must be present", state.resume)

        val skillsSection = state.resume!!.sections.firstOrNull { it.title == "Technical Skills" }
        assertNotNull("Technical Skills section must be present", skillsSection)
        val skillTexts = skillsSection!!.lines.map { it.text }

        assertEquals("All 5 confirmed skills must be present", 5, skillTexts.size)

        // Required skills (Docker, Python) must appear first (alphabetical within required)
        assertEquals("Docker", skillTexts[0])
        assertEquals("Python", skillTexts[1])

        // Preferred skills (SQL) must appear next
        assertEquals("SQL", skillTexts[2])

        // Unmentioned skills (Android, Kotlin) must appear last (alphabetical)
        assertEquals("Android", skillTexts[3])
        assertEquals("Kotlin", skillTexts[4])
    }

    @Test
    fun `ResumePreviewViewModel falls back gracefully to alphabetical order when notes lack JdRequirement`() = runTest(testDispatcher) {
        seedCandidateProfile()

        // Create an application record with null notes (or legacy note format)
        val record = com.cellclaw.tracker.ApplicationRecord(
            id = "legacy-app-1",
            company = "Legacy Corp",
            role = "Software Engineer",
            jdHash = "dummyhash123",
            eligibilityResult = "ELIGIBLE",
            notes = null
        )
        tracker.registerApplication(record)

        val resumePreviewViewModel = ResumePreviewViewModel(
            applicationTracker = tracker,
            profileRepository = profileRepo
        )

        resumePreviewViewModel.load("legacy-app-1")
        testScheduler.advanceUntilIdle()

        val state = resumePreviewViewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.errorMessage)
        assertNotNull(state.resume)

        val skillsSection = state.resume!!.sections.first { it.title == "Technical Skills" }
        val skillTexts = skillsSection.lines.map { it.text }

        // Untailored fallback: all skills in alphabetical order
        assertEquals(listOf("Android", "Docker", "Kotlin", "Python", "SQL"), skillTexts)
    }
}
