package com.cellclaw.eligibility

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EligibilityModelTest {

    @Test
    fun `all mandatory criteria PASS results in ELIGIBLE overall`() {
        val criteria = listOf(
            EligibilityResult("CGPA >= 7.5", EligibilityStatus.PASS, "Candidate CGPA is 8.5", isMandatory = true),
            EligibilityResult("Degree == B.Tech", EligibilityStatus.PASS, "Candidate degree is B.Tech", isMandatory = true),
            EligibilityResult("Kotlin", EligibilityStatus.PASS, "Candidate has confirmed Kotlin skill", isMandatory = false)
        )
        val report = EligibilityReport.evaluate(criteria)
        assertEquals(OverallEligibility.ELIGIBLE, report.overall)
        assertEquals(3, report.criteria.size)
    }

    @Test
    fun `any mandatory criterion FAIL results in NOT_ELIGIBLE overall`() {
        val criteria = listOf(
            EligibilityResult("CGPA >= 8.0", EligibilityStatus.FAIL, "Candidate CGPA is 7.2", isMandatory = true),
            EligibilityResult("Degree == B.Tech", EligibilityStatus.PASS, "Candidate degree is B.Tech", isMandatory = true)
        )
        val report = EligibilityReport.evaluate(criteria)
        assertEquals(OverallEligibility.NOT_ELIGIBLE, report.overall)
    }

    @Test
    fun `mandatory FAIL takes precedence over mandatory UNKNOWN`() {
        val criteria = listOf(
            EligibilityResult("CGPA >= 8.0", EligibilityStatus.FAIL, "Candidate CGPA is 7.2", isMandatory = true),
            EligibilityResult("Backlog == 0", EligibilityStatus.UNKNOWN, "Backlog status unknown in profile", isMandatory = true)
        )
        val report = EligibilityReport.evaluate(criteria)
        assertEquals(OverallEligibility.NOT_ELIGIBLE, report.overall)
    }

    @Test
    fun `mandatory UNKNOWN without FAIL results in UNKNOWN overall`() {
        val criteria = listOf(
            EligibilityResult("CGPA >= 7.0", EligibilityStatus.PASS, "Candidate CGPA is 7.8", isMandatory = true),
            EligibilityResult("Passing Year == 2026", EligibilityStatus.UNKNOWN, "Graduation year unconfirmed", isMandatory = true)
        )
        val report = EligibilityReport.evaluate(criteria)
        assertEquals(OverallEligibility.UNKNOWN, report.overall)
    }

    @Test
    fun `preferred criterion FAIL does not fail overall when mandatory criteria PASS`() {
        val criteria = listOf(
            EligibilityResult("CGPA >= 7.0", EligibilityStatus.PASS, "Candidate CGPA is 7.8", isMandatory = true),
            EligibilityResult("Docker", EligibilityStatus.FAIL, "Candidate lacks Docker skill", isMandatory = false)
        )
        val report = EligibilityReport.evaluate(criteria)
        assertEquals(OverallEligibility.ELIGIBLE, report.overall)
    }

    @Test
    fun `preferred criterion UNKNOWN does not make overall UNKNOWN when mandatory criteria PASS`() {
        val criteria = listOf(
            EligibilityResult("CGPA >= 7.0", EligibilityStatus.PASS, "Candidate CGPA is 7.8", isMandatory = true),
            EligibilityResult("Kubernetes", EligibilityStatus.UNKNOWN, "Kubernetes skill unconfirmed", isMandatory = false)
        )
        val report = EligibilityReport.evaluate(criteria)
        assertEquals(OverallEligibility.ELIGIBLE, report.overall)
    }

    @Test
    fun `empty criteria list evaluates to ELIGIBLE`() {
        val report = EligibilityReport.evaluate(emptyList())
        assertEquals(OverallEligibility.ELIGIBLE, report.overall)
        assertTrue(report.criteria.isEmpty())
    }

    @Test
    fun `serialization round trip preserves all fields and enum values`() {
        val original = EligibilityReport(
            overall = OverallEligibility.NOT_ELIGIBLE,
            criteria = listOf(
                EligibilityResult(
                    criterion = "CGPA >= 8.0",
                    status = EligibilityStatus.FAIL,
                    reason = "Candidate CGPA 7.5 < required 8.0",
                    isMandatory = true
                ),
                EligibilityResult(
                    criterion = "Python",
                    status = EligibilityStatus.PASS,
                    reason = "Confirmed skill",
                    isMandatory = false
                )
            )
        )

        val jsonStr = Json.encodeToString(original)
        val deserialized = Json.decodeFromString<EligibilityReport>(jsonStr)
        assertEquals(original, deserialized)
        assertEquals(original.criteria[0].isMandatory, deserialized.criteria[0].isMandatory)
        assertEquals(original.criteria[1].isMandatory, deserialized.criteria[1].isMandatory)
    }
}
