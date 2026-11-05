package com.cellclaw.form

import com.cellclaw.ai.AiClient
import com.cellclaw.ai.AiClientException
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
import org.junit.Test
import java.time.Instant

class AnswerDrafterTest {

    private val sampleTime = Instant.parse("2026-01-01T00:00:00Z")

    private fun sampleProfile(): CandidateProfile {
        return CandidateProfile(
            name = ProfileFact("fact-name", "name", "Rohith Developer", ProfileFactSource.USER_ENTERED, true, sampleTime),
            degree = ProfileFact("fact-degree", "degree", "B.Tech Computer Science", ProfileFactSource.USER_ENTERED, true, sampleTime),
            cgpa = ProfileFact("fact-cgpa", "cgpa", "8.9", ProfileFactSource.USER_ENTERED, true, sampleTime),
            skills = listOf(
                ProfileFact("fact-skill-1", "skill", "Kotlin", ProfileFactSource.USER_ENTERED, true, sampleTime),
                ProfileFact("fact-skill-2", "skill", "Jetpack Compose", ProfileFactSource.USER_ENTERED, true, sampleTime),
                ProfileFact("fact-skill-3", "skill", "Android SDK", ProfileFactSource.USER_ENTERED, true, sampleTime)
            ),
            projectDetails = listOf(
                ProjectDetail(
                    id = "proj-1",
                    title = "Elixir Autonomous Placement Agent",
                    problem = "Students struggled with repetitive placement forms and un-tailored resumes.",
                    role = "Lead Mobile Architect",
                    tools = listOf("Kotlin", "Compose", "Room", "Hilt"),
                    challenge = "Enforcing strict zero-fabrication ground truth while navigating dynamic Chrome accessibility trees.",
                    verifiedResults = "Accelerated application velocity by 85% with 100% verified factual integrity.",
                    shortStory = "Engineered an on-device personal assistant that guarantees candidate agency under direct biometric supervision.",
                    confirmed = true
                )
            )
        )
    }

    class MockAiClient(
        private val cannedResponse: String? = null,
        private val shouldFail: Boolean = false
    ) : AiClient {
        var callCount = 0

        override suspend fun complete(
            request: CompletionRequest,
            applicationId: String?,
            jdHash: String?
        ): CompletionResponse {
            callCount++
            if (shouldFail) {
                throw AiClientException("Simulated AI Failure")
            }
            return CompletionResponse(
                content = listOf(ContentBlock.Text(cannedResponse ?: "AI Answer [fact-skill-1] [proj-1-results]")),
                stopReason = StopReason.END_TURN,
                usage = Usage(15, 20)
            )
        }

        override fun getRemainingBudget(applicationId: String): Int = 5
        override fun clearCache() {}
        override fun resetBudget(applicationId: String) {}
    }

    @Test
    fun testZeroFabricationGuaranteeInDeterministicDrafting() = runTest {
        val drafter = DefaultAnswerDrafter(aiClient = null)
        val profile = sampleProfile()

        val answer = drafter.draftAnswer(
            question = "Tell us about your core technical skills and expertise.",
            profile = profile,
            company = "Google",
            role = "Android Engineer"
        )

        assertNotNull(answer.text)
        assertTrue("Must reference confirmed Kotlin skill", answer.text.contains("Kotlin"))
        assertTrue("Must reference confirmed Compose skill", answer.text.contains("Jetpack Compose"))
        assertFalse("Must never fabricate unconfirmed skills like React or Swift", answer.text.contains("React"))
        assertFalse("Must never fabricate Swift", answer.text.contains("Swift"))

        // Check cited facts
        assertTrue("Must cite fact-skill-1", answer.citedFactIds.contains("fact-skill-1"))
        assertTrue("Must cite fact-skill-2", answer.citedFactIds.contains("fact-skill-2"))
    }

    @Test
    fun testStrictCharacterLengthLimitEnforcement() = runTest {
        val drafter = DefaultAnswerDrafter(aiClient = null)
        val profile = sampleProfile()
        val limit = 100

        val answer = drafter.draftAnswer(
            question = "Why are you interested in joining our mobile engineering team?",
            profile = profile,
            company = "Stripe",
            role = "Mobile Engineer",
            lengthLimit = limit
        )

        assertTrue(
            "Character count ${answer.characterCount} must be <= limit $limit",
            answer.characterCount <= limit
        )
        assertEquals(limit, answer.lengthLimit)
        assertTrue(answer.isWithinLimit)
    }

    @Test
    fun testProjectDetailsIntegrationInFreeTextDrafting() = runTest {
        val drafter = DefaultAnswerDrafter(aiClient = null)
        val profile = sampleProfile()

        val answer = drafter.draftAnswer(
            question = "Describe a challenging technical project you delivered.",
            profile = profile,
            company = "Uber",
            role = "Software Engineer"
        )

        // Must weave project role, problem, and results
        assertTrue("Must mention project role", answer.text.contains("Lead Mobile Architect"))
        assertTrue("Must mention project title", answer.text.contains("Elixir Autonomous Placement Agent"))
        assertTrue("Must mention verified results", answer.text.contains("Accelerated application velocity"))

        // Provenance verification
        assertTrue("Must cite project title fact", answer.citedFactIds.contains("proj-1-title"))
        assertTrue("Must cite project results fact", answer.citedFactIds.contains("proj-1-results"))
    }

    @Test
    fun testApprovalGateInitiallyRequiresUserConfirmation() = runTest {
        val drafter = DefaultAnswerDrafter(aiClient = null)
        val profile = sampleProfile()

        val answer = drafter.draftAnswer(
            question = "Why do you want to work with us?",
            profile = profile,
            company = "Amazon",
            role = "SDE"
        )

        assertTrue("Drafted answer must require approval", answer.requiresApproval)
        assertFalse("Answer must not be pre-approved", answer.approved)

        // Simulate user reviewing and tapping approve
        val approvedAnswer = answer.withApproval(true)
        assertTrue("Answer is now approved for filling", approvedAnswer.approved)
    }

    @Test
    fun testAiDraftingWithGracefulDeterministicFallback() = runTest {
        // AI client that fails (simulating rate limit / network failure)
        val failingAi = MockAiClient(shouldFail = true)
        val drafter = DefaultAnswerDrafter(aiClient = failingAi)
        val profile = sampleProfile()

        val answer = drafter.draftAnswer(
            question = "Describe your relevant technical skills.",
            profile = profile,
            company = "Atlassian",
            role = "Android Engineer"
        )

        // Verifies fallback cleanly synthesizes an answer without throwing
        assertNotNull(answer.text)
        assertTrue(answer.text.contains("Kotlin"))
        assertEquals(1, failingAi.callCount)
    }

    @Test
    fun testAiDraftingExtractsCitationsAndEnforcesLength() = runTest {
        val successfulAi = MockAiClient(cannedResponse = "I specialize in mobile platform development with Kotlin [fact-skill-1] achieving high velocity [proj-1-results].")
        val drafter = DefaultAnswerDrafter(aiClient = successfulAi)
        val profile = sampleProfile()

        val answer = drafter.draftAnswer(
            question = "Why are you a good fit?",
            profile = profile,
            company = "Netflix",
            role = "Android Engineer",
            lengthLimit = 200
        )

        assertEquals(1, successfulAi.callCount)
        assertTrue("Must extract cited fact IDs", answer.citedFactIds.contains("fact-skill-1"))
        assertTrue("Must extract cited project IDs", answer.citedFactIds.contains("proj-1-results"))
        assertFalse("Cleaned answer must strip brackets", answer.text.contains("[fact-skill-1]"))
    }
}

