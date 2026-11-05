package com.cellclaw.form

import com.cellclaw.ai.AiClient
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.ProfileFact
import com.cellclaw.profile.ProfileFactSource
import com.cellclaw.profile.ProjectDetail
import com.cellclaw.provider.CompletionRequest
import com.cellclaw.provider.CompletionResponse
import com.cellclaw.provider.ContentBlock
import com.cellclaw.provider.StopReason
import com.cellclaw.provider.Usage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ApprovedAnswersLibraryTest {

    private lateinit var store: InMemoryApprovedAnswersStore
    private lateinit var library: DefaultApprovedAnswersLibrary

    private val sampleTime = Instant.parse("2026-01-01T00:00:00Z")

    private fun sampleProfile(): CandidateProfile {
        return CandidateProfile(
            name = ProfileFact("fact-name", "name", "Rohith Developer", ProfileFactSource.USER_ENTERED, true, sampleTime),
            degree = ProfileFact("fact-degree", "degree", "B.Tech Computer Science", ProfileFactSource.USER_ENTERED, true, sampleTime),
            cgpa = ProfileFact("fact-cgpa", "cgpa", "8.9", ProfileFactSource.USER_ENTERED, true, sampleTime),
            skills = listOf(
                ProfileFact("fact-skill-1", "skill", "Kotlin", ProfileFactSource.USER_ENTERED, true, sampleTime),
                ProfileFact("fact-skill-2", "skill", "Jetpack Compose", ProfileFactSource.USER_ENTERED, true, sampleTime)
            ),
            projectDetails = listOf(
                ProjectDetail(
                    id = "proj-1",
                    title = "Elixir Autonomous Placement Agent",
                    problem = "Fragmented placement forms required repetitive typing.",
                    role = "Lead Mobile Architect",
                    tools = listOf("Kotlin", "Compose", "Room"),
                    challenge = "Enforcing strict zero-fabrication ground truth.",
                    verifiedResults = "Accelerated application velocity by 85%.",
                    shortStory = "Engineered on-device assistant with supervisor approval.",
                    confirmed = true
                )
            )
        )
    }

    class MockAiClient : AiClient {
        var callCount = 0

        override suspend fun complete(
            request: CompletionRequest,
            applicationId: String?,
            jdHash: String?
        ): CompletionResponse {
            callCount++
            return CompletionResponse(
                content = listOf(ContentBlock.Text("AI synthesized answer [fact-skill-1]")),
                stopReason = StopReason.END_TURN,
                usage = Usage(10, 15)
            )
        }

        override fun getRemainingBudget(applicationId: String): Int = 5
        override fun clearCache() {}
        override fun resetBudget(applicationId: String) {}
    }

    @Before
    fun setUp() {
        store = InMemoryApprovedAnswersStore()
        library = DefaultApprovedAnswersLibrary(store)
    }

    @Test
    fun testSaveAndRetrieveApprovedAnswer() = runTest {
        val answer = ApprovedAnswer(
            id = "ans-1",
            questionCluster = "technical_challenge",
            promptQuestion = "Describe a difficult technical challenge you solved.",
            answerText = "When building our app, handling stale accessibility nodes was a major hurdle. I resolved it using bounded polling and read-back verification.",
            citedFactIds = listOf("proj-1-challenge", "proj-1-results")
        )

        val saved = library.saveAnswer(answer)
        assertEquals("ans-1", saved.id)
        assertTrue("Auto-extracted keywords should contain challenge", saved.keywords.contains("challenge"))

        val retrieved = library.getAnswerById("ans-1")
        assertNotNull(retrieved)
        assertEquals(answer.answerText, retrieved?.answerText)
        assertEquals("technical_challenge", retrieved?.questionCluster)
    }

    @Test
    fun testOfferToSaveFromDraftedAnswer() = runTest {
        val drafted = DraftedAnswer(
            question = "Tell us about a time you overcame an obstacle on a software project",
            text = "As Lead Mobile Architect on Elixir Autonomous Placement Agent, I solved accessibility node invalidation via bounded exponential polling.",
            citedFactIds = listOf("proj-1-role", "proj-1-challenge"),
            characterCount = 135,
            requiresApproval = true,
            approved = true
        )

        val approved = library.offerToSave(drafted)
        assertNotNull(approved.id)
        assertEquals("technical_challenge", approved.questionCluster)
        assertEquals(drafted.text, approved.answerText)
        assertEquals(drafted.citedFactIds, approved.citedFactIds)

        val all = library.getAllAnswers()
        assertEquals(1, all.size)
        assertEquals(approved.id, all.first().id)
    }

    @Test
    fun testFindMatchingAnswerExactAndFuzzy() = runTest {
        val answer = ApprovedAnswer(
            id = "ans-why",
            questionCluster = "why_company",
            promptQuestion = "Why do you want to join Google as an Android Engineer?",
            answerText = "With a confirmed B.Tech in Computer Science and expertise in Kotlin and Jetpack Compose, I am excited to contribute to Android at scale.",
            keywords = setOf("join", "google", "android", "engineer", "company"),
            citedFactIds = listOf("fact-degree", "fact-skill-1", "fact-skill-2")
        )
        library.saveAnswer(answer)

        // 1. Exact match
        val exactMatch = library.findBestMatch("Why do you want to join Google as an Android Engineer?")
        assertNotNull(exactMatch)
        assertEquals(1.0, exactMatch!!.similarityScore, 0.001)
        assertEquals(answer.answerText, exactMatch.fittedText)

        // 2. Similar / paraphrased question
        val fuzzyMatch = library.findBestMatch("Tell us why you are interested in joining our company for the Android role")
        assertNotNull(fuzzyMatch)
        assertTrue("Fuzzy score should be >= 0.35 threshold (was ${fuzzyMatch!!.similarityScore})", fuzzyMatch.similarityScore >= 0.35)
        assertEquals(answer.answerText, fuzzyMatch.fittedText)
    }

    @Test
    fun testZeroAiCallReuseViaDefaultAnswerDrafter() = runTest {
        // Pre-populate approved answer in library
        val answer = ApprovedAnswer(
            id = "ans-relocate",
            questionCluster = "relocation",
            promptQuestion = "Are you willing to relocate for this position?",
            answerText = "Yes, I am fully open to relocating for this opportunity based on verified project and career preferences.",
            keywords = setOf("relocate", "relocation", "willing", "location", "travel")
        )
        library.saveAnswer(answer)

        val mockAi = MockAiClient()
        val drafter = DefaultAnswerDrafter(aiClient = mockAi, answersLibrary = library)
        val profile = sampleProfile()

        // Draft answer for a matching relocation question
        val result = drafter.draftAnswer(
            question = "Are you willing to relocate for work?",
            profile = profile
        )

        // Strict verification: ZERO AI calls made!
        assertEquals("Mock AI client must have ZERO calls (token cost = 0)", 0, mockAi.callCount)
        assertEquals(answer.answerText, result.text)
        assertTrue("Reused answer from approved library is pre-approved", result.approved)

        // Verify usage increment
        val updatedAnswer = library.getAnswerById("ans-relocate")
        assertEquals(1, updatedAnswer?.useCount)
    }

    @Test
    fun testFallbackToAiWhenNoLibraryMatchFound() = runTest {
        val mockAi = MockAiClient()
        val drafter = DefaultAnswerDrafter(aiClient = mockAi, answersLibrary = library)
        val profile = sampleProfile()

        // Novel question with zero matches in the empty library
        val result = drafter.draftAnswer(
            question = "What is your philosophy on test-driven development?",
            profile = profile
        )

        // AI client must be invoked as fallback
        assertEquals("Mock AI client must be called exactly once when library has no match", 1, mockAi.callCount)
        assertTrue(result.text.contains("AI synthesized answer"))
        assertFalse("AI synthesized answer requires user approval", result.approved)
    }

    @Test
    fun testLengthLimitEnforcementOnLibraryMatch() = runTest {
        val longText = "As Lead Mobile Architect on Elixir, I spearheaded on-device zero-fabrication placement assistance. We overcame strict Chrome accessibility timing constraints."
        val answer = ApprovedAnswer(
            id = "ans-long",
            questionCluster = "project_experience",
            promptQuestion = "What project did you build?",
            answerText = longText,
            keywords = setOf("project", "build", "built", "architect")
        )
        library.saveAnswer(answer)

        // Impose a tight 60-character limit
        val match = library.findBestMatch("Describe a project you built", lengthLimit = 60)
        assertNotNull(match)
        assertTrue("Fitted text must be <= 60 chars (was ${match!!.fittedText.length})", match.fittedText.length <= 60)
        assertTrue("Fitted text should end cleanly with ellipsis or sentence end", match.fittedText.endsWith("…") || match.fittedText.endsWith("."))
    }

    @Test
    fun testClusterFilteringAndDeletion() = runTest {
        library.saveAnswer(ApprovedAnswer(id = "1", questionCluster = "why_company", promptQuestion = "Why us?", answerText = "A1"))
        library.saveAnswer(ApprovedAnswer(id = "2", questionCluster = "why_company", promptQuestion = "Why join?", answerText = "A2"))
        library.saveAnswer(ApprovedAnswer(id = "3", questionCluster = "leadership", promptQuestion = "Lead team?", answerText = "A3"))

        val whyAnswers = library.getAnswersByCluster("why_company")
        assertEquals(2, whyAnswers.size)

        val leadershipAnswers = library.getAnswersByCluster("leadership")
        assertEquals(1, leadershipAnswers.size)

        assertTrue(library.deleteAnswer("3"))
        assertEquals(0, library.getAnswersByCluster("leadership").size)

        library.clear()
        assertEquals(0, library.getAllAnswers().size)
    }

    @Test
    fun testTieBreakerPrefersHigherUseCount() = runTest {
        val ans1 = ApprovedAnswer(
            id = "ans-1",
            questionCluster = "technical_challenge",
            promptQuestion = "Describe a challenging bug you solved",
            answerText = "Answer 1 (Less used)",
            useCount = 1
        )
        val ans2 = ApprovedAnswer(
            id = "ans-2",
            questionCluster = "technical_challenge",
            promptQuestion = "Describe a challenging bug you solved",
            answerText = "Answer 2 (More frequently used)",
            useCount = 8
        )
        library.saveAnswer(ans1)
        library.saveAnswer(ans2)

        val match = library.findBestMatch("Describe a challenging bug you solved")
        assertNotNull(match)
        assertEquals("ans-2", match!!.approvedAnswer.id)
        assertEquals("Answer 2 (More frequently used)", match.fittedText)
    }
}

