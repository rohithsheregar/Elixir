package com.cellclaw.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserOpenerTest {

    private val validator = UrlValidator()

    @Test
    fun testValidHttpsUrls() {
        val validUrls = listOf(
            "https://docs.google.com/forms/d/e/1FAIpQLSc.../viewform",
            "https://careers.google.com/jobs/results",
            "https://subdomain.company.co.in/apply?id=123",
            "https://jobs.lever.co/company/abc-123"
        )

        for (url in validUrls) {
            val result = validator.validate(url)
            assertTrue("Expected Valid for $url but got $result", result is UrlValidationResult.Valid)
            assertEquals(url, (result as UrlValidationResult.Valid).normalizedUrl)
        }
    }

    @Test
    fun testHttpWarning() {
        val httpUrls = listOf(
            "http://example.com/apply",
            "http://careers.startup.io/form?ref=campus"
        )

        for (url in httpUrls) {
            val result = validator.validate(url)
            assertTrue("Expected WarningHttp for $url but got $result", result is UrlValidationResult.WarningHttp)
            assertEquals(url, (result as UrlValidationResult.WarningHttp).url)
            assertTrue(result.message.contains("Insecure HTTP", ignoreCase = true))
        }
    }

    @Test
    fun testRejectDangerousSchemes() {
        val dangerousUrls = listOf(
            "javascript:alert(document.cookie)",
            "file:///sdcard/Download/exploit.html",
            "intent://#Intent;scheme=something;package=com.evil;end",
            "data:text/html,<html>alert()</html>",
            "content://com.android.providers.media/external",
            "ftp://ftp.example.com/resource",
            "blob:https://example.com/uuid"
        )

        for (url in dangerousUrls) {
            val result = validator.validate(url)
            assertTrue("Expected Rejected for dangerous scheme: $url but got $result", result is UrlValidationResult.Rejected)
            val reason = (result as UrlValidationResult.Rejected).reason
            assertTrue("Reason should explain rejection: $reason", reason.isNotBlank())
        }
    }

    @Test
    fun testRejectLocalhostAndPrivateNetworkHosts() {
        val privateUrls = listOf(
            "https://localhost/form",
            "http://localhost:8080/apply",
            "https://app.localhost:3000",
            "https://127.0.0.1/form",
            "https://127.0.0.1:8000/apply",
            "https://0.0.0.0:8080",
            "https://10.0.0.1/admin/form",
            "https://192.168.1.50/jobs",
            "https://172.16.0.1/form",
            "https://172.31.255.255/apply"
        )

        for (url in privateUrls) {
            val result = validator.validate(url)
            assertTrue("Expected Rejected for private/loopback host: $url but got $result", result is UrlValidationResult.Rejected)
            val reason = (result as UrlValidationResult.Rejected).reason
            assertTrue("Reason should indicate localhost/private network: $reason",
                reason.contains("localhost", ignoreCase = true) ||
                reason.contains("private", ignoreCase = true) ||
                reason.contains("loopback", ignoreCase = true))
        }
    }

    @Test
    fun testRejectBlankAndMalformed() {
        val invalidInputs = listOf(
            "",
            "   ",
            "not a url",
            "https://",
            "://missing-scheme",
            "https:///no-host"
        )

        for (input in invalidInputs) {
            val result = validator.validate(input)
            assertTrue("Expected Rejected for invalid input '$input' but got $result", result is UrlValidationResult.Rejected)
        }
    }

    @Test
    fun testIntentPackageSpec() {
        // Verify pure intent configuration constants
        assertEquals("com.android.chrome", BrowserOpener.CHROME_PACKAGE)
        assertEquals("android.intent.action.VIEW", BrowserOpener.ACTION_VIEW)
    }
}
