package com.cellclaw.agent

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AutonomyPolicyTest {

    private lateinit var policy: AutonomyPolicy

    @Before
    fun setup() {
        policy = AutonomyPolicy()
    }

    @Test
    fun `initial profile is CAUTIOUS by default`() {
        // Basic reads are AUTO under CAUTIOUS
        assertEquals(ToolApprovalPolicy.AUTO, policy.getPolicy("calendar.query"))
        assertEquals(ToolApprovalPolicy.AUTO, policy.getPolicy("file.read"))
        assertEquals(ToolApprovalPolicy.AUTO, policy.getPolicy("file.list"))
        assertEquals(ToolApprovalPolicy.AUTO, policy.getPolicy("settings.get"))
        assertEquals(ToolApprovalPolicy.AUTO, policy.getPolicy("sensor.read"))
        assertEquals(ToolApprovalPolicy.AUTO, policy.getPolicy("clipboard.read"))
        assertEquals(ToolApprovalPolicy.AUTO, policy.getPolicy("browser.search"))

        // Sensitive reads require approval (ASK) under CAUTIOUS
        assertEquals(ToolApprovalPolicy.ASK, policy.getPolicy("sms.read"))
        assertEquals(ToolApprovalPolicy.ASK, policy.getPolicy("location.get"))
        assertEquals(ToolApprovalPolicy.ASK, policy.getPolicy("phone.log"))

        // Writes and consequential actions require approval (ASK) under CAUTIOUS
        assertEquals(ToolApprovalPolicy.ASK, policy.getPolicy("file.write"))
        assertEquals(ToolApprovalPolicy.ASK, policy.getPolicy("sms.send"))
        assertEquals(ToolApprovalPolicy.ASK, policy.getPolicy("calendar.create"))
        assertEquals(ToolApprovalPolicy.ASK, policy.getPolicy("email.send"))
        assertEquals(ToolApprovalPolicy.ASK, policy.getPolicy("script.exec"))
    }

    @Test
    fun `unknown tools deny-by-default to ASK`() {
        // AG0.3 requirement: unknown tools must evaluate to ASK (deny-by-default), never AUTO
        assertEquals(ToolApprovalPolicy.ASK, policy.getPolicy("unknown.tool"))
        assertEquals(ToolApprovalPolicy.ASK, policy.getPolicy("arbitrary.plugin.command"))
        assertEquals(ToolApprovalPolicy.ASK, policy.getPolicy("unregistered_tool"))
    }

    @Test
    fun `write operations default to ASK under BALANCED profile`() {
        policy.applyProfile(PermissionProfile.BALANCED)
        assertEquals(ToolApprovalPolicy.ASK, policy.getPolicy("sms.send"))
        assertEquals(ToolApprovalPolicy.ASK, policy.getPolicy("phone.call"))
        assertEquals(ToolApprovalPolicy.ASK, policy.getPolicy("script.exec"))
        // Note: app.automate is in appControl → AUTO under BALANCED
        assertEquals(ToolApprovalPolicy.AUTO, policy.getPolicy("app.automate"))
        assertEquals(ToolApprovalPolicy.ASK, policy.getPolicy("file.write"))
    }

    @Test
    fun `read operations are AUTO under FULL_AUTO profile`() {
        policy.applyProfile(PermissionProfile.FULL_AUTO)
        assertEquals(ToolApprovalPolicy.AUTO, policy.getPolicy("sms.read"))
        assertEquals(ToolApprovalPolicy.AUTO, policy.getPolicy("contacts.search"))
        assertEquals(ToolApprovalPolicy.AUTO, policy.getPolicy("calendar.query"))
        assertEquals(ToolApprovalPolicy.AUTO, policy.getPolicy("location.get"))
        assertEquals(ToolApprovalPolicy.AUTO, policy.getPolicy("file.read"))
        assertEquals(ToolApprovalPolicy.AUTO, policy.getPolicy("settings.get"))
    }

    @Test
    fun `can override policy`() {
        policy.setPolicy("sms.send", ToolApprovalPolicy.AUTO)
        assertEquals(ToolApprovalPolicy.AUTO, policy.getPolicy("sms.send"))
    }

    @Test
    fun `can set to DENY`() {
        policy.setPolicy("script.exec", ToolApprovalPolicy.DENY)
        assertEquals(ToolApprovalPolicy.DENY, policy.getPolicy("script.exec"))
    }

    @Test
    fun `allPolicies returns snapshot`() {
        val all = policy.allPolicies()
        assertTrue(all.isNotEmpty())
        assertTrue(all.containsKey("sms.read"))
        assertTrue(all.containsKey("sms.send"))
    }
}
