package com.cellclaw.form

import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.ProfileFact
import com.cellclaw.profile.ProfileFactSource
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class FieldFillerTest {

    private lateinit var testProfile: CandidateProfile
    private lateinit var mockOperator: MockNodeOperator
    private lateinit var filler: FieldFiller

    class MockNodeOperator : FormNodeOperator {
        val typedValues = mutableMapOf<String, String>()
        val clickedNodes = mutableListOf<String>()
        var readBackOverride: String? = null

        override fun setNodeText(node: AccessibilityNodeData, text: String): Boolean {
            typedValues[node.combinedLabel] = text
            return true
        }

        override fun clickNode(node: AccessibilityNodeData): Boolean {
            clickedNodes.add(node.combinedLabel)
            return true
        }

        override fun readBackNodeText(node: AccessibilityNodeData): String? {
            return readBackOverride ?: typedValues[node.combinedLabel]
        }

        override fun isNodeChecked(node: AccessibilityNodeData): Boolean {
            return clickedNodes.contains(node.combinedLabel)
        }
    }

    @Before
    fun setUp() {
        testProfile = CandidateProfile(
            name = ProfileFact("fact-name", "name", "Rohith Sheregar", ProfileFactSource.USER_ENTERED, confirmed = true, updatedAt = Instant.now()),
            email = ProfileFact("fact-email", "email", "rohith@example.com", ProfileFactSource.USER_ENTERED, confirmed = true, updatedAt = Instant.now()),
            phone = ProfileFact("fact-phone", "phone", "+91 9876543210", ProfileFactSource.USER_ENTERED, confirmed = true, updatedAt = Instant.now()),
            college = ProfileFact("fact-college", "college", "National Institute of Technology", ProfileFactSource.USER_ENTERED, confirmed = true, updatedAt = Instant.now()),
            degree = ProfileFact("fact-degree", "degree", "B.Tech", ProfileFactSource.USER_ENTERED, confirmed = true, updatedAt = Instant.now()),
            branch = ProfileFact("fact-branch", "branch", "Computer Science and Engineering", ProfileFactSource.USER_ENTERED, confirmed = true, updatedAt = Instant.now()),
            cgpa = ProfileFact("fact-cgpa", "cgpa", "9.15", ProfileFactSource.USER_ENTERED, confirmed = true, updatedAt = Instant.now()),
            graduationYear = ProfileFact("fact-grad", "graduationYear", "2026", ProfileFactSource.USER_ENTERED, confirmed = true, updatedAt = Instant.now())
        )
        mockOperator = MockNodeOperator()
        filler = FieldFiller(operator = mockOperator)
    }

    @Test
    fun testFillNameFromConfirmedFactWithReadBack() {
        val node = AccessibilityNodeData(text = "Full Name *", className = "android.widget.EditText")
        val result = filler.fillField(node, testProfile)

        assertTrue("Expected Success for confirmed name but got $result", result is FillResult.Success)
        val success = result as FillResult.Success
        assertEquals("Rohith Sheregar", success.filledValue)
        assertTrue(success.verifiedReadBack)
        assertEquals("fact-name", success.factId)
        assertEquals("Rohith Sheregar", mockOperator.typedValues["Full Name *"])
    }

    @Test
    fun testRefuseUnconfirmedFact() {
        val unconfirmedProfile = testProfile.copy(
            email = ProfileFact("fact-email-unconf", "email", "unconfirmed@example.com", ProfileFactSource.IMPORTED_FROM_RESUME, confirmed = false, updatedAt = Instant.now())
        )
        val node = AccessibilityNodeData(text = "Email Address *", className = "android.widget.EditText")
        val result = filler.fillField(node, unconfirmedProfile)

        assertTrue("Expected NeedsUserInput for unconfirmed email but got $result", result is FillResult.NeedsUserInput)
        val needsInput = result as FillResult.NeedsUserInput
        assertTrue(needsInput.reason.contains("unconfirmed", ignoreCase = true) || needsInput.reason.contains("missing", ignoreCase = true))
        assertNull(mockOperator.typedValues["Email Address *"])
    }

    @Test
    fun testReadBackFailureDetected() {
        // Force the mock read-back to return something different (e.g. keyboard lag or truncated input)
        mockOperator.readBackOverride = "Rohith"

        val node = AccessibilityNodeData(text = "Full Name *", className = "android.widget.EditText")
        val result = filler.fillField(node, testProfile)

        assertTrue("Expected ReadBackFailed but got $result", result is FillResult.ReadBackFailed)
        val failed = result as FillResult.ReadBackFailed
        assertEquals("Rohith Sheregar", failed.expectedValue)
        assertEquals("Rohith", failed.actualValue)
    }

    @Test
    fun testDropdownExactMatch() {
        val node = AccessibilityNodeData(text = "Degree *", className = "android.widget.Spinner")
        val availableOptions = listOf("B.Tech", "B.E.", "M.Tech", "MCA")

        val result = filler.fillDropdown(node, availableOptions, testProfile)
        assertTrue("Expected Success for exact matching degree dropdown", result is FillResult.Success)
        val success = result as FillResult.Success
        assertEquals("B.Tech", success.filledValue)
        assertTrue(mockOperator.clickedNodes.contains("B.Tech"))
    }

    @Test
    fun testDropdownNoMatchRoutesToNeedsInput() {
        val node = AccessibilityNodeData(text = "Degree *", className = "android.widget.Spinner")
        val availableOptions = listOf("Diploma", "MBA", "Ph.D.") // B.Tech not present

        val result = filler.fillDropdown(node, availableOptions, testProfile)
        assertTrue("Expected NeedsUserInput when dropdown options do not match", result is FillResult.NeedsUserInput)
    }

    @Test
    fun testFreeTextRequiresExplicitApproval() {
        val node = AccessibilityNodeData(
            text = "Why do you want to work with us?",
            className = "android.widget.EditText",
            isEditable = true
        )
        val result = filler.fillField(node, testProfile)

        assertTrue("Expected NeedsUserApproval for free-text field", result is FillResult.NeedsUserApproval)
        val approval = result as FillResult.NeedsUserApproval
        assertTrue(approval.reason.contains("approval", ignoreCase = true))
        // Verify it was NOT typed into the field automatically
        assertNull(mockOperator.typedValues[node.combinedLabel])
    }

    @Test
    fun testUnknownFieldGoesToNeedsInput() {
        val unknownNode = AccessibilityNodeData(
            text = "Custom Questionnaire Field X",
            className = "android.widget.EditText"
        )
        val result = filler.fillField(unknownNode, testProfile)

        assertTrue("Expected NeedsUserInput for unknown field", result is FillResult.NeedsUserInput)
        val needsInput = result as FillResult.NeedsUserInput
        assertTrue(needsInput.reason.contains("unmatched", ignoreCase = true) || needsInput.reason.contains("unknown", ignoreCase = true))
    }

    @Test
    fun testSensitiveFieldRoutesToNeedsInput() {
        val passwordNode = AccessibilityNodeData(
            text = "Portal Account Password *",
            className = "android.widget.EditText"
        )
        val result = filler.fillField(passwordNode, testProfile)

        assertTrue("Expected NeedsUserInput for sensitive field", result is FillResult.NeedsUserInput)
        val needsInput = result as FillResult.NeedsUserInput
        assertTrue(needsInput.isSensitive)
    }
}
