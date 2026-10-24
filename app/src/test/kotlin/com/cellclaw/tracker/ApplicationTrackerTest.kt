package com.cellclaw.tracker

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApplicationTrackerTest {

    private lateinit var tracker: ApplicationTracker

    private val sampleApp = ApplicationRecord(
        id = "app-001",
        company = "Acme Corp",
        role = "Android Engineer",
        jobId = "REQ-101",
        sourceUrl = "https://acme.com/apply/101",
        jdHash = "sha256:jd123",
        deadline = "2026-11-15",
        eligibilityResult = "ELIGIBLE",
        resumeVersion = 1,
        resumeHash = "sha256:pdf123",
        state = ApplicationState.DRAFT
    )

    @Before
    fun setUp() {
        tracker = InMemoryApplicationTracker()
    }

    @Test
    fun registerAndGetApplication_persistsAndRetrievesCorrectly() = runTest {
        val result = tracker.registerApplication(sampleApp)
        assertFalse(result.hasLikelyDuplicate)

        val retrieved = tracker.getApplication(sampleApp.id)
        assertNotNull(retrieved)
        assertEquals("Acme Corp", retrieved?.company)
        assertEquals("Android Engineer", retrieved?.role)
        assertEquals(ApplicationState.DRAFT, retrieved?.state)
    }

    @Test
    fun duplicateDetection_warnsOnMatchingCompanyRoleOrUrl() = runTest {
        tracker.registerApplication(sampleApp)

        // Same company + role (different case/spacing)
        val dupCompanyRole = ApplicationRecord(
            id = "app-002",
            company = "  acme corp  ",
            role = "android engineer ",
            sourceUrl = "https://different-job-board.com/acme",
            jdHash = "sha256:jd456",
            eligibilityResult = "ELIGIBLE"
        )
        val dup1 = tracker.registerApplication(dupCompanyRole)
        assertTrue(dup1.hasLikelyDuplicate)
        assertEquals(sampleApp.id, dup1.existingApplication?.id)
        assertTrue(dup1.reason?.contains("already exists for Acme Corp - Android Engineer") == true)

        // Same URL, different company name
        val dupUrl = ApplicationRecord(
            id = "app-003",
            company = "Acme Subsidiary",
            role = "Mobile Engineer",
            sourceUrl = "https://acme.com/apply/101",
            jdHash = "sha256:jd789",
            eligibilityResult = "ELIGIBLE"
        )
        val dup2 = tracker.registerApplication(dupUrl)
        assertTrue(dup2.hasLikelyDuplicate)
        assertEquals(sampleApp.id, dup2.existingApplication?.id)
        assertTrue(dup2.reason?.contains("matching source URL already exists") == true)

        // Completely distinct
        val distinct = ApplicationRecord(
            id = "app-004",
            company = "TechStart Inc",
            role = "Kotlin Developer",
            sourceUrl = "https://techstart.io/jobs/1",
            jdHash = "sha256:jd999",
            eligibilityResult = "ELIGIBLE"
        )
        val noDup = tracker.registerApplication(distinct)
        assertFalse(noDup.hasLikelyDuplicate)
        assertNull(noDup.existingApplication)
    }

    @Test
    fun transitionState_validFlowSucceeds() = runTest {
        tracker.registerApplication(sampleApp)

        // DRAFT -> READY_FOR_REVIEW
        val rfr = tracker.transitionState(sampleApp.id, ApplicationState.READY_FOR_REVIEW)
        assertEquals(ApplicationState.READY_FOR_REVIEW, rfr.state)

        // READY_FOR_REVIEW -> SUBMITTING
        val sub = tracker.transitionState(sampleApp.id, ApplicationState.SUBMITTING)
        assertEquals(ApplicationState.SUBMITTING, sub.state)

        // SUBMITTING -> SUBMISSION_UNKNOWN
        val unk = tracker.transitionState(
            sampleApp.id,
            ApplicationState.SUBMISSION_UNKNOWN,
            notes = "Timeout waiting for confirmation"
        )
        assertEquals(ApplicationState.SUBMISSION_UNKNOWN, unk.state)

        // SUBMISSION_UNKNOWN -> SUBMITTED_CONFIRMED with explicit reference
        val conf = tracker.transitionState(
            sampleApp.id,
            ApplicationState.SUBMITTED_CONFIRMED,
            confirmationReference = "CONF-REF-12345"
        )
        assertEquals(ApplicationState.SUBMITTED_CONFIRMED, conf.state)
        assertEquals("CONF-REF-12345", conf.confirmationReference)
    }

    @Test
    fun transitionState_enforcesExplicitConfirmationRequirement() = runTest {
        tracker.registerApplication(sampleApp)
        tracker.transitionState(sampleApp.id, ApplicationState.READY_FOR_REVIEW)
        tracker.transitionState(sampleApp.id, ApplicationState.SUBMITTING)

        // Attempting SUBMITTED_CONFIRMED without confirmationReference must throw
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            tracker.transitionState(
                sampleApp.id,
                ApplicationState.SUBMITTED_CONFIRMED,
                confirmationReference = null
            )
        }

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            tracker.transitionState(
                sampleApp.id,
                ApplicationState.SUBMITTED_CONFIRMED,
                confirmationReference = "   "
            )
        }
    }

    @Test
    fun transitionState_disallowsAutoResubmitFromSubmissionUnknown() = runTest {
        tracker.registerApplication(sampleApp)
        tracker.transitionState(sampleApp.id, ApplicationState.READY_FOR_REVIEW)
        tracker.transitionState(sampleApp.id, ApplicationState.SUBMITTING)
        tracker.transitionState(sampleApp.id, ApplicationState.SUBMISSION_UNKNOWN)

        // Auto-resubmit from SUBMISSION_UNKNOWN to SUBMITTING is strictly disallowed (§3.5)
        kotlin.test.assertFailsWith<IllegalStateException> {
            tracker.transitionState(sampleApp.id, ApplicationState.SUBMITTING)
        }
    }

    @Test
    fun transitionState_nonExistentIdThrowsNoSuchElement() = runTest {
        kotlin.test.assertFailsWith<NoSuchElementException> {
            tracker.transitionState("missing-id", ApplicationState.READY_FOR_REVIEW)
        }
    }

    @Test
    fun flowAndDeletion_updatesCorrectly() = runTest {
        tracker.registerApplication(sampleApp)
        val initialList = tracker.observeApplications().first()
        assertEquals(1, initialList.size)

        val app2 = sampleApp.copy(id = "app-002", company = "Zeta Corp")
        tracker.registerApplication(app2)
        val twoList = tracker.getAllApplications()
        assertEquals(2, twoList.size)

        tracker.deleteApplication("app-001")
        val afterDelete = tracker.getAllApplications()
        assertEquals(1, afterDelete.size)
        assertEquals("app-002", afterDelete[0].id)

        tracker.clearAll()
        val afterClear = tracker.getAllApplications()
        assertTrue(afterClear.isEmpty())
    }
}
