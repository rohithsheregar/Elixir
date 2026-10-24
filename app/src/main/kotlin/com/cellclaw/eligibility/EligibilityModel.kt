package com.cellclaw.eligibility

import kotlinx.serialization.Serializable

@Serializable
enum class EligibilityStatus {
    PASS,
    FAIL,
    UNKNOWN
}

@Serializable
enum class OverallEligibility {
    ELIGIBLE,
    NOT_ELIGIBLE,
    UNKNOWN
}

@Serializable
data class EligibilityResult(
    val criterion: String,
    val status: EligibilityStatus,
    val reason: String,
    val isMandatory: Boolean = true
)

@Serializable
data class EligibilityReport(
    val overall: OverallEligibility,
    val criteria: List<EligibilityResult>
) {
    companion object {
        fun evaluate(criteria: List<EligibilityResult>): EligibilityReport {
            val mandatory = criteria.filter { it.isMandatory }
            val overall = when {
                mandatory.any { it.status == EligibilityStatus.FAIL } -> OverallEligibility.NOT_ELIGIBLE
                mandatory.any { it.status == EligibilityStatus.UNKNOWN } -> OverallEligibility.UNKNOWN
                else -> OverallEligibility.ELIGIBLE
            }
            return EligibilityReport(overall = overall, criteria = criteria)
        }
    }
}
