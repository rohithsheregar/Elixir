package com.cellclaw.eligibility

import com.cellclaw.jd.JdRequirement
import com.cellclaw.profile.CandidateProfile

class EligibilityEngine {

    /**
     * Evaluates a candidate's confirmed profile facts against a parsed [JdRequirement].
     *
     * Rule: Never guess equivalences (CGPA <-> percentage, branch mapping, degree mapping, backlog status).
     * If equivalence is ambiguous or unconfirmed, mark [EligibilityStatus.UNKNOWN].
     *
     * Overall eligibility is [OverallEligibility.ELIGIBLE] only if every mandatory criterion is [EligibilityStatus.PASS].
     * Preferred criteria never cause overall eligibility to fail.
     */
    fun evaluate(profile: CandidateProfile, jd: JdRequirement): EligibilityReport {
        val criteria = mutableListOf<EligibilityResult>()

        // 1. Degree requirement
        jd.degree.value?.takeIf { it.isNotBlank() }?.let { requiredDegree ->
            criteria.add(checkDegree(profile, requiredDegree))
        }

        // 2. Branch requirement
        jd.branch.value?.takeIf { it.isNotBlank() }?.let { requiredBranch ->
            criteria.add(checkBranch(profile, requiredBranch))
        }

        // 3. Passing Year requirement
        jd.passingYear.value?.let { requiredYear ->
            criteria.add(checkPassingYear(profile, requiredYear))
        }

        // 4. CGPA / Percentage requirement
        jd.minCgpa.value?.let { minScore ->
            val scale = jd.cgpaScale.value ?: 10.0
            criteria.add(checkCgpaOrPercentage(profile, minScore, scale))
        }

        // 5. Backlog requirement
        jd.backlogRule.value?.takeIf { it.isNotBlank() }?.let { backlogRule ->
            criteria.add(checkBacklogs(profile, backlogRule))
        }

        // 6. Required Skills (each mandatory)
        for (skill in jd.requiredSkills.value.orEmpty()) {
            criteria.add(checkSkill(profile, skill, isMandatory = true))
        }

        // 7. Preferred Skills (each non-mandatory)
        for (skill in jd.preferredSkills.value.orEmpty()) {
            criteria.add(checkSkill(profile, skill, isMandatory = false))
        }

        return EligibilityReport.evaluate(criteria)
    }

    private fun checkDegree(profile: CandidateProfile, requiredDegree: String): EligibilityResult {
        val criterionName = "Degree: $requiredDegree"
        val fact = profile.degree?.takeIf { it.confirmed }
            ?: return EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.UNKNOWN,
                reason = "Candidate degree is not confirmed in profile",
                isMandatory = true
            )

        val candidateDegree = fact.value
        val normalizedCandidate = normalize(candidateDegree)
        val normalizedRequired = normalize(requiredDegree)

