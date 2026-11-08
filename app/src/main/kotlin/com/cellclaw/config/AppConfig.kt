package com.cellclaw.config

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AppConfig {
    private val prefs: SharedPreferences?
    private val inMemoryProps = mutableMapOf<String, Any>()

    @Inject
    constructor(@ApplicationContext context: Context) {
        this.prefs = try {
            context.getSharedPreferences("cellclaw_config", Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
    }

    /** Secondary constructor for JVM unit tests with no Android context */
    constructor() {
        this.prefs = null
    }

    open var providerType: String
        get() = prefs?.getString("provider_type", "anthropic")
            ?: (inMemoryProps["provider_type"] as? String ?: "anthropic")
        set(value) {
            prefs?.edit()?.putString("provider_type", value)?.apply()
            inMemoryProps["provider_type"] = value
        }

    open var model: String
        get() = prefs?.getString("model", "claude-sonnet-4-6")
            ?: (inMemoryProps["model"] as? String ?: "claude-sonnet-4-6")
        set(value) {
            prefs?.edit()?.putString("model", value)?.apply()
            inMemoryProps["model"] = value
        }

    open var maxTokens: Int
        get() = prefs?.getInt("max_tokens", 4096)
            ?: (inMemoryProps["max_tokens"] as? Int ?: 4096)
        set(value) {
            prefs?.edit()?.putInt("max_tokens", value)?.apply()
            inMemoryProps["max_tokens"] = value
        }

    open var autoStartOnBoot: Boolean
        get() = prefs?.getBoolean("auto_start_boot", false)
            ?: (inMemoryProps["auto_start_boot"] as? Boolean ?: false)
        set(value) {
            prefs?.edit()?.putBoolean("auto_start_boot", value)?.apply()
            inMemoryProps["auto_start_boot"] = value
        }

    open var isSetupComplete: Boolean
        get() = prefs?.getBoolean("setup_complete", false)
            ?: (inMemoryProps["setup_complete"] as? Boolean ?: false)
        set(value) {
            prefs?.edit()?.putBoolean("setup_complete", value)?.apply()
            inMemoryProps["setup_complete"] = value
        }

    open var personalityPrompt: String
        get() = prefs?.getString("personality_prompt", "")
            ?: (inMemoryProps["personality_prompt"] as? String ?: "")
        set(value) {
            prefs?.edit()?.putString("personality_prompt", value)?.apply()
            inMemoryProps["personality_prompt"] = value
        }

    open var userName: String
        get() = prefs?.getString("user_name", "")
            ?: (inMemoryProps["user_name"] as? String ?: "")
        set(value) {
            prefs?.edit()?.putString("user_name", value)?.apply()
            inMemoryProps["user_name"] = value
        }

    open var voiceEnabled: Boolean
        get() = prefs?.getBoolean("voice_enabled", false)
            ?: (inMemoryProps["voice_enabled"] as? Boolean ?: false)
        set(value) {
            prefs?.edit()?.putBoolean("voice_enabled", value)?.apply()
            inMemoryProps["voice_enabled"] = value
        }

    open var autoSpeakResponses: Boolean
        get() = prefs?.getBoolean("auto_speak_responses", false)
            ?: (inMemoryProps["auto_speak_responses"] as? Boolean ?: false)
        set(value) {
            prefs?.edit()?.putBoolean("auto_speak_responses", value)?.apply()
            inMemoryProps["auto_speak_responses"] = value
        }

    open var overlayEnabled: Boolean
        get() = prefs?.getBoolean("overlay_enabled", false)
            ?: (inMemoryProps["overlay_enabled"] as? Boolean ?: false)
        set(value) {
            prefs?.edit()?.putBoolean("overlay_enabled", value)?.apply()
            inMemoryProps["overlay_enabled"] = value
        }

    /** Max agent loop iterations. 0 = unlimited. */
    open var maxIterations: Int
        get() = prefs?.getInt("max_iterations", 0)
            ?: (inMemoryProps["max_iterations"] as? Int ?: 0)
        set(value) {
            prefs?.edit()?.putInt("max_iterations", value)?.apply()
            inMemoryProps["max_iterations"] = value
        }

    open var permissionProfile: String
        get() = prefs?.getString("permission_profile", "CAUTIOUS")
            ?: (inMemoryProps["permission_profile"] as? String ?: "CAUTIOUS")
        set(value) {
            prefs?.edit()?.putString("permission_profile", value)?.apply()
            inMemoryProps["permission_profile"] = value
        }

    open var wakeWordEnabled: Boolean
        get() = prefs?.getBoolean("wake_word_enabled", false)
            ?: (inMemoryProps["wake_word_enabled"] as? Boolean ?: false)
        set(value) {
            prefs?.edit()?.putBoolean("wake_word_enabled", value)?.apply()
            inMemoryProps["wake_word_enabled"] = value
        }

    open var heartbeatEnabled: Boolean
        get() = prefs?.getBoolean("heartbeat_enabled", true)
            ?: (inMemoryProps["heartbeat_enabled"] as? Boolean ?: true)
        set(value) {
            prefs?.edit()?.putBoolean("heartbeat_enabled", value)?.apply()
            inMemoryProps["heartbeat_enabled"] = value
        }

    /** Whether to poll even when there's no active task context. */
    open var heartbeatAlwaysPoll: Boolean
        get() = prefs?.getBoolean("heartbeat_always_poll", false)
            ?: (inMemoryProps["heartbeat_always_poll"] as? Boolean ?: false)
        set(value) {
            prefs?.edit()?.putBoolean("heartbeat_always_poll", value)?.apply()
            inMemoryProps["heartbeat_always_poll"] = value
        }
}
