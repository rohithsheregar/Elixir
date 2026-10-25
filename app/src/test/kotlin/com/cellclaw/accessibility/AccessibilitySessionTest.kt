package com.cellclaw.accessibility

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AccessibilitySessionTest {

    private var currentTime: Long = 1000L
    private lateinit var session: AccessibilitySession

    @Before
    fun setUp() {
        currentTime = 1000L
        session = AccessibilitySession(
            config = SessionConfig(
                browserPackage = "com.android.chrome",
                maxDurationMs = 60_000L,
                maxPollAttempts = 5,
                pollIntervalMs = 100L
            ),
            timeProvider = { currentTime }
        )
    }

    @Test
    fun testInitialStateIsIdle() {
        assertEquals(SessionState.IDLE, session.state)
        assertFalse(session.isActive())
        assertNull(session.stopReason)
    }

    @Test
    fun testStartSessionOnUserTap() {
        val started = session.start()
        assertTrue("Session should start successfully on explicit user action", started)
        assertEquals(SessionState.ACTIVE, session.state)
        assertTrue(session.isActive())
        assertEquals(1000L, session.startTimeMs)
    }

    @Test
    fun testPermittedPackagesScope() {
        session.start()

        // Permitted: target browser, document pickers, and assistant
        assertTrue(session.isPackagePermitted("com.android.chrome"))
        assertTrue(session.isPackagePermitted("com.android.documentsui"))
        assertTrue(session.isPackagePermitted("com.google.android.documentsui"))
        assertTrue(session.isPackagePermitted("com.cellclaw"))

        // Forbidden packages: other apps must never be inspected
        assertFalse(session.isPackagePermitted("com.whatsapp"))
        assertFalse(session.isPackagePermitted("com.google.android.gm"))
        assertFalse(session.isPackagePermitted("com.android.settings"))
        assertFalse(session.isPackagePermitted("org.telegram.messenger"))
    }

    @Test
    fun testStopOnAppSwitchToUnpermittedPackage() {
        session.start()
        assertTrue(session.isActive())

        // Switch to Chrome: stays active
        session.onPackageChanged("com.android.chrome")
        assertTrue(session.isActive())

        // Switch to document picker: stays active
        session.onPackageChanged("com.google.android.documentsui")
        assertTrue(session.isActive())

        // Switch to unexpected app (e.g. WhatsApp): MUST stop immediately
        session.onPackageChanged("com.whatsapp")
        assertEquals(SessionState.STOPPED, session.state)
        assertEquals(StopReason.APP_SWITCH, session.stopReason)
        assertTrue(session.stopDetails?.contains("com.whatsapp") == true)
        assertFalse(session.isActive())
    }

    @Test
    fun testStopOnScreenLock() {
        session.start()
        assertTrue(session.isActive())

        session.onScreenLocked()
        assertEquals(SessionState.STOPPED, session.state)
        assertEquals(StopReason.SCREEN_LOCK, session.stopReason)
        assertFalse(session.isActive())
    }

    @Test
    fun testStopOnUserCancel() {
        session.start()
        assertTrue(session.isActive())

        session.cancel("User tapped cancel on notification")
        assertEquals(SessionState.STOPPED, session.state)
        assertEquals(StopReason.USER_CANCEL, session.stopReason)
        assertEquals("User tapped cancel on notification", session.stopDetails)
    }

    @Test
    fun testPauseAndResumeForUserIntervention() {
        session.start()
        assertTrue(session.isActive())

        // User needs to solve CAPTCHA or sign in
        session.pause("User Google sign-in required")
        assertEquals(SessionState.PAUSED, session.state)
        assertFalse(session.isActive()) // Not active for automation while paused

        // Cannot inspect while paused
        assertFalse(session.canInspect("com.android.chrome"))

        // User finishes and resumes
        val resumed = session.resume()
        assertTrue(resumed)
        assertEquals(SessionState.ACTIVE, session.state)
        assertTrue(session.canInspect("com.android.chrome"))
    }

    @Test
    fun testSessionTimeout() {
        session.start()
        assertTrue(session.isActive())

        // Advance time by 30 seconds (within 60s maxDuration)
        currentTime += 30_000L
        assertFalse(session.checkTimeout())
        assertEquals(SessionState.ACTIVE, session.state)

        // Advance time past 60s
        currentTime += 30_001L
        assertTrue(session.checkTimeout())
        assertEquals(SessionState.STOPPED, session.state)
        assertEquals(StopReason.TIMEOUT, session.stopReason)
    }

    @Test
    fun testBoundedPolling() {
        session.start()

        var pollCount = 0
        // Condition never met: polling should terminate cleanly after maxPollAttempts (5)
        val result = session.pollUntil(
            maxAttempts = 5,
            delayMs = 10L,
            predicate = {
                pollCount++
                false
            }
        )

        assertFalse("Polling should fail when condition is never met", result.success)
        assertTrue(result.timedOut)
        assertEquals(5, pollCount)
        assertEquals(5, result.attemptsMade)
    }

    @Test
    fun testBoundedPollingSuccessBeforeLimit() {
        session.start()

        var pollCount = 0
        // Condition met on attempt 3
        val result = session.pollUntil(
            maxAttempts = 5,
            delayMs = 10L,
            predicate = {
                pollCount++
                pollCount == 3
            }
        )

        assertTrue("Polling should succeed when condition becomes true", result.success)
        assertFalse(result.timedOut)
        assertEquals(3, pollCount)
        assertEquals(3, result.attemptsMade)
    }
}
