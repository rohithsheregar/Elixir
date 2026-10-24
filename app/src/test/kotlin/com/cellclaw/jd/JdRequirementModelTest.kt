package com.cellclaw.jd

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JdRequirementModelTest {
    @Test fun `defaults preserve unknown JD facts`() {
        val requirement = JdRequirement()

        assertNull(requirement.company.value)
        assertEquals(0f, requirement.requiredSkills.confidence)
        assertTrue(requirement.questions.value.isNullOrEmpty())
    }

    @Test fun `fields retain typed values and their evidence`() {
        val requirement = JdRequirement(
            company = JdField("Example Labs", "Company: Example Labs", 1f),
            role = JdField("Android Engineer", "Role: Android Engineer", 1f),
            jobType = JdField(JdJobType.INTERNSHIP, "Internship opportunity", 0.9f),
            requiredSkills = JdField(listOf("Kotlin", "Android"), "Required: Kotlin, Android", 0.95f),
            passingYear = JdField(2026, "Batch: 2026", 0.8f),
            minCgpa = JdField(7.5, "Minimum CGPA: 7.5/10", 1f),
            cgpaScale = JdField(10.0, "Minimum CGPA: 7.5/10", 1f),
            deadline = JdField(LocalDate.parse("2026-03-31"), "Apply by 31 March 2026", 0.7f)
        )

        assertEquals("Example Labs", requirement.company.value)
        assertEquals(JdJobType.INTERNSHIP, requirement.jobType.value)
        assertEquals(listOf("Kotlin", "Android"), requirement.requiredSkills.value)
        assertEquals("Minimum CGPA: 7.5/10", requirement.minCgpa.sourceSnippet)
        assertEquals(LocalDate.parse("2026-03-31"), requirement.deadline.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `confidence outside zero to one is rejected`() {
        JdField("Example Labs", "Company: Example Labs", 1.1f)
    }
}
