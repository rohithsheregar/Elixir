package com.cellclaw.agent

import com.cellclaw.approval.*
import com.cellclaw.config.AppConfig
import com.cellclaw.config.Identity
import com.cellclaw.memory.ConversationStore
import com.cellclaw.memory.MessageDao
import com.cellclaw.memory.MessageEntity
import com.cellclaw.provider.*
import com.cellclaw.tools.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class AgentLoopCompactionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var toolRegistry: ToolRegistry
    private lateinit var approvalQueue: ApprovalQueue
    private lateinit var approvalTokenManager: ApprovalTokenManager
    private lateinit var autonomyPolicy: AutonomyPolicy
    private lateinit var conversationStore: ConversationStore
    private lateinit var identity: Identity
    private lateinit var appConfig: AppConfig
    private lateinit var contextCompactor: DefaultContextCompactor

    private val inMemoryDao = object : MessageDao {
        val messages = mutableListOf<MessageEntity>()
        override suspend fun insert(message: MessageEntity): Long {
            messages.add(message)
            return messages.size.toLong()
        }
        override suspend fun getRecent(conversationId: String, limit: Int): List<MessageEntity> {
            return messages.filter { it.conversationId == conversationId }.takeLast(limit)
        }
        override suspend fun getAll(conversationId: String): List<MessageEntity> {
            return messages.filter { it.conversationId == conversationId }
        }
        override suspend fun clearConversation(conversationId: String) {
            messages.removeAll { it.conversationId == conversationId }
        }
        override suspend fun count(conversationId: String): Int {
            return messages.count { it.conversationId == conversationId }
        }
    }

    class MockCompactingProvider(
        private val cannedResponses: MutableList<() -> CompletionResponse>
    ) : com.cellclaw.provider.Provider {
        override val name: String = "MockCompactingProvider"
        var callCount = 0

        override suspend fun complete(request: CompletionRequest): CompletionResponse {
            callCount++
            if (cannedResponses.isEmpty()) {
                return CompletionResponse(
                    content = listOf(ContentBlock.Text("Default response")),
                    stopReason = StopReason.END_TURN,
                    usage = Usage(10, 10)
                )
            }
            return cannedResponses.removeAt(0).invoke()
        }

        override fun stream(request: CompletionRequest): Flow<StreamEvent> = emptyFlow()
    }

    @Before
    fun setUp() {
        toolRegistry = ToolRegistry()
        approvalQueue = ApprovalQueue()
        approvalTokenManager = ApprovalTokenManager()
        autonomyPolicy = AutonomyPolicy()
        conversationStore = ConversationStore(inMemoryDao)
        appConfig = AppConfig()
        identity = Identity(appConfig)
        contextCompactor = DefaultContextCompactor()
    }

    private fun createAgentLoop(
        provider: com.cellclaw.provider.Provider,
        compactor: ContextCompactor = contextCompactor
    ): AgentLoop {
        val providerManager = ProviderManager(provider)
        return AgentLoop(
            providerManager = providerManager,
            toolRegistry = toolRegistry,
            approvalQueue = approvalQueue,
            conversationStore = conversationStore,
            identity = identity,
            autonomyPolicy = autonomyPolicy,
            appConfig = appConfig,
            heartbeatManagerProvider = Provider { null },
            approvalTokenManager = approvalTokenManager,
            coroutineScope = testScope,
            contextCompactor = compactor
        )
    }

    @Test
    fun testEstimateTokensCalculatesCorrectly() {
        val textMsg = Message.user("Hello world, this is a test prompt.")
        val estimated = contextCompactor.estimateTokens(textMsg)
        // Base 4 tokens + (36 + 3) / 4 = 13 tokens
        assertTrue("Estimated tokens should be positive and reasonable", estimated in 10..20)

        val toolUseMsg = Message(
            Role.ASSISTANT,
            listOf(ContentBlock.ToolUse("tool_1", "calendar.query", buildJsonObject { put("days", 7) }))
        )
        val toolTokens = contextCompactor.estimateTokens(toolUseMsg)
        assertTrue(toolTokens > 4)

        val toolResultMsg = Message(
            Role.USER,
            listOf(ContentBlock.ToolResult("tool_1", "Found 2 events"))
        )
        val resultTokens = contextCompactor.estimateTokens(toolResultMsg)
        assertTrue(resultTokens > 4)

        val total = contextCompactor.estimateTokens(listOf(textMsg, toolUseMsg, toolResultMsg))
        assertEquals(estimated + toolTokens + resultTokens, total)
    }

    @Test
    fun testShouldCompactRespectsThresholds() {
        val shortHistory = listOf(
            Message.user("Hello"),
            Message.assistant("Hi! How can I help you today?"),
            Message.user("Tell me about placement opportunities"),
            Message.assistant("Here is the list of upcoming placement drives...")
        )

        // Under thresholds
        assertFalse(contextCompactor.shouldCompact(shortHistory, maxTokens = 1000, maxMessages = 20))

        // Exceeds max messages
        assertTrue(contextCompactor.shouldCompact(shortHistory, maxTokens = 1000, maxMessages = 3))

        // Exceeds token budget
        assertTrue(contextCompactor.shouldCompact(shortHistory, maxTokens = 10, maxMessages = 20))
    }

    @Test
    fun testDeterministicCompactionSummarizesOlderTurnsAndKeepsRecent() = runTest {
        val messages = mutableListOf<Message>()
        for (i in 1..8) {
            messages.add(Message.user("Turn $i: Please parse job description for company $i"))
            messages.add(Message.assistant("Turn $i: Parsed requirements for company $i successfully."))
        }
        assertEquals(16, messages.size)

        val compacted = contextCompactor.compact(
            messages = messages,
            maxTokens = 200,
            maxMessages = 6,
            keepRecentCount = 4
        )

        // Compacted list should be significantly shorter than original 16
        assertTrue("Compacted message count (${compacted.size}) should be <= 6", compacted.size <= 6)

        // First message must be the summary user message
        val firstMsg = compacted.first()
        assertEquals(Role.USER, firstMsg.role)
        val summaryText = (firstMsg.content.first() as ContentBlock.Text).text
        assertTrue("Summary should have prefix", summaryText.startsWith(ContextCompactor.SUMMARY_PREFIX))
        assertTrue("Summary should mention earlier turns", summaryText.contains("earlier turns"))

        // Second message must be assistant acknowledgment
        val secondMsg = compacted[1]
        assertEquals(Role.ASSISTANT, secondMsg.role)
        val ackText = (secondMsg.content.first() as ContentBlock.Text).text
        assertTrue("Ack should mention context preservation", ackText.contains("preserved the context"))

        // Recent turns (last messages of original) must be preserved at tail
        val lastOriginal = messages.last()
        val lastCompacted = compacted.last()
        assertEquals((lastOriginal.content.first() as ContentBlock.Text).text, (lastCompacted.content.first() as ContentBlock.Text).text)
    }

    @Test
    fun testCompactionPreventsOrphanedToolResults() = runTest {
        val messages = listOf(
            Message.user("Check my eligibility for Google"),
            Message.assistant("I will query the database."),
            Message.user("Also check Microsoft"),
            Message(Role.ASSISTANT, listOf(ContentBlock.ToolUse("call_1", "eligibility.evaluate", buildJsonObject { put("company", "Microsoft") }))),
            Message(Role.USER, listOf(ContentBlock.ToolResult("call_1", "ELIGIBLE: CGPA 8.5 meets requirement 7.5"))),
            Message.assistant("You are eligible for Microsoft.")
        )

        // Target keep 2 messages (which would fall between ToolUse and ToolResult if naive)
        val compacted = contextCompactor.compact(
            messages = messages,
            maxTokens = 100,
            maxMessages = 4,
            keepRecentCount = 2
        )

        // Verify no message in compacted history has an orphaned ToolResult without its ToolUse
        for (i in compacted.indices) {
            val msg = compacted[i]
            val hasToolResult = msg.content.any { it is ContentBlock.ToolResult }
            if (hasToolResult) {
                assertTrue("ToolResult must have preceding message", i > 0)
                val prevMsg = compacted[i - 1]
                val hasToolUse = prevMsg.content.any { it is ContentBlock.ToolUse }
                assertTrue("Preceding message must contain matching ToolUse", hasToolUse)
            }
        }
    }

    @Test
    fun testRoleAlternationStrictlyPreserved() = runTest {
        val messages = mutableListOf<Message>()
        for (i in 1..10) {
            messages.add(Message.user("Query $i"))
            messages.add(Message.assistant("Reply $i"))
        }

        val compacted = contextCompactor.compact(
            messages = messages,
            maxTokens = 100,
            maxMessages = 4,
            keepRecentCount = 4
        )

        // Strict role alternation: no two consecutive messages have the same role
        for (i in 0 until compacted.size - 1) {
            assertNotEquals(
                "Role alternation violated at index $i and ${i + 1}",
                compacted[i].role,
                compacted[i + 1].role
            )
        }
    }

    @Test
    fun testChainedCompactionPreservesPriorSummary() = runTest {
        val messages = mutableListOf<Message>()
        for (i in 1..6) {
            messages.add(Message.user("Step $i: Generate resume"))
            messages.add(Message.assistant("Step $i: Resume generated."))
        }

        // First compaction pass
        val pass1 = contextCompactor.compact(messages, maxTokens = 100, maxMessages = 4, keepRecentCount = 2)
        val firstSummaryText = (pass1.first().content.first() as ContentBlock.Text).text
        assertTrue(firstSummaryText.contains(ContextCompactor.SUMMARY_PREFIX))

        // Add more turns
        val pass2Input = pass1.toMutableList()
        for (i in 7..10) {
            pass2Input.add(Message.user("Step $i: Upload to portal"))
            pass2Input.add(Message.assistant("Step $i: Upload completed."))
        }

        // Second compaction pass
        val pass2 = contextCompactor.compact(pass2Input, maxTokens = 100, maxMessages = 4, keepRecentCount = 2)
        val secondSummaryText = (pass2.first().content.first() as ContentBlock.Text).text

        assertTrue("Second summary must include prior context marker", secondSummaryText.contains("Prior Summary:"))
        assertTrue("Second summary must retain earlier compacted details", secondSummaryText.contains(ContextCompactor.SUMMARY_PREFIX))
    }

    @Test
    fun testCompactionWithAiProviderSuccess() = runTest {
        val aiResponses = mutableListOf<() -> CompletionResponse>(
            {
                CompletionResponse(
                    content = listOf(
                        ContentBlock.Text("- User analyzed Google JD\n- Confirmed eligibility PASS\n- Generated tailored resume PDF")
                    ),
                    stopReason = StopReason.END_TURN,
                    usage = Usage(30, 25)
                )
            }
        )
        val mockAiProvider = MockCompactingProvider(aiResponses)

        val messages = mutableListOf<Message>()
        for (i in 1..8) {
            messages.add(Message.user("Turn $i: Check JD $i"))
            messages.add(Message.assistant("Turn $i: Evaluated JD $i."))
        }

        val compacted = contextCompactor.compact(
            messages = messages,
            maxTokens = 200,
            maxMessages = 4,
            keepRecentCount = 2,
            aiProvider = mockAiProvider
        )

        val summaryText = (compacted.first().content.first() as ContentBlock.Text).text
        assertTrue("Should include AI Summarized tag", summaryText.contains("(AI Summarized)"))
        assertTrue("Should contain AI bullet content", summaryText.contains("User analyzed Google JD"))
        assertEquals(1, mockAiProvider.callCount)
    }

    @Test
    fun testCompactionGracefullyFallsBackWhenAiProviderFails() = runTest {
        val failingProvider = MockCompactingProvider(
            mutableListOf({ throw IOException("Network connection reset") })
        )

        val messages = mutableListOf<Message>()
        for (i in 1..8) {
            messages.add(Message.user("Turn $i: Request $i"))
            messages.add(Message.assistant("Turn $i: Response $i."))
        }

        // Compact with failing provider — should NOT throw
        val compacted = contextCompactor.compact(
            messages = messages,
            maxTokens = 200,
            maxMessages = 4,
            keepRecentCount = 2,
            aiProvider = failingProvider
        )

        assertNotNull(compacted)
        val summaryText = (compacted.first().content.first() as ContentBlock.Text).text
        assertTrue("Should fall back to deterministic summary", summaryText.contains(ContextCompactor.SUMMARY_PREFIX))
        assertTrue("Should extract user turn details", summaryText.contains("User requested:"))
    }

    @Test
    fun testAgentLoopAutomaticCompactionDuringExecution() = testScope.runTest {
        val mockProvider = MockCompactingProvider(mutableListOf(
            {
                CompletionResponse(
                    content = listOf(ContentBlock.Text("Assistant answer to step")),
                    stopReason = StopReason.END_TURN,
                    usage = Usage(15, 15)
                )
            }
        ))

        // Configure loop with small maxTokens to trigger compaction quickly
        appConfig.maxTokens = 50

        val agentLoop = createAgentLoop(mockProvider)

        val events = mutableListOf<AgentEvent>()
        val job = launch {
            agentLoop.events.collect { events.add(it) }
        }

        try {
            // Seed conversation with multiple turns
            for (i in 1..6) {
                agentLoop.submitMessage("Long message turn $i to fill up the token budget and trigger compaction in AgentLoop.")
                advanceUntilIdle()
            }

            // Verify ContextCompacted event was emitted
            val compactionEvents = events.filterIsInstance<AgentEvent.ContextCompacted>()
            assertTrue("AgentLoop should emit ContextCompacted event", compactionEvents.isNotEmpty())

            val history = agentLoop.getConversationHistory()
            assertTrue("Compacted history size (${history.size}) should be smaller than raw uncompacted turns", history.size < 12)
            assertTrue("Compacted history should contain summary prefix",
                history.any { it.content.any { b -> (b as? ContentBlock.Text)?.text?.contains(ContextCompactor.SUMMARY_PREFIX) == true } }
            )
        } finally {
            job.cancel()
        }
    }

    @Test
    fun testAgentLoopManualCompactHistory() = testScope.runTest {
        val mockProvider = MockCompactingProvider(mutableListOf())
        val agentLoop = createAgentLoop(mockProvider)

        // Direct submission of messages
        for (i in 1..5) {
            agentLoop.submitMessage("Message $i")
            advanceUntilIdle()
        }

        val beforeCount = agentLoop.getConversationHistory().size
        assertTrue(beforeCount >= 5)

        // Manual compact with tight budget
        val compacted = agentLoop.compactHistory(maxTokens = 30, maxMessages = 3, keepRecentCount = 2)
        assertTrue("compactHistory should return true when compaction was executed", compacted)

        val afterHistory = agentLoop.getConversationHistory()
        assertTrue("History size after compaction (${afterHistory.size}) should be less than before ($beforeCount)", afterHistory.size < beforeCount)
    }
}

