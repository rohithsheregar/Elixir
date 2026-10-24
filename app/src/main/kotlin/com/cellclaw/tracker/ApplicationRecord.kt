package com.cellclaw.tracker

import kotlinx.serialization.Serializable

@Serializable
enum class ApplicationState {
    DRAFT,
    READY_FOR_REVIEW,
    SUBMITTING,
    SUBMITTED_CONFIRMED,
    SUBMISSION_UNKNOWN,
    FAILED,
    CANCELLED;

    /**
     * Checks whether transitioning from `this` state to [target] is valid.
     */
    fun canTransitionTo(target: ApplicationState): Boolean {
        if (this == target) return true
        return when (this) {
            DRAFT -> target in setOf(READY_FOR_REVIEW, CANCELLED)
            READY_FOR_REVIEW -> target in setOf(SUBMITTING, DRAFT, CANCELLED)
            SUBMITTING -> target in setOf(SUBMITTED_CONFIRMED, SUBMISSION_UNKNOWN, FAILED, CANCELLED)
            // Hard rule: on timeout/unknown, do not auto-resubmit; can only confirm manually, fail, or cancel
            SUBMISSION_UNKNOWN -> target in setOf(SUBMITTED_CONFIRMED, FAILED, CANCELLED)
            FAILED -> target in setOf(DRAFT, CANCELLED)
            CANCELLED -> target in setOf(DRAFT)
            SUBMITTED_CONFIRMED -> false // Terminal confirmed state
        }
    }
}

@Serializable
data class ApplicationRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val company: String,
    val role: String,
    val jobId: String? = null,
    val sourceUrl: String? = null,
    val jdHash: String,
    val deadline: String? = null,
    val eligibilityResult: String, // e.g. "ELIGIBLE", "NOT_ELIGIBLE", "UNKNOWN"
    val resumeVersion: Int = 1,
    val resumeHash: String? = null,
    val state: ApplicationState = ApplicationState.DRAFT,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val confirmationReference: String? = null
) {
    /**
     * Transitions this record to [targetState], updating [updatedAt].
     * Throws [IllegalStateException] if the transition is invalid.
     */
    fun transitionTo(
        targetState: ApplicationState,
        notes: String? = this.notes,
        confirmationReference: String? = this.confirmationReference
    ): ApplicationRecord {
        check(state.canTransitionTo(targetState)) {
            "Illegal state transition from $state to $targetState for application $id ($company - $role)"
        }
        return copy(
            state = targetState,
            updatedAt = System.currentTimeMillis(),
            notes = notes,
            confirmationReference = confirmationReference
        )
    }

    /**
     * Returns true if this application is a likely duplicate of [other], matching either:
     * 1) Same company and role (case-insensitive, whitespace-trimmed), or
     * 2) Same non-blank source URL (case-insensitive, whitespace-trimmed).
     */
    fun isLikelyDuplicateOf(other: ApplicationRecord): Boolean {
        if (this.id == other.id) return false

        val sameCompany = company.trim().equals(other.company.trim(), ignoreCase = true)
        val sameRole = role.trim().equals(other.role.trim(), ignoreCase = true)
        if (sameCompany && sameRole) return true

        val myUrl = sourceUrl?.trim()?.takeIf { it.isNotBlank() }
        val otherUrl = other.sourceUrl?.trim()?.takeIf { it.isNotBlank() }
        if (myUrl != null && otherUrl != null && myUrl.equals(otherUrl, ignoreCase = true)) {
            return true
        }

        return false
    }
}
