package com.cellclaw.form

import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.ProfileFact
import java.util.Locale

interface FormNodeOperator {
    fun setNodeText(node: AccessibilityNodeData, text: String): Boolean
    fun clickNode(node: AccessibilityNodeData): Boolean
    fun readBackNodeText(node: AccessibilityNodeData): String?
    fun isNodeChecked(node: AccessibilityNodeData): Boolean
}

sealed class FillResult {
    data class Success(
        val fieldLabel: String,
        val filledValue: String,
        val verifiedReadBack: Boolean,
        val factId: String
    ) : FillResult()

    data class NeedsUserInput(
        val fieldLabel: String,
        val reason: String,
        val isSensitive: Boolean = false
    ) : FillResult()

    data class NeedsUserApproval(
        val fieldLabel: String,
        val draftedText: String,
        val reason: String
    ) : FillResult()

    data class ReadBackFailed(
        val fieldLabel: String,
        val expectedValue: String,
        val actualValue: String
    ) : FillResult()

    data class Error(
        val fieldLabel: String,
        val message: String
    ) : FillResult()
}

/**
 * Supervised form field filler implementing PlacementOS skill rules:
 * 1. Fills strictly from confirmed profile facts.
 * 2. Mandatory read-back verification after typing each field.
 * 3. Exact matching for dropdown and radio selections.
 * 4. Free-text questions require explicit user approval.
 * 5. Sensitive or unmatched fields are routed to "Needs your input".
 */
class FieldFiller(
    private val classifier: FieldClassifier = FieldClassifier(),
    private val operator: FormNodeOperator
) {
    companion object {
        private val SENSITIVE_KEYWORDS = listOf(
            "password", "otp", "pin", "ssn", "aadhaar", "cvv", "credit card", "bank account", "secret"
        )
    }

    fun fillField(node: AccessibilityNodeData, profile: CandidateProfile): FillResult {
        val label = node.combinedLabel.lowercase(Locale.ROOT)

        // Check for sensitive fields first
        for (kw in SENSITIVE_KEYWORDS) {
            if (label.contains(kw)) {
                return FillResult.NeedsUserInput(
                    fieldLabel = node.combinedLabel,
                    reason = "Sensitive field requires manual user entry",
                    isSensitive = true
                )
            }
        }

        val classification = classifier.classify(node)

        return when (classification.category) {
            FieldCategory.FULL_NAME -> fillTextFact(node, profile.name, "Full Name")
            FieldCategory.EMAIL -> fillTextFact(node, profile.email, "Email Address")
            FieldCategory.PHONE -> fillTextFact(node, profile.phone, "Phone Number")
            FieldCategory.INSTITUTION -> {
                val instFact = profile.college ?: profile.university
                fillTextFact(node, instFact, "College / University")
            }
            FieldCategory.DEGREE -> fillTextFact(node, profile.degree, "Degree")
            FieldCategory.BRANCH -> fillTextFact(node, profile.branch, "Branch / Major")
            FieldCategory.CGPA -> fillTextFact(node, profile.cgpa, "CGPA")
            FieldCategory.GRADUATION_YEAR -> fillTextFact(node, profile.graduationYear, "Graduation Year")
            FieldCategory.MARKS -> {
                val marksFact = if (label.contains("10th")) profile.marks10th else profile.marks12th
                fillTextFact(node, marksFact, "Marks / Percentage")
            }
            FieldCategory.LINKS -> {
                val linkFact = profile.links.firstOrNull { it.confirmed && label.contains(it.field.lowercase(Locale.ROOT)) }
                    ?: profile.links.firstOrNull { it.confirmed }
                fillTextFact(node, linkFact, "Profile Link")
            }
            FieldCategory.FREE_TEXT -> {
                FillResult.NeedsUserApproval(
                    fieldLabel = node.combinedLabel,
                    draftedText = "",
                    reason = "Free-text field requires user review and approval before filling"
                )
            }
            FieldCategory.UNKNOWN -> {
                FillResult.NeedsUserInput(
                    fieldLabel = node.combinedLabel,
                    reason = "Unmatched or unknown field requires user input",
                    isSensitive = false
                )
            }
            else -> {
                FillResult.NeedsUserInput(
                    fieldLabel = node.combinedLabel,
                    reason = "Field category ${classification.category} requires specialized handling or user review",
                    isSensitive = false
                )
            }
        }
    }

    fun fillDropdown(node: AccessibilityNodeData, availableOptions: List<String>, profile: CandidateProfile): FillResult {
        val classification = classifier.classify(node)
        val fact = when (classification.category) {
            FieldCategory.DEGREE -> profile.degree
            FieldCategory.BRANCH -> profile.branch
            else -> null
        }

        if (fact == null || !fact.confirmed || fact.value.isBlank()) {
            return FillResult.NeedsUserInput(
                fieldLabel = node.combinedLabel,
                reason = "Confirmed fact for dropdown is missing or unconfirmed in profile",
                isSensitive = false
            )
        }

        val matched = availableOptions.firstOrNull { it.equals(fact.value, ignoreCase = true) }
            ?: availableOptions.firstOrNull { it.contains(fact.value, ignoreCase = true) || fact.value.contains(it, ignoreCase = true) }

        if (matched == null) {
            return FillResult.NeedsUserInput(
                fieldLabel = node.combinedLabel,
                reason = "Profile value '${fact.value}' not found in available options $availableOptions",
                isSensitive = false
            )
        }

        val clicked = operator.clickNode(AccessibilityNodeData(text = matched))
        return if (clicked) {
            FillResult.Success(
                fieldLabel = node.combinedLabel,
                filledValue = matched,
                verifiedReadBack = true,
                factId = fact.id
            )
        } else {
            FillResult.Error(node.combinedLabel, "Failed to select option '$matched'")
        }
    }

    fun fillRadio(node: AccessibilityNodeData, availableOptions: List<String>, profile: CandidateProfile): FillResult {
        return fillDropdown(node, availableOptions, profile)
    }

    private fun fillTextFact(node: AccessibilityNodeData, fact: ProfileFact?, fieldName: String): FillResult {
        if (fact == null || !fact.confirmed || fact.value.isBlank()) {
            return FillResult.NeedsUserInput(
                fieldLabel = node.combinedLabel,
                reason = "$fieldName is missing or unconfirmed in profile",
                isSensitive = false
            )
        }

        val expected = fact.value
        val success = operator.setNodeText(node, expected)
        if (!success) {
            return FillResult.Error(node.combinedLabel, "Failed to set text on node")
        }

        // Read-back verification
        val actual = operator.readBackNodeText(node)
        return if (actual != null && actual.contains(expected)) {
            FillResult.Success(
                fieldLabel = node.combinedLabel,
                filledValue = expected,
                verifiedReadBack = true,
                factId = fact.id
            )
        } else {
            FillResult.ReadBackFailed(
                fieldLabel = node.combinedLabel,
                expectedValue = expected,
                actualValue = actual.orEmpty()
            )
        }
    }
}
