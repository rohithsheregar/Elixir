package com.cellclaw.ai

import com.cellclaw.provider.CompletionRequest
import com.cellclaw.provider.CompletionResponse
import com.cellclaw.provider.ContentBlock
import com.cellclaw.provider.GeminiProvider
import com.cellclaw.provider.Provider
import com.cellclaw.provider.ProviderManager
import com.cellclaw.provider.StopReason
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.ApplicationTracker
import kotlinx.coroutines.delay
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class RateLimitException(message: String = "HTTP 429: Rate Limit Exceeded", cause: Throwable? = null) : IOException(message, cause)
class CallBudgetExceededException(val applicationId: String, val maxBudget: Int) : IllegalStateException("AI call budget ($maxBudget) exceeded for application $applicationId")
class AiClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class AiClientConfig(
    val maxRetries: Int = 3,
    val initialBackoffMs: Long = 50L,
    val backoffMultiplier: Double = 2.0,
    val maxBudgetPerApplication: Int = 5
)

interface AiClient {
    suspend fun complete(
        request: CompletionRequest,
        applicationId: String? = null,
        jdHash: String? = null
    ): CompletionResponse

    fun getRemainingBudget(applicationId: String): Int
    fun clearCache()
    fun resetBudget(applicationId: String)
}

@Singleton
class ResilientAiClient(
    private val primaryProvider: Provider,
    private val fallbackProvider: Provider? = null,
    private val applicationTracker: ApplicationTracker? = null,
    private val config: AiClientConfig = AiClientConfig()
) : AiClient {

    @Inject
    constructor(
        providerManager: ProviderManager,
        geminiProvider: GeminiProvider,
        applicationTracker: ApplicationTracker
    ) : this(
        primaryProvider = providerManager.activeProvider(),
        fallbackProvider = geminiProvider,
        applicationTracker = applicationTracker,
        config = AiClientConfig()
    )

    private val cache = mutableMapOf<String, CompletionResponse>()
    private val applicationUsage = mutableMapOf<String, Int>()

    override fun getRemainingBudget(applicationId: String): Int {
        val used = applicationUsage[applicationId] ?: 0
        return (config.maxBudgetPerApplication - used).coerceAtLeast(0)
    }

    override fun clearCache() {
        cache.clear()
    }

    override fun resetBudget(applicationId: String) {
        applicationUsage.remove(applicationId)
    }

    override suspend fun complete(
        request: CompletionRequest,
        applicationId: String?,
        jdHash: String?
    ): CompletionResponse {
        // 1. Check Cache by JD hash
        val cacheKey = if (!jdHash.isNullOrBlank()) {
            "jd_${jdHash}_${request.systemPrompt.hashCode()}_${request.messages.hashCode()}"
        } else null

        if (cacheKey != null && cache.containsKey(cacheKey)) {
            return cache[cacheKey]!!
        }

        // 2. Enforce Per-Application Call Budget
        if (!applicationId.isNullOrBlank()) {
            val currentUsage = applicationUsage[applicationId] ?: 0
            if (currentUsage >= config.maxBudgetPerApplication) {
                ensureApplicationInDraft(applicationId)
                throw CallBudgetExceededException(applicationId, config.maxBudgetPerApplication)
            }
        }

        // 3. Attempt Primary Provider with 429 Exponential Backoff
        try {
            val response = executeWithRetry(primaryProvider, request)
            recordSuccess(applicationId, cacheKey, response)
            return response
        } catch (e: Exception) {
            // 4. Fallback Provider Failover
            if (fallbackProvider != null && fallbackProvider != primaryProvider) {
                try {
                    val fallbackResponse = executeWithRetry(fallbackProvider, request)
                    recordSuccess(applicationId, cacheKey, fallbackResponse)
                    return fallbackResponse
                } catch (fallbackEx: Exception) {
                    handleTerminalFailure(applicationId)
                    throw fallbackEx
                }
            } else {
                handleTerminalFailure(applicationId)
                throw e
            }
        }
    }

    private suspend fun executeWithRetry(provider: Provider, request: CompletionRequest): CompletionResponse {
        var currentDelay = config.initialBackoffMs
        var lastException: Exception? = null

        for (attempt in 0..config.maxRetries) {
            try {
                val response = provider.complete(request)
                if (response.stopReason == StopReason.ERROR &&
                    response.content.any { it is ContentBlock.Text && it.text.contains("429") }
                ) {
                    throw RateLimitException("HTTP 429 rate limit returned by ${provider.name}")
                }
                return response
            } catch (e: Exception) {
                lastException = e
                val is429 = e is RateLimitException ||
                        (e.message?.contains("429") == true) ||
                        (e.message?.contains("rate limit", ignoreCase = true) == true)

                if (is429 && attempt < config.maxRetries) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * config.backoffMultiplier).toLong()
                } else if (!is429) {
                    throw e
                }
            }
        }
        throw lastException ?: AiClientException("Execution failed after retries")
    }

    private fun recordSuccess(applicationId: String?, cacheKey: String?, response: CompletionResponse) {
        if (!applicationId.isNullOrBlank()) {
            applicationUsage[applicationId] = (applicationUsage[applicationId] ?: 0) + 1
        }
        if (cacheKey != null) {
            cache[cacheKey] = response
        }
    }

    private suspend fun handleTerminalFailure(applicationId: String?) {
        if (!applicationId.isNullOrBlank()) {
            ensureApplicationInDraft(applicationId)
        }
    }

    private suspend fun ensureApplicationInDraft(applicationId: String) {
        applicationTracker?.let { tracker ->
            try {
                val app = tracker.getApplication(applicationId)
                if (app != null && app.state != ApplicationState.DRAFT) {
                    tracker.updateApplication(app.copy(state = ApplicationState.DRAFT))
                }
            } catch (_: Exception) {
                // Ignore tracker errors during failure recovery
            }
        }
    }
}

