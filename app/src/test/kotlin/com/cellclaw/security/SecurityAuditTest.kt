package com.cellclaw.security

import com.cellclaw.browser.UrlValidationResult
import com.cellclaw.browser.UrlValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SecurityAuditTest {

    private val validator = UrlValidator()

    @Test
    fun testOnlyHttpsAllowed() {
        val httpsResult = validator.validate("https://forms.google.com/form123")
        assertTrue(httpsResult is UrlValidationResult.Valid)

        val httpResult = validator.validate("http://insecure-forms.com/form123")
        assertTrue(httpResult is UrlValidationResult.WarningHttp) // Requires confirmation per PlacementOS rule 4.1

        val jsResult = validator.validate("javascript:alert(1)")
        assertTrue(jsResult is UrlValidationResult.Rejected)

        val fileResult = validator.validate("file:///etc/passwd")
        assertTrue(fileResult is UrlValidationResult.Rejected)

        val localResult = validator.validate("https://localhost:8080")
        assertTrue(localResult is UrlValidationResult.Rejected)
    }

    @Test
    fun testManifestHasNoDangerousActivePermissions() {
        val manifestFile = File("src/main/AndroidManifest.xml").takeIf { it.exists() }
            ?: File("app/src/main/AndroidManifest.xml")

        assertTrue("AndroidManifest.xml should exist", manifestFile.exists())
        val content = manifestFile.readText()

        // S1 audit remediation: verify dangerous permissions are removed from active manifest
        assertFalse(content.contains("<uses-permission android:name=\"android.permission.READ_SMS\""))
        assertFalse(content.contains("<uses-permission android:name=\"android.permission.SEND_SMS\""))
        assertFalse(content.contains("<uses-permission android:name=\"android.permission.RECEIVE_SMS\""))
        assertFalse(content.contains("<uses-permission android:name=\"android.permission.CALL_PHONE\""))
        assertFalse(content.contains("<uses-permission android:name=\"android.permission.RECORD_AUDIO\""))
        assertFalse(content.contains("<uses-permission android:name=\"android.permission.CAMERA\""))
        assertFalse(content.contains("<uses-permission android:name=\"android.permission.ACCESS_FINE_LOCATION\""))

        // Backups disabled
        assertTrue(content.contains("android:allowBackup=\"false\""))

        // Network security config referenced
        assertTrue(content.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
    }
}
