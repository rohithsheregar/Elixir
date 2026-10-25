package com.cellclaw.ui

import androidx.lifecycle.SavedStateHandle
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.InMemoryApplicationTracker
import com.cellclaw.ui.viewmodel.ResultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResultScreenTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tracker: InMemoryApplicationTracker

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
    fun `ResultViewModel loads confirmed application record and reference`() = runTest(testDispatcher) {
        val app = ApplicationRecord(
            id = "app-res-1",
            company = "Global Payments",
            role = "Security Analyst",
            sourceUrl = "https://example.com/apply/security",
            jdHash = "jdhash111",
            eligibilityResult = "ELIGIBLE",
            state = ApplicationState.SUBMITTED_CONFIRMED,
            confirmationReference = "CONF-PAY-778899",
            resumeVersion = 2,
            resumeHash = "resumehash222"
        )
        tracker.registerApplication(app)

        val savedState = SavedStateHandle(mapOf(ResultViewModel.KEY_APP_ID to app.id))
        val viewModel = ResultViewModel(tracker, savedState)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("app-res-1", state.applicationId)
        assertEquals("Global Payments", state.company)
        assertEquals("Security Analyst", state.role)
        assertEquals(ApplicationState.SUBMITTED_CONFIRMED, state.state)
        assertEquals("CONF-PAY-778899", state.confirmationReference)
        assertEquals(2, state.resumeVersion)
    }

    @Test
    fun `manual confirmation on SUBMISSION_UNKNOWN updates tracker and state`() = runTest(testDispatcher) {
        val app = ApplicationRecord(
            id = "app-res-2",
            company = "Alpha Networks",
            role = "Cloud Engineer",
            sourceUrl = "https://example.com/apply/cloud",
            jdHash = "jdhash222",
            eligibilityResult = "ELIGIBLE",
            state = ApplicationState.SUBMISSION_UNKNOWN
        )
        tracker.registerApplication(app)

        val viewModel = ResultViewModel(tracker)
        viewModel.load(app.id)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ApplicationState.SUBMISSION_UNKNOWN, viewModel.uiState.value.state)

        // User enters confirmation reference from browser receipt
        viewModel.updateManualReferenceInput("MANUAL-RECEIPT-4040")
        viewModel.confirmSubmissionManually()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ApplicationState.SUBMITTED_CONFIRMED, viewModel.uiState.value.state)
        assertEquals("MANUAL-RECEIPT-4040", viewModel.uiState.value.confirmationReference)

        // Verify tracker was updated
        val updatedApp = tracker.getApplication(app.id)
        assertNotNull(updatedApp)
        assertEquals(ApplicationState.SUBMITTED_CONFIRMED, updatedApp?.state)
        assertEquals("MANUAL-RECEIPT-4040", updatedApp?.confirmationReference)
    }

    @Test
    fun `retrySubmission from FAILED resets application to READY_FOR_REVIEW`() = runTest(testDispatcher) {
        val app = ApplicationRecord(
            id = "app-res-3",
            company = "Beta Robotics",
            role = "Embedded Systems",
            sourceUrl = "https://example.com/apply/embedded",
            jdHash = "jdhash333",
            eligibilityResult = "ELIGIBLE",
            state = ApplicationState.FAILED,
            notes = "Network drop during file upload"
        )
        tracker.registerApplication(app)

        val viewModel = ResultViewModel(tracker)
        viewModel.load(app.id)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ApplicationState.FAILED, viewModel.uiState.value.state)

        var retriedId: String? = null
        viewModel.retrySubmission { id ->
            retriedId = id
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(app.id, retriedId)
        assertEquals(ApplicationState.READY_FOR_REVIEW, viewModel.uiState.value.state)

        // Verify tracker was transitioned
        val updatedApp = tracker.getApplication(app.id)
        assertEquals(ApplicationState.READY_FOR_REVIEW, updatedApp?.state)
    }

    @Test
    fun `retrySubmission from SUBMISSION_UNKNOWN resets application to READY_FOR_REVIEW`() = runTest(testDispatcher) {
        val app = ApplicationRecord(
            id = "app-res-4",
            company = "Gamma AI",
            role = "ML Engineer",
            sourceUrl = "https://example.com/apply/ml",
            jdHash = "jdhash444",
            eligibilityResult = "ELIGIBLE",
            state = ApplicationState.SUBMISSION_UNKNOWN
        )
        tracker.registerApplication(app)

        val viewModel = ResultViewModel(tracker)
        viewModel.load(app.id)
        testDispatcher.scheduler.advanceUntilIdle()

        var retriedId: String? = null
        viewModel.retrySubmission { id ->
            retriedId = id
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(app.id, retriedId)
        assertEquals(ApplicationState.READY_FOR_REVIEW, viewModel.uiState.value.state)

        val updatedApp = tracker.getApplication(app.id)
        assertEquals(ApplicationState.READY_FOR_REVIEW, updatedApp?.state)
    }

    @Test
    fun `markAsFailed updates application state to FAILED`() = runTest(testDispatcher) {
        val app = ApplicationRecord(
            id = "app-res-5",
            company = "Delta Aerospace",
            role = "Avionics Dev",
            sourceUrl = "https://example.com/apply/avionics",
            jdHash = "jdhash555",
            eligibilityResult = "ELIGIBLE",
            state = ApplicationState.SUBMISSION_UNKNOWN
        )
        tracker.registerApplication(app)

        val viewModel = ResultViewModel(tracker)
        viewModel.load(app.id)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.markAsFailed("Application was rejected on site")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ApplicationState.FAILED, viewModel.uiState.value.state)
        val updatedApp = tracker.getApplication(app.id)
        assertEquals(ApplicationState.FAILED, updatedApp?.state)
        assertEquals("Application was rejected on site", updatedApp?.notes)
    }
}
