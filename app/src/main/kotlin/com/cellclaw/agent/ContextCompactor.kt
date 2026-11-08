package com.cellclaw.agent

import com.cellclaw.provider.*
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

interface ContextCompactor {
    /**
     * Estimates total tokens across the provided messages.
     * Uses heuristic (~1 token per 4 characters + base message overhead).
     */
    fun estimateTokens(messages: List<Message>): Int

    /**
     * Estimates tokens in an individual message.
     */
    fun estimateTokens(message: Message): Int

    /**
     * Checks if history exceeds configured token or message count thresholds.
     */
    fun shouldCompact(
        messages: List<Message>,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        maxMessages: Int = DEFAULT_MAX_MESSAGES
    ): Boolean

    /**
     * Compacts older conversation turns into a consolidated context summary block,
     * preserving recent messages and ensuring strict role alternation without
     * orphaning tool invocations/results.
     */
    suspend fun compact(
        messages: List<Message>,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        maxMessages: Int = DEFAULT_MAX_MESSAGES,
        keepRecentCount: Int = DEFAULT_KEEP_RECENT,
        aiProvider: Provider? = null
    ): List<Message>

    companion object {
        const val DEFAULT_MAX_TOKENS = 4000
        const val DEFAULT_MAX_MESSAGES = 20
        const val DEFAULT_KEEP_RECENT = 4
        const val SUMMARY_PREFIX = "[Context Compaction Summary"
    }
}

@Singleton
class DefaultContextCompactor @Inject constructor() : ContextCompactor {

    override fun estimateTokens(message: Message): Int {
        var tokens = 4 // Base message overhead (role + boundary)
        for (block in message.content) {
            tokens += when (block) {
                is ContentBlock.Text -> (block.text.length + 3) / 4
                is ContentBlock.ToolUse -> (block.name.length + block.input.toString().length + 10) / 4
                is ContentBlock.ToolResult -> (block.toolUseId.length + block.content.length + 10) / 4
                else -> 4
            }
        }
        return tokens
    }

    override fun estimateTokens(messages: List<Message>): Int {
        var total = 0
        for (msg in messages) {
            total += estimateTokens(msg)
        }
        return total
    }

    override fun shouldCompact(
        messages: List<Message>,
        maxTokens: Int,
        maxMessages: Int
    ): Boolean {
        if (messages.size <= 1) return false
        val tokenCount = estimateTokens(messages)
        return tokenCount > maxTokens || messages.size > maxMessages
    }

    override suspend fun compact(
        messages: List<Message>,
        maxTokens: Int,
        maxMessages: Int,
        keepRecentCount: Int,
        aiProvider: Provider?
    ): List<Message> {
        if (messages.size <= 1) return messages
        val targetKeep = keepRecentCount.coerceAtMost(messages.size - 1).coerceAtLeast(1)

        val splitIndex = findSafeSplitIndex(messages, targetKeep)
        if (splitIndex <= 0 || splitIndex >= messages.size) return messages

        val toCompact = messages.subList(0, splitIndex)
        val toKeep = messages.subList(splitIndex, messages.size)

        if (toCompact.isEmpty()) return messages

        // Extract prior summary if existing from previous compaction
        val (priorSummary, cleanedToCompact) = extractPriorSummary(toCompact)

        // Attempt AI summary if provider is supplied, else use deterministic structured compaction
        val summaryText = if (aiProvider != null) {
            try {
                generateAiSummary(aiProvider, priorSummary, cleanedToCompact)
            } catch (_: Exception) {
                generateDeterministicSummary(priorSummary, cleanedToCompact, toCompact.size)
            }
        } else {
            generateDeterministicSummary(priorSummary, cleanedToCompact, toCompact.size)
        }

        val summaryMessage = Message.user(summaryText)
        val ackMessage = Message.assistant(
            "Understood. I have preserved the context from our earlier conversation and will proceed."
        )

        return if (toKeep.first().role == Role.USER) {
            listOf(summaryMessage, ackMessage) + toKeep
        } else {
            listOf(summaryMessage) + toKeep
        }
    }

    private fun findSafeSplitIndex(messages: List<Message>, targetKeep: Int): Int {
        var desiredSplit = messages.size - targetKeep

        // A safe split must not split a ToolUse from its matching ToolResult.
        // Also prefer splitting at a clean User text prompt.
        while (desiredSplit > 0) {
            val current = messages[desiredSplit]
            val prev = messages[desiredSplit - 1]

            val isCurrentToolResult = current.content.any { it is ContentBlock.ToolResult }
            val isPrevToolUse = prev.content.any { it is ContentBlock.ToolUse }

            if (!isCurrentToolResult && !isPrevToolUse) {
                return desiredSplit
            }
            desiredSplit--
        }

        // If scanning backward didn't find a boundary, scan forward from original target
        var forwardSplit = messages.size - targetKeep
        while (forwardSplit < messages.size) {
            val current = messages[forwardSplit]
            val prev = messages[forwardSplit - 1]

            val isCurrentToolResult = current.content.any { it is ContentBlock.ToolResult }
            val isPrevToolUse = prev.content.any { it is ContentBlock.ToolUse }

            if (!isCurrentToolResult && !isPrevToolUse) {
                return forwardSplit
            }
            forwardSplit++
        }

        return messages.size - targetKeep
    }

