package com.cellclaw.tracker

import com.cellclaw.eligibility.EligibilityReport
import com.cellclaw.jd.JdField
import com.cellclaw.jd.JdJobType
import com.cellclaw.jd.JdRequirement
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class PersistedJdField(
    val value: String? = null,
    val listValue: List<String>? = null,
    val intValue: Int? = null,
    val doubleValue: Double? = null,
    val sourceSnippet: String = "",
    val confidence: Float = 0f
)

@Serializable
data class PersistedJdRequirement(
    val company: PersistedJdField = PersistedJdField(),
    val role: PersistedJdField = PersistedJdField(),
    val jobType: String? = null,
    val location: PersistedJdField = PersistedJdField(),
    val requiredSkills: PersistedJdField = PersistedJdField(),
    val preferredSkills: PersistedJdField = PersistedJdField(),
    val degree: PersistedJdField = PersistedJdField(),
    val branch: PersistedJdField = PersistedJdField(),
    val passingYear: PersistedJdField = PersistedJdField(),
    val minCgpa: PersistedJdField = PersistedJdField(),
    val cgpaScale: PersistedJdField = PersistedJdField(),
    val backlogRule: PersistedJdField = PersistedJdField(),
    val deadline: String? = null,
    val questions: PersistedJdField = PersistedJdField()
)

@Serializable
data class ApplicationPayload(
    val eligibilityReport: EligibilityReport? = null,
    val jdRequirement: PersistedJdRequirement? = null,
    val rawJdText: String? = null
)

fun JdRequirement.toPersisted(): PersistedJdRequirement = PersistedJdRequirement(
    company = PersistedJdField(value = company.value, sourceSnippet = company.sourceSnippet, confidence = company.confidence),
    role = PersistedJdField(value = role.value, sourceSnippet = role.sourceSnippet, confidence = role.confidence),
    jobType = jobType.value?.name,
    location = PersistedJdField(value = location.value, sourceSnippet = location.sourceSnippet, confidence = location.confidence),
    requiredSkills = PersistedJdField(listValue = requiredSkills.value, sourceSnippet = requiredSkills.sourceSnippet, confidence = requiredSkills.confidence),
    preferredSkills = PersistedJdField(listValue = preferredSkills.value, sourceSnippet = preferredSkills.sourceSnippet, confidence = preferredSkills.confidence),
    degree = PersistedJdField(value = degree.value, sourceSnippet = degree.sourceSnippet, confidence = degree.confidence),
    branch = PersistedJdField(value = branch.value, sourceSnippet = branch.sourceSnippet, confidence = branch.confidence),
    passingYear = PersistedJdField(intValue = passingYear.value, sourceSnippet = passingYear.sourceSnippet, confidence = passingYear.confidence),
    minCgpa = PersistedJdField(doubleValue = minCgpa.value, sourceSnippet = minCgpa.sourceSnippet, confidence = minCgpa.confidence),
    cgpaScale = PersistedJdField(doubleValue = cgpaScale.value, sourceSnippet = cgpaScale.sourceSnippet, confidence = cgpaScale.confidence),
    backlogRule = PersistedJdField(value = backlogRule.value, sourceSnippet = backlogRule.sourceSnippet, confidence = backlogRule.confidence),
    deadline = deadline.value?.toString(),
    questions = PersistedJdField(listValue = questions.value, sourceSnippet = questions.sourceSnippet, confidence = questions.confidence)
)

private fun <T> makeField(value: T?, snippet: String, confidence: Float): JdField<T> {
    val safeConf = confidence.coerceIn(0f, 1f)
    val safeSnippet = if (value == null) "" else snippet
    return JdField(value = value, sourceSnippet = safeSnippet, confidence = safeConf)
}

fun PersistedJdRequirement.toJdRequirement(): JdRequirement = JdRequirement(
    company = makeField(company.value, company.sourceSnippet, company.confidence),
    role = makeField(role.value, role.sourceSnippet, role.confidence),
    jobType = makeField(jobType?.let { runCatching { JdJobType.valueOf(it) }.getOrNull() }, "", 0f),
    location = makeField(location.value, location.sourceSnippet, location.confidence),
    requiredSkills = makeField(requiredSkills.listValue, requiredSkills.sourceSnippet, requiredSkills.confidence),
    preferredSkills = makeField(preferredSkills.listValue, preferredSkills.sourceSnippet, preferredSkills.confidence),
    degree = makeField(degree.value, degree.sourceSnippet, degree.confidence),
    branch = makeField(branch.value, branch.sourceSnippet, branch.confidence),
    passingYear = makeField(passingYear.intValue, passingYear.sourceSnippet, passingYear.confidence),
    minCgpa = makeField(minCgpa.doubleValue, minCgpa.sourceSnippet, minCgpa.confidence),
    cgpaScale = makeField(cgpaScale.doubleValue, cgpaScale.sourceSnippet, cgpaScale.confidence),
    backlogRule = makeField(backlogRule.value, backlogRule.sourceSnippet, backlogRule.confidence),
    deadline = makeField(deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, "", 0f),
    questions = makeField(questions.listValue, questions.sourceSnippet, questions.confidence)
)
