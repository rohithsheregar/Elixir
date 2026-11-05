package com.cellclaw.ui

import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.ProfileFact
import com.cellclaw.profile.ProfileFactSource
import com.cellclaw.ui.screens.ProfileFactCategory
import com.cellclaw.ui.screens.categorizeProfileFact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class ProfileScreenUiTest {

    private val timestamp = Instant.parse("2026-01-01T00:00:00Z")

    private fun createFact(field: String, value: String, id: String = "$field-id") = ProfileFact(
        id = id,
        field = field,
        value = value,
        source = ProfileFactSource.USER_ENTERED,
        confirmed = true,
        updatedAt = timestamp
    )

    private fun findSourceFile(path: String): File {
        val f1 = File(path)
        if (f1.exists()) return f1
        val stripped = path.removePrefix("app/")
        val f2 = File(stripped)
        if (f2.exists()) return f2
        val f3 = File("app", path)
        if (f3.exists()) return f3
        return f1
    }

    @Test
    fun testCategorizeProfileFactPersonal() {
        assertEquals(ProfileFactCategory.PERSONAL, categorizeProfileFact("name"))
        assertEquals(ProfileFactCategory.PERSONAL, categorizeProfileFact("email"))
        assertEquals(ProfileFactCategory.PERSONAL, categorizeProfileFact("phone"))
        assertEquals(ProfileFactCategory.PERSONAL, categorizeProfileFact("links"))
        assertEquals(ProfileFactCategory.PERSONAL, categorizeProfileFact("github"))
        assertEquals(ProfileFactCategory.PERSONAL, categorizeProfileFact("linkedin"))
        assertEquals(ProfileFactCategory.PERSONAL, categorizeProfileFact("address"))
    }

    @Test
    fun testCategorizeProfileFactAcademics() {
        assertEquals(ProfileFactCategory.ACADEMICS, categorizeProfileFact("college"))
        assertEquals(ProfileFactCategory.ACADEMICS, categorizeProfileFact("university"))
        assertEquals(ProfileFactCategory.ACADEMICS, categorizeProfileFact("degree"))
        assertEquals(ProfileFactCategory.ACADEMICS, categorizeProfileFact("branch"))
        assertEquals(ProfileFactCategory.ACADEMICS, categorizeProfileFact("cgpa"))
        assertEquals(ProfileFactCategory.ACADEMICS, categorizeProfileFact("cgpa_scale"))
        assertEquals(ProfileFactCategory.ACADEMICS, categorizeProfileFact("marks10th"))
        assertEquals(ProfileFactCategory.ACADEMICS, categorizeProfileFact("marks12th"))
        assertEquals(ProfileFactCategory.ACADEMICS, categorizeProfileFact("graduationYear"))
    }

    @Test
    fun testCategorizeProfileFactExperience() {
        assertEquals(ProfileFactCategory.EXPERIENCE, categorizeProfileFact("projects"))
        assertEquals(ProfileFactCategory.EXPERIENCE, categorizeProfileFact("internships"))
        assertEquals(ProfileFactCategory.EXPERIENCE, categorizeProfileFact("certificates"))
        assertEquals(ProfileFactCategory.EXPERIENCE, categorizeProfileFact("work_experience"))
        assertEquals(ProfileFactCategory.EXPERIENCE, categorizeProfileFact("reusableAnswers"))
    }

    @Test
    fun testCategorizeProfileFactSkills() {
        assertEquals(ProfileFactCategory.SKILLS, categorizeProfileFact("skills"))
        assertEquals(ProfileFactCategory.SKILLS, categorizeProfileFact("skill"))
        assertEquals(ProfileFactCategory.SKILLS, categorizeProfileFact("tech_stack"))
        assertEquals(ProfileFactCategory.SKILLS, categorizeProfileFact("tools"))
        assertEquals(ProfileFactCategory.SKILLS, categorizeProfileFact("languages"))
    }

    @Test
    fun testGroupingCandidateProfileIntoCategories() {
        val facts = listOf(
            createFact("name", "Jane Developer"),
            createFact("email", "jane@example.com"),
            createFact("degree", "B.Tech Computer Science"),
            createFact("cgpa", "8.9"),
            createFact("skills", "Kotlin, Jetpack Compose, Android"),
            createFact("projects", "Elixir AI Agent on Android"),
            createFact("internships", "Android Intern at TechCorp")
        )

        val grouped = facts.groupBy { categorizeProfileFact(it.field) }

        assertEquals(2, grouped[ProfileFactCategory.PERSONAL]?.size)
        assertEquals(2, grouped[ProfileFactCategory.ACADEMICS]?.size)
        assertEquals(1, grouped[ProfileFactCategory.SKILLS]?.size)
        assertEquals(2, grouped[ProfileFactCategory.EXPERIENCE]?.size)
    }

    @Test
    fun testProfileScreenSourceMatchesSpecification() {
        val file = findSourceFile("app/src/main/kotlin/com/cellclaw/ui/screens/ProfileScreen.kt")
        assertTrue("ProfileScreen.kt must exist", file.exists())
        val content = file.readText()

        // Accordion group definitions
        assertTrue("Must define ProfileFactCategory", content.contains("enum class ProfileFactCategory"))
        assertTrue("Must include PERSONAL category", content.contains("PERSONAL"))
        assertTrue("Must include ACADEMICS category", content.contains("ACADEMICS"))
        assertTrue("Must include EXPERIENCE category", content.contains("EXPERIENCE"))
        assertTrue("Must include SKILLS category", content.contains("SKILLS"))

        // Category Accordion Card component
        assertTrue("Must define CategoryAccordionCard", content.contains("fun CategoryAccordionCard"))
        assertTrue("Must use AnimatedVisibility for accordion expansion", content.contains("AnimatedVisibility(visible = isExpanded)"))
        assertTrue("Must use ArrowDropDown icon for collapse/expand indicator", content.contains("Icons.Default.ArrowDropDown"))

        // Verification of EmptyState and Motion transitions
        assertTrue("Must retain EmptyState import", content.contains("import com.cellclaw.ui.components.EmptyState"))
        assertTrue("Must retain EmptyState call", content.contains("EmptyState("))
        assertTrue("Must retain item keys for proposals", content.contains("items(uiState.proposals, key = { it.proposed.id })"))
        assertTrue("Must retain item keys for confirmed facts", content.contains("items(uiState.confirmedFacts, key = { it.id })"))
        assertTrue("Must retain animateItem modifier", content.contains("Modifier.animateItem()"))

        // Theme and dark mode compliance: zero hardcoded hex colors
        assertFalse("Must not have hardcoded Color(0xFF...) literals", content.contains("Color(0xFF"))
        assertTrue("Must use MaterialTheme.elixirColors", content.contains("MaterialTheme.elixirColors"))
    }
}

