package com.cellclaw.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import com.cellclaw.accessibility.AccessibilitySession
import com.cellclaw.approval.ApprovalTokenManager
import com.cellclaw.approval.ConfirmationDetector
import com.cellclaw.approval.SubmitSupervisor
import com.cellclaw.automation.AutomationOrchestrator
import com.cellclaw.automation.AutomationStep
import com.cellclaw.browser.BrowserOpener
import com.cellclaw.browser.BrowserOpenResult
import com.cellclaw.browser.UrlValidator
import com.cellclaw.form.FieldClassifier
import com.cellclaw.form.FieldFiller
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.InMemoryProfileRepository
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.InMemoryApplicationTracker
import com.cellclaw.ui.viewmodel.RunViewModel
import com.cellclaw.upload.FakeResumeUriProvider
import com.cellclaw.upload.PostUploadVerifier
import com.cellclaw.upload.ResumeFileValidator
import com.cellclaw.upload.ResumeUploader
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
class RunScreenTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tracker: InMemoryApplicationTracker
    private lateinit var profileRepo: InMemoryProfileRepository
    private lateinit var tokenManager: ApprovalTokenManager
    private lateinit var orchestrator: AutomationOrchestrator
    private lateinit var accessibilitySession: AccessibilitySession

    private inner class FakeContext : ContextWrapper(null) {
        override fun getPackageName(): String = "com.cellclaw"
    }

    private class FakeBrowserOpener(
        context: Context,
        validator: UrlValidator = UrlValidator()
    ) : BrowserOpener(context, validator) {
        override fun open(url: String, preferChrome: Boolean): BrowserOpenResult {
            return BrowserOpenResult.Success(url, null)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tracker = InMemoryApplicationTracker()
        profileRepo = InMemoryProfileRepository()
        tokenManager = ApprovalTokenManager()
        accessibilitySession = AccessibilitySession()

        val classifier = FieldClassifier()
        val filler = FieldFiller(classifier = classifier)
        val uriProvider = FakeResumeUriProvider("content://com.cellclaw.fileprovider/resumes")
        val uploader = ResumeUploader(ResumeFileValidator(), uriProvider)
        val verifier = PostUploadVerifier()
        val supervisor = SubmitSupervisor(tokenManager, tracker, ConfirmationDetector())

        orchestrator = AutomationOrchestrator(
            browserOpener = FakeBrowserOpener(FakeContext()),
            accessibilitySession = accessibilitySession,
            fieldClassifier = classifier,
            fieldFiller = filler,
            resumeUploader = uploader,
            postUploadVerifier = verifier,
            submitSupervisor = supervisor,
            tracker = tracker,
            tokenManager = tokenManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `RunViewModel loads target application details and initial state`() = runTest(testDispatcher) {
        val app = ApplicationRecord(
            id = "app-run-1",
            company = "TechStart Inc",
            role = "Mobile Engineer",
            sourceUrl = "https://example.com/apply",
            jdHash = "hash111",
            eligibilityResult = "ELIGIBLE",
            state = ApplicationState.READY_FOR_REVIEW
        )
        tracker.registerApplication(app)

        val savedState = SavedStateHandle(mapOf(RunViewModel.KEY_APP_ID to app.id))
        val viewModel = RunViewModel(tracker, profileRepo, orchestrator, savedState)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("TechStart Inc", state.company)
        assertEquals("Mobile Engineer", state.role)
        assertEquals("https://example.com/apply", state.sourceUrl)
        assertEquals(AutomationStep.Idle, state.step)
        assertFalse(state.isPaused)
    }

    @Test
    fun `pause and resume controls update state and orchestrator`() = runTest(testDispatcher) {
        val app = ApplicationRecord(
            id = "app-run-2",
            company = "DataCore",
            role = "Data Engineer",
            sourceUrl = "https://example.com/apply2",
            jdHash = "hash222",
            eligibilityResult = "ELIGIBLE"
        )
        tracker.registerApplication(app)

        val viewModel = RunViewModel(tracker, profileRepo, orchestrator)
        viewModel.load(app.id)
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. User taps Pause
        viewModel.pause("User clicked pause")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPaused)
        assertEquals("User clicked pause", viewModel.uiState.value.pauseReason)
        assertTrue(viewModel.uiState.value.step is AutomationStep.Paused)

        // 2. User taps Resume
        viewModel.resume()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPaused)
        assertEquals(null, viewModel.uiState.value.pauseReason)
    }

    @Test
    fun `manual intervention handoff triggers pause and resumes on completion`() = runTest(testDispatcher) {
        val app = ApplicationRecord(
            id = "app-run-3",
            company = "CloudCorp",
            role = "Backend Dev",
            sourceUrl = "https://example.com/apply3",
            jdHash = "hash333",
            eligibilityResult = "ELIGIBLE"
        )
        tracker.registerApplication(app)

        val viewModel = RunViewModel(tracker, profileRepo, orchestrator)
        viewModel.load(app.id)
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Trigger handoff for CAPTCHA/OTP
        viewModel.triggerManualHandoff("Solve reCAPTCHA challenge")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.manualHandoffActive)
        assertEquals("Solve reCAPTCHA challenge", viewModel.uiState.value.manualHandoffMessage)
        assertTrue(viewModel.uiState.value.isPaused)

        // 2. User completes handoff
        viewModel.completeManualHandoff()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.manualHandoffActive)
        assertFalse(viewModel.uiState.value.isPaused)
    }

    @Test
    fun `cancel automation transitions application state to CANCELLED`() = runTest(testDispatcher) {
        val app = ApplicationRecord(
            id = "app-run-4",
            company = "FinTech Corp",
            role = "Systems Engineer",
            sourceUrl = "https://example.com/apply4",
            jdHash = "hash444",
            eligibilityResult = "ELIGIBLE",
            state = ApplicationState.READY_FOR_REVIEW
        )
        tracker.registerApplication(app)

        val viewModel = RunViewModel(tracker, profileRepo, orchestrator)
        viewModel.load(app.id)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.cancelAutomation("User stopped automation")
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedApp = tracker.getApplication(app.id)
        assertEquals(ApplicationState.CANCELLED, updatedApp?.state)
    }
}