    private fun extractPriorSummary(toCompact: List<Message>): Pair<String?, List<Message>> {
        val first = toCompact.firstOrNull() ?: return Pair(null, toCompact)
        val firstBlock = first.content.firstOrNull()
        if (first.role == Role.USER && firstBlock is ContentBlock.Text &&
            firstBlock.text.startsWith(ContextCompactor.SUMMARY_PREFIX)) {
            val prior = firstBlock.text
            // Also skip companion synthetic assistant ack if present
            val remaining = if (toCompact.size > 1 && toCompact[1].role == Role.ASSISTANT &&
                toCompact[1].content.any { (it as? ContentBlock.Text)?.text?.contains("preserved the context") == true }) {
                toCompact.subList(2, toCompact.size)
            } else {
                toCompact.subList(1, toCompact.size)
            }
            return Pair(prior, remaining)
        }
        return Pair(null, toCompact)
    }

    private suspend fun generateAiSummary(
        provider: Provider,
        priorSummary: String?,
        messages: List<Message>
    ): String {
        val transcript = buildString {
            if (priorSummary != null) {
                append("PRIOR CONTEXT:\n").append(priorSummary).append("\n\n")
            }
            append("NEW TURNS TO SUMMARIZE:\n")
            for (msg in messages) {
                append("[${msg.role}]: ")
                for (b in msg.content) {
                    when (b) {
                        is ContentBlock.Text -> append(b.text.take(200)).append(" ")
                        is ContentBlock.ToolUse -> append("[Call tool ${b.name}] ")
                        is ContentBlock.ToolResult -> append("[Result: ${b.content.take(100)}] ")
                        else -> {}
                    }
                }
                append("\n")
            }
        }

        val prompt = "Summarize the following prior conversation into 3-5 concise bullet points highlighting user goals, actions taken, and current status:\n\n$transcript"
        val request = CompletionRequest(
            systemPrompt = "You are a concise conversation summarizer. Output only bullet points summarizing the conversation context.",
            messages = listOf(Message.user(prompt)),
            maxTokens = 500
        )
        val response = provider.complete(request)
        val responseText = response.content.filterIsInstance<ContentBlock.Text>()
            .filter { !it.thought }
            .joinToString("\n") { it.text.trim() }

        return if (responseText.isNotBlank()) {
            "${ContextCompactor.SUMMARY_PREFIX} (AI Summarized):\n$responseText\n]"
        } else {
            generateDeterministicSummary(priorSummary, messages, messages.size)
        }
    }

    private fun generateDeterministicSummary(
        priorSummary: String?,
        messages: List<Message>,
        totalCompactedCount: Int
    ): String {
        val bullets = mutableListOf<String>()

        for (msg in messages) {
            when (msg.role) {
                Role.USER -> {
                    for (b in msg.content) {
                        when (b) {
                            is ContentBlock.Text -> {
                                val clean = b.text.trim().replace("\n", " ").take(150)
                                if (clean.isNotBlank()) {
                                    bullets.add("- User requested: \"$clean\"")
                                }
                            }
                            is ContentBlock.ToolResult -> {
                                val clean = b.content.trim().replace("\n", " ").take(100)
                                bullets.add("  -> Tool outcome: \"$clean\"")
                            }
                            else -> {}
                        }
                    }
                }
                Role.ASSISTANT -> {
                    for (b in msg.content) {
                        when (b) {
                            is ContentBlock.ToolUse -> {
                                bullets.add("- Executed action '${b.name}'")
                            }
                            is ContentBlock.Text -> {
                                if (!b.thought) {
                                    val clean = b.text.trim().replace("\n", " ").take(150)
                                    if (clean.isNotBlank() && !clean.contains("preserved the context")) {
                                        bullets.add("- Assistant noted: \"$clean\"")
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }

        return buildString {
            append("${ContextCompactor.SUMMARY_PREFIX} of earlier turns ($totalCompactedCount messages):\n")
            if (priorSummary != null) {
                append("Prior Summary:\n").append(priorSummary.trim()).append("\n\n")
            }
            append("Key Inquiries & Outcomes:\n")
            if (bullets.isEmpty()) {
                append("- Previous turn history maintained.\n")
            } else {
                for (b in bullets.take(15)) {
                    append(b).append("\n")
                }
            }
            append("]")
        }
    }
}

