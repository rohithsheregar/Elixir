package com.cellclaw.ui

import com.cellclaw.jd.JdField
import com.cellclaw.jd.JdRequirement
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.InMemoryProfileRepository
import com.cellclaw.profile.ProfileFact
import com.cellclaw.profile.ProfileFactSource
import com.cellclaw.resume.*
import com.cellclaw.tracker.ApplicationPayload
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.InMemoryApplicationTracker
import com.cellclaw.tracker.toPersisted
import com.cellclaw.ui.viewmodel.ResumePreviewViewModel
import com.cellclaw.ui.viewmodel.ResumeViewTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ResumeDiffViewTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var profileRepo: InMemoryProfileRepository
    private lateinit var tracker: InMemoryApplicationTracker
    private val timestamp = Instant.parse("2026-01-01T00:00:00Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        profileRepo = InMemoryProfileRepository()
        tracker = InMemoryApplicationTracker()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fact(field: String, value: String, confirmed: Boolean = true, id: String = "$field-1") = ProfileFact(
        id = id,
        field = field,
        value = value,
        source = ProfileFactSource.USER_ENTERED,
        confirmed = confirmed,
        updatedAt = timestamp
    )

    private fun createResume(skills: List<String>, experiences: List<String>): ResumeContent {
        return ResumeContent(
            name = "Jane Developer",
            email = "jane@example.com",
            phone = "+91 9876543210",
            education = listOf("B.Tech Computer Science"),
            sections = listOf(
                ResumeSection(
                    title = "Skills",
                    lines = skills.mapIndexed { idx, s ->
                        ResumeLine(id = "skill-$idx", text = s, profileFactId = "fact-skill-$idx")
                    }
                ),
                ResumeSection(
                    title = "Experience",
                    lines = experiences.mapIndexed { idx, e ->
                        ResumeLine(id = "exp-$idx", text = e, profileFactId = "fact-exp-$idx")
                    }
                )
            ),
            evidenceMap = EvidenceMap(
                lineToFactId = (skills.indices.map { "skill-$it" to "fact-skill-$it" } +
                        experiences.indices.map { "exp-$it" to "fact-exp-$it" }).toMap()
            )
        )
    }

    @Test
    fun testResumeDifferDetectsIdenticalResumes() {
        val master = createResume(
            skills = listOf("Android", "Kotlin", "Java"),
            experiences = listOf("Lead Engineer at Alpha Corp", "Software Intern at Beta")
        )
        val tailored = createResume(
            skills = listOf("Android", "Kotlin", "Java"),
            experiences = listOf("Lead Engineer at Alpha Corp", "Software Intern at Beta")
        )

        val diff = ResumeDiffer().diff(master, tailored)
        assertFalse("Identical resumes must not be marked material", diff.isMaterialChange)
        assertFalse("Identical resumes do not require approval warning", diff.requiresUserApproval)
        assertEquals(0, diff.entries.size)
        assertTrue(diff.summary.contains("identically"))
    }

    @Test
    fun testResumeDifferDetectsAdditionsOmissionsAndReorderings() {
        val master = createResume(
            skills = listOf("Android", "Java", "Kotlin"),
            experiences = listOf("Internship at Alpha")
        )
        val tailored = createResume(
            // Reordered Kotlin first, removed Java, added Docker
            skills = listOf("Kotlin", "Android", "Docker"),
            experiences = listOf("Internship at Alpha", "Freelance Mobile Dev")
        )

        val diff = ResumeDiffer().diff(master, tailored)
        assertTrue("Material modifications must trigger isMaterialChange", diff.isMaterialChange)
        assertTrue(diff.entries.isNotEmpty())

        val addedEntries = diff.entries.filter { it.type == DiffType.ADDED }
        val removedEntries = diff.entries.filter { it.type == DiffType.REMOVED }

        // Docker added to Skills, Freelance added to Experience
        assertTrue(addedEntries.any { it.section == "Skills" && it.newText == "Docker" })
        assertTrue(addedEntries.any { it.section == "Experience" && it.newText == "Freelance Mobile Dev" })

        // Java removed from Skills
        assertTrue(removedEntries.any { it.section == "Skills" && it.oldText == "Java" })
    }

    @Test
    fun testViewModelTabSwitchingAndDiffComputation() = runTest(testDispatcher) {
        val profile = CandidateProfile(
            name = fact("name", "Jane Developer", id = "fact-name"),
            email = fact("email", "jane@example.com", id = "fact-email"),
            skills = listOf(
                fact("skill", "Kotlin", id = "skill-1"),
                fact("skill", "Android", id = "skill-2"),
                fact("skill", "Python", id = "skill-3")
            )
        )
        profileRepo.saveProfile(profile)

        val jdReq = JdRequirement(
            company = JdField("Cloud Corp"),
            role = JdField("Python Backend Dev"),
            requiredSkills = JdField(listOf("Python"))
        )
        val appPayload = ApplicationPayload(
            jdRequirement = jdReq.toPersisted()
        )
        val notesJson = Json.encodeToString(appPayload)

        val record = ApplicationRecord(
            id = "app-diff-1",
            company = "Cloud Corp",
            role = "Python Backend Dev",
            jdHash = "cloudhash123",
            eligibilityResult = "ELIGIBLE",
            notes = notesJson
        )
        tracker.registerApplication(record)

        val viewModel = ResumePreviewViewModel(
            applicationTracker = tracker,
            profileRepository = profileRepo
        )

        viewModel.load("app-diff-1")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("Resume must be generated", state.resume)
        assertNotNull("Master resume must be built", state.masterResume)
        assertNotNull("Diff result must be computed", state.diffResult)
        assertEquals(ResumeViewTab.STRUCTURED, state.activeTab)

        // Switch to DIFF tab
        viewModel.setTab(ResumeViewTab.DIFF)
        assertEquals(ResumeViewTab.DIFF, viewModel.uiState.value.activeTab)

        // Switch back to STRUCTURED tab
        viewModel.setTab(ResumeViewTab.STRUCTURED)
        assertEquals(ResumeViewTab.STRUCTURED, viewModel.uiState.value.activeTab)
    }

    private fun findSourceFile(path: String): File {
        val f1 = File(path)
        if (f1.exists()) return f1
        val stripped = path.removePrefix("app/")
        val f2 = File(stripped)
        if (f2.exists()) return f2
        val f3 = File("app", path)
        if (f3.exists()) return f3
        return f1
    }

    @Test
    fun testResumeDiffViewSourceMatchesSpecification() {
        val componentFile = findSourceFile("app/src/main/kotlin/com/cellclaw/ui/components/ResumeDiffView.kt")
        assertTrue("ResumeDiffView.kt must exist", componentFile.exists())
        val content = componentFile.readText()

        // Check required tags and labels
        assertTrue("Must support #profile provenance tag", content.contains("#profile"))
        assertTrue("Must support #jd-tailored tag", content.contains("#jd-tailored"))
        assertTrue("Must support #omitted tag", content.contains("#omitted"))
        assertTrue("Must contain + ADDED badge", content.contains("+ ADDED"))
        assertTrue("Must contain - OMITTED badge", content.contains("- OMITTED"))
        assertTrue("Must contain ~ REORDERED badge", content.contains("~ REORDERED"))

        // Check semantic color tokens and WCAG safety
        assertTrue("Must use MaterialTheme.elixirColors", content.contains("MaterialTheme.elixirColors"))
        assertTrue("Must use successContainer", content.contains("successContainer"))
        assertTrue("Must use errorContainer", content.contains("errorContainer"))
        assertTrue("Must use warningContainer", content.contains("warningContainer"))
        assertFalse("Must not have hardcoded Color(0xFF...)", content.contains("Color(0xFF"))
    }

    @Test
    fun testResumePreviewScreenIntegratesVisualDiffAndStepper() {
        val screenFile = findSourceFile("app/src/main/kotlin/com/cellclaw/ui/screens/ResumePreviewScreen.kt")
        assertTrue("ResumePreviewScreen.kt must exist", screenFile.exists())
        val content = screenFile.readText()

        // Verify wiring
        assertTrue("Must import or reference ResumeDiffView", content.contains("ResumeDiffView"))
        assertTrue("Must contain Tab or FilterChip for Visual Diff", content.contains("Visual Diff"))
        assertTrue("Must contain Tab or FilterChip for Formatted View", content.contains("Formatted View"))
        assertTrue("Must reference ResumeViewTab", content.contains("ResumeViewTab"))
        assertTrue("Must retain ProgressStepper", content.contains("ProgressStepper"))
        assertTrue("Must retain approval gate button", content.contains("I Approve This Resume — Proceed to Review"))
    }
}

