package com.cellclaw.form

import java.util.Locale

enum class FieldCategory {
    FULL_NAME,
    EMAIL,
    PHONE,
    INSTITUTION,
    DEGREE,
    BRANCH,
    GRADUATION_YEAR,
    CGPA,
    MARKS,
    SKILLS,
    LINKS,
    YES_NO,
    DROPDOWN,
    RADIO,
    CHECKBOX,
    FILE_UPLOAD,
    FREE_TEXT,
    UNKNOWN
}

data class AccessibilityNodeData(
    val text: String? = null,
    val contentDescription: String? = null,
    val hintText: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val roleDescription: String? = null,
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isCheckable: Boolean = false,
    val surroundingText: String? = null
) {
    val combinedLabel: String
        get() = listOfNotNull(text, contentDescription, hintText, surroundingText)
            .joinToString(" ")
            .trim()
}

data class FieldClassification(
    val category: FieldCategory,
    val confidence: Float,
    val matchedPattern: String? = null,
    val isRequired: Boolean = false
)

class FieldClassifier {

    fun classify(node: AccessibilityNodeData): FieldClassification {
        val label = node.combinedLabel.lowercase(Locale.ROOT)
        val role = (node.roleDescription ?: "").lowercase(Locale.ROOT)
        val className = node.className ?: ""

        val isRequired = label.contains("*") || label.contains("(required)") || label.contains("required")

        // 1. File Upload (highest priority to avoid mixing with 'resume' in text)
        if (role.contains("file upload") ||
            label.contains("upload resume") ||
            label.contains("attach resume") ||
            label.contains("upload cv") ||
            (label.contains("upload") && label.contains("file")) ||
            label.contains("add file")
        ) {
            return FieldClassification(FieldCategory.FILE_UPLOAD, 0.95f, "file_upload", isRequired)
        }

        // 2. Email
        if (label.contains("email") || label.contains("e-mail") || label.contains("mail id")) {
            return FieldClassification(FieldCategory.EMAIL, 0.95f, "email", isRequired)
        }

        // 3. Phone
        if (label.contains("phone") || label.contains("mobile") || label.contains("contact number") || label.contains("whatsapp")) {
            return FieldClassification(FieldCategory.PHONE, 0.95f, "phone", isRequired)
        }

        // 4. Full Name (guard against "college name", "company name", "file name")
        val isName = (label.contains("full name") || label.contains("candidate name") || label.contains("applicant name") ||
            (label.contains("name") && !label.contains("college") && !label.contains("university") &&
             !label.contains("company") && !label.contains("file") && !label.contains("father")))
        if (isName) {
            return FieldClassification(FieldCategory.FULL_NAME, 0.90f, "full_name", isRequired)
        }

        // 5. College / Institution
        if (label.contains("college") || label.contains("university") || label.contains("institution") || label.contains("campus")) {
            return FieldClassification(FieldCategory.INSTITUTION, 0.90f, "institution", isRequired)
        }

        // 6. CGPA / GPA
        if (label.contains("cgpa") || label.contains("gpa") || label.contains("pointer")) {
            return FieldClassification(FieldCategory.CGPA, 0.95f, "cgpa", isRequired)
        }

        // 7. Marks / 10th / 12th / Percentage
        if (label.contains("10th") || label.contains("12th") || label.contains("percentage") || label.contains("marks") || label.contains("aggregate")) {
            return FieldClassification(FieldCategory.MARKS, 0.90f, "marks", isRequired)
        }

        // 8. Graduation Year / Passing Year
        if (label.contains("graduation year") || label.contains("passing year") || label.contains("year of graduation") ||
            label.contains("year of passing") || label.contains("passout year") || (label.contains("year") && label.contains("grad"))
        ) {
            return FieldClassification(FieldCategory.GRADUATION_YEAR, 0.90f, "graduation_year", isRequired)
        }

        // 9. Degree
        if (label.contains("degree") || label.contains("qualification") || label.contains("course") || label.contains("program")) {
            return FieldClassification(FieldCategory.DEGREE, 0.90f, "degree", isRequired)
        }

        // 10. Branch / Major
        if (label.contains("branch") || label.contains("specialization") || label.contains("major") || label.contains("discipline") || label.contains("stream")) {
            return FieldClassification(FieldCategory.BRANCH, 0.90f, "branch", isRequired)
        }

        // 11. Skills
        if (label.contains("skills") || label.contains("technologies") || label.contains("tech stack") || label.contains("programming languages")) {
            return FieldClassification(FieldCategory.SKILLS, 0.90f, "skills", isRequired)
        }

        // 12. Links
        if (label.contains("linkedin") || label.contains("github") || label.contains("portfolio") || label.contains("website") || label.contains("profile url")) {
            return FieldClassification(FieldCategory.LINKS, 0.90f, "links", isRequired)
        }

        // 13. Yes / No Question
        if (label.contains("willing to relocate") || label.contains("are you willing") || label.contains("yes/no") ||
            label.contains("do you have") || label.contains("have you ever") || label.contains("any active backlog")
        ) {
            return FieldClassification(FieldCategory.YES_NO, 0.85f, "yes_no", isRequired)
        }

        // 14. Free Text Questions
        if (label.contains("why") || label.contains("describe") || label.contains("tell us about") ||
            label.contains("statement of purpose") || label.contains("cover letter") || label.contains("additional comments") ||
            (node.isEditable && label.contains("reason"))
        ) {
            return FieldClassification(FieldCategory.FREE_TEXT, 0.85f, "free_text", isRequired)
        }

        // 15. Widget-type specific fallbacks
        if (className.endsWith("RadioButton") || role == "radio") {
            return FieldClassification(FieldCategory.RADIO, 0.80f, "widget_radio", isRequired)
        }

        if (className.endsWith("CheckBox") || role == "checkbox" || node.isCheckable) {
            return FieldClassification(FieldCategory.CHECKBOX, 0.80f, "widget_checkbox", isRequired)
        }

        if (className.endsWith("Spinner") || role == "listbox" || role.contains("dropdown")) {
            return FieldClassification(FieldCategory.DROPDOWN, 0.80f, "widget_dropdown", isRequired)
        }

        // 16. Fallback: Unknown
        return FieldClassification(FieldCategory.UNKNOWN, 0.0f, null, isRequired)
    }
}
