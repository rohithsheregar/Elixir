package com.cellclaw.eligibility

import com.cellclaw.jd.JdField
import com.cellclaw.jd.JdRequirement
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.ProfileFact
import com.cellclaw.profile.ProfileFactSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

class EligibilityEngineTest {

    private val engine = EligibilityEngine()
    private val timestamp = Instant.parse("2026-01-01T00:00:00Z")

    private fun fact(field: String, value: String, confirmed: Boolean = true) = ProfileFact(
        id = "$field-1",
        field = field,
        value = value,
        source = ProfileFactSource.USER_ENTERED,
        confirmed = confirmed,
        updatedAt = timestamp
    )

    private fun <T> field(value: T) = JdField(value = value, sourceSnippet = value.toString(), confidence = 1.0f)

    @Test
    fun `all criteria pass results in ELIGIBLE`() {
        val profile = CandidateProfile(
            degree = fact("degree", "B.Tech"),
            branch = fact("branch", "Computer Science"),
            graduationYear = fact("graduationYear", "2026"),
            cgpa = fact("cgpa", "8.5"),
            cgpaScale = fact("cgpaScale", "10.0"),
            skills = listOf(
                fact("skill", "Kotlin"),
                fact("skill", "Android"),
                fact("skill", "Git")
            ),
            reusableAnswers = listOf(
                fact("backlogs", "0")
            )
        )

        val jd = JdRequirement(
            company = field("Acme Corp"),
            role = field("Android Engineer"),
            degree = field("B.Tech"),
            branch = field("Computer Science"),
            passingYear = field(2026),
            minCgpa = field(7.5),
            cgpaScale = field(10.0),
            backlogRule = field("No active backlogs"),
            requiredSkills = field(listOf("Kotlin", "Android")),
            preferredSkills = field(listOf("Git"))
        )

        val report = engine.evaluate(profile, jd)
        assertEquals(OverallEligibility.ELIGIBLE, report.overall)
        assertEquals(8, report.criteria.size)
        report.criteria.forEach {
            assertEquals(EligibilityStatus.PASS, it.status)
        }
    }

    @Test
    fun `preferred skill missing does not cause overall failure`() {
        val profile = CandidateProfile(
            degree = fact("degree", "B.Tech"),
            branch = fact("branch", "Computer Science"),
            graduationYear = fact("graduationYear", "2026"),
            cgpa = fact("cgpa", "8.5"),
            cgpaScale = fact("cgpaScale", "10.0"),
            skills = listOf(fact("skill", "Kotlin"))
        )

        val jd = JdRequirement(
            company = field("Acme Corp"),
            role = field("Android Engineer"),
            degree = field("B.Tech"),
            branch = field("Computer Science"),
            passingYear = field(2026),
            minCgpa = field(7.5),
            cgpaScale = field(10.0),
            requiredSkills = field(listOf("Kotlin")),
            preferredSkills = field(listOf("Docker", "Kubernetes"))
        )

        val report = engine.evaluate(profile, jd)
        assertEquals(OverallEligibility.ELIGIBLE, report.overall)

        val dockerResult = report.criteria.find { it.criterion.contains("Docker") }
        assertNotNull(dockerResult)
        assertEquals(EligibilityStatus.FAIL, dockerResult!!.status)
        assertEquals(false, dockerResult.isMandatory)
    }

    @Test
    fun `border CGPA evaluation - pass on exact and fail on strict below`() {
        val jd = JdRequirement(
            company = field("Acme Corp"),
            role = field("SDE"),
            minCgpa = field(8.0),
            cgpaScale = field(10.0)
        )

        // Exact border
        val exactProfile = CandidateProfile(
            cgpa = fact("cgpa", "8.0"),
            cgpaScale = fact("cgpaScale", "10.0")
        )
        val exactReport = engine.evaluate(exactProfile, jd)
        assertEquals(OverallEligibility.ELIGIBLE, exactReport.overall)
        assertEquals(EligibilityStatus.PASS, exactReport.criteria.single().status)

        // Just below border
        val belowProfile = CandidateProfile(
            cgpa = fact("cgpa", "7.99"),
            cgpaScale = fact("cgpaScale", "10.0")
        )
        val belowReport = engine.evaluate(belowProfile, jd)
        assertEquals(OverallEligibility.NOT_ELIGIBLE, belowReport.overall)
        assertEquals(EligibilityStatus.FAIL, belowReport.criteria.single().status)

        // Above border
        val aboveProfile = CandidateProfile(
            cgpa = fact("cgpa", "8.01"),
            cgpaScale = fact("cgpaScale", "10.0")
        )
        val aboveReport = engine.evaluate(aboveProfile, jd)
        assertEquals(OverallEligibility.ELIGIBLE, aboveReport.overall)
        assertEquals(EligibilityStatus.PASS, aboveReport.criteria.single().status)
    }

    @Test
    fun `percentage border evaluation`() {
        val jd = JdRequirement(
            company = field("Acme Corp"),
            role = field("SDE"),
            minCgpa = field(75.0),
            cgpaScale = field(100.0)
        )

        val passProfile = CandidateProfile(marks12th = fact("marks12th", "75.0%"))
        assertEquals(OverallEligibility.ELIGIBLE, engine.evaluate(passProfile, jd).overall)

        val failProfile = CandidateProfile(marks12th = fact("marks12th", "74.9%"))
        assertEquals(OverallEligibility.NOT_ELIGIBLE, engine.evaluate(failProfile, jd).overall)
    }

