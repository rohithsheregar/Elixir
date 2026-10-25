package com.cellclaw.accessibility

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import com.cellclaw.approval.ApprovalTokenManager
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.InMemoryProfileRepository
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.InMemoryApplicationTracker
import com.cellclaw.ui.viewmodel.ReviewViewModel
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccessibilityEnablePromptTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tracker: InMemoryApplicationTracker
    private lateinit var profileRepo: InMemoryProfileRepository
    private lateinit var tokenManager: ApprovalTokenManager
    private lateinit var fakeContext: FakeTestContext
    private lateinit var fakeChecker: FakeAccessibilityChecker

    private var startedIntent: Intent? = null

    private inner class FakeTestContext : ContextWrapper(null) {
        override fun getPackageName(): String = "com.cellclaw"
        override fun startActivity(intent: Intent) {
            startedIntent = intent
        }
    }

    private class FakeAccessibilityChecker(
        context: Context,
        var isServiceActive: Boolean = false
    ) : AccessibilityChecker(context) {
        override fun isEnabled(): Boolean = isServiceActive
        override fun isServiceEnabled(context: Context): Boolean = isServiceActive
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tracker = InMemoryApplicationTracker()
        profileRepo = InMemoryProfileRepository()
        tokenManager = ApprovalTokenManager()
        fakeContext = FakeTestContext()
        fakeChecker = FakeAccessibilityChecker(fakeContext, isServiceActive = false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `settings intent creates valid intent`() {
        val checker = AccessibilityChecker(fakeContext)
        val intent = checker.createSettingsIntent(fakeContext)
        assertNotNull("Settings intent must not be null", intent)
    }

    @Test
    fun `approval blocked and prompt triggered when accessibility service is disabled`() = runTest(testDispatcher) {
        val app = ApplicationRecord(
            id = "app-a11y-1",
            company = "Acme Corp",
            role = "Software Engineer",
            sourceUrl = "https://example.com/apply",
            jdHash = "hash123",
            eligibilityResult = "ELIGIBLE"
        )
        tracker.registerApplication(app)
        profileRepo.saveProfile(CandidateProfile())

        fakeChecker.isServiceActive = false

        val viewModel = ReviewViewModel(
            applicationTracker = tracker,
            profileRepository = profileRepo,
            tokenManager = tokenManager,
            accessibilityChecker = fakeChecker
        )

        viewModel.load(app.id)
        testDispatcher.scheduler.advanceUntilIdle()

        var tokenIssued = false
        viewModel.approveSubmission(userDirectTapConfirmed = true) {
            tokenIssued = true
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Must NOT issue token and must NOT advance state
        assertFalse("Token callback must not be invoked when accessibility is disabled", tokenIssued)
        assertNull(viewModel.uiState.value.approvalToken)
        assertEquals(ApplicationState.DRAFT, tracker.getApplication(app.id)?.state)

        // 2. Prompt state is raised
        assertTrue("showAccessibilityPrompt must be true", viewModel.uiState.value.showAccessibilityPrompt)

        // 3. User dismisses prompt
        viewModel.dismissAccessibilityPrompt()
        assertFalse("showAccessibilityPrompt must be reset to false", viewModel.uiState.value.showAccessibilityPrompt)

        // 4. User opens settings via prompt
        viewModel.openAccessibilitySettings(fakeContext)
        assertNotNull("Settings intent must have been launched on context", startedIntent)
    }

    @Test
    fun `approval succeeds immediately when accessibility service is enabled`() = runTest(testDispatcher) {
        val app = ApplicationRecord(
            id = "app-a11y-2",
            company = "Beta Labs",
            role = "Android Developer",
            sourceUrl = "https://example.com/careers",
            jdHash = "hash456",
            eligibilityResult = "ELIGIBLE"
        )
        tracker.registerApplication(app)
        profileRepo.saveProfile(CandidateProfile())

        // Accessibility service is active
        fakeChecker.isServiceActive = true

        val viewModel = ReviewViewModel(
            applicationTracker = tracker,
            profileRepository = profileRepo,
            tokenManager = tokenManager,
            accessibilityChecker = fakeChecker
        )

        viewModel.load(app.id)
        testDispatcher.scheduler.advanceUntilIdle()

        var tokenIssued = false
        viewModel.approveSubmission(userDirectTapConfirmed = true) {
            tokenIssued = true
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Accessibility prompt is NOT shown
        assertFalse(viewModel.uiState.value.showAccessibilityPrompt)

        // 2. Token is issued and state transitions to READY_FOR_REVIEW
        assertTrue(tokenIssued)
        assertNotNull(viewModel.uiState.value.approvalToken)
        assertEquals(ApplicationState.READY_FOR_REVIEW, tracker.getApplication(app.id)?.state)
        assertEquals(ApplicationState.READY_FOR_REVIEW, viewModel.uiState.value.currentState)
    }
}
