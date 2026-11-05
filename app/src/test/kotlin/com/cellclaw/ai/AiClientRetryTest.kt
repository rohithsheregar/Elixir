package com.cellclaw.ai

import com.cellclaw.provider.*
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.InMemoryApplicationTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class AiClientRetryTest {

    private lateinit var tracker: InMemoryApplicationTracker

    @Before
    fun setUp() {
        tracker = InMemoryApplicationTracker()
    }

    private fun sampleRequest(prompt: String = "Extract skills") = CompletionRequest(
        systemPrompt = "You are a JD parser",
        messages = listOf(Message.user(prompt))
    )

    private fun sampleResponse(text: String) = CompletionResponse(
        content = listOf(ContentBlock.Text(text)),
        stopReason = StopReason.END_TURN,
        usage = Usage(10, 10)
    )

    class MockProvider(
        override val name: String,
        private val responses: MutableList<() -> CompletionResponse>
    ) : Provider {
        var callCount = 0

        override suspend fun complete(request: CompletionRequest): CompletionResponse {
            callCount++
            if (responses.isEmpty()) {
                throw IOException("No more responses configured for $name")
            }
            return responses.removeAt(0).invoke()
        }

        override fun stream(request: CompletionRequest): Flow<StreamEvent> = emptyFlow()
    }

    @Test
    fun testRetriesWithExponentialBackoffOn429() = runTest {
        val responses = mutableListOf<() -> CompletionResponse>(
            { throw RateLimitException("HTTP 429: Too Many Requests") },
            { throw RateLimitException("HTTP 429: Rate limit exceeded") },
            { sampleResponse("Success on third attempt") }
        )
        val mockPrimary = MockProvider("PrimaryMock", responses)

        val client = ResilientAiClient(
            primaryProvider = mockPrimary,
            fallbackProvider = null,
            applicationTracker = tracker,
            config = AiClientConfig(maxRetries = 3, initialBackoffMs = 1L)
        )

        val result = client.complete(sampleRequest())
        assertEquals("Success on third attempt", (result.content[0] as ContentBlock.Text).text)
        assertEquals(3, mockPrimary.callCount)
    }

    @Test
    fun testFailoverToFallbackProviderWhenPrimaryFails() = runTest {
        val primaryResponses = mutableListOf<() -> CompletionResponse>(
            { throw RateLimitException("429 Rate limit") },
            { throw RateLimitException("429 Rate limit") },
            { throw RateLimitException("429 Rate limit") },
            { throw RateLimitException("429 Rate limit") }
        )
        val primary = MockProvider("FailingPrimary", primaryResponses)

        val fallbackResponses = mutableListOf<() -> CompletionResponse>(
            { sampleResponse("Fallback Success") }
        )
        val fallback = MockProvider("HealthyFallback", fallbackResponses)

        val client = ResilientAiClient(
            primaryProvider = primary,
            fallbackProvider = fallback,
            applicationTracker = tracker,
            config = AiClientConfig(maxRetries = 2, initialBackoffMs = 1L)
        )

        val result = client.complete(sampleRequest())
        assertEquals("Fallback Success", (result.content[0] as ContentBlock.Text).text)
        assertEquals(3, primary.callCount) // initial + 2 retries
        assertEquals(1, fallback.callCount)
    }

    @Test
    fun testEnforcesPerApplicationCallBudget() = runTest {
        val responses = mutableListOf<() -> CompletionResponse>(
            { sampleResponse("Response 1") },
            { sampleResponse("Response 2") },
            { sampleResponse("Response 3") }
        )
        val provider = MockProvider("BudgetMock", responses)

        val client = ResilientAiClient(
            primaryProvider = provider,
            fallbackProvider = null,
            applicationTracker = tracker,
            config = AiClientConfig(maxRetries = 1, maxBudgetPerApplication = 2)
        )

        // 1st call for app-1
        client.complete(sampleRequest("call 1"), applicationId = "app-1")
        assertEquals(1, client.getRemainingBudget("app-1"))

        // 2nd call for app-1
        client.complete(sampleRequest("call 2"), applicationId = "app-1")
        assertEquals(0, client.getRemainingBudget("app-1"))

        // 3rd call for app-1 should exceed budget
        try {
            client.complete(sampleRequest("call 3"), applicationId = "app-1")
            fail("Expected CallBudgetExceededException")
        } catch (e: CallBudgetExceededException) {
            assertEquals("app-1", e.applicationId)
            assertEquals(2, e.maxBudget)
        }

        // Another application app-2 should still have full budget
        assertEquals(2, client.getRemainingBudget("app-2"))
    }

    @Test
    fun testCachesResponsesByJdHash() = runTest {
        val responses = mutableListOf<() -> CompletionResponse>(
            { sampleResponse("Cached extraction result") }
        )
        val provider = MockProvider("CacheMock", responses)

        val client = ResilientAiClient(
            primaryProvider = provider,
            fallbackProvider = null,
            applicationTracker = tracker
        )

        // 1st call: network call
        val result1 = client.complete(sampleRequest(), jdHash = "jd_hash_12345")
        assertEquals("Cached extraction result", (result1.content[0] as ContentBlock.Text).text)
        assertEquals(1, provider.callCount)

        // 2nd call with identical jdHash: returned from cache, 0 new calls
        val result2 = client.complete(sampleRequest(), jdHash = "jd_hash_12345")
        assertEquals("Cached extraction result", (result2.content[0] as ContentBlock.Text).text)
        assertEquals(1, provider.callCount)
    }

    @Test
    fun testFailureLeavesApplicationInDraft() = runTest {
        val responses = mutableListOf<() -> CompletionResponse>(
            { throw RateLimitException("429 Permanent Failure") }
        )
        val provider = MockProvider("FailingMock", responses)

        val record = ApplicationRecord(
            id = "app-draft-test",
            company = "TestCorp",
            role = "Dev",
            jdHash = "hash123",
            eligibilityResult = "ELIGIBLE",
            state = ApplicationState.READY_FOR_REVIEW
        )
        tracker.registerApplication(record)
        assertEquals(ApplicationState.READY_FOR_REVIEW, tracker.getApplication("app-draft-test")?.state)

        val client = ResilientAiClient(
            primaryProvider = provider,
            fallbackProvider = null,
            applicationTracker = tracker,
            config = AiClientConfig(maxRetries = 0)
        )

        try {
            client.complete(sampleRequest(), applicationId = "app-draft-test")
            fail("Expected failure")
        } catch (_: Exception) {
            // Expected
        }

        // State must be reset or left in DRAFT
        assertEquals(ApplicationState.DRAFT, tracker.getApplication("app-draft-test")?.state)
    }
}

