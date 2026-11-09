package com.cellclaw.tools

import com.cellclaw.browser.UrlValidator
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.InMemoryApplicationTracker
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class JobSearchToolTest {

    private lateinit var urlValidator: UrlValidator
    private lateinit var applicationTracker: InMemoryApplicationTracker
    private lateinit var tool: JobSearchTool

    @Before
    fun setUp() {
        urlValidator = UrlValidator()
        applicationTracker = InMemoryApplicationTracker()
        tool = JobSearchTool(
            urlValidator = urlValidator,
            applicationTracker = applicationTracker
        )
    }

    @Test
    fun testToolMetadataAndRiskTier() {
        assertEquals("job.search", tool.name)
        assertEquals(RiskTier.READ, tool.riskTier)
        assertFalse("job.search is read-only and requires no human approval token", tool.requiresApproval)
        assertTrue(tool.parameters.required.contains("query"))
    }

    @Test
    fun testParseJobResultsExtractsStructuredJobOpenings() {
        val sampleDdgHtml = """
            <div class="result results_links">
                <h2 class="result__title">
                    <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fcareers.google.com%2Fjobs%2Fresults%2F123&rut=abc">
                        Software Engineer, Android at Google
                    </a>
                </h2>
                <a class="result__snippet" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fcareers.google.com%2Fjobs%2Fresults%2F123">
                    Google is hiring an Android Software Engineer in Bangalore, India. Requirements: Kotlin, Java, Jetpack Compose.
                </a>
            </div>
            <div class="result results_links">
                <h2 class="result__title">
                    <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fjobs.netflix.com%2Fjobs%2F456&rut=xyz">
                        Senior Mobile Developer - Netflix
                    </a>
                </h2>
                <a class="result__snippet" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fjobs.netflix.com%2Fjobs%2F456">
                    Join Netflix Mobile Engineering team to craft next-generation streaming experiences.
                </a>
            </div>
        """.trimIndent()

        val results = tool.parseJobResults(sampleDdgHtml)

        assertEquals(2, results.size)

        val first = results[0]
        assertEquals("Software Engineer, Android at Google", first["title"]?.jsonPrimitive?.content)
        assertEquals("Google", first["company"]?.jsonPrimitive?.content)
        assertEquals("Software Engineer, Android", first["role"]?.jsonPrimitive?.content)
        assertEquals("https://careers.google.com/jobs/results/123", first["url"]?.jsonPrimitive?.content)
        assertTrue(first["snippet"]?.jsonPrimitive?.content?.contains("Bangalore, India") == true)

        val second = results[1]
        assertEquals("Senior Mobile Developer - Netflix", second["title"]?.jsonPrimitive?.content)
        assertEquals("Netflix", second["company"]?.jsonPrimitive?.content)
        assertEquals("Senior Mobile Developer", second["role"]?.jsonPrimitive?.content)
        assertEquals("https://jobs.netflix.com/jobs/456", second["url"]?.jsonPrimitive?.content)
    }

    @Test
    fun testAutoStageTopResultIntoTracker() = runTest {
        val sampleHtml = """
            <div class="result results_links">
                <h2 class="result__title">
                    <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fcareers.amazon.jobs%2Fjob%2F789&rut=1">
                        SDE 1 (Android) at Amazon
                    </a>
                </h2>
                <a class="result__snippet" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fcareers.amazon.jobs%2Fjob%2F789">
                    Amazon campus hiring for fresher software development engineers.
                </a>
            </div>
        """.trimIndent()

        // Test parsing and tracker integration
        val parsed = tool.parseJobResults(sampleHtml)
        assertEquals(1, parsed.size)
        val top = parsed.first()

        val topCompany = top["company"]?.jsonPrimitive?.content ?: ""
        val topRole = top["role"]?.jsonPrimitive?.content ?: ""
        val topUrl = top["url"]?.jsonPrimitive?.content ?: ""

        val stagedRecord = com.cellclaw.tracker.ApplicationRecord(
            company = topCompany,
            role = topRole,
            sourceUrl = topUrl,
            jdHash = "",
            eligibilityResult = "UNKNOWN",
            state = ApplicationState.DRAFT,
            notes = "Discovered via automated internet research query: Android Fresher"
        )
        val regResult = applicationTracker.registerApplication(stagedRecord)
        assertFalse(regResult.hasLikelyDuplicate)

        // Verify record in tracker
        val retrieved = applicationTracker.getApplication(stagedRecord.id)
        assertNotNull(retrieved)
        assertEquals("Amazon", retrieved?.company)
        assertEquals("SDE 1 (Android)", retrieved?.role)
        assertEquals(ApplicationState.DRAFT, retrieved?.state)
    }

    @Test
    fun testMissingQueryReturnsError() = runTest {
        val params = buildJsonObject {
            put("location", "Bangalore")
        }
        val result = tool.execute(params)
        assertFalse(result.success)
        assertEquals("Missing 'query' parameter", result.error)
    }
}

