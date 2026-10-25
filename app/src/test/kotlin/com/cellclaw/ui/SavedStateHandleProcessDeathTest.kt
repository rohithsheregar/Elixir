package com.cellclaw.ui

import androidx.lifecycle.SavedStateHandle
import com.cellclaw.eligibility.EligibilityEngine
import com.cellclaw.jd.JdDocumentReader
import com.cellclaw.jd.JdParser
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.InMemoryProfileRepository
import com.cellclaw.profile.ProfileFact
import com.cellclaw.profile.ProfileFactSource
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.InMemoryApplicationTracker
import com.cellclaw.ui.viewmodel.NewApplicationViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SavedStateHandleProcessDeathTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var profileRepo: InMemoryProfileRepository
    private lateinit var tracker: InMemoryApplicationTracker
    private lateinit var jdParser: JdParser
    private lateinit var eligibilityEngine: EligibilityEngine
    private lateinit var jdDocumentReader: JdDocumentReader

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        profileRepo = InMemoryProfileRepository()
        tracker = InMemoryApplicationTracker()
        jdParser = JdParser()
        eligibilityEngine = EligibilityEngine()
        jdDocumentReader = JdDocumentReader()
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
    fun `NewApplicationViewModel form inputs survive process death restoration`() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle()

        // 1. Initial ViewModel instance before process termination
        val initialVm = NewApplicationViewModel(
            savedStateHandle = savedStateHandle,
            jdParser = jdParser,
            jdDocumentReader = jdDocumentReader,
            eligibilityEngine = eligibilityEngine,
            profileRepository = profileRepo,
            applicationTracker = tracker
        )

        initialVm.onJdTextChanged("Looking for Senior Android Dev at InnovateCorp. Required: Kotlin, Coroutines.")
        initialVm.onSourceUrlChanged("https://innovatecorp.com/apply/123")
        initialVm.onCompanyChanged("InnovateCorp")
        initialVm.onRoleChanged("Senior Android Dev")

        // 2. Simulate process death: recreate ViewModel passing the surviving SavedStateHandle
        val restoredVm = NewApplicationViewModel(
            savedStateHandle = savedStateHandle,
            jdParser = jdParser,
            jdDocumentReader = jdDocumentReader,
            eligibilityEngine = eligibilityEngine,
            profileRepository = profileRepo,
            applicationTracker = tracker
        )

        val restoredState = restoredVm.uiState.value
        assertEquals("Looking for Senior Android Dev at InnovateCorp. Required: Kotlin, Coroutines.", restoredState.jdText)
        assertEquals("https://innovatecorp.com/apply/123", restoredState.sourceUrl)
        assertEquals("InnovateCorp", restoredState.company)
        assertEquals("Senior Android Dev", restoredState.role)
    }

    @Test
    fun `NewApplicationViewModel parsed document text survives process death`() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle()

        val initialVm = NewApplicationViewModel(
            savedStateHandle = savedStateHandle,
            jdParser = jdParser,
            jdDocumentReader = jdDocumentReader,
            eligibilityEngine = eligibilityEngine,
            profileRepository = profileRepo,
            applicationTracker = tracker
        )

        val documentContent = """
            Company: CloudScale Inc
            Role: Backend Developer
            Requirements: Go, Docker, Kubernetes
        """.trimIndent()

        initialVm.processDocumentStream(
            inputStream = ByteArrayInputStream(documentContent.toByteArray(Charsets.UTF_8)),
            mimeType = "text/plain",
            fileName = "jd.txt"
        )
        advanceUntilIdle()

        // Recreate ViewModel with surviving SavedStateHandle
        val restoredVm = NewApplicationViewModel(
            savedStateHandle = savedStateHandle,
            jdParser = jdParser,
            jdDocumentReader = jdDocumentReader,
            eligibilityEngine = eligibilityEngine,
            profileRepository = profileRepo,
            applicationTracker = tracker
        )

        val restoredState = restoredVm.uiState.value
        assertEquals(documentContent, restoredState.jdText)
        assertEquals("CloudScale Inc", restoredState.company)
        assertEquals("Backend Developer", restoredState.role)
    }

    @Test
    fun `ReviewViewModel automatically loads application when appId is in SavedStateHandle`() = runTest(testDispatcher) {
        profileRepo.saveProfile(
            CandidateProfile(
                name = fact("name", "Alex Hunter"),
                email = fact("email", "alex@example.com"),
                phone = fact("phone", "+1234567890")
            )
        )

        val app = ApplicationRecord(
            id = "app-alpha-1",
            company = "AlphaTech",
            role = "Staff Software Engineer",
            sourceUrl = "https://alphatech.com/careers/job1",
            jdHash = "hash123",
            eligibilityResult = "ELIGIBLE"
        )
        tracker.registerApplication(app)

        // Simulate Navigation component injecting {appId} into SavedStateHandle
        val savedStateHandle = SavedStateHandle(mapOf("appId" to app.id))

        val viewModel = ReviewViewModel(
            applicationTracker = tracker,
            profileRepository = profileRepo,
            savedStateHandle = savedStateHandle
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNotNull(state.application)
        assertEquals(app.id, state.application?.id)
        assertEquals("AlphaTech", state.company)
        assertEquals("Staff Software Engineer", state.role)
        assertEquals("alphatech.com", state.domain)
    }

    @Test
    fun `ReviewViewModel survives process death after approval and retains success state`() = runTest(testDispatcher) {
        profileRepo.saveProfile(
            CandidateProfile(
                name = fact("name", "Jordan Lee"),
                email = fact("email", "jordan@example.com")
            )
        )

        val app = ApplicationRecord(
            id = "app-nova-1",
            company = "Nova Robotics",
            role = "Embedded Systems Engineer",
            sourceUrl = "https://novarobotics.ai/apply",
            jdHash = "hash456",
            eligibilityResult = "ELIGIBLE"
        )
        tracker.registerApplication(app)

        val savedStateHandle = SavedStateHandle()

        // 1. Initial session
        val initialVm = ReviewViewModel(
            applicationTracker = tracker,
            profileRepository = profileRepo,
            savedStateHandle = savedStateHandle
        )
        initialVm.load(app.id)
        advanceUntilIdle()

        // User approves submission
        initialVm.approveSubmission()
        advanceUntilIdle()

        assertEquals(ApplicationState.SUBMITTING, initialVm.uiState.value.currentState)
        assertTrue(initialVm.uiState.value.actionSuccessMessage?.contains("Approved!") == true)

        // 2. Simulate process death: recreate ViewModel from the surviving handle
        val restoredVm = ReviewViewModel(
            applicationTracker = tracker,
            profileRepository = profileRepo,
            savedStateHandle = savedStateHandle
        )
        advanceUntilIdle()

        val restoredState = restoredVm.uiState.value
        assertEquals(app.id, restoredState.application?.id)
        assertEquals("Nova Robotics", restoredState.company)
        assertEquals(ApplicationState.SUBMITTING, restoredState.currentState)
        assertTrue(restoredState.actionSuccessMessage?.contains("Approved!") == true)
    }
}
