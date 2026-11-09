package com.cellclaw.automation

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.cellclaw.accessibility.AccessibilitySession
import com.cellclaw.approval.ApprovalTokenManager
import com.cellclaw.approval.ConfirmationDetector
import com.cellclaw.approval.SubmitOutcome
import com.cellclaw.approval.SubmitSupervisor
import com.cellclaw.browser.BrowserOpener
import com.cellclaw.browser.UrlValidator
import com.cellclaw.form.AccessibilityNodeData
import com.cellclaw.form.FieldClassifier
import com.cellclaw.form.FieldFiller
import com.cellclaw.form.FormNodeOperator
import com.cellclaw.form.NeedsInputCollector
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.InMemoryProfileRepository
import com.cellclaw.profile.ProfileFact
import com.cellclaw.profile.ProfileFactSource
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.InMemoryApplicationTracker
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OrchestratorFlowTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tracker: InMemoryApplicationTracker
    private lateinit var profileRepo: InMemoryProfileRepository
    private lateinit var tokenManager: ApprovalTokenManager
    private lateinit var fieldClassifier: FieldClassifier
    private lateinit var nodeOperator: TrackingNodeOperator
    private lateinit var needsInputCollector: NeedsInputCollector
    private lateinit var fieldFiller: FieldFiller
    private lateinit var resumeUploader: ResumeUploader
    private lateinit var postUploadVerifier: PostUploadVerifier
    private lateinit var submitSupervisor: SubmitSupervisor
    private lateinit var accessibilitySession: AccessibilitySession
    private lateinit var browserOpener: BrowserOpener
    private lateinit var orchestrator: AutomationOrchestrator

    private var lastOpenedUrl: String? = null

    private inner class FakeBrowserContext : ContextWrapper(null) {
        override fun getPackageName(): String = "com.cellclaw"
    }

    private inner class FakeBrowserOpener(
        context: Context,
        validator: UrlValidator = UrlValidator()
    ) : BrowserOpener(context, validator) {
        override fun open(url: String, preferChrome: Boolean): com.cellclaw.browser.BrowserOpenResult {
            val validation = validateUrl(url)
            val (targetUrl, warning) = when (validation) {
                is com.cellclaw.browser.UrlValidationResult.Rejected -> return com.cellclaw.browser.BrowserOpenResult.Rejected(validation.reason)
                is com.cellclaw.browser.UrlValidationResult.WarningHttp -> validation.url to validation.message
                is com.cellclaw.browser.UrlValidationResult.Valid -> validation.normalizedUrl to null
            }
            lastOpenedUrl = targetUrl
            return if (warning != null) {
                com.cellclaw.browser.BrowserOpenResult.WarningOpened(targetUrl, warning, if (preferChrome) CHROME_PACKAGE else null)
            } else {
                com.cellclaw.browser.BrowserOpenResult.Success(targetUrl, if (preferChrome) CHROME_PACKAGE else null)
            }
        }
    }

    private class TrackingNodeOperator : FormNodeOperator {
        val filledTexts = mutableMapOf<String, String>()
        val clickedLabels = mutableListOf<String>()

        override fun setNodeText(node: AccessibilityNodeData, text: String): Boolean {
            filledTexts[node.combinedLabel] = text
            return true
        }

        override fun clickNode(node: AccessibilityNodeData): Boolean {
            clickedLabels.add(node.combinedLabel)
            return true
        }

        override fun readBackNodeText(node: AccessibilityNodeData): String? {
            return filledTexts[node.combinedLabel]
        }

        override fun isNodeChecked(node: AccessibilityNodeData): Boolean {
            return clickedLabels.contains(node.combinedLabel)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tracker = InMemoryApplicationTracker()
        profileRepo = InMemoryProfileRepository()
        tokenManager = ApprovalTokenManager()
        fieldClassifier = FieldClassifier()
        nodeOperator = TrackingNodeOperator()
        needsInputCollector = NeedsInputCollector()
        fieldFiller = FieldFiller(
            classifier = fieldClassifier,
            operator = nodeOperator,
            needsInputCollector = needsInputCollector
        )
        val uriProvider = FakeResumeUriProvider("content://com.cellclaw.fileprovider/resumes")
        resumeUploader = ResumeUploader(ResumeFileValidator(), uriProvider)
        postUploadVerifier = PostUploadVerifier()
        val detector = ConfirmationDetector()
        submitSupervisor = SubmitSupervisor(tokenManager, tracker, detector)
        accessibilitySession = AccessibilitySession()
        browserOpener = FakeBrowserOpener(FakeBrowserContext())

        orchestrator = AutomationOrchestrator(
            browserOpener = browserOpener,
            accessibilitySession = accessibilitySession,
            fieldClassifier = fieldClassifier,
            fieldFiller = fieldFiller,
            resumeUploader = resumeUploader,
            postUploadVerifier = postUploadVerifier,
            submitSupervisor = submitSupervisor,
            tracker = tracker,
            tokenManager = tokenManager
        )
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

    private fun createValidPdf(name: String, body: String = "Test Resume"): Pair<File, String> {
        val file = tempFolder.newFile(name)
        val content = "%PDF-1.4\n$body\n%%EOF"
        val bytes = content.toByteArray(Charsets.UTF_8)
        file.writeBytes(bytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sha256 = digest.joinToString("") { "%02x".format(it) }
        return file to sha256
    }

    /**
     * Builds a synthetic accessibility node tree mirroring docs/dummy_google_form.html.
     */
    private fun buildDummyFormNodes(includeUnknownFreeText: Boolean = false): List<AccessibilityNodeData> {
        val nodes = mutableListOf(
            AccessibilityNodeData(text = "Full Name *", className = "android.widget.EditText", isEditable = true),
            AccessibilityNodeData(text = "Email Address *", className = "android.widget.EditText", isEditable = true),
            AccessibilityNodeData(text = "Phone Number *", className = "android.widget.EditText", isEditable = true),
            AccessibilityNodeData(text = "College / University Name *", className = "android.widget.EditText", isEditable = true),
            AccessibilityNodeData(text = "Degree *", className = "android.widget.Spinner", roleDescription = "dropdown", isClickable = true),
            AccessibilityNodeData(text = "Branch / Major *", className = "android.widget.Spinner", roleDescription = "dropdown", isClickable = true),
            AccessibilityNodeData(text = "Current CGPA (out of 10.0) *", className = "android.widget.EditText", isEditable = true),
            AccessibilityNodeData(text = "Graduation Year *", className = "android.widget.EditText", isEditable = true),
            AccessibilityNodeData(text = "Are you willing to relocate? *", className = "android.widget.RadioButton", roleDescription = "radio", isClickable = true),
            AccessibilityNodeData(text = "Technical Skills (Select all that apply)", className = "android.widget.CheckBox", roleDescription = "checkbox", isClickable = true),
            AccessibilityNodeData(text = "Upload Tailored Resume (PDF only, max 5 MB) *", className = "android.widget.Button", roleDescription = "file upload", isClickable = true)
        )

        if (includeUnknownFreeText) {
            nodes.add(
                AccessibilityNodeData(text = "Why are you interested in this position?", className = "android.widget.EditText", roleDescription = "free text", isEditable = true)
            )
        }

        nodes.add(
            AccessibilityNodeData(text = "I confirm that all provided details and resume contents are strictly derived from confirmed facts. *", className = "android.widget.CheckBox", roleDescription = "declaration", isClickable = true)
        )
        nodes.add(
            AccessibilityNodeData(text = "Submit", className = "android.widget.Button", isClickable = true)
        )

        return nodes
    }

    @Test
    fun `complete supervised automation flow fills form, stops before submit, and consumes ApprovalToken on user approval`() = runTest(testDispatcher) {
        val (pdfFile, pdfHash) = createValidPdf("resume_acme_v1.pdf")

        val profile = CandidateProfile(
            name = fact("name", "Rohith Sheregar"),
            email = fact("email", "rohith@example.com"),
            phone = fact("phone", "+91 9876543210"),
            college = fact("college", "National Institute of Technology"),
            degree = fact("degree", "B.Tech"),
            branch = fact("branch", "Computer Science and Engineering"),
            cgpa = fact("cgpa", "9.15"),
            graduationYear = fact("graduationYear", "2026"),
            reusableAnswers = listOf(
                fact("relocate", "Yes"),
                fact("skills", "Kotlin, Android")
            )
        )

        val app = ApplicationRecord(
            id = "app-flow-1",
            company = "Acme Corp",
            role = "Android Engineer",
            sourceUrl = "https://docs.google.com/forms/d/e/1FAIpQLSc_TEST/viewform",
            jdHash = "jdhash111",
            resumeHash = pdfHash,
            eligibilityResult = "ELIGIBLE",
            state = ApplicationState.READY_FOR_REVIEW
        )
        tracker.registerApplication(app)

        val formHash = tokenManager.computeFormStateHash(mapOf("Full Name" to "Rohith Sheregar", "Email Address" to "rohith@example.com"))

        // Issue ApprovalToken strictly with direct user tap
        val tokenResult = tokenManager.issueToken(
            userDirectTapConfirmed = true,
            applicationId = app.id,
            company = app.company,
            role = app.role,
            urlOrigin = "https://docs.google.com",
            formStateHash = formHash,
            resumeHash = pdfHash
        )
        val token = (tokenResult as com.cellclaw.approval.TokenIssuanceResult.Success).token

        val dummyNodes = buildDummyFormNodes(includeUnknownFreeText = false)

        // 1. Run automation sequence
        val step = orchestrator.runAutomation(
            application = app,
            token = token,
            profile = profile,
            resumeFile = pdfFile,
            nodeProvider = { dummyNodes }
        )

        // 2. Assertions on intermediate state
        assertNotNull(lastOpenedUrl)
        assertTrue(lastOpenedUrl?.contains("docs.google.com") == true)
        assertTrue(accessibilitySession.isActive())

        // Form fields were filled
        assertEquals("Rohith Sheregar", nodeOperator.filledTexts["Full Name *"])
        assertEquals("rohith@example.com", nodeOperator.filledTexts["Email Address *"])
        assertEquals("+91 9876543210", nodeOperator.filledTexts["Phone Number *"])
        assertEquals("National Institute of Technology", nodeOperator.filledTexts["College / University Name *"])
        assertEquals("9.15", nodeOperator.filledTexts["Current CGPA (out of 10.0) *"])
        assertEquals("2026", nodeOperator.filledTexts["Graduation Year *"])

        // Tracker transitioned to SUBMITTING while automation is active
        assertEquals(ApplicationState.SUBMITTING, tracker.getApplication(app.id)?.state)

        // PlacementOS hard rule: Automation MUST STOP before Submit for explicit user confirmation
        assertEquals(AutomationStep.AwaitingUserFinalSubmitApproval, step)
        assertEquals(AutomationStep.AwaitingUserFinalSubmitApproval, orchestrator.step.value)

        // 3. User approves final submit
        val outcome = orchestrator.executeFinalSubmit(
            tapSubmitAction = {
                // Simulate tapping Submit button
                nodeOperator.clickNode(AccessibilityNodeData(text = "Submit"))
            },
            fetchPostSubmitPageTexts = {
                listOf(
                    "Your response has been recorded.",
                    "Thank you for submitting your application.",
                    "Reference: CONF-TEST-2026-001"
                )
            }
        )

        // 4. Assertions on final submit outcome
        assertTrue("Outcome must be Confirmed", outcome is SubmitOutcome.Confirmed)
        val confirmed = outcome as SubmitOutcome.Confirmed
        assertEquals("CONF-TEST-2026-001", confirmed.reference)
        assertTrue(confirmed.message.contains("Submission confirmed"))

        // Orchestrator step is Completed
        val finalStep = orchestrator.step.value
        assertTrue(finalStep is AutomationStep.Completed)
        assertEquals("CONF-TEST-2026-001", (finalStep as AutomationStep.Completed).confirmationReference)

        // Tracker transitioned to terminal confirmed state
        val updatedApp = tracker.getApplication(app.id)
        assertEquals(ApplicationState.SUBMITTED_CONFIRMED, updatedApp?.state)
        assertEquals("CONF-TEST-2026-001", updatedApp?.confirmationReference)

        // Approval token has been consumed and cannot be reused
        val secondConsume = tokenManager.consumeToken(token.tokenId)
        assertEquals(false, secondConsume)
    }

    @Test
    fun `unmatched free-text field halts automation in NeedsUserInput step`() = runTest(testDispatcher) {
        val (pdfFile, pdfHash) = createValidPdf("resume_acme_v2.pdf")

        val profile = CandidateProfile(
            name = fact("name", "Rohith Sheregar"),
            email = fact("email", "rohith@example.com")
        )

        val app = ApplicationRecord(
            id = "app-flow-2",
            company = "Acme Corp",
            role = "Android Engineer",
            sourceUrl = "https://docs.google.com/forms/d/e/1FAIpQLSc_TEST/viewform",
            jdHash = "jdhash222",
            resumeHash = pdfHash,
            eligibilityResult = "ELIGIBLE",
            state = ApplicationState.READY_FOR_REVIEW
        )
        tracker.registerApplication(app)

        val tokenResult = tokenManager.issueToken(
            userDirectTapConfirmed = true,
            applicationId = app.id,
            company = app.company,
            role = app.role,
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash",
            resumeHash = pdfHash
        )
        val token = (tokenResult as com.cellclaw.approval.TokenIssuanceResult.Success).token

        // Include unknown free-text question in dummy form nodes
        val dummyNodesWithFreeText = buildDummyFormNodes(includeUnknownFreeText = true)

        val step = orchestrator.runAutomation(
            application = app,
            token = token,
            profile = profile,
            resumeFile = pdfFile,
            nodeProvider = { dummyNodesWithFreeText }
        )

        // Must pause in NeedsUserInput step and not proceed to final submission
        assertTrue("Must pause for user input", step is AutomationStep.NeedsUserInput)
        val needsInputStep = step as AutomationStep.NeedsUserInput
        assertTrue(needsInputStep.pendingFields.isNotEmpty())
        assertTrue(needsInputStep.pendingFields.any { it.label.contains("Why are you interested") })
    }

    @Test
    fun `dangerous URL rejects automation before opening browser`() = runTest(testDispatcher) {
        val app = ApplicationRecord(
            id = "app-flow-3",
            company = "Evil Corp",
            role = "Hacker",
            sourceUrl = "javascript:alert(document.cookie)",
            jdHash = "jdhash333",
            resumeHash = "somehash",
            eligibilityResult = "ELIGIBLE"
        )
        tracker.registerApplication(app)

        val tokenResult = tokenManager.issueToken(
            userDirectTapConfirmed = true,
            applicationId = app.id,
            company = app.company,
            role = app.role,
            urlOrigin = "javascript:",
            formStateHash = "hash",
            resumeHash = "somehash"
        )
        val token = (tokenResult as com.cellclaw.approval.TokenIssuanceResult.Success).token

        val step = orchestrator.runAutomation(
            application = app,
            token = token,
            profile = CandidateProfile(),
            resumeFile = null,
            nodeProvider = { emptyList() }
        )

        assertTrue(step is AutomationStep.Failed)
        val failed = step as AutomationStep.Failed
        assertTrue(failed.error.contains("URL rejected"))
        assertEquals(null, lastOpenedUrl)
    }

    @Test
    fun `multi-page form advances through Next buttons until Submit page`() = runTest(testDispatcher) {
        val app = ApplicationRecord(
            id = "app-flow-multipage",
            company = "MultiPage Corp",
            role = "Software Engineer",
            sourceUrl = "https://docs.google.com/forms/d/e/multi-page/viewform",
            jdHash = "jdhashmulti",
            resumeHash = "somehash",
            eligibilityResult = "ELIGIBLE"
        )
        tracker.registerApplication(app)

        val tokenResult = tokenManager.issueToken(
            userDirectTapConfirmed = true,
            applicationId = app.id,
            company = app.company,
            role = app.role,
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash",
            resumeHash = "somehash"
        )
        val token = (tokenResult as com.cellclaw.approval.TokenIssuanceResult.Success).token

        val profile = CandidateProfile(
            name = fact("name", "Rohith Sheregar"),
            email = fact("email", "rohith@example.com")
        )

        var callCount = 0
        val page1Nodes = listOf(
            AccessibilityNodeData(text = "Full Name *", className = "android.widget.EditText", isEditable = true),
            AccessibilityNodeData(text = "Next", className = "android.widget.Button", isClickable = true)
        )
        val page2Nodes = listOf(
            AccessibilityNodeData(text = "Email Address *", className = "android.widget.EditText", isEditable = true),
            AccessibilityNodeData(text = "Submit", className = "android.widget.Button", isClickable = true)
        )

        val step = orchestrator.runAutomation(
            application = app,
            token = token,
            profile = profile,
            resumeFile = null,
            nodeProvider = {
                callCount++
                if (callCount == 1) page1Nodes else page2Nodes
            }
        )

        assertTrue("Must click Next button", nodeOperator.clickedLabels.any { it.contains("Next", ignoreCase = true) })
        assertTrue("Must reach AwaitingUserFinalSubmitApproval on page 2", step is AutomationStep.AwaitingUserFinalSubmitApproval)
    }
}
