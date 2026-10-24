package com.cellclaw.resume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeDiffTest {

    private val differ = ResumeDiffer()

    private val masterResume = ResumeContent(
        name = "Jane Doe",
        email = "jane@example.com",
        phone = "+919876543210",
        sections = listOf(
            ResumeSection(
                title = "Skills",
                lines = listOf(
                    ResumeLine("l1", "Kotlin", "fact-skill-1"),
                    ResumeLine("l2", "Android", "fact-skill-2"),
                    ResumeLine("l3", "Git", "fact-skill-3")
                )
            ),
            ResumeSection(
                title = "Projects",
                lines = listOf(
                    ResumeLine("l4", "Placement Assistant", "fact-proj-1"),
                    ResumeLine("l5", "Personal Finance App", "fact-proj-2")
                )
            )
        )
    )

    @Test
    fun diff_identicalResumes_returnsNoMaterialChange() {
        val tailored = masterResume.copy()
        val result = differ.diff(masterResume, tailored)

        assertFalse(result.isMaterialChange)
        assertFalse(result.requiresUserApproval)
        assertTrue(result.entries.isEmpty())
        assertTrue(result.summary.contains("identically"))

        val tagged = differ.attachDiffStatus(masterResume, tailored)
        assertFalse(tagged.isMaterialChangeFromMaster)
    }

    @Test
    fun diff_reorderedLines_detectsReorderAndRequiresApproval() {
        val tailored = masterResume.copy(
            sections = listOf(
                ResumeSection(
                    title = "Skills",
                    lines = listOf(
                        ResumeLine("l2", "Android", "fact-skill-2"),
                        ResumeLine("l1", "Kotlin", "fact-skill-1"),
                        ResumeLine("l3", "Git", "fact-skill-3")
                    )
                ),
                masterResume.sections[1]
            )
        )

        val result = differ.diff(masterResume, tailored)

        assertTrue(result.isMaterialChange)
        assertTrue(result.requiresUserApproval)
        assertEquals(1, result.entries.size)
        assertEquals(DiffType.REORDERED, result.entries[0].type)
        assertEquals("Skills", result.entries[0].section)

        val tagged = differ.attachDiffStatus(masterResume, tailored)
        assertTrue(tagged.isMaterialChangeFromMaster)
    }

    @Test
    fun diff_addedAndRemovedLines_detectsChanges() {
        val tailored = masterResume.copy(
            sections = listOf(
                ResumeSection(
                    title = "Skills",
                    lines = listOf(
                        ResumeLine("l1", "Kotlin", "fact-skill-1"),
                        ResumeLine("l6", "Jetpack Compose", "fact-skill-4") // added, Git removed
                    )
                ),
                masterResume.sections[1]
            )
        )

        val result = differ.diff(masterResume, tailored)

        assertTrue(result.isMaterialChange)
        assertTrue(result.requiresUserApproval)
        assertTrue(result.entries.any { it.type == DiffType.ADDED && it.newText == "Jetpack Compose" })
        assertTrue(result.entries.any { it.type == DiffType.REMOVED && it.oldText == "Android" })
        assertTrue(result.entries.any { it.type == DiffType.REMOVED && it.oldText == "Git" })

        val tagged = differ.attachDiffStatus(masterResume, tailored)
        assertTrue(tagged.isMaterialChangeFromMaster)
    }

    @Test
    fun diff_addedOrRemovedSection_detectsSectionDifference() {
        val tailored = masterResume.copy(
            sections = masterResume.sections + ResumeSection(
                title = "Certifications",
                lines = listOf(
                    ResumeLine("l7", "Associate Android Developer", "fact-cert-1")
                )
            )
        )

        val result = differ.diff(masterResume, tailored)

        assertTrue(result.isMaterialChange)
        assertTrue(result.requiresUserApproval)
        val certEntry = result.entries.firstOrNull { it.section == "Certifications" }
        assertEquals(DiffType.ADDED, certEntry?.type)
        assertEquals("Associate Android Developer", certEntry?.newText)
    }
}
