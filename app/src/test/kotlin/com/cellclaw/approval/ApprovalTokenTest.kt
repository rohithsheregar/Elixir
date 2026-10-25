package com.cellclaw.approval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApprovalTokenTest {

    private lateinit var manager: ApprovalTokenManager

    @Before
    fun setUp() {
        manager = ApprovalTokenManager()
    }

    @Test
    fun testIssueTokenRequiresDirectUserTap() {
        val resultWithoutTap = manager.issueToken(
            userDirectTapConfirmed = false,
            applicationId = "app-1",
            company = "Google",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1"
        )
        assertTrue(resultWithoutTap is TokenIssuanceResult.Rejected)
        val rejected = resultWithoutTap as TokenIssuanceResult.Rejected
        assertTrue(rejected.reason.contains("direct user tap"))

        val resultWithTap = manager.issueToken(
            userDirectTapConfirmed = true,
            applicationId = "app-1",
            company = "Google",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1"
        )
        assertTrue(resultWithTap is TokenIssuanceResult.Success)
        val success = resultWithTap as TokenIssuanceResult.Success
        assertNotNull(success.token.tokenId)
        assertEquals("Google", success.token.company)
        assertEquals("SWE", success.token.role)
    }

    @Test
    fun testTokenBindingValidationSuccess() {
        val result = manager.issueToken(
            userDirectTapConfirmed = true,
            applicationId = "app-1",
            company = "Google",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1"
        ) as TokenIssuanceResult.Success

        val validation = manager.validateToken(
            tokenId = result.token.tokenId,
            company = "Google",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1"
        )
        assertTrue(validation is TokenValidationResult.Valid)
    }

    @Test
    fun testTokenBindingRejectsMismatch() {
        val result = manager.issueToken(
            userDirectTapConfirmed = true,
            applicationId = "app-1",
            company = "Google",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1"
        ) as TokenIssuanceResult.Success

        // Company mismatch
        val compMismatch = manager.validateToken(
            tokenId = result.token.tokenId,
            company = "Meta",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1"
        )
        assertTrue(compMismatch is TokenValidationResult.CompanyMismatch)

        // Role mismatch
        val roleMismatch = manager.validateToken(
            tokenId = result.token.tokenId,
            company = "Google",
            role = "Product Manager",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1"
        )
        assertTrue(roleMismatch is TokenValidationResult.RoleMismatch)

        // URL origin mismatch
        val originMismatch = manager.validateToken(
            tokenId = result.token.tokenId,
            company = "Google",
            role = "SWE",
            urlOrigin = "https://evil.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1"
        )
        assertTrue(originMismatch is TokenValidationResult.OriginMismatch)

        // Form state mismatch
        val formMismatch = manager.validateToken(
            tokenId = result.token.tokenId,
            company = "Google",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "tampered_form_state",
            resumeHash = "hash_resume_1"
        )
        assertTrue(formMismatch is TokenValidationResult.FormStateMismatch)

        // Resume hash mismatch
        val resumeMismatch = manager.validateToken(
            tokenId = result.token.tokenId,
            company = "Google",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "other_resume_hash"
        )
        assertTrue(resumeMismatch is TokenValidationResult.ResumeHashMismatch)
    }

    @Test
    fun testTokenSingleUseOnly() {
        val result = manager.issueToken(
            userDirectTapConfirmed = true,
            applicationId = "app-1",
            company = "Google",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1"
        ) as TokenIssuanceResult.Success

        // Consume first time
        val consumedFirst = manager.consumeToken(result.token.tokenId)
        assertTrue(consumedFirst)

        // Consume second time
        val consumedSecond = manager.consumeToken(result.token.tokenId)
        assertFalse(consumedSecond)

        // Validate consumed token
        val validation = manager.validateToken(
            tokenId = result.token.tokenId,
            company = "Google",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1"
        )
        assertTrue(validation is TokenValidationResult.AlreadyUsed)
    }

    @Test
    fun testTokenExpiresOnTimeout() {
        // Issue token with 1ms validity
        val result = manager.issueToken(
            userDirectTapConfirmed = true,
            applicationId = "app-1",
            company = "Google",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1",
            validityDurationMs = 1L
        ) as TokenIssuanceResult.Success

        Thread.sleep(10)

        val validation = manager.validateToken(
            tokenId = result.token.tokenId,
            company = "Google",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1"
        )
        assertTrue(validation is TokenValidationResult.Expired)
    }

    @Test
    fun testTokenRevocationOnNavigation() {
        val result = manager.issueToken(
            userDirectTapConfirmed = true,
            applicationId = "app-1",
            company = "Google",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1"
        ) as TokenIssuanceResult.Success

        manager.onNavigationOccurred(result.token.tokenId)

        val validation = manager.validateToken(
            tokenId = result.token.tokenId,
            company = "Google",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1"
        )
        assertTrue(validation is TokenValidationResult.Revoked)
        assertEquals("NAVIGATION", (validation as TokenValidationResult.Revoked).reason)
    }

    @Test
    fun testTokenRevocationOnFieldChanged() {
        val result = manager.issueToken(
            userDirectTapConfirmed = true,
            applicationId = "app-1",
            company = "Google",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1"
        ) as TokenIssuanceResult.Success

        manager.onFieldChanged(result.token.tokenId)

        val validation = manager.validateToken(
            tokenId = result.token.tokenId,
            company = "Google",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1"
        )
        assertTrue(validation is TokenValidationResult.Revoked)
        assertEquals("FIELD_CHANGE", (validation as TokenValidationResult.Revoked).reason)
    }

    @Test
    fun testTokenRevocationOnUserCancelled() {
        val result = manager.issueToken(
            userDirectTapConfirmed = true,
            applicationId = "app-1",
            company = "Google",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1"
        ) as TokenIssuanceResult.Success

        manager.onUserCancelled(result.token.tokenId)

        val validation = manager.validateToken(
            tokenId = result.token.tokenId,
            company = "Google",
            role = "SWE",
            urlOrigin = "https://docs.google.com",
            formStateHash = "hash_form_1",
            resumeHash = "hash_resume_1"
        )
        assertTrue(validation is TokenValidationResult.Revoked)
        assertEquals("USER_CANCELLED", (validation as TokenValidationResult.Revoked).reason)
    }

    @Test
    fun testComputeFormStateHashDeterministic() {
        val fields1 = mapOf("Name" to "John Doe", "Email" to "john@example.com", "CGPA" to "9.1")
        val fields2 = mapOf("Email" to "john@example.com", "CGPA" to "9.1", "Name" to "John Doe")
        val fieldsEdited = mapOf("Name" to "John Doe", "Email" to "john@example.com", "CGPA" to "9.5")

        val hash1 = manager.computeFormStateHash(fields1)
        val hash2 = manager.computeFormStateHash(fields2)
        val hashEdited = manager.computeFormStateHash(fieldsEdited)

        assertEquals(hash1, hash2)
        assertFalse(hash1 == hashEdited)
    }
}
