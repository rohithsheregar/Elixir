package com.cellclaw.ui

import com.cellclaw.eligibility.EligibilityEngine
import com.cellclaw.jd.DocumentReadResult
import com.cellclaw.jd.JdDocumentReader
import com.cellclaw.jd.JdParser
import com.cellclaw.profile.InMemoryProfileRepository
import com.cellclaw.tracker.InMemoryApplicationTracker
import com.cellclaw.ui.viewmodel.NewApplicationViewModel
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
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ShareIntentTest {

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

    private fun loadManifest(): String {
        val manifestFile = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).firstOrNull { it.exists() } ?: error("AndroidManifest.xml not found")
        return manifestFile.readText()
    }

    @Test
    fun `manifest configures ACTION_SEND intent-filters on MainActivity`() {
        val manifest = loadManifest().replace(Regex("<!--[\\s\\S]*?-->"), "")

        assertTrue(
            "MainActivity must declare intent-filter with ACTION_SEND",
            manifest.contains("<action android:name=\"android.intent.action.SEND\" />")
        )
        assertTrue(
            "MainActivity must declare CATEGORY_DEFAULT for share intents",
            manifest.contains("<category android:name=\"android.intent.category.DEFAULT\" />")
        )
        assertTrue(
            "MainActivity must declare mimeType text/plain",
            manifest.contains("<data android:mimeType=\"text/plain\" />")
        )
        assertTrue(
            "MainActivity must declare mimeType application/pdf",
            manifest.contains("<data android:mimeType=\"application/pdf\" />")
        )
        assertTrue(
            "MainActivity must declare mimeType text/*",
            manifest.contains("<data android:mimeType=\"text/*\" />")
        )
    }

    @Test
    fun `shared plain JD text pre-fills jdText and parses company and role`() = runTest(testDispatcher) {
        val viewModel = NewApplicationViewModel(
            jdParser = jdParser,
            jdDocumentReader = jdDocumentReader,
            eligibilityEngine = eligibilityEngine,
            profileRepository = profileRepo,
            applicationTracker = tracker
        )

        val sharedJdText = """
            Company: Acme Innovations
            Role: Mobile Android Engineer
            Required Skills: Kotlin, Jetpack Compose
            Minimum CGPA: 8.0
        """.trimIndent()

        // Simulating the prefill logic performed on shared text in NewApplication
        viewModel.onJdTextChanged(sharedJdText)
        viewModel.parseCurrentJd()

        val state = viewModel.uiState.value
        assertEquals(sharedJdText, state.jdText)
        assertEquals("Acme Innovations", state.company)
        assertEquals("Mobile Android Engineer", state.role)
        assertNotNull(state.parsedRequirement)
        assertEquals(listOf("Kotlin", "Jetpack Compose"), state.parsedRequirement!!.requiredSkills.value)
    }

    @Test
    fun `shared URL pre-fills sourceUrl without overwriting jdText`() = runTest(testDispatcher) {
        val viewModel = NewApplicationViewModel(
            jdParser = jdParser,
            jdDocumentReader = jdDocumentReader,
            eligibilityEngine = eligibilityEngine,
            profileRepository = profileRepo,
            applicationTracker = tracker
        )

        val sharedUrl = "https://forms.gle/abc123xyzPlacementForm"

        // Simulating the prefill logic when shared text is a pure URL
        if ((sharedUrl.startsWith("http://") || sharedUrl.startsWith("https://")) && !sharedUrl.contains("\n") && !sharedUrl.contains(" ")) {
            viewModel.onSourceUrlChanged(sharedUrl)
        } else {
            viewModel.onJdTextChanged(sharedUrl)
        }

        val state = viewModel.uiState.value
        assertEquals(sharedUrl, state.sourceUrl)
        assertEquals("", state.jdText)
    }

    @Test
    fun `shared document stream processes content and parses JD`() = runTest(testDispatcher) {
        val viewModel = NewApplicationViewModel(
            jdParser = jdParser,
            jdDocumentReader = jdDocumentReader,
            eligibilityEngine = eligibilityEngine,
            profileRepository = profileRepo,
            applicationTracker = tracker
        )

        val documentContent = """
            Company: CloudScale Systems
            Role: Systems Engineer
            Required Skills: Linux, Go
            Minimum CGPA: 7.5
        """.trimIndent()

        val stream = ByteArrayInputStream(documentContent.toByteArray(Charsets.UTF_8))
        viewModel.processDocumentStream(stream, "text/plain", "job_description.txt")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(documentContent, state.jdText)
        assertEquals("CloudScale Systems", state.company)
        assertEquals("Systems Engineer", state.role)
    }
}
