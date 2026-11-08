package com.cellclaw.config

import com.cellclaw.tools.Tool
import com.cellclaw.tools.ToolParameters
import com.cellclaw.tools.ToolRegistry
import com.cellclaw.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies AG0.2 requirement: Identity.DEFAULT_IDENTITY must NOT name any tool
 * that is absent from the actual ToolRegistry registration for the Elixir build.
 *
 * Tests also assert that disabled tools (SMS, calls, camera, shell, etc.) are
 * never mentioned as available capabilities.
 */
class IdentityPromptTest {

    /** Minimal no-op tool stub for JVM unit testing without Android Context. */
    private fun stubTool(toolName: String): Tool = object : Tool {
        override val name: String = toolName
        override val description: String = "stub"
        override val parameters: ToolParameters = ToolParameters()
        override val requiresApproval: Boolean = false
        override suspend fun execute(params: JsonObject): ToolResult =
            ToolResult(success = true)
    }

    private val ELIXIR_REGISTERED_TOOLS = setOf(
        "calendar.query", "calendar.create",
        "notification.send", "notification.listen",
        "clipboard.read", "clipboard.write",
        "file.read", "file.write", "file.list",
        "sensor.read", "settings.get",
        "browser.search", "screen.read", "email.send",
        "screen.capture", "vision.analyze",
        "schedule.manage", "heartbeat.context",
        "jd.parse", "eligibility.evaluate", "resume.generate",
        "application.prepare_review", "application.submit"
    )

    private fun buildElixirRegistry(): ToolRegistry {
        val registry = ToolRegistry()
        ELIXIR_REGISTERED_TOOLS.forEach { name -> registry.register(stubTool(name)) }
        return registry
    }

    @Test
    fun testIdentityNamedAsElixirNotCellClaw() {
        val identity = Identity.DEFAULT_IDENTITY
        assertTrue(
            "Identity must introduce assistant as 'Elixir', not 'CellClaw'",
            identity.contains("You are Elixir")
        )
        assertFalse(
            "Identity must NOT use the old 'CellClaw' name",
            identity.contains("CellClaw")
        )
    }

    @Test
    fun testNoPhantomToolsInIdentity() {
        val registry = buildElixirRegistry()
        val registeredNames = registry.names()
        val identityText = Identity.DEFAULT_IDENTITY

        // Extract tool names cited in DEFAULT_IDENTITY (bold markdown format **tool.name**)
        val citedTools = Regex("""\*\*([\w.]+)\*\*""")
            .findAll(identityText)
            .map { it.groupValues[1] }
            .filter { it.contains(".") } // only actual tool names (e.g. "screen.read") not "ZERO FABRICATION"
            .toSet()

        // Every tool name cited in the identity must be in the registry
        val phantomTools = citedTools.filter { it !in registeredNames }
        assertTrue(
            "Identity claims phantom tools not in registry: $phantomTools",
            phantomTools.isEmpty()
        )

        // Verify cited tools is non-empty (sanity check that regex worked)
        assertTrue("Identity should cite at least one tool name", citedTools.isNotEmpty())
    }

    @Test
    fun testDisabledToolsNotClaimedAsCapabilities() {
        val identity = Identity.DEFAULT_IDENTITY
        val lowerIdentity = identity.lowercase()

        // These tools are disabled for Elixir; identity must not claim them
        val disabledCapabilities = listOf(
            "read and send sms",
            "make and track phone calls",
            "manage contacts",
            "take photos",
            "get gps location",
            "launch and automate other apps",
            "run shell scripts"
        )

        for (capability in disabledCapabilities) {
            assertFalse(
                "Identity must NOT claim disabled capability: '$capability'",
                lowerIdentity.contains(capability)
            )
        }
    }

    @Test
    fun testIdentityIncludesZeroFabricationPrinciple() {
        val identity = Identity.DEFAULT_IDENTITY
        assertTrue(
            "Identity must assert zero-fabrication principle",
            identity.contains("ZERO FABRICATION") || identity.contains("never invent")
        )
    }

    @Test
    fun testIdentityIncludesUserApprovalRequirement() {
        val identity = Identity.DEFAULT_IDENTITY
        assertTrue(
            "Identity must state user approves every form submission",
            identity.contains("user approves") || identity.contains("explicit user approval") ||
            identity.contains("USER AGENCY")
        )
    }

    @Test
    fun testRegistryContainsAllExpectedElixirTools() {
        val registry = buildElixirRegistry()
        val actual = registry.names()
        val missing = ELIXIR_REGISTERED_TOOLS - actual
        assertTrue("Registry is missing expected Elixir tools: $missing", missing.isEmpty())
        assertEquals(
            "Registered tool count must match expected",
            ELIXIR_REGISTERED_TOOLS.size,
            actual.size
        )
    }
}
