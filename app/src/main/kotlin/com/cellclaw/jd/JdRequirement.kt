package com.cellclaw.jd

import java.time.LocalDate

/** A parsed JD value with its exact source excerpt and parser confidence. */
data class JdField<T>(
    val value: T? = null,
    val sourceSnippet: String = "",
    val confidence: Float = 0f
) {
    init {
        require(confidence in 0f..1f) { "JD confidence must be between 0 and 1" }
        require(value != null || sourceSnippet.isBlank()) {
            "An unknown JD field cannot claim a source snippet"
        }
    }
}

enum class JdJobType { FULL_TIME, INTERNSHIP, APPRENTICESHIP, CONTRACT, UNKNOWN }

/**
 * Typed, evidence-carrying representation of a job description. Parsers populate only values
 * explicitly present in the source; absent values remain [JdField] defaults.
 */
data class JdRequirement(
    val company: JdField<String> = JdField(),
    val role: JdField<String> = JdField(),
    val jobType: JdField<JdJobType> = JdField(),
    val location: JdField<String> = JdField(),
    val requiredSkills: JdField<List<String>> = JdField(),
    val preferredSkills: JdField<List<String>> = JdField(),
    val degree: JdField<String> = JdField(),
    val branch: JdField<String> = JdField(),
    val passingYear: JdField<Int> = JdField(),
    val minCgpa: JdField<Double> = JdField(),
    val cgpaScale: JdField<Double> = JdField(),
    val backlogRule: JdField<String> = JdField(),
    val deadline: JdField<LocalDate> = JdField(),
    val questions: JdField<List<String>> = JdField()
)
