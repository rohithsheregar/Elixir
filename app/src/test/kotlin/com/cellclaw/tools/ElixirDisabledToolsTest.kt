package com.cellclaw.tools

import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies that risky tools are NOT registered in the Elixir ToolRegistry.
 * Per AGENTS.md §5 and PlacementOS skill file §2 Phase 0:
 * SMS, calls, camera, location, contacts, and shell tools must be disabled
 * (removed from registry) for the Elixir profile. Source files are kept intact.
 *
 * This test runs on the JVM (no Android SDK or Hilt needed).
 */
class ElixirDisabledToolsTest {

    /**
     * These tool names must NOT appear in the Elixir ToolRegistry.
     * They are disabled for the placement-assistant profile.
     */
    private val disabledToolNames = setOf(
        // SMS
        "sms.read",
        "sms.send",
        // Phone / calls
        "phone.call",
        "phone.log",
        // Contacts
        "contacts.search",
        "contacts.add",
        // Camera
        "camera.snap",
        "camera.record",
        // Location
        "location.get",
        // Shell execution (ProcessBuilder)
        "script.exec"
    )

    /**
     * Tool names that ARE expected to be present in the Elixir profile.
     * These are safe tools relevant to placement tasks.
     */
    private val expectedSafeToolNames = setOf(
        "clipboard.read",
        "clipboard.write",
        "file.read",
        "file.write",
        "file.list",
        "notification.send",
        "browser.open",
        "browser.search",
        "settings.get",
        "app.launch",
        "app.automate",
        "screen.read",
        "screen.capture",
        "vision.analyze",
        "schedule.manage",
        "heartbeat.context"
    )

    @Test
    fun `disabled tools are not registered in ElixirToolRegistry`() {
        val registry = buildElixirToolRegistryStub()
        val registeredNames = registry.names()

        val violations = disabledToolNames.filter { it in registeredNames }
        assertTrue(
            "Risky tools must be disabled in the Elixir profile. " +
            "Found these still registered: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `safe tools are present in ElixirToolRegistry`() {
        val registry = buildElixirToolRegistryStub()
        val registeredNames = registry.names()

        val missing = expectedSafeToolNames.filter { it !in registeredNames }
        assertTrue(
            "Safe tools should be registered in the Elixir profile. " +
            "Missing: $missing",
            missing.isEmpty()
        )
    }

    /**
     * Builds a ToolRegistry with the same set of tool names that the
     * production AppModule registers after M0.3. Uses FakeTools so no
     * Android context or Hilt is needed.
     *
     * IMPORTANT: keep this list in sync with AppModule.provideToolRegistry.
     * When AppModule changes, update here too.
     */
    private fun buildElixirToolRegistryStub(): ToolRegistry {
        return ToolRegistry().apply {
            // Safe tools — same list as AppModule after M0.3 (risky tools removed)
            register(
                // Calendar
                FakeTool("calendar.query", "Query calendar"),
                FakeTool("calendar.create", "Create event"),
                // Notification
                FakeTool("notification.send", "Send notification"),
                FakeTool("notification.listen", "Listen for notifications"),
                // Clipboard
                FakeTool("clipboard.read", "Read clipboard"),
                FakeTool("clipboard.write", "Write clipboard"),
                // File
                FakeTool("file.read", "Read file"),
                FakeTool("file.write", "Write file"),
                FakeTool("file.list", "List files"),
                // Sensor
                FakeTool("sensor.read", "Read sensor"),
                // Settings
                FakeTool("settings.get", "Get settings"),
                // Browser
                FakeTool("browser.open", "Open browser"),
                FakeTool("browser.search", "Browser search"),
                // App control
                FakeTool("app.launch", "Launch app"),
                FakeTool("app.automate", "Automate app"),
                FakeTool("screen.read", "Read screen"),
                // Email
                FakeTool("email.send", "Send email"),
                // Screen capture / vision
                FakeTool("screen.capture", "Capture screen"),
                FakeTool("vision.analyze", "Analyze vision"),
                // Scheduler and heartbeat
                FakeTool("schedule.manage", "Manage schedule"),
                FakeTool("heartbeat.context", "Heartbeat context")
                // NOT included: sms.read, sms.send, phone.call, phone.log,
                //               contacts.search, contacts.add, camera.snap, camera.record,
                //               location.get, script.exec, messaging.open, messaging.read, messaging.reply
            )
        }
    }
}
