package com.cellclaw.provider

import com.cellclaw.config.AppConfig
import com.cellclaw.config.SecureKeyStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages available AI providers and handles provider switching.
 * Reads the active provider from AppConfig and configures it with
 * the encrypted API key from SecureKeyStore.
 */
@Singleton
open class ProviderManager {
    private val appConfig: AppConfig?
    private val secureKeyStore: SecureKeyStore?
    private val anthropicProvider: AnthropicProvider?
    private val openAIProvider: OpenAIProvider?
    private val geminiProvider: GeminiProvider?
    private val openRouterProvider: OpenRouterProvider?
    private val testProvider: Provider?

    private val providers: Map<String, Provider>

    @Inject
    constructor(
        appConfig: AppConfig,
        secureKeyStore: SecureKeyStore,
        anthropicProvider: AnthropicProvider,
        openAIProvider: OpenAIProvider,
        geminiProvider: GeminiProvider,
        openRouterProvider: OpenRouterProvider
    ) {
        this.appConfig = appConfig
        this.secureKeyStore = secureKeyStore
        this.anthropicProvider = anthropicProvider
        this.openAIProvider = openAIProvider
        this.geminiProvider = geminiProvider
        this.openRouterProvider = openRouterProvider
        this.testProvider = null
        this.providers = mapOf(
            "anthropic" to anthropicProvider,
            "openai" to openAIProvider,
            "gemini" to geminiProvider,
            "openrouter" to openRouterProvider
        )
    }

    constructor(testProvider: Provider) {
        this.appConfig = null
        this.secureKeyStore = null
        this.anthropicProvider = null
        this.openAIProvider = null
        this.geminiProvider = null
        this.openRouterProvider = null
        this.testProvider = testProvider
        this.providers = emptyMap()
    }

    /** Get the currently active provider, configured with its API key */
    open fun activeProvider(): Provider {
        testProvider?.let { return it }
        val config = appConfig ?: throw IllegalStateException("ProviderManager not configured with AppConfig")
        val type = config.providerType
        val fallback = anthropicProvider ?: throw IllegalStateException("No default provider available")
        val provider = providers[type] ?: fallback
        configureProvider(type, provider)
        return provider
    }

    /** Switch to a different provider */
    fun switchProvider(type: String) {
        if (type in providers) {
            appConfig?.providerType = type
        }
    }

    /** Get the active provider type name */
    fun activeType(): String = appConfig?.providerType ?: (testProvider?.name ?: "unknown")

    /** Get all available provider types */
    fun availableProviders(): List<ProviderInfo> {
        val keyStore = secureKeyStore ?: return emptyList()
        return listOf(
            ProviderInfo("anthropic", "Anthropic (Claude)", "claude-sonnet-4-6",
                keyStore.hasApiKey("anthropic"),
                models = listOf("claude-sonnet-4-6", "claude-opus-4-6", "claude-haiku-4-5")),
            ProviderInfo("openai", "OpenAI (GPT)", "gpt-5.2",
                keyStore.hasApiKey("openai"),
                models = listOf("gpt-5.2", "gpt-5.2-chat-latest", "gpt-5-mini", "gpt-4.1", "gpt-4.1-mini")),
            ProviderInfo("gemini", "Google (Gemini)", "gemini-2.0-flash",
                keyStore.hasApiKey("gemini"),
                models = listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-2.5-flash", "gemini-2.5-pro", "gemini-1.5-pro")),
            ProviderInfo("openrouter", "OpenRouter", "google/gemini-2.5-flash",
                keyStore.hasApiKey("openrouter"),
                models = listOf("google/gemini-2.5-flash", "google/gemini-2.5-pro", "anthropic/claude-sonnet-4.6", "openai/gpt-5.2"))
        )
    }

    /** Check if a provider has an API key configured */
    fun hasKey(type: String): Boolean = secureKeyStore?.hasApiKey(type) ?: false

    /** Store an API key for a provider */
    fun setApiKey(type: String, apiKey: String) {
        secureKeyStore?.storeApiKey(type, apiKey.trim())
    }

    /** Remove an API key for a provider */
    fun removeApiKey(type: String) {
        secureKeyStore?.deleteApiKey(type)
    }

    private fun configureProvider(type: String, provider: Provider) {
        val keyStore = secureKeyStore ?: return
        val config = appConfig ?: return
        val apiKey = keyStore.getApiKey(type) ?: return
        val model = config.model
        when (provider) {
            is AnthropicProvider -> provider.configure(apiKey, model)
            is OpenAIProvider -> provider.configure(apiKey, model)
            is GeminiProvider -> provider.configure(apiKey, model)
            is OpenRouterProvider -> provider.configure(apiKey, model)
        }
    }
}

data class ProviderInfo(
    val type: String,
    val displayName: String,
    val defaultModel: String,
    val hasKey: Boolean,
    val models: List<String> = emptyList()
)
