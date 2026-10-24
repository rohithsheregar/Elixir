package com.cellclaw.resume

import com.cellclaw.jd.JdField
import com.cellclaw.jd.JdRequirement
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.ProfileFact
import com.cellclaw.profile.ProfileFactSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ResumeBuilderTest {

    private val builder = ResumeBuilder()
    private val timestamp = Instant.parse("2026-01-01T00:00:00Z")

    private fun fact(field: String, value: String, confirmed: Boolean = true, id: String = "$field-1") = ProfileFact(
        id = id,
        field = field,
        value = value,
        source = ProfileFactSource.USER_ENTERED,
        confirmed = confirmed,
        updatedAt = timestamp
    )

    private fun <T> field(value: T) = JdField(value = value, sourceSnippet = value.toString(), confidence = 1.0f)

    @Test
    fun `build constructs resume and verifies evidence integrity`() {
        val profile = CandidateProfile(
            name = fact("name", "Asha Test", confirmed = true, id = "name-fact"),
            email = fact("email", "asha@example.invalid", confirmed = true, id = "email-fact"),
            phone = fact("phone", "+91 9876543210", confirmed = true, id = "phone-fact"),
            degree = fact("degree", "B.Tech", confirmed = true, id = "degree-fact"),
            branch = fact("branch", "Computer Science", confirmed = true, id = "branch-fact"),
            cgpa = fact("cgpa", "8.9", confirmed = true, id = "cgpa-fact"),
            skills = listOf(
                fact("skill", "Kotlin", confirmed = true, id = "skill-kotlin"),
                fact("skill", "Jetpack Compose", confirmed = true, id = "skill-compose")
            ),
            projects = listOf(
                fact("project", "Android chat app built with Kotlin and Jetpack Compose", confirmed = true, id = "proj-chat")
            )
        )

        val jd = JdRequirement(
            company = field("MobileCorp"),
            role = field("Senior Android Engineer"),
            requiredSkills = field(listOf("Kotlin", "Jetpack Compose"))
        )

        val resume = builder.build(profile, jd)

        assertEquals("Asha Test", resume.name)
        assertEquals("asha@example.invalid", resume.email)
        assertEquals("+91 9876543210", resume.phone)
        assertTrue(resume.verifyEvidenceIntegrity())

        // Check evidence map contains mappings
        val evidence = resume.evidenceMap
        assertEquals("degree-fact", evidence.factIdForLine("edu-degree"))
        assertEquals("cgpa-fact", evidence.factIdForLine("edu-cgpa"))
        assertEquals("skill-compose", evidence.factIdForLine("skill-0"))
        assertEquals("skill-kotlin", evidence.factIdForLine("skill-1"))
        assertEquals("proj-chat", evidence.factIdForLine("project-0"))
    }

    @Test
    fun `unconfirmed facts are strictly excluded from resume`() {
        val profile = CandidateProfile(
            name = fact("name", "Asha Test", confirmed = true, id = "name-fact"),
            email = fact("email", "asha@example.invalid", confirmed = true, id = "email-fact"),
            skills = listOf(
                fact("skill", "Kotlin", confirmed = true, id = "skill-kotlin"),
                fact("skill", "Rust", confirmed = false, id = "skill-rust")
            ),
            projects = listOf(
                fact("project", "Confirmed project", confirmed = true, id = "proj-1"),
                fact("project", "Fabricated or unconfirmed project", confirmed = false, id = "proj-unconfirmed")
            )
        )

        val jd = JdRequirement(
            company = field("TestCorp"),
            role = field("Engineer")
        )

        val resume = builder.build(profile, jd)

        val allResumeTexts = resume.sections.flatMap { it.lines }.map { it.text }
        assertTrue(allResumeTexts.contains("Kotlin"))
        assertFalse(allResumeTexts.contains("Rust"))
        assertTrue(allResumeTexts.contains("Confirmed project"))
        assertFalse(allResumeTexts.contains("Fabricated or unconfirmed project"))

        // Unconfirmed IDs must not exist in evidence map
        assertFalse(resume.evidenceMap.lineToFactId.containsValue("skill-rust"))
        assertFalse(resume.evidenceMap.lineToFactId.containsValue("proj-unconfirmed"))
        assertTrue(resume.verifyEvidenceIntegrity())
    }

    @Test
    fun `skills are ranked - required first, preferred second, other third`() {
        val profile = CandidateProfile(
            name = fact("name", "Asha Test", confirmed = true, id = "name-fact"),
            email = fact("email", "asha@example.invalid", confirmed = true, id = "email-fact"),
            skills = listOf(
                fact("skill", "C++", confirmed = true, id = "skill-cpp"),
                fact("skill", "Docker", confirmed = true, id = "skill-docker"),
                fact("skill", "Kotlin", confirmed = true, id = "skill-kotlin"),
                fact("skill", "Python", confirmed = true, id = "skill-python")
            )
        )

        val jd = JdRequirement(
            company = field("AndroidTech"),
            role = field("Dev"),
            requiredSkills = field(listOf("Kotlin")),
            preferredSkills = field(listOf("Docker"))
        )

        val resume = builder.build(profile, jd)
        val skillSection = resume.sections.find { it.title == "Technical Skills" }
        org.junit.Assert.assertNotNull(skillSection)

        val orderedSkills = skillSection!!.lines.map { it.text }
        // Kotlin is required -> 1st
        // Docker is preferred -> 2nd
        // C++ and Python are other -> alphabetical
        assertEquals("Kotlin", orderedSkills[0])
        assertEquals("Docker", orderedSkills[1])
        assertEquals("C++", orderedSkills[2])
        assertEquals("Python", orderedSkills[3])
    }

    @Test
    fun `projects are ranked by overlap with JD skills`() {
        val profile = CandidateProfile(
            name = fact("name", "Asha Test", confirmed = true, id = "name-fact"),
            email = fact("email", "asha@example.invalid", confirmed = true, id = "email-fact"),
            projects = listOf(
                fact("project", "Simple calculator in C", confirmed = true, id = "proj-c"),
                fact("project", "Full stack Kotlin Android app with Docker backend", confirmed = true, id = "proj-fullstack"),
                fact("project", "Android camera app in Java", confirmed = true, id = "proj-android")
            )
        )

        val jd = JdRequirement(
            company = field("TechCorp"),
            role = field("Android Dev"),
            requiredSkills = field(listOf("Kotlin", "Android")),
            preferredSkills = field(listOf("Docker"))
        )

        val resume = builder.build(profile, jd)
        val projectSection = resume.sections.find { it.title == "Projects" }
        org.junit.Assert.assertNotNull(projectSection)

        // proj-fullstack mentions Kotlin, Android, Docker (3 matches) -> should be first!
        // proj-android mentions Android (1 match) -> should be second!
        // proj-c mentions neither (0 matches) -> should be last!
        assertEquals("Full stack Kotlin Android app with Docker backend", projectSection!!.lines[0].text)
        assertEquals("Android camera app in Java", projectSection.lines[1].text)
        assertEquals("Simple calculator in C", projectSection.lines[2].text)
    }

    @Test
    fun `serialization round trip preserves evidence map and section hierarchy`() {
        val original = ResumeContent(
            name = "Asha Test",
            email = "asha@example.invalid",
            sections = listOf(
                ResumeSection(
                    title = "Skills",
                    lines = listOf(ResumeLine("line-1", "Kotlin", "fact-1"))
                )
            ),
            evidenceMap = EvidenceMap(mapOf("line-1" to "fact-1"))
        )

        val jsonStr = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<ResumeContent>(jsonStr)
        assertEquals(original, deserialized)
        assertTrue(deserialized.verifyEvidenceIntegrity())
    }
}
