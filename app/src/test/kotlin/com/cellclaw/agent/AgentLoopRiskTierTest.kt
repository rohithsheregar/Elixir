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
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class AgentLoopRiskTierTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var toolRegistry: ToolRegistry
    private lateinit var approvalQueue: ApprovalQueue
    private lateinit var approvalTokenManager: ApprovalTokenManager
    private lateinit var autonomyPolicy: AutonomyPolicy
    private lateinit var conversationStore: ConversationStore
    private lateinit var identity: Identity
    private lateinit var appConfig: AppConfig

    private val executedTools = mutableListOf<String>()

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

    private val readTool = object : Tool {
        override val name = "test.read"
        override val description = "Safe read tool"
        override val parameters = ToolParameters()
        override val requiresApproval = false
        override val riskTier = RiskTier.READ
        override suspend fun execute(params: JsonObject): ToolResult {
            executedTools.add(name)
            return ToolResult.success(buildJsonObject { put("status", "read_ok") })
        }
    }

    private val reversibleWriteTool = object : Tool {
        override val name = "test.reversible_write"
        override val description = "Reversible write tool"
        override val parameters = ToolParameters()
        override val requiresApproval = false
        override val riskTier = RiskTier.REVERSIBLE_WRITE
        override suspend fun execute(params: JsonObject): ToolResult {
            executedTools.add(name)
            return ToolResult.success(buildJsonObject { put("status", "reversible_ok") })
        }
    }

    private val consequentialTool = object : Tool {
        override val name = "test.consequential_submit"
        override val description = "Consequential write tool"
        override val parameters = ToolParameters()
        override val requiresApproval = true
        override val riskTier = RiskTier.CONSEQUENTIAL_WRITE
        override suspend fun execute(params: JsonObject): ToolResult {
            executedTools.add(name)
            return ToolResult.success(buildJsonObject { put("status", "submitted_ok") })
        }
    }

    private class MockAiProvider(
        private val responses: List<CompletionResponse>
    ) : com.cellclaw.provider.Provider {
        private var callIndex = 0
        override val name = "mock"
        override suspend fun complete(request: CompletionRequest): CompletionResponse {
            val idx = callIndex.coerceAtMost(responses.size - 1)
            callIndex++
            return responses[idx]
        }
        override fun stream(request: CompletionRequest): Flow<StreamEvent> = emptyFlow()
    }

    @Before
    fun setup() {
        executedTools.clear()
        toolRegistry = ToolRegistry()
        toolRegistry.register(readTool, reversibleWriteTool, consequentialTool)

        approvalQueue = ApprovalQueue()
        approvalTokenManager = ApprovalTokenManager()
        autonomyPolicy = AutonomyPolicy()
        conversationStore = ConversationStore(inMemoryDao)
        appConfig = AppConfig()
        identity = Identity(appConfig)
    }

    private fun createAgentLoop(responses: List<CompletionResponse>): AgentLoop {
        val mockProvider = MockAiProvider(responses)
        val providerManager = ProviderManager(mockProvider)
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
            coroutineScope = testScope
        )
    }

    @Test
    fun testReadToolExecutesWithoutApprovalToken() = testScope.runTest {
        // Read tools evaluate according to AutonomyPolicy; set policy to AUTO
        autonomyPolicy.setPolicy("test.read", ToolApprovalPolicy.AUTO)

        val responses = listOf(
            CompletionResponse(
                content = listOf(
                    ContentBlock.ToolUse(
                        id = "call_1",
                        name = "test.read",
                        input = buildJsonObject {}
                    )
                ),
                stopReason = StopReason.TOOL_USE
            ),
            CompletionResponse(
                content = listOf(ContentBlock.Text("Read complete")),
                stopReason = StopReason.END_TURN
            )
        )

        val loop = createAgentLoop(responses)
        loop.submitMessage("Perform read")
        advanceUntilIdle()

        assertTrue("READ tool must execute without approval token", executedTools.contains("test.read"))
        assertEquals(AgentState.IDLE, loop.state.value)
    }

    @Test
    fun testConsequentialToolWithValidApprovalTokenSucceedsAndConsumesToken() = testScope.runTest {
        // Direct human tap issues token
        val tokenResult = approvalTokenManager.issueActionToken(
            userDirectTapConfirmed = true,
            actionName = "test.consequential_submit"
        )
        assertTrue(tokenResult is TokenIssuanceResult.Success)
        val token = (tokenResult as TokenIssuanceResult.Success).token

        val responses = listOf(
            CompletionResponse(
                content = listOf(
                    ContentBlock.ToolUse(
                        id = "call_2",
                        name = "test.consequential_submit",
                        input = buildJsonObject {
                            put("approval_token", token.tokenId)
                        }
                    )
                ),
                stopReason = StopReason.TOOL_USE
            ),
            CompletionResponse(
                content = listOf(ContentBlock.Text("Submission complete")),
                stopReason = StopReason.END_TURN
            )
        )

        val loop = createAgentLoop(responses)
        loop.submitMessage("Submit application")
        advanceUntilIdle()

        assertTrue("CONSEQUENTIAL_WRITE tool must execute with valid token", executedTools.contains("test.consequential_submit"))
        assertEquals(AgentState.IDLE, loop.state.value)

        // Token must be consumed and unusable for subsequent calls
        val secondValidation = approvalTokenManager.validateActionToken(token.tokenId, "test.consequential_submit")
        assertEquals("Token must be consumed after execution", TokenValidationResult.AlreadyUsed, secondValidation)
    }

    @Test
    fun testConsequentialToolWithForgedTokenIsRejected() = testScope.runTest {
        // Model attempts to invent a fake token
        val fakeTokenId = "forged_by_model_9999"

        val responses = listOf(
            CompletionResponse(
                content = listOf(
                    ContentBlock.ToolUse(
                        id = "call_3",
                        name = "test.consequential_submit",
                        input = buildJsonObject {
                            put("approval_token", fakeTokenId)
                        }
                    )
                ),
                stopReason = StopReason.TOOL_USE
            ),
            CompletionResponse(
                content = listOf(ContentBlock.Text("Handled denial")),
                stopReason = StopReason.END_TURN
            )
        )

        val loop = createAgentLoop(responses)
        loop.submitMessage("Submit with forged token")
        advanceUntilIdle()

        assertFalse("CONSEQUENTIAL_WRITE tool must NOT execute with forged token", executedTools.contains("test.consequential_submit"))
    }

    @Test
    fun testConsequentialToolWithAlreadyUsedTokenIsRejected() = testScope.runTest {
        val tokenResult = approvalTokenManager.issueActionToken(
            userDirectTapConfirmed = true,
            actionName = "test.consequential_submit"
        )
        val token = (tokenResult as TokenIssuanceResult.Success).token
        // Consume token beforehand
        approvalTokenManager.consumeToken(token.tokenId)

        val responses = listOf(
            CompletionResponse(
                content = listOf(
                    ContentBlock.ToolUse(
                        id = "call_4",
                        name = "test.consequential_submit",
                        input = buildJsonObject {
                            put("approval_token", token.tokenId)
                        }
                    )
                ),
                stopReason = StopReason.TOOL_USE
            ),
            CompletionResponse(
                content = listOf(ContentBlock.Text("Done")),
                stopReason = StopReason.END_TURN
            )
        )

        val loop = createAgentLoop(responses)
        loop.submitMessage("Submit with used token")
        advanceUntilIdle()

        assertFalse("CONSEQUENTIAL_WRITE tool must NOT execute with already consumed token", executedTools.contains("test.consequential_submit"))
    }

    @Test
    fun testConsequentialToolWithActionMismatchIsRejected() = testScope.runTest {
        // Token issued for a different action
        val tokenResult = approvalTokenManager.issueActionToken(
            userDirectTapConfirmed = true,
            actionName = "different.action"
        )
        val token = (tokenResult as TokenIssuanceResult.Success).token

        val responses = listOf(
            CompletionResponse(
                content = listOf(
                    ContentBlock.ToolUse(
                        id = "call_5",
                        name = "test.consequential_submit",
                        input = buildJsonObject {
                            put("approval_token", token.tokenId)
                        }
                    )
                ),
                stopReason = StopReason.TOOL_USE
            ),
            CompletionResponse(
                content = listOf(ContentBlock.Text("Done")),
                stopReason = StopReason.END_TURN
            )
        )

        val loop = createAgentLoop(responses)
        loop.submitMessage("Submit with mismatched action token")
        advanceUntilIdle()

        assertFalse("CONSEQUENTIAL_WRITE tool must NOT execute with mismatched action token", executedTools.contains("test.consequential_submit"))
    }

    @Test
    fun testConsequentialToolWithoutTokenPromptsUserAndExecutesOnApproval() = testScope.runTest {
        val responses = listOf(
            CompletionResponse(
                content = listOf(
                    ContentBlock.ToolUse(
                        id = "call_6",
                        name = "test.consequential_submit",
                        input = buildJsonObject {}
                    )
                ),
                stopReason = StopReason.TOOL_USE
            ),
            CompletionResponse(
                content = listOf(ContentBlock.Text("Done after human approval")),
                stopReason = StopReason.END_TURN
            )
        )

        val loop = createAgentLoop(responses)

        val job = launch {
            approvalQueue.requests.collect { list ->
                val req = list.firstOrNull()
                if (req != null) {
                    assertEquals("test.consequential_submit", req.toolName)
                    approvalQueue.respond(req.id, ApprovalResult.APPROVED)
                }
            }
        }

        try {
            loop.submitMessage("Submit without token")
            advanceUntilIdle()

            assertTrue("Tool must execute after human direct approval", executedTools.contains("test.consequential_submit"))
            assertEquals(AgentState.IDLE, loop.state.value)
        } finally {
            job.cancel()
        }
    }

    @Test
    fun testConsequentialToolWithoutTokenDeniedWhenUserRejects() = testScope.runTest {
        val responses = listOf(
            CompletionResponse(
                content = listOf(
                    ContentBlock.ToolUse(
                        id = "call_7",
                        name = "test.consequential_submit",
                        input = buildJsonObject {}
                    )
                ),
                stopReason = StopReason.TOOL_USE
            ),
            CompletionResponse(
                content = listOf(ContentBlock.Text("Denied")),
                stopReason = StopReason.END_TURN
            )
        )

        val loop = createAgentLoop(responses)

        val job = launch {
            approvalQueue.requests.collect { list ->
                val req = list.firstOrNull()
                if (req != null) {
                    approvalQueue.respond(req.id, ApprovalResult.DENIED)
                }
            }
        }

        try {
            loop.submitMessage("Submit without token")
            advanceUntilIdle()

            assertFalse("Tool must NOT execute when user denies in ApprovalQueue", executedTools.contains("test.consequential_submit"))
        } finally {
            job.cancel()
        }
    }

    @Test
    fun testFullAutoProfileCannotBypassConsequentialWriteTokenRequirement() = testScope.runTest {
        // Set policy to FULL_AUTO
        autonomyPolicy.applyProfile(PermissionProfile.FULL_AUTO)

        val responses = listOf(
            CompletionResponse(
                content = listOf(
                    ContentBlock.ToolUse(
                        id = "call_8",
                        name = "test.consequential_submit",
                        input = buildJsonObject {}
                    )
                ),
                stopReason = StopReason.TOOL_USE
            ),
            CompletionResponse(
                content = listOf(ContentBlock.Text("Done")),
                stopReason = StopReason.END_TURN
            )
        )

        val loop = createAgentLoop(responses)

        // User denies approval in prompt
        val job = launch {
            approvalQueue.requests.collect { list ->
                val req = list.firstOrNull()
                if (req != null) {
                    approvalQueue.respond(req.id, ApprovalResult.DENIED)
                }
            }
        }

        try {
            loop.submitMessage("Attempt bypass in FULL_AUTO")
            advanceUntilIdle()

            assertFalse("FULL_AUTO profile must NEVER bypass CONSEQUENTIAL_WRITE token requirement", executedTools.contains("test.consequential_submit"))
        } finally {
            job.cancel()
        }
    }

    @Test
    fun testModelCannotCreateValidApprovalTokenWithoutDirectUserTap() {
        val modelAttempt = approvalTokenManager.issueActionToken(
            userDirectTapConfirmed = false,
            actionName = "test.consequential_submit"
        )

        assertTrue("Token issuance without userDirectTapConfirmed must be rejected", modelAttempt is TokenIssuanceResult.Rejected)
    }

    @Test
    fun testReversibleWriteToolFollowsAutonomyPolicy() = testScope.runTest {
        // Under CAUTIOUS profile, reversible write requires ASK
        autonomyPolicy.applyProfile(PermissionProfile.CAUTIOUS)

        val responses = listOf(
            CompletionResponse(
                content = listOf(
                    ContentBlock.ToolUse(
                        id = "call_9",
                        name = "test.reversible_write",
                        input = buildJsonObject {}
                    )
                ),
                stopReason = StopReason.TOOL_USE
            ),
            CompletionResponse(
                content = listOf(ContentBlock.Text("Done")),
                stopReason = StopReason.END_TURN
            )
        )

        val loop = createAgentLoop(responses)

        val job = launch {
            approvalQueue.requests.collect { list ->
                val req = list.firstOrNull()
                if (req != null) {
                    assertEquals("test.reversible_write", req.toolName)
                    approvalQueue.respond(req.id, ApprovalResult.APPROVED)
                }
            }
        }

        try {
            loop.submitMessage("Run reversible write")
            advanceUntilIdle()

            assertTrue("REVERSIBLE_WRITE must execute after user approval", executedTools.contains("test.reversible_write"))
        } finally {
            job.cancel()
        }
    }
}
