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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NeedsInputResolutionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tracker: InMemoryApplicationTracker
    private lateinit var profileRepo: InMemoryProfileRepository
    private lateinit var tokenManager: ApprovalTokenManager

    private val timestamp = Instant.parse("2026-01-01T00:00:00Z")

    private fun fact(field: String, value: String, confirmed: Boolean = true, id: String = "$field-1") = ProfileFact(
        id = id,
        field = field,
        value = value,
        source = ProfileFactSource.USER_ENTERED,
        confirmed = confirmed,
        updatedAt = timestamp
    )

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

    @Test
    fun `unanswered and sensitive fields are collected into needsInputItems`() = runTest(testDispatcher) {
        // Candidate has no phone number confirmed
        val profile = CandidateProfile(
            name = fact("name", "Alice Wonder"),
            email = fact("email", "alice@example.com")
            // phone is null
        )
        profileRepo.saveProfile(profile)

        val app = ApplicationRecord(
            id = "app-needs-1",
            company = "TechVentures",
            role = "Android Lead",
            sourceUrl = "https://example.com/apply",
            jdHash = "jd123",
            eligibilityResult = "ELIGIBLE",
            state = ApplicationState.DRAFT,
            resumeHash = "res123"
        )
        tracker.registerApplication(app)

        val viewModel = ReviewViewModel(tracker, profileRepo, tokenManager)
        viewModel.load(app.id)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Phone number should be in unanswered fields", state.unansweredFields.contains("Phone Number"))

        val phoneItem = state.needsInputItems.find { it.label == "Phone Number" }
        assertNotNull("Phone item must be in needsInputItems", phoneItem)
        assertFalse("Phone item must start unresolved", phoneItem!!.isResolved)
        assertEquals("Missing Profile Fact", phoneItem.category)

        val ctcItem = state.needsInputItems.find { it.id == "sensitive_stipend_ctc" }
        assertNotNull("CTC/Stipend question must be in needsInputItems", ctcItem)
        assertFalse(ctcItem!!.isResolved)
        assertEquals("Sensitive Compensation Question", ctcItem.category)

        val bondItem = state.needsInputItems.find { it.id == "sensitive_service_bond" }
        assertNotNull("Bond item must be in needsInputItems", bondItem)
        assertFalse(bondItem!!.isResolved)
    }

    @Test
    fun `modal open and dismiss updates showNeedsInputModal state`() = runTest(testDispatcher) {
        val app = ApplicationRecord(
            id = "app-needs-2",
            company = "CloudScale",
            role = "Backend Dev",
            sourceUrl = "https://example.com/apply2",
            jdHash = "jd234",
            eligibilityResult = "ELIGIBLE"
        )
        tracker.registerApplication(app)

        val viewModel = ReviewViewModel(tracker, profileRepo, tokenManager)
        viewModel.load(app.id)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showNeedsInputModal)

        viewModel.openNeedsInputModal()
        assertTrue(viewModel.uiState.value.showNeedsInputModal)

        viewModel.dismissNeedsInputModal()
        assertFalse(viewModel.uiState.value.showNeedsInputModal)
    }

    @Test
    fun `resolveFieldInput marks item resolved, updates filledFields, and clears unanswered status`() = runTest(testDispatcher) {
        val profile = CandidateProfile(
            name = fact("name", "Bob Builder"),
            email = fact("email", "bob@example.com")
            // phone is missing
        )
        profileRepo.saveProfile(profile)

        val app = ApplicationRecord(
            id = "app-needs-3",
            company = "InnoCorp",
            role = "Mobile Dev",
            sourceUrl = "https://example.com/apply3",
            jdHash = "jd345",
            eligibilityResult = "ELIGIBLE",
            state = ApplicationState.DRAFT,
            resumeHash = "res345"
        )
        tracker.registerApplication(app)

        val savedState = SavedStateHandle()
        val viewModel = ReviewViewModel(tracker, profileRepo, tokenManager, savedStateHandle = savedState)
        viewModel.load(app.id)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.unansweredFields.contains("Phone Number"))

        // 1. User answers the missing phone question
        viewModel.resolveFieldInput("missing_phone_number", "+91 9876543210")

        val stateAfterPhone = viewModel.uiState.value
        assertFalse("Phone Number should be removed from unanswered", stateAfterPhone.unansweredFields.contains("Phone Number"))
        val resolvedPhoneItem = stateAfterPhone.needsInputItems.find { it.id == "missing_phone_number" }
        assertNotNull(resolvedPhoneItem)
        assertTrue(resolvedPhoneItem!!.isResolved)
        assertEquals("+91 9876543210", resolvedPhoneItem.currentValue)

        val filledPhone = stateAfterPhone.filledFields.find { it.label == "Phone Number" }
        assertNotNull("Filled fields must now include Phone Number", filledPhone)
        assertEquals("+91 9876543210", filledPhone!!.value)

        // 2. User answers the sensitive compensation question
        viewModel.resolveFieldInput("sensitive_stipend_ctc", "18 LPA / 1.5 Lakhs pm")

        val stateAfterCtc = viewModel.uiState.value
        val filledCtc = stateAfterCtc.filledFields.find { it.label == "Expected CTC / Stipend" }
        assertNotNull("Filled fields must include CTC", filledCtc)
        assertEquals("18 LPA / 1.5 Lakhs pm", filledCtc!!.value)

        // 3. User approves submission — approval token binds the resolved values
        var issuedToken: com.cellclaw.approval.ApprovalToken? = null
        viewModel.approveSubmission(userDirectTapConfirmed = true) { token ->
            issuedToken = token
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull("Approval token must be issued", issuedToken)
        val validation = tokenManager.validateToken(
            tokenId = issuedToken!!.tokenId,
            company = app.company,
            role = app.role,
            urlOrigin = "https://example.com",
            formStateHash = tokenManager.computeFormStateHash(viewModel.uiState.value.filledFields.associate { it.label to it.value }),
            resumeHash = app.resumeHash!!
        )
        assertTrue("Token must be valid with resolved inputs", validation is TokenValidationResult.Valid)
    }

    @Test
    fun `resolutions persist across savedStateHandle restoration`() = runTest(testDispatcher) {
        val app = ApplicationRecord(
            id = "app-needs-4",
            company = "NextGen",
            role = "QA Engineer",
            sourceUrl = "https://example.com/apply4",
            jdHash = "jd456",
            eligibilityResult = "ELIGIBLE",
            state = ApplicationState.DRAFT
        )
        tracker.registerApplication(app)

        val savedState = SavedStateHandle(mapOf(
            ReviewViewModel.KEY_APP_ID to app.id,
            "resolved_missing_phone_number" to "+1 555-0199",
            "resolved_sensitive_service_bond" to "Agreed (1 Year)"
        ))

        val viewModel = ReviewViewModel(tracker, profileRepo, tokenManager, savedStateHandle = savedState)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val phoneField = state.filledFields.find { it.label == "Phone Number" }
        assertNotNull("Phone Number should be restored into filledFields", phoneField)
        assertEquals("+1 555-0199", phoneField!!.value)

        val bondField = state.filledFields.find { it.label == "Service Bond / Agreement" }
        assertNotNull("Bond answer should be restored into filledFields", bondField)
        assertEquals("Agreed (1 Year)", bondField!!.value)
    }
}
