package com.cellclaw.form

import org.junit.Assert.*
import org.junit.Test

class FieldClassifierTest {

    private val classifier = FieldClassifier()

    @Test
    fun testClassifyFullName() {
        val nodes = listOf(
            AccessibilityNodeData(text = "Full Name *", className = "android.widget.EditText"),
            AccessibilityNodeData(contentDescription = "Candidate Name", className = "android.widget.EditText"),
            AccessibilityNodeData(hintText = "Enter your full name", className = "android.widget.EditText")
        )

        for (node in nodes) {
            val result = classifier.classify(node)
            assertEquals("Expected FULL_NAME for ${node.combinedLabel}", FieldCategory.FULL_NAME, result.category)
            assertTrue(result.confidence >= 0.8f)
        }
    }

    @Test
    fun testClassifyEmail() {
        val node = AccessibilityNodeData(
            text = "Email Address *",
            hintText = "your.email@example.com",
            className = "android.widget.EditText"
        )
        val result = classifier.classify(node)
        assertEquals(FieldCategory.EMAIL, result.category)
        assertTrue(result.isRequired)
        assertTrue(result.confidence >= 0.9f)
    }

    @Test
    fun testClassifyPhone() {
        val node = AccessibilityNodeData(
            contentDescription = "Mobile Number (10 digits) *",
            className = "android.widget.EditText"
        )
        val result = classifier.classify(node)
        assertEquals(FieldCategory.PHONE, result.category)
        assertTrue(result.isRequired)
    }

    @Test
    fun testClassifyAcademics() {
        val collegeNode = AccessibilityNodeData(text = "College / University Name *")
        val degreeNode = AccessibilityNodeData(text = "Degree *", className = "android.widget.Spinner")
        val branchNode = AccessibilityNodeData(text = "Branch / Specialization *")
        val cgpaNode = AccessibilityNodeData(text = "Current CGPA (out of 10.0) *")
        val yearNode = AccessibilityNodeData(text = "Year of Graduation *")
        val marksNode = AccessibilityNodeData(text = "10th Percentage / Marks *")

        assertEquals(FieldCategory.INSTITUTION, classifier.classify(collegeNode).category)
        assertEquals(FieldCategory.DEGREE, classifier.classify(degreeNode).category)
        assertEquals(FieldCategory.BRANCH, classifier.classify(branchNode).category)
        assertEquals(FieldCategory.CGPA, classifier.classify(cgpaNode).category)
        assertEquals(FieldCategory.GRADUATION_YEAR, classifier.classify(yearNode).category)
        assertEquals(FieldCategory.MARKS, classifier.classify(marksNode).category)
    }

    @Test
    fun testClassifyFileUpload() {
        val node = AccessibilityNodeData(
            text = "Upload Resume (PDF only, max 5 MB) *",
            roleDescription = "file upload",
            className = "android.widget.Button"
        )
        val result = classifier.classify(node)
        assertEquals(FieldCategory.FILE_UPLOAD, result.category)
        assertTrue(result.isRequired)
    }

    @Test
    fun testClassifySkillsAndLinks() {
        val skillsNode = AccessibilityNodeData(text = "Key Technical Skills (comma separated)")
        val linksNode = AccessibilityNodeData(text = "LinkedIn Profile URL")

        assertEquals(FieldCategory.SKILLS, classifier.classify(skillsNode).category)
        assertEquals(FieldCategory.LINKS, classifier.classify(linksNode).category)
    }

    @Test
    fun testClassifyYesNoAndRadio() {
        val yesNoNode = AccessibilityNodeData(
            text = "Are you willing to relocate? *",
            className = "android.widget.RadioGroup"
        )
        val radioOption = AccessibilityNodeData(
            text = "Yes",
            className = "android.widget.RadioButton"
        )

        assertEquals(FieldCategory.YES_NO, classifier.classify(yesNoNode).category)
        assertEquals(FieldCategory.RADIO, classifier.classify(radioOption).category)
    }

    @Test
    fun testClassifyCheckboxAndFreeText() {
        val checkNode = AccessibilityNodeData(
            text = "I accept the terms and conditions *",
            className = "android.widget.CheckBox",
            isCheckable = true
        )
        val freeTextNode = AccessibilityNodeData(
            text = "Why do you want to join our engineering team?",
            className = "android.widget.EditText",
            isEditable = true
        )

        assertEquals(FieldCategory.CHECKBOX, classifier.classify(checkNode).category)
        assertEquals(FieldCategory.FREE_TEXT, classifier.classify(freeTextNode).category)
    }

    @Test
    fun testClassifyUnknown() {
        val unknownNode = AccessibilityNodeData(
            text = "Some random unlabeled widget 123",
            className = "android.view.View"
        )
        val result = classifier.classify(unknownNode)
        assertEquals(FieldCategory.UNKNOWN, result.category)
        assertEquals(0.0f, result.confidence, 0.001f)
    }
}
