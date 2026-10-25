package com.cellclaw.accessibility

enum class SessionState {
    IDLE,
    ACTIVE,
    PAUSED,
    STOPPED
}

enum class StopReason {
    USER_CANCEL,
    APP_SWITCH,
    SCREEN_LOCK,
    TIMEOUT,
    COMPLETED,
    ERROR
}

data class SessionConfig(
    val browserPackage: String = "com.android.chrome",
    val maxDurationMs: Long = 300_000L, // 5 minutes default
    val maxPollAttempts: Int = 10,
    val pollIntervalMs: Long = 500L,
    val extraPermittedPackages: Set<String> = emptySet()
)

data class PollResult(
    val success: Boolean,
    val timedOut: Boolean,
    val attemptsMade: Int,
    val error: String? = null
)

/**
 * Supervised, Elixir-scoped accessibility automation session state machine.
 *
 * Enforces PlacementOS Skill rules:
 * 1. Starts ONLY on explicit user tap.
 * 2. Stops immediately on user cancel, screen lock, or unexpected app switch.
 * 3. Inspects ONLY the selected browser and the system file picker.
 * 4. Bounded polling prevents runaway loops and repeated relaunching.
 */
class AccessibilitySession(
    val config: SessionConfig = SessionConfig(),
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        val SYSTEM_FILE_PICKERS = setOf(
            "com.android.documentsui",
            "com.google.android.documentsui",
            "com.google.android.apps.docs"
        )
        const val ELIXIR_PACKAGE = "com.cellclaw"
    }

    var state: SessionState = SessionState.IDLE
        private set

    var stopReason: StopReason? = null
        private set

    var stopDetails: String? = null
        private set

    var startTimeMs: Long = 0L
        private set

    private val permittedPackages: Set<String> by lazy {
        setOf(config.browserPackage, ELIXIR_PACKAGE) + SYSTEM_FILE_PICKERS + config.extraPermittedPackages
    }

    fun isActive(): Boolean = state == SessionState.ACTIVE

    fun isPackagePermitted(packageName: String): Boolean {
        return packageName in permittedPackages
    }

    fun canInspect(packageName: String): Boolean {
        return isActive() && isPackagePermitted(packageName)
    }

    fun start(): Boolean {
        if (state == SessionState.ACTIVE) return true
        startTimeMs = timeProvider()
        state = SessionState.ACTIVE
        stopReason = null
        stopDetails = null
        return true
    }

    fun pause(reason: String = ""): Boolean {
        if (state != SessionState.ACTIVE) return false
        state = SessionState.PAUSED
        stopDetails = reason
        return true
    }

    fun resume(): Boolean {
        if (state != SessionState.PAUSED) return false
        state = SessionState.ACTIVE
        stopDetails = null
        return true
    }

    fun cancel(reason: String = "User cancelled session") {
        stop(StopReason.USER_CANCEL, reason)
    }

    fun onScreenLocked() {
        if (state == SessionState.ACTIVE || state == SessionState.PAUSED) {
            stop(StopReason.SCREEN_LOCK, "Screen locked by user or system timeout")
        }
    }

    fun onPackageChanged(newPackageName: String) {
        if (state != SessionState.ACTIVE && state != SessionState.PAUSED) return
        if (!isPackagePermitted(newPackageName)) {
            stop(StopReason.APP_SWITCH, "Unexpected app switch to unpermitted package: $newPackageName")
        }
    }

    fun checkTimeout(): Boolean {
        if (state != SessionState.ACTIVE && state != SessionState.PAUSED) return false
        val elapsed = timeProvider() - startTimeMs
        if (elapsed > config.maxDurationMs) {
            stop(StopReason.TIMEOUT, "Session exceeded maximum allowed duration of ${config.maxDurationMs}ms (elapsed: ${elapsed}ms)")
            return true
        }
        return false
    }

    fun complete(details: String = "Session completed normally") {
        stop(StopReason.COMPLETED, details)
    }

    fun error(details: String) {
        stop(StopReason.ERROR, details)
    }

    private fun stop(reason: StopReason, details: String) {
        state = SessionState.STOPPED
        stopReason = reason
        stopDetails = details
    }

    /**
     * Bounded polling loop that tests [predicate] up to [maxAttempts] times.
     * Prevents runaway polling or indefinite hangs.
     */
    fun pollUntil(
        maxAttempts: Int = config.maxPollAttempts,
        delayMs: Long = config.pollIntervalMs,
        predicate: () -> Boolean
    ): PollResult {
        var attempts = 0
        while (attempts < maxAttempts) {
            if (checkTimeout() || !isActive()) {
                return PollResult(
                    success = false,
                    timedOut = true,
                    attemptsMade = attempts,
                    error = "Session stopped or timed out during polling: $stopDetails"
                )
            }

            attempts++
            val matched = try {
                predicate()
            } catch (e: Exception) {
                return PollResult(
                    success = false,
                    timedOut = false,
                    attemptsMade = attempts,
                    error = e.message
                )
            }

            if (matched) {
                return PollResult(
                    success = true,
                    timedOut = false,
                    attemptsMade = attempts
                )
            }

            if (attempts < maxAttempts && delayMs > 0) {
                try {
                    Thread.sleep(delayMs)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return PollResult(
                        success = false,
                        timedOut = false,
                        attemptsMade = attempts,
                        error = "Polling interrupted"
                    )
                }
            }
        }

        return PollResult(
            success = false,
            timedOut = true,
            attemptsMade = attempts,
            error = "Condition not met after $attempts attempts"
        )
    }
}