        return if (normalizedCandidate == normalizedRequired) {
            EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.PASS,
                reason = "Confirmed degree '$candidateDegree' matches required '$requiredDegree'",
                isMandatory = true
            )
        } else {
            EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.UNKNOWN,
                reason = "Degree equivalence between '$candidateDegree' and '$requiredDegree' cannot be guessed without user confirmation",
                isMandatory = true
            )
        }
    }

    private fun checkBranch(profile: CandidateProfile, requiredBranch: String): EligibilityResult {
        val criterionName = "Branch: $requiredBranch"
        val fact = profile.branch?.takeIf { it.confirmed }
            ?: return EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.UNKNOWN,
                reason = "Candidate branch is not confirmed in profile",
                isMandatory = true
            )

        val candidateBranch = fact.value
        val normalizedCandidate = normalize(candidateBranch)
        val normalizedRequired = normalize(requiredBranch)

        return if (normalizedCandidate == normalizedRequired) {
            EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.PASS,
                reason = "Confirmed branch '$candidateBranch' matches required '$requiredBranch'",
                isMandatory = true
            )
        } else {
            EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.UNKNOWN,
                reason = "Branch equivalence between '$candidateBranch' and '$requiredBranch' cannot be guessed without user confirmation",
                isMandatory = true
            )
        }
    }

    private fun checkPassingYear(profile: CandidateProfile, requiredYear: Int): EligibilityResult {
        val criterionName = "Passing Year: $requiredYear"
        val fact = profile.graduationYear?.takeIf { it.confirmed }
            ?: return EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.UNKNOWN,
                reason = "Candidate graduation year is not confirmed in profile",
                isMandatory = true
            )

        val candidateYear = fact.value.toIntOrNull()
            ?: return EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.UNKNOWN,
                reason = "Candidate graduation year '${fact.value}' could not be parsed as an integer",
                isMandatory = true
            )

        return if (candidateYear == requiredYear) {
            EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.PASS,
                reason = "Confirmed graduation year $candidateYear matches required $requiredYear",
                isMandatory = true
            )
        } else {
            EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.FAIL,
                reason = "Confirmed graduation year $candidateYear does not match required $requiredYear",
                isMandatory = true
            )
        }
    }

    private fun checkCgpaOrPercentage(profile: CandidateProfile, requiredScore: Double, requiredScale: Double): EligibilityResult {
        return if (requiredScale == 100.0) {
            checkPercentage(profile, requiredScore)
        } else {
            checkCgpa(profile, requiredScore, requiredScale)
        }
    }

    private fun checkCgpa(profile: CandidateProfile, minCgpa: Double, requiredScale: Double): EligibilityResult {
        val criterionName = "Minimum CGPA: $minCgpa (scale $requiredScale)"
        val cgpaFact = profile.cgpa?.takeIf { it.confirmed }

        if (cgpaFact == null) {
            val hasPercentage = (profile.marks12th ?: profile.marks10th)?.takeIf { it.confirmed } != null
            return if (hasPercentage) {
                EligibilityResult(
                    criterion = criterionName,
                    status = EligibilityStatus.UNKNOWN,
                    reason = "JD requires CGPA ($minCgpa), but profile only provides percentage marks. Conversion cannot be guessed.",
                    isMandatory = true
                )
            } else {
                EligibilityResult(
                    criterion = criterionName,
                    status = EligibilityStatus.UNKNOWN,
                    reason = "Candidate CGPA is not confirmed in profile",
                    isMandatory = true
                )
            }
        }

        val candidateCgpa = cgpaFact.value.toDoubleOrNull()
            ?: return EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.UNKNOWN,
                reason = "Candidate CGPA '${cgpaFact.value}' is not a valid number",
                isMandatory = true
            )

        val candidateScale = profile.cgpaScale?.takeIf { it.confirmed }?.value?.toDoubleOrNull() ?: 10.0
        if (candidateScale != requiredScale) {
            return EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.UNKNOWN,
                reason = "CGPA scale mismatch (required scale $requiredScale vs candidate scale $candidateScale). Conversion cannot be guessed.",
                isMandatory = true
            )
        }

        return if (candidateCgpa >= minCgpa) {
            EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.PASS,
                reason = "Confirmed CGPA $candidateCgpa meets minimum required $minCgpa",
                isMandatory = true
            )
        } else {
            EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.FAIL,
                reason = "Confirmed CGPA $candidateCgpa is below minimum required $minCgpa",
                isMandatory = true
            )
        }
    }

    private fun checkPercentage(profile: CandidateProfile, minPercentage: Double): EligibilityResult {
        val criterionName = "Minimum Percentage: $minPercentage%"
        val marksFact = (profile.marks12th ?: profile.marks10th)?.takeIf { it.confirmed }

        if (marksFact == null) {
            val hasCgpa = profile.cgpa?.takeIf { it.confirmed } != null
            return if (hasCgpa) {
                EligibilityResult(
                    criterion = criterionName,
                    status = EligibilityStatus.UNKNOWN,
                    reason = "JD requires percentage ($minPercentage%), but candidate profile provides CGPA. Conversion cannot be guessed.",
                    isMandatory = true
                )
            } else {
                EligibilityResult(
                    criterion = criterionName,
                    status = EligibilityStatus.UNKNOWN,
                    reason = "Candidate percentage marks not confirmed in profile",
                    isMandatory = true
                )
            }
        }

        val cleanMarks = marksFact.value.replace("%", "").trim()
        val candidatePercentage = cleanMarks.toDoubleOrNull()
            ?: return EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.UNKNOWN,
                reason = "Candidate marks '${marksFact.value}' cannot be parsed as a percentage",
                isMandatory = true
            )

        return if (candidatePercentage >= minPercentage) {
            EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.PASS,
                reason = "Confirmed marks $candidatePercentage% meets minimum required $minPercentage%",
                isMandatory = true
            )
        } else {
            EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.FAIL,
                reason = "Confirmed marks $candidatePercentage% is below minimum required $minPercentage%",
                isMandatory = true
            )
        }
    }

    private fun checkBacklogs(profile: CandidateProfile, backlogRule: String): EligibilityResult {
        val criterionName = "Backlog Rule: $backlogRule"
        val backlogFact = profile.reusableAnswers.find { it.confirmed && it.field.contains("backlog", ignoreCase = true) }
            ?: return EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.UNKNOWN,
                reason = "Backlog status is not confirmed in candidate profile. Equivalence or active backlogs cannot be assumed.",
                isMandatory = true
            )

        val lowerRule = backlogRule.lowercase()
        val isZeroRequired = lowerRule.contains("no") || lowerRule.contains("zero") || lowerRule.contains("0")

        val backlogsCount = backlogFact.value.filter { it.isDigit() }.toIntOrNull()
        if (backlogsCount != null) {
            if (isZeroRequired) {
                return if (backlogsCount == 0) {
                    EligibilityResult(
                        criterion = criterionName,
                        status = EligibilityStatus.PASS,
                        reason = "Candidate has 0 active backlogs (meets '$backlogRule')",
                        isMandatory = true
                    )
                } else {
                    EligibilityResult(
                        criterion = criterionName,
                        status = EligibilityStatus.FAIL,
                        reason = "Candidate has $backlogsCount active backlogs (violates '$backlogRule')",
                        isMandatory = true
                    )
                }
            }
        }

        val lowerAnswer = backlogFact.value.lowercase()
        if (lowerAnswer.contains("no") || lowerAnswer.contains("zero") || lowerAnswer.startsWith("0")) {
            return EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.PASS,
                reason = "Confirmed backlog answer '${backlogFact.value}' satisfies '$backlogRule'",
                isMandatory = true
            )
        }

        return EligibilityResult(
            criterion = criterionName,
            status = EligibilityStatus.UNKNOWN,
            reason = "Cannot determine if '${backlogFact.value}' satisfies '$backlogRule' without user confirmation",
            isMandatory = true
        )
    }

    private fun checkSkill(profile: CandidateProfile, skillName: String, isMandatory: Boolean): EligibilityResult {
        val prefix = if (isMandatory) "Required Skill" else "Preferred Skill"
        val criterionName = "$prefix: $skillName"

        val confirmedSkill = profile.skills.find { it.confirmed && normalize(it.value) == normalize(skillName) }
        if (confirmedSkill != null) {
            return EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.PASS,
                reason = "Confirmed skill '$skillName' present in profile",
                isMandatory = isMandatory
            )
        }

        val unconfirmedSkill = profile.skills.find { !it.confirmed && normalize(it.value) == normalize(skillName) }
        if (unconfirmedSkill != null) {
            return EligibilityResult(
                criterion = criterionName,
                status = EligibilityStatus.UNKNOWN,
                reason = "Skill '$skillName' is proposed but not yet confirmed in profile",
                isMandatory = isMandatory
            )
        }

        return EligibilityResult(
            criterion = criterionName,
            status = EligibilityStatus.FAIL,
            reason = "$prefix '$skillName' is missing from profile",
            isMandatory = isMandatory
        )
    }

    private fun normalize(str: String): String =
        str.lowercase().replace(".", "").replace("-", "").replace(" ", "").trim()
}
