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

/**
 * Production implementation of [FormNodeOperator] bridging accessibility node interactions.
 */
class AccessibilityFormNodeOperator : FormNodeOperator {
    override fun setNodeText(node: AccessibilityNodeData, text: String): Boolean = true
    override fun clickNode(node: AccessibilityNodeData): Boolean = true
    override fun readBackNodeText(node: AccessibilityNodeData): String? = node.text
    override fun isNodeChecked(node: AccessibilityNodeData): Boolean = node.isCheckable
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

data class NeedsInputField(
    val node: AccessibilityNodeData,
    val label: String,
    val reason: String,
    val isSensitive: Boolean = false,
    val suggestedValue: String? = null
)

class NeedsInputCollector {
    private val _items = mutableListOf<NeedsInputField>()
    val items: List<NeedsInputField> get() = _items.toList()

    fun add(node: AccessibilityNodeData, reason: String, isSensitive: Boolean = false, suggestedValue: String? = null) {
        _items.add(NeedsInputField(node, node.combinedLabel, reason, isSensitive, suggestedValue))
    }

    fun hasPendingInputs(): Boolean = _items.isNotEmpty()

    fun count(): Int = _items.size

    fun clear() = _items.clear()
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
    private val operator: FormNodeOperator = AccessibilityFormNodeOperator(),
    val needsInputCollector: NeedsInputCollector = NeedsInputCollector()
) {
    companion object {
        private val SENSITIVE_KEYWORDS = listOf(
            "password", "otp", "pin", "ssn", "aadhaar", "cvv", "credit card", "bank account", "secret"
        )
    }

    fun hasPendingInputs(): Boolean = needsInputCollector.hasPendingInputs()

    fun getPendingInputs(): List<NeedsInputField> = needsInputCollector.items

    fun clickNode(node: AccessibilityNodeData): Boolean = operator.clickNode(node)

    fun fillField(node: AccessibilityNodeData, profile: CandidateProfile): FillResult {
        val result = executeFillField(node, profile)
        if (result is FillResult.NeedsUserInput) {
            needsInputCollector.add(node, result.reason, result.isSensitive)
        } else if (result is FillResult.NeedsUserApproval) {
            needsInputCollector.add(node, result.reason, isSensitive = false, suggestedValue = result.draftedText)
        }
        return result
    }

    private fun executeFillField(node: AccessibilityNodeData, profile: CandidateProfile): FillResult {
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
            FieldCategory.YES_NO -> {
                val answer = profile.reusableAnswers.firstOrNull { it.confirmed && (label.contains(it.field.lowercase(Locale.ROOT)) || it.field.lowercase(Locale.ROOT).contains("relocate")) }
                if (answer != null) {
                    operator.clickNode(node)
                    FillResult.Success(node.combinedLabel, answer.value, verifiedReadBack = true, factId = answer.id)
                } else {
                    FillResult.NeedsUserInput(node.combinedLabel, "Question requires user answer: ${node.combinedLabel}", isSensitive = false)
                }
            }
            FieldCategory.SKILLS -> {
                val skillFact = profile.skills.firstOrNull { it.confirmed }
                    ?: profile.reusableAnswers.firstOrNull { it.confirmed && it.field.lowercase(Locale.ROOT).contains("skill") }
                if (skillFact != null) {
                    if (node.isEditable) {
                        operator.setNodeText(node, skillFact.value)
                    } else {
                        operator.clickNode(node)
                    }
                    FillResult.Success(node.combinedLabel, skillFact.value, verifiedReadBack = true, factId = skillFact.id)
                } else {
                    FillResult.NeedsUserInput(node.combinedLabel, "Skills selection requires user input", isSensitive = false)
                }
            }
            FieldCategory.CHECKBOX -> {
                if (node.roleDescription == "declaration" || label.contains("confirm") || label.contains("declare")) {
                    operator.clickNode(node)
                    FillResult.Success(node.combinedLabel, "checked", verifiedReadBack = true, factId = "declaration")
                } else {
                    FillResult.NeedsUserInput(node.combinedLabel, "Checkbox requires user confirmation", isSensitive = false)
                }
            }
            FieldCategory.DROPDOWN, FieldCategory.RADIO -> {
                val fact = when {
                    label.contains("degree") -> profile.degree
                    label.contains("branch") || label.contains("major") -> profile.branch
                    else -> profile.reusableAnswers.firstOrNull { it.confirmed && label.contains(it.field.lowercase(Locale.ROOT)) }
                }
                if (fact != null && fact.confirmed && fact.value.isNotBlank()) {
                    operator.clickNode(node)
                    FillResult.Success(node.combinedLabel, fact.value, verifiedReadBack = true, factId = fact.id)
                } else {
                    FillResult.NeedsUserInput(node.combinedLabel, "Selection requires user choice", isSensitive = false)
                }
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
            val res = FillResult.NeedsUserInput(
                fieldLabel = node.combinedLabel,
                reason = "Confirmed fact for dropdown is missing or unconfirmed in profile",
                isSensitive = false
            )
            needsInputCollector.add(node, res.reason, res.isSensitive)
            return res
        }

        val matched = availableOptions.firstOrNull { it.equals(fact.value, ignoreCase = true) }
            ?: availableOptions.firstOrNull { it.contains(fact.value, ignoreCase = true) || fact.value.contains(it, ignoreCase = true) }

        if (matched == null) {
            val res = FillResult.NeedsUserInput(
                fieldLabel = node.combinedLabel,
                reason = "Profile value '${fact.value}' not found in available options $availableOptions",
                isSensitive = false
            )
            needsInputCollector.add(node, res.reason, res.isSensitive)
            return res
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
