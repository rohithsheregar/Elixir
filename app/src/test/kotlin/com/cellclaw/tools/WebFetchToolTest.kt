package com.cellclaw.tools

import com.cellclaw.browser.UrlValidator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WebFetchToolTest {

    private lateinit var urlValidator: UrlValidator
    private lateinit var tool: WebFetchTool

    @Before
    fun setUp() {
        urlValidator = UrlValidator()
        tool = WebFetchTool(urlValidator = urlValidator)
    }

    @Test
    fun testToolMetadataAndRiskTier() {
        assertEquals("web.fetch", tool.name)
        assertEquals(RiskTier.READ, tool.riskTier)
        assertFalse("web.fetch is read-only and requires no human approval token", tool.requiresApproval)
        assertTrue(tool.parameters.required.contains("url"))
    }

    @Test
    fun testRejectsSsrfAndDisallowedSchemes() = runTest {
        val rejectedUrls = listOf(
            "file:///etc/passwd",
            "javascript:alert(1)",
            "http://127.0.0.1:8080/admin",
            "http://localhost:3000/keys",
            "http://169.254.169.254/metadata",
            "data:text/html,<b>pwned</b>"
        )

        for (badUrl in rejectedUrls) {
            val params = buildJsonObject { put("url", badUrl) }
            val result = tool.execute(params)
            assertFalse("URL $badUrl must be rejected by security validator", result.success)
            assertTrue("Error message should mention rejection: ${result.error}", result.error?.contains("rejected", ignoreCase = true) == true)
        }
    }

    @Test
    fun testExtractReadableContentStripsBoilerplateAndPreservesSemantics() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Software Engineer - Campus Placement 2025</title>
                <meta name="description" content="Exciting job opening for 2025 graduates in Android & Kotlin." />
                <style>body { font-size: 14px; } .banner { display: none; }</style>
                <script>console.log("analytics tracking code"); alert('track');</script>
            </head>
            <body>
                <header>Navigation header links: Home, About, Contact</header>
                <nav><a href="/jobs">Jobs</a></nav>
                <!-- This is an internal developer comment -->
                <main>
                    <h1>Android Software Engineer</h1>
                    <p>We are seeking high-performing <b>B.Tech graduates</b> with strong foundations in &quot;Kotlin&quot; &amp; &lt;Android SDK&gt;.</p>
                    <p>Requirements include:</p>
                    <ul>
                        <li>Minimum 7.5 CGPA</li>
                        <li>No active backlogs</li>
                        <li>Strong problem-solving skills</li>
                    </ul>
                </main>
                <footer>&copy; 2025 Acme Corp &nbsp; All rights reserved.</footer>
            </body>
            </html>
        """.trimIndent()

        val parsed = tool.extractReadableContent(sampleHtml, maxChars = 5000)

        assertEquals("Software Engineer - Campus Placement 2025", parsed.title)
        assertEquals("Exciting job opening for 2025 graduates in Android & Kotlin.", parsed.description)

        // Verify scripts, styles, comments, headers, footers were stripped
        assertFalse("Script code must be stripped", parsed.text.contains("analytics tracking code"))
        assertFalse("CSS styles must be stripped", parsed.text.contains("font-size"))
        assertFalse("HTML comments must be stripped", parsed.text.contains("internal developer comment"))
        assertFalse("Header boilerplate must be stripped", parsed.text.contains("Navigation header links"))
        assertFalse("Footer boilerplate must be stripped", parsed.text.contains("All rights reserved"))

        // Verify content and entities decoded
        assertTrue("Contains title", parsed.text.contains("Android Software Engineer"))
        assertTrue("Entities decoded: quotes", parsed.text.contains("\"Kotlin\""))
        assertTrue("Entities decoded: ampersand", parsed.text.contains("&"))
        assertTrue("Entities decoded: brackets", parsed.text.contains("<Android SDK>"))
        assertTrue("Bullet points preserved", parsed.text.contains("• Minimum 7.5 CGPA"))
        assertTrue("Bullet points preserved", parsed.text.contains("• No active backlogs"))
    }

    @Test
    fun testExtractReadableContentTruncation() {
        val longHtml = "<html><head><title>Long Page</title></head><body>" +
                "<p>" + "A".repeat(1000) + "</p>" +
                "</body></html>"

        val parsed = tool.extractReadableContent(longHtml, maxChars = 100)
        assertEquals(100 + "...\n[Content truncated]".length, parsed.text.length)
        assertTrue(parsed.text.endsWith("[Content truncated]"))
    }

    @Test
    fun testEmptyHtmlGracefulHandling() {
        val parsed = tool.extractReadableContent("", maxChars = 1000)
        assertEquals("", parsed.title)
        assertEquals("", parsed.description)
        assertEquals("", parsed.text)
    }
}

