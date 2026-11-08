package com.cellclaw.agent

import android.util.Log
import com.cellclaw.approval.ApprovalQueue
import com.cellclaw.approval.ApprovalRequest
import com.cellclaw.approval.ApprovalResult
import com.cellclaw.approval.ApprovalTokenManager
import com.cellclaw.approval.TokenIssuanceResult
import com.cellclaw.approval.TokenValidationResult
import com.cellclaw.config.AppConfig
import com.cellclaw.config.Identity
import com.cellclaw.memory.ConversationStore
import com.cellclaw.provider.*
import com.cellclaw.provider.ProviderManager
import com.cellclaw.tools.RiskTier
import com.cellclaw.tools.ToolRegistry
import com.cellclaw.tools.ToolResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AgentLoop {
    private val providerManager: ProviderManager
    private val toolRegistry: ToolRegistry
    private val approvalQueue: ApprovalQueue
    private val conversationStore: ConversationStore
    private val identity: Identity
    private val autonomyPolicy: AutonomyPolicy
    private val appConfig: AppConfig
    private val heartbeatManagerProvider: Provider<HeartbeatManager>
    private val approvalTokenManager: ApprovalTokenManager
    private val contextCompactor: ContextCompactor
    private val scope: CoroutineScope

    @Inject
    constructor(
        providerManager: ProviderManager,
        toolRegistry: ToolRegistry,
        approvalQueue: ApprovalQueue,
        conversationStore: ConversationStore,
        identity: Identity,
        autonomyPolicy: AutonomyPolicy,
        appConfig: AppConfig,
        heartbeatManagerProvider: Provider<HeartbeatManager>,
        approvalTokenManager: ApprovalTokenManager,
        contextCompactor: ContextCompactor
    ) : this(
        providerManager = providerManager,
        toolRegistry = toolRegistry,
        approvalQueue = approvalQueue,
        conversationStore = conversationStore,
        identity = identity,
        autonomyPolicy = autonomyPolicy,
        appConfig = appConfig,
        heartbeatManagerProvider = heartbeatManagerProvider,
        approvalTokenManager = approvalTokenManager,
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        contextCompactor = contextCompactor
    )

    constructor(
        providerManager: ProviderManager,
        toolRegistry: ToolRegistry,
        approvalQueue: ApprovalQueue,
        conversationStore: ConversationStore,
        identity: Identity,
        autonomyPolicy: AutonomyPolicy,
        appConfig: AppConfig,
        heartbeatManagerProvider: Provider<HeartbeatManager>,
        approvalTokenManager: ApprovalTokenManager,
        coroutineScope: CoroutineScope
    ) : this(
        providerManager = providerManager,
        toolRegistry = toolRegistry,
        approvalQueue = approvalQueue,
        conversationStore = conversationStore,
        identity = identity,
        autonomyPolicy = autonomyPolicy,
        appConfig = appConfig,
        heartbeatManagerProvider = heartbeatManagerProvider,
        approvalTokenManager = approvalTokenManager,
        coroutineScope = coroutineScope,
        contextCompactor = DefaultContextCompactor()
    )

    constructor(
        providerManager: ProviderManager,
        toolRegistry: ToolRegistry,
        approvalQueue: ApprovalQueue,
        conversationStore: ConversationStore,
        identity: Identity,
        autonomyPolicy: AutonomyPolicy,
        appConfig: AppConfig,
        heartbeatManagerProvider: Provider<HeartbeatManager>,
        approvalTokenManager: ApprovalTokenManager,
        coroutineScope: CoroutineScope,
        contextCompactor: ContextCompactor
    ) {
        this.providerManager = providerManager
        this.toolRegistry = toolRegistry
        this.approvalQueue = approvalQueue
        this.conversationStore = conversationStore
        this.identity = identity
        this.autonomyPolicy = autonomyPolicy
        this.appConfig = appConfig
        this.heartbeatManagerProvider = heartbeatManagerProvider
        this.approvalTokenManager = approvalTokenManager
        this.scope = coroutineScope
        this.contextCompactor = contextCompactor
    }
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(AgentState.IDLE)
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AgentEvent>(replay = 1, extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    private val conversationHistory = mutableListOf<Message>()
    private var currentJob: Job? = null
    private var isHeartbeatRun = false

    fun submitMessage(text: String) {
        currentJob?.cancel()
        currentJob = scope.launch {
            try {
                isHeartbeatRun = false
                _state.value = AgentState.THINKING
                conversationHistory.add(Message.user(text))
                conversationStore.addMessage("user", text)
                _events.emit(AgentEvent.UserMessage(text))
                runAgentLoop()
            } catch (e: CancellationException) {
                _state.value = AgentState.IDLE
            } catch (e: Exception) {
                Log.e(TAG, "Agent loop error: ${e::class.simpleName}: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                _state.value = AgentState.ERROR
                _events.emit(AgentEvent.Error(e.message ?: "Unknown error"))
            }
        }
    }

    /**
     * Submit a heartbeat prompt. Unlike submitMessage(), this does NOT cancel the
     * current job. If the agent is busy, it reports SKIPPED_BUSY to the heartbeat manager.
     */
    fun submitHeartbeat(prompt: String) {
        if (_state.value != AgentState.IDLE) {
            heartbeatManagerProvider.get().onHeartbeatResult(HeartbeatResult.SKIPPED_BUSY)
            return
        }

        currentJob = scope.launch {
            try {
                isHeartbeatRun = true
                _state.value = AgentState.THINKING
                // Add to in-memory history but do NOT persist to ConversationStore
                conversationHistory.add(Message.user(prompt))
                _events.emit(AgentEvent.HeartbeatStart)
                runAgentLoop()
            } catch (e: CancellationException) {
                _state.value = AgentState.IDLE
                heartbeatManagerProvider.get().onHeartbeatResult(HeartbeatResult.SKIPPED_BUSY)
            } catch (e: Exception) {
                Log.e(TAG, "Heartbeat error: ${e::class.simpleName}: ${e.message}")
                _state.value = AgentState.ERROR
                heartbeatManagerProvider.get().onHeartbeatResult(HeartbeatResult.ERROR)
            } finally {
                isHeartbeatRun = false
            }
        }
    }

    fun stop() {
        currentJob?.cancel()
        _state.value = AgentState.IDLE
    }

    fun pause() {
        _state.value = AgentState.PAUSED
    }

    fun resume() {
        if (_state.value == AgentState.PAUSED) {
            _state.value = AgentState.THINKING
            currentJob = scope.launch { runAgentLoop() }
        }
    }

    private suspend fun runAgentLoop() {
        var iterations = 0
        val maxIterations = appConfig.maxIterations
        var hadToolCallsThisRun = false

        while ((maxIterations == 0 || iterations < maxIterations) && _state.value == AgentState.THINKING) {
            iterations++

            compactConversationHistoryIfNeeded()

            val request = CompletionRequest(
                systemPrompt = identity.buildSystemPrompt(toolRegistry),
                messages = conversationHistory.toList(),
                tools = toolRegistry.toApiSchema(),
                maxTokens = 4096
            )

            val response = providerManager.activeProvider().complete(request)

            // Process response content
            val textParts = mutableListOf<String>()
            val toolCalls = mutableListOf<ContentBlock.ToolUse>()

            for (block in response.content) {
                when (block) {
                    is ContentBlock.Text -> {
                        if (block.thought) {
                            if (!isHeartbeatRun) _events.emit(AgentEvent.ThinkingText(block.text))
                        } else {
                            textParts.add(block.text)
                            if (!isHeartbeatRun) _events.emit(AgentEvent.AssistantText(block.text))
                        }
                    }
                    is ContentBlock.ToolUse -> toolCalls.add(block)
                    else -> {}
                }
            }

            // Add assistant message to history
            conversationHistory.add(Message(Role.ASSISTANT, response.content))

            // Only persist to ConversationStore for non-heartbeat runs,
            // or for heartbeat runs where the agent took action
            if (textParts.isNotEmpty() && (!isHeartbeatRun || hadToolCallsThisRun)) {
                conversationStore.addMessage("assistant", textParts.joinToString(""))
            }

            // If no tool calls, we're done
            Log.d(TAG, "Response: stopReason=${response.stopReason}, " +
                "toolCalls=${toolCalls.size}, blocks=${response.content.size}, " +
                "types=${response.content.map { it::class.simpleName }}")
            if (response.stopReason != StopReason.TOOL_USE || toolCalls.isEmpty()) {
                // Handle heartbeat detection before transitioning to IDLE
                if (isHeartbeatRun) {
                    val detection = HeartbeatDetector.analyze(textParts, hadToolCallsThisRun)
                    _events.emit(AgentEvent.HeartbeatComplete(detection))

                    if (detection.isTaskComplete) {
                        heartbeatManagerProvider.get().clearActiveTaskContext()
                    }

                    // Prune HEARTBEAT_OK exchanges from history to avoid context pollution
                    if (detection.heartbeatResult == HeartbeatResult.OK_NOTHING_TO_DO) {
                        pruneLastHeartbeatExchange()
                    }

                    heartbeatManagerProvider.get().onHeartbeatResult(detection.heartbeatResult)
                }
                _state.value = AgentState.IDLE
                return
            }

            // Execute tool calls
            hadToolCallsThisRun = true
            _state.value = AgentState.EXECUTING_TOOLS
            val toolResults = mutableListOf<ContentBlock>()

            for (call in toolCalls) {
                if (!isHeartbeatRun) _events.emit(AgentEvent.ToolCallStart(call.name, call.input))

                val tool = toolRegistry.get(call.name)
                if (tool == null) {
                    toolResults.add(ContentBlock.ToolResult(
                        call.id, "Error: Unknown tool '${call.name}'", isError = true
                    ))
                    continue
                }

                // Check approval based on risk tier and autonomy policy
                val approved: Boolean
                if (tool.riskTier == RiskTier.CONSEQUENTIAL_WRITE) {
                    // CONSEQUENTIAL_WRITE strictly requires a valid ApprovalToken created by a direct user tap.
                    // Models cannot fabricate or issue valid ApprovalTokens.
                    val tokenParam = try {
                        call.input["approval_token"]?.jsonPrimitive?.content
                            ?: call.input["token_id"]?.jsonPrimitive?.content
                    } catch (_: Exception) {
                        null
                    }

                    if (tokenParam != null) {
                        val validation = approvalTokenManager.validateActionToken(tokenParam, call.name)
                        if (validation == TokenValidationResult.Valid) {
                            approvalTokenManager.consumeToken(tokenParam)
                            approved = true
                        } else {
                            toolResults.add(ContentBlock.ToolResult(
                                call.id,
                                "Security policy violation: Tool '${call.name}' is a CONSEQUENTIAL_WRITE action. " +
                                "Provided ApprovalToken is invalid ($validation). A valid single-use ApprovalToken created via direct user tap is required.",
                                isError = true
                            ))
                            if (!isHeartbeatRun) _events.emit(AgentEvent.ToolCallDenied(call.name))
                            continue
                        }
                    } else {
                        // Model did not provide an ApprovalToken: pause and request direct human tap via ApprovalQueue
                        _state.value = AgentState.WAITING_APPROVAL
                        val request = ApprovalRequest(
                            toolName = call.name,
                            parameters = call.input,
                            description = "[CONSEQUENTIAL_WRITE] Allow ${call.name}?"
                        )
                        val result = approvalQueue.request(request)
                        _state.value = AgentState.EXECUTING_TOOLS
                        if (result == ApprovalResult.APPROVED) {
                            val tokenResult = approvalTokenManager.issueActionToken(
                                userDirectTapConfirmed = true,
                                actionName = call.name
                            )
                            if (tokenResult is TokenIssuanceResult.Success) {
                                approvalTokenManager.consumeToken(tokenResult.token.tokenId)
                                approved = true
                            } else {
                                approved = false
                            }
                        } else {
                            approved = false
                        }
                    }
                } else {
                    approved = checkApproval(call.name, call.input)
                }

                if (!approved) {
                    toolResults.add(ContentBlock.ToolResult(
                        call.id, "Tool execution denied by user", isError = true
                    ))
                    if (!isHeartbeatRun) _events.emit(AgentEvent.ToolCallDenied(call.name))
                    continue
                }

                // Execute
                val result = try {
                    tool.execute(call.input)
                } catch (e: Exception) {
                    ToolResult.error("Execution failed: ${e.message}")
                }

                val resultStr = if (result.success) {
                    result.data?.toString() ?: "Success"
                } else {
                    result.error ?: "Unknown error"
                }

                toolResults.add(ContentBlock.ToolResult(
                    call.id, resultStr, isError = !result.success
                ))
                if (!isHeartbeatRun) _events.emit(AgentEvent.ToolCallResult(call.name, result))
            }

            // Add tool results to history
            conversationHistory.add(Message(Role.USER, toolResults))
            _state.value = AgentState.THINKING
        }

        if (iterations >= maxIterations) {
            _events.emit(AgentEvent.Error("Max iterations ($maxIterations) reached"))
            if (isHeartbeatRun) {
                heartbeatManagerProvider.get().onHeartbeatResult(HeartbeatResult.ERROR)
            }
            _state.value = AgentState.IDLE
        }
    }

    /**
     * Remove the last heartbeat prompt + response pair from conversation history.
     * This mirrors OpenClaw's transcript pruning for HEARTBEAT_OK responses.
     */
    private fun pruneLastHeartbeatExchange() {
        // Walk backwards and remove the heartbeat user message + assistant response.
        // There may be multiple messages if the agent did tool calls before responding.
        // For a simple HEARTBEAT_OK (no tools), it's just 2 messages: user + assistant.
        if (conversationHistory.size >= 2) {
            val last = conversationHistory.last()
            val secondLast = conversationHistory[conversationHistory.size - 2]
            if (last.role == Role.ASSISTANT && secondLast.role == Role.USER) {
                conversationHistory.removeAt(conversationHistory.size - 1)
                conversationHistory.removeAt(conversationHistory.size - 1)
            }
        }
    }

    private suspend fun checkApproval(toolName: String, params: JsonObject): Boolean {
        val policy = autonomyPolicy.getPolicy(toolName)
        return when (policy) {
            ToolApprovalPolicy.AUTO -> true
            ToolApprovalPolicy.DENY -> false
            ToolApprovalPolicy.ASK -> {
                _state.value = AgentState.WAITING_APPROVAL
                val request = ApprovalRequest(
                    toolName = toolName,
                    parameters = params,
                    description = "Allow $toolName?"
                )
                val result = approvalQueue.request(request)
                _state.value = AgentState.EXECUTING_TOOLS
                result == ApprovalResult.APPROVED
            }
        }
    }

    fun loadHistory() {
        scope.launch {
            conversationHistory.clear()
            val stored = conversationStore.getRecentMessages(50)
            for (msg in stored) {
                val role = if (msg.role == "user") Role.USER else Role.ASSISTANT
                conversationHistory.add(Message(role, listOf(ContentBlock.Text(msg.content))))
            }
            compactConversationHistoryIfNeeded()
        }
    }

    /**
     * Compacts conversation history if it exceeds token or message thresholds.
     */
    private suspend fun compactConversationHistoryIfNeeded() {
        val maxTokens = appConfig.maxTokens
        if (contextCompactor.shouldCompact(conversationHistory, maxTokens = maxTokens)) {
            val provider = try { providerManager.activeProvider() } catch (_: Exception) { null }
            val compacted = contextCompactor.compact(
                messages = conversationHistory,
                maxTokens = maxTokens,
                aiProvider = provider
            )
            if (compacted != conversationHistory && compacted.isNotEmpty()) {
                conversationHistory.clear()
                conversationHistory.addAll(compacted)
                _events.emit(AgentEvent.ContextCompacted(compacted.size))
            }
        }
    }

    /**
     * Compacts conversation history with explicit limits.
     * Exposed for manual triggering or testing.
     */
    suspend fun compactHistory(
        maxTokens: Int = ContextCompactor.DEFAULT_MAX_TOKENS,
        maxMessages: Int = ContextCompactor.DEFAULT_MAX_MESSAGES,
        keepRecentCount: Int = ContextCompactor.DEFAULT_KEEP_RECENT
    ): Boolean {
        if (contextCompactor.shouldCompact(conversationHistory, maxTokens = maxTokens, maxMessages = maxMessages)) {
            val provider = try { providerManager.activeProvider() } catch (_: Exception) { null }
            val compacted = contextCompactor.compact(
                messages = conversationHistory,
                maxTokens = maxTokens,
                maxMessages = maxMessages,
                keepRecentCount = keepRecentCount,
                aiProvider = provider
            )
            if (compacted != conversationHistory && compacted.isNotEmpty()) {
                conversationHistory.clear()
                conversationHistory.addAll(compacted)
                _events.emit(AgentEvent.ContextCompacted(compacted.size))
                return true
            }
        }
        return false
    }

    /**
     * Returns an unmodifiable snapshot of the current conversation history.
     */
    fun getConversationHistory(): List<Message> = conversationHistory.toList()

    companion object {
        private const val TAG = "AgentLoop"
    }
}

enum class AgentState {
    IDLE, THINKING, EXECUTING_TOOLS, WAITING_APPROVAL, PAUSED, ERROR
}

sealed class AgentEvent {
    data class UserMessage(val text: String) : AgentEvent()
    data class AssistantText(val text: String) : AgentEvent()
    data class ThinkingText(val text: String) : AgentEvent()
    data class ToolCallStart(val name: String, val params: JsonObject) : AgentEvent()
    data class ToolCallResult(val name: String, val result: ToolResult) : AgentEvent()
    data class ToolCallDenied(val name: String) : AgentEvent()
    data class Error(val message: String) : AgentEvent()
    data object HeartbeatStart : AgentEvent()
    data class HeartbeatComplete(val detection: HeartbeatDetector.DetectionResult) : AgentEvent()
    data class ContextCompacted(val messageCount: Int) : AgentEvent()
}
