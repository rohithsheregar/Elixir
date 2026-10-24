package com.cellclaw.tracker

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationTrackerModelTest {

    private val sampleRecord = ApplicationRecord(
        id = "app-001",
        company = "Acme Corp",
        role = "Android Engineer",
        jobId = "REQ-12345",
        sourceUrl = "https://acme.com/jobs/req-12345",
        jdHash = "sha256:abc123jd",
        deadline = "2026-11-01",
        eligibilityResult = "ELIGIBLE",
        resumeVersion = 1,
        resumeHash = "sha256:xyz789pdf",
        state = ApplicationState.DRAFT,
        notes = "Campus drive 2026"
    )

    @Test
    fun validTransitions_followExpectedLifecycle() {
        // DRAFT -> READY_FOR_REVIEW
        val ready = sampleRecord.transitionTo(ApplicationState.READY_FOR_REVIEW)
        assertEquals(ApplicationState.READY_FOR_REVIEW, ready.state)
        assertTrue(ready.updatedAt >= sampleRecord.updatedAt)

        // READY_FOR_REVIEW -> SUBMITTING
        val submitting = ready.transitionTo(ApplicationState.SUBMITTING)
        assertEquals(ApplicationState.SUBMITTING, submitting.state)

        // SUBMITTING -> SUBMITTED_CONFIRMED with reference
        val confirmed = submitting.transitionTo(
            targetState = ApplicationState.SUBMITTED_CONFIRMED,
            confirmationReference = "CONF-998877"
        )
        assertEquals(ApplicationState.SUBMITTED_CONFIRMED, confirmed.state)
        assertEquals("CONF-998877", confirmed.confirmationReference)
    }

    @Test
    fun validTransitions_submissionUnknownPath() {
        val submitting = sampleRecord
            .transitionTo(ApplicationState.READY_FOR_REVIEW)
            .transitionTo(ApplicationState.SUBMITTING)

        // Timeout or network ambiguity -> SUBMISSION_UNKNOWN
        val unknown = submitting.transitionTo(
            targetState = ApplicationState.SUBMISSION_UNKNOWN,
            notes = "Submission timed out while waiting for confirmation screen."
        )
        assertEquals(ApplicationState.SUBMISSION_UNKNOWN, unknown.state)

        // Manual user confirmation -> SUBMITTED_CONFIRMED
        val confirmed = unknown.transitionTo(
            targetState = ApplicationState.SUBMITTED_CONFIRMED,
            confirmationReference = "USER-CONFIRMED-MANUALLY"
        )
        assertEquals(ApplicationState.SUBMITTED_CONFIRMED, confirmed.state)
    }

    @Test
    fun validTransitions_failedAndCancelledRecovery() {
        // Failure recovery: SUBMITTING -> FAILED -> DRAFT
        val failed = sampleRecord
            .transitionTo(ApplicationState.READY_FOR_REVIEW)
            .transitionTo(ApplicationState.SUBMITTING)
            .transitionTo(ApplicationState.FAILED, notes = "Form upload error")

        assertEquals(ApplicationState.FAILED, failed.state)
        val redraft = failed.transitionTo(ApplicationState.DRAFT)
        assertEquals(ApplicationState.DRAFT, redraft.state)

        // Cancellation recovery: DRAFT -> CANCELLED -> DRAFT
        val cancelled = sampleRecord.transitionTo(ApplicationState.CANCELLED)
        assertEquals(ApplicationState.CANCELLED, cancelled.state)
        val reopened = cancelled.transitionTo(ApplicationState.DRAFT)
        assertEquals(ApplicationState.DRAFT, reopened.state)
    }

    @Test
    fun invalidTransitions_throwIllegalStateException() {
        // DRAFT cannot jump directly to SUBMITTING or SUBMITTED_CONFIRMED
        assertThrows(IllegalStateException::class.java) {
            sampleRecord.transitionTo(ApplicationState.SUBMITTING)
        }
        assertThrows(IllegalStateException::class.java) {
            sampleRecord.transitionTo(ApplicationState.SUBMITTED_CONFIRMED)
        }

        // SUBMISSION_UNKNOWN cannot auto-resubmit (hard rule §3.5)
        val unknown = sampleRecord
            .transitionTo(ApplicationState.READY_FOR_REVIEW)
            .transitionTo(ApplicationState.SUBMITTING)
            .transitionTo(ApplicationState.SUBMISSION_UNKNOWN)

        assertThrows(IllegalStateException::class.java) {
            unknown.transitionTo(ApplicationState.SUBMITTING)
        }

        // SUBMITTED_CONFIRMED is a terminal state
        val confirmed = unknown.transitionTo(ApplicationState.SUBMITTED_CONFIRMED)
        assertThrows(IllegalStateException::class.java) {
            confirmed.transitionTo(ApplicationState.SUBMITTING)
        }
        assertThrows(IllegalStateException::class.java) {
            confirmed.transitionTo(ApplicationState.DRAFT)
        }
    }

    @Test
    fun duplicateDetection_detectsMatchingCompanyRoleOrUrl() {
        val sameCompanyRole = sampleRecord.copy(
            id = "app-002",
            company = "  acme corp  ",
            role = "android engineer ",
            sourceUrl = "https://different-portal.com/job"
        )
        assertTrue(sampleRecord.isLikelyDuplicateOf(sameCompanyRole))

        val sameUrl = sampleRecord.copy(
            id = "app-003",
            company = "Acme Subsidiary",
            role = "Mobile Dev",
            sourceUrl = "  https://acme.com/jobs/req-12345  "
        )
        assertTrue(sampleRecord.isLikelyDuplicateOf(sameUrl))

        val completelyDifferent = sampleRecord.copy(
            id = "app-004",
            company = "Beta Labs",
            role = "Backend Dev",
            sourceUrl = "https://betalabs.com/careers/1"
        )
        assertFalse(sampleRecord.isLikelyDuplicateOf(completelyDifferent))

        // Same record id should not flag as duplicate of itself
        assertFalse(sampleRecord.isLikelyDuplicateOf(sampleRecord))
    }

    @Test
    fun serialization_roundTripPreservesAllFields() {
        val json = Json { prettyPrint = true }
        val serialized = json.encodeToString(sampleRecord)
        val deserialized = json.decodeFromString<ApplicationRecord>(serialized)

        assertEquals(sampleRecord.id, deserialized.id)
        assertEquals(sampleRecord.company, deserialized.company)
        assertEquals(sampleRecord.role, deserialized.role)
        assertEquals(sampleRecord.jobId, deserialized.jobId)
        assertEquals(sampleRecord.sourceUrl, deserialized.sourceUrl)
        assertEquals(sampleRecord.jdHash, deserialized.jdHash)
        assertEquals(sampleRecord.deadline, deserialized.deadline)
        assertEquals(sampleRecord.eligibilityResult, deserialized.eligibilityResult)
        assertEquals(sampleRecord.resumeVersion, deserialized.resumeVersion)
        assertEquals(sampleRecord.resumeHash, deserialized.resumeHash)
        assertEquals(sampleRecord.state, deserialized.state)
        assertEquals(sampleRecord.createdAt, deserialized.createdAt)
        assertEquals(sampleRecord.updatedAt, deserialized.updatedAt)
        assertEquals(sampleRecord.notes, deserialized.notes)
    }
}
