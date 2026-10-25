package com.cellclaw.ui

import androidx.lifecycle.SavedStateHandle
import com.cellclaw.approval.ApprovalTokenManager
import com.cellclaw.approval.TokenValidationResult
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.InMemoryProfileRepository
import com.cellclaw.profile.ProfileFact
import com.cellclaw.profile.ProfileFactSource
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.InMemoryApplicationTracker
import com.cellclaw.ui.viewmodel.ReviewViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewApprovalWiringTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tracker: InMemoryApplicationTracker
    private lateinit var profileRepo: InMemoryProfileRepository
    private lateinit var tokenManager: ApprovalTokenManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tracker = InMemoryApplicationTracker()
        profileRepo = InMemoryProfileRepository()
        tokenManager = ApprovalTokenManager()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val timestamp = Instant.parse("2026-01-01T00:00:00Z")

    private fun fact(field: String, value: String, confirmed: Boolean = true, id: String = "$field-1") = ProfileFact(
        id = id,
        field = field,
        value = value,
        source = ProfileFactSource.USER_ENTERED,
        confirmed = confirmed,
        updatedAt = timestamp
    )

    @Test
    fun `approveSubmission rejects token when not confirmed by direct user tap`() = runTest(testDispatcher) {
        val app = ApplicationRecord(
            id = "app-direct-tap-1",
            company = "FinTech Labs",
            role = "Security Engineer",
            sourceUrl = "https://fintech.example.com/apply",
            jdHash = "jdhash123",
            eligibilityResult = "ELIGIBLE"
        )
        tracker.registerApplication(app)

        val viewModel = ReviewViewModel(
            applicationTracker = tracker,
            profileRepository = profileRepo,
            tokenManager = tokenManager,
            savedStateHandle = SavedStateHandle(mapOf("appId" to app.id))
        )
        advanceUntilIdle()

        // Attempt approval with simulated background/model invocation without direct user tap
        viewModel.approveSubmission(userDirectTapConfirmed = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull("ApprovalToken must not be issued without direct user tap", state.approvalToken)
        assertTrue(state.errorMessage?.contains("Security policy violation") == true)
        assertEquals(ApplicationState.DRAFT, state.currentState)
        assertEquals(ApplicationState.DRAFT, tracker.getApplication(app.id)?.state)
    }

    @Test
    fun `approveSubmission creates cryptographically bound ApprovalToken and transitions to READY_FOR_REVIEW without entering SUBMITTING`() = runTest(testDispatcher) {
        profileRepo.saveProfile(
            CandidateProfile(
                name = fact("name", "Jane Doe"),
                email = fact("email", "jane@example.com"),
                phone = fact("phone", "+91 9123456789")
            )
        )

        val app = ApplicationRecord(
            id = "app-bound-1",
            company = "Stripe",
            role = "Backend Systems Engineer",
            sourceUrl = "https://stripe.com/jobs/apply",
            jdHash = "jdhash456",
            resumeHash = "resume-sha256-abcdef1234567890",
            eligibilityResult = "ELIGIBLE"
        )
        tracker.registerApplication(app)

        val viewModel = ReviewViewModel(
            applicationTracker = tracker,
            profileRepository = profileRepo,
            tokenManager = tokenManager,
            savedStateHandle = SavedStateHandle(mapOf("appId" to app.id))
        )
        advanceUntilIdle()

        var callbackTokenReceived = false
        viewModel.approveSubmission(userDirectTapConfirmed = true) { token ->
            callbackTokenReceived = true
            assertNotNull(token)
            assertEquals("Stripe", token.company)
            assertEquals("Backend Systems Engineer", token.role)
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Callback must receive issued token", callbackTokenReceived)
        assertNotNull("ApprovalToken must be stored in uiState", state.approvalToken)

        val token = state.approvalToken!!
        assertEquals(app.id, token.applicationId)
        assertEquals("Stripe", token.company)
        assertEquals("Backend Systems Engineer", token.role)
        assertEquals("https://stripe.com", token.urlOrigin)
        assertEquals(app.resumeHash, token.resumeHash)
        assertNotNull(token.formStateHash)

        // Strict state machine invariant: must transition to READY_FOR_REVIEW, NEVER early SUBMITTING
        assertEquals(ApplicationState.READY_FOR_REVIEW, state.currentState)
        assertEquals(ApplicationState.READY_FOR_REVIEW, tracker.getApplication(app.id)?.state)

        // Validate token cryptographic binding against tokenManager
        val validation = tokenManager.validateToken(
            tokenId = token.tokenId,
            company = "Stripe",
            role = "Backend Systems Engineer",
            urlOrigin = "https://stripe.com",
            formStateHash = token.formStateHash,
            resumeHash = app.resumeHash!!
        )
        assertEquals(TokenValidationResult.Valid, validation)

        // Validate mismatch detection on tampered origin or company
        val tamperedValidation = tokenManager.validateToken(
            tokenId = token.tokenId,
            company = "OtherCorp",
            role = "Backend Systems Engineer",
            urlOrigin = "https://stripe.com",
            formStateHash = token.formStateHash,
            resumeHash = app.resumeHash!!
        )
        assertTrue(tamperedValidation is TokenValidationResult.CompanyMismatch)
    }
}