    @Test
    fun `never guess CGPA and percentage equivalence`() {
        // JD requires CGPA, candidate only has percentage
        val jdCgpa = JdRequirement(company = field("Acme"), role = field("SDE"), minCgpa = field(8.0), cgpaScale = field(10.0))
        val profilePercentage = CandidateProfile(marks12th = fact("marks12th", "92%"))
        val report1 = engine.evaluate(profilePercentage, jdCgpa)
        assertEquals(OverallEligibility.UNKNOWN, report1.overall)
        assertEquals(EligibilityStatus.UNKNOWN, report1.criteria.single().status)
        org.junit.Assert.assertTrue(report1.criteria.single().reason.contains("Conversion cannot be guessed"))

        // JD requires percentage, candidate only has CGPA
        val jdPercentage = JdRequirement(company = field("Acme"), role = field("SDE"), minCgpa = field(80.0), cgpaScale = field(100.0))
        val profileCgpa = CandidateProfile(cgpa = fact("cgpa", "9.2"))
        val report2 = engine.evaluate(profileCgpa, jdPercentage)
        assertEquals(OverallEligibility.UNKNOWN, report2.overall)
        assertEquals(EligibilityStatus.UNKNOWN, report2.criteria.single().status)
        org.junit.Assert.assertTrue(report2.criteria.single().reason.contains("Conversion cannot be guessed"))
    }

    @Test
    fun `never guess CGPA scale conversions`() {
        val jd = JdRequirement(company = field("Acme"), role = field("SDE"), minCgpa = field(3.5), cgpaScale = field(4.0))
        val profile = CandidateProfile(cgpa = fact("cgpa", "8.5"), cgpaScale = fact("cgpaScale", "10.0"))
        val report = engine.evaluate(profile, jd)
        assertEquals(OverallEligibility.UNKNOWN, report.overall)
        assertEquals(EligibilityStatus.UNKNOWN, report.criteria.single().status)
        org.junit.Assert.assertTrue(report.criteria.single().reason.contains("scale mismatch"))
    }

    @Test
    fun `never guess degree or branch equivalences`() {
        // Degree mismatch: BE vs BTech
        val jdDegree = JdRequirement(company = field("Acme"), role = field("SDE"), degree = field("B.Tech"))
        val profileBe = CandidateProfile(degree = fact("degree", "B.E."))
        val reportDegree = engine.evaluate(profileBe, jdDegree)
        assertEquals(OverallEligibility.UNKNOWN, reportDegree.overall)
        assertEquals(EligibilityStatus.UNKNOWN, reportDegree.criteria.single().status)
        org.junit.Assert.assertTrue(reportDegree.criteria.single().reason.contains("Degree equivalence"))

        // Branch mismatch: Information Science vs Computer Science
        val jdBranch = JdRequirement(company = field("Acme"), role = field("SDE"), branch = field("Computer Science"))
        val profileIs = CandidateProfile(branch = fact("branch", "Information Science"))
        val reportBranch = engine.evaluate(profileIs, jdBranch)
        assertEquals(OverallEligibility.UNKNOWN, reportBranch.overall)
        assertEquals(EligibilityStatus.UNKNOWN, reportBranch.criteria.single().status)
        org.junit.Assert.assertTrue(reportBranch.criteria.single().reason.contains("Branch equivalence"))
    }

    @Test
    fun `unconfirmed facts in profile result in UNKNOWN`() {
        val profile = CandidateProfile(
            degree = fact("degree", "B.Tech", confirmed = false),
            branch = fact("branch", "Computer Science", confirmed = false),
            graduationYear = fact("graduationYear", "2026", confirmed = false),
            cgpa = fact("cgpa", "9.0", confirmed = false)
        )

        val jd = JdRequirement(
            company = field("Acme"),
            role = field("SDE"),
            degree = field("B.Tech"),
            branch = field("Computer Science"),
            passingYear = field(2026),
            minCgpa = field(7.5),
            cgpaScale = field(10.0)
        )

        val report = engine.evaluate(profile, jd)
        assertEquals(OverallEligibility.UNKNOWN, report.overall)
        report.criteria.forEach {
            assertEquals(EligibilityStatus.UNKNOWN, it.status)
        }
    }

    @Test
    fun `backlog evaluation`() {
        val jd = JdRequirement(company = field("Acme"), role = field("SDE"), backlogRule = field("0 active backlogs"))

        // Missing backlog fact -> UNKNOWN
        val noBacklogProfile = CandidateProfile()
        assertEquals(OverallEligibility.UNKNOWN, engine.evaluate(noBacklogProfile, jd).overall)

        // 0 backlogs -> PASS
        val zeroBacklogProfile = CandidateProfile(
            reusableAnswers = listOf(fact("backlogs", "0 active backlogs"))
        )
        assertEquals(OverallEligibility.ELIGIBLE, engine.evaluate(zeroBacklogProfile, jd).overall)

        // 1 backlog -> FAIL
        val oneBacklogProfile = CandidateProfile(
            reusableAnswers = listOf(fact("backlogs", "1 active backlog"))
        )
        assertEquals(OverallEligibility.NOT_ELIGIBLE, engine.evaluate(oneBacklogProfile, jd).overall)
    }
}
