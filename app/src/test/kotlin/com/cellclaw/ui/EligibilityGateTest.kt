package com.cellclaw.ui

import com.cellclaw.eligibility.EligibilityReport
import com.cellclaw.eligibility.EligibilityResult
import com.cellclaw.eligibility.EligibilityStatus
import com.cellclaw.eligibility.OverallEligibility
import com.cellclaw.tracker.ApplicationPayload
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.InMemoryApplicationTracker
import com.cellclaw.ui.viewmodel.EligibilityReportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EligibilityGateTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tracker: InMemoryApplicationTracker
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tracker = InMemoryApplicationTracker()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `eligible application allows progression without override`() = runTest(testDispatcher) {
        val report = EligibilityReport(
            overall = OverallEligibility.ELIGIBLE,
            criteria = listOf(
                EligibilityResult(
                    criterion = "CGPA >= 7.0",
                    status = EligibilityStatus.PASS,
                    reason = "Candidate CGPA 8.5 satisfies requirement"
                )
            )
        )
        val payload = ApplicationPayload(eligibilityReport = report)
        val app = ApplicationRecord(
            id = "app-gate-1",
            company = "Acme Corp",
            role = "Software Engineer",
            jdHash = "jd1",
            eligibilityResult = "ELIGIBLE",
            notes = json.encodeToString(payload)
        )
        tracker.registerApplication(app)

        val viewModel = EligibilityReportViewModel(tracker)
        viewModel.load(app.id)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isNotEligible)
        assertTrue(state.canProceed)
        assertFalse(state.userOverrideConfirmed)
        assertTrue(viewModel.canProceed())
    }

    @Test
    fun `unknown eligibility application allows progression without override`() = runTest(testDispatcher) {
        val report = EligibilityReport(
            overall = OverallEligibility.UNKNOWN,
            criteria = listOf(
                EligibilityResult(
                    criterion = "Branch in [CS, IT]",
                    status = EligibilityStatus.UNKNOWN,
                    reason = "Branch fact unconfirmed in candidate profile"
                )
            )
        )
        val payload = ApplicationPayload(eligibilityReport = report)
        val app = ApplicationRecord(
            id = "app-gate-2",
            company = "Beta Dynamics",
            role = "Systems Dev",
            jdHash = "jd2",
            eligibilityResult = "UNKNOWN",
            notes = json.encodeToString(payload)
        )
        tracker.registerApplication(app)

        val viewModel = EligibilityReportViewModel(tracker)
        viewModel.load(app.id)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isNotEligible)
        assertTrue(state.canProceed)
        assertTrue(viewModel.canProceed())
    }

    @Test
    fun `not eligible application enforces hard stop until user explicitly confirms override`() = runTest(testDispatcher) {
        val report = EligibilityReport(
            overall = OverallEligibility.NOT_ELIGIBLE,
            criteria = listOf(
                EligibilityResult(
                    criterion = "CGPA >= 8.5",
                    status = EligibilityStatus.FAIL,
                    reason = "Candidate CGPA 7.2 does not satisfy minimum requirement"
                )
            )
        )
        val payload = ApplicationPayload(eligibilityReport = report)
        val app = ApplicationRecord(
            id = "app-gate-3",
            company = "Quantum Labs",
            role = "Research Scientist",
            jdHash = "jd3",
            eligibilityResult = "NOT_ELIGIBLE",
            notes = json.encodeToString(payload)
        )
        tracker.registerApplication(app)

        val viewModel = EligibilityReportViewModel(tracker)
        viewModel.load(app.id)
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Initial state: Hard Stop enforced
        val stateInitial = viewModel.uiState.value
        assertTrue("Must be marked as isNotEligible", stateInitial.isNotEligible)
        assertFalse("User override must be false by default", stateInitial.userOverrideConfirmed)
        assertFalse("Progression must be blocked by default (Hard Stop)", stateInitial.canProceed)
        assertFalse(viewModel.canProceed())

        // 2. User affirms explicit override checkbox
        viewModel.setOverrideConfirmed(true)
        val stateOverridden = viewModel.uiState.value
        assertTrue(stateOverridden.userOverrideConfirmed)
        assertTrue("Progression must be unlocked after explicit override", stateOverridden.canProceed)
        assertTrue(viewModel.canProceed())

        // 3. User unchecks override
        viewModel.setOverrideConfirmed(false)
        val stateRevoked = viewModel.uiState.value
        assertFalse(stateRevoked.userOverrideConfirmed)
        assertFalse("Progression must be locked again if override unchecked", stateRevoked.canProceed)
        assertFalse(viewModel.canProceed())
    }

    @Test
    fun `hard stop enforced even when report is reconstructed from legacy eligibilityResult string`() = runTest(testDispatcher) {
        val app = ApplicationRecord(
            id = "app-gate-4",
            company = "Legacy Corp",
            role = "DevOps Engineer",
            jdHash = "jd4",
            eligibilityResult = "NOT_ELIGIBLE",
            notes = null // Legacy record without serialized report
        )
        tracker.registerApplication(app)

        val viewModel = EligibilityReportViewModel(tracker)
        viewModel.load(app.id)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Legacy NOT_ELIGIBLE status must trigger isNotEligible", state.isNotEligible)
        assertFalse("Must enforce hard stop", state.canProceed)

        viewModel.setOverrideConfirmed(true)
        assertTrue("Override unlocks legacy record", viewModel.canProceed())
    }
}
