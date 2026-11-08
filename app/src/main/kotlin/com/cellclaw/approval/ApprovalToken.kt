package com.cellclaw.approval

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class ApprovalToken(
    val tokenId: String,
    val applicationId: String = "",
    val company: String = "",
    val role: String = "",
    val urlOrigin: String = "",
    val formStateHash: String = "",
    val resumeHash: String = "",
    val actionName: String = "",
    val actionTarget: String = "",
    val issuedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long,
    val used: Boolean = false,
    val revoked: Boolean = false,
    val revocationReason: String? = null
)

sealed interface TokenIssuanceResult {
    data class Success(val token: ApprovalToken) : TokenIssuanceResult
    data class Rejected(val reason: String) : TokenIssuanceResult
}

sealed interface TokenValidationResult {
    data object Valid : TokenValidationResult
    data object TokenNotFound : TokenValidationResult
    data object AlreadyUsed : TokenValidationResult
    data class Revoked(val reason: String) : TokenValidationResult
    data object Expired : TokenValidationResult
    data class CompanyMismatch(val expected: String, val actual: String) : TokenValidationResult
    data class RoleMismatch(val expected: String, val actual: String) : TokenValidationResult
    data class OriginMismatch(val expected: String, val actual: String) : TokenValidationResult
    data class FormStateMismatch(val expected: String, val actual: String) : TokenValidationResult
    data class ResumeHashMismatch(val expected: String, val actual: String) : TokenValidationResult
    data class ActionMismatch(val expected: String, val actual: String) : TokenValidationResult
}

@Singleton
class ApprovalTokenManager @Inject constructor() {

    private val tokens = ConcurrentHashMap<String, ApprovalToken>()
    private val secureRandom = SecureRandom()

    private fun generateSecureTokenId(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Issues a submission approval token.
     * Enforces the hard rule: tokens can only be created by an explicit direct user tap.
     * Models and background tasks cannot create or forge approval tokens.
     */
    fun issueToken(
        userDirectTapConfirmed: Boolean,
        applicationId: String,
        company: String,
        role: String,
        urlOrigin: String,
        formStateHash: String,
        resumeHash: String,
        validityDurationMs: Long = 5 * 60 * 1000L // 5 minutes default
    ): TokenIssuanceResult {
        if (!userDirectTapConfirmed) {
            return TokenIssuanceResult.Rejected(
                "Security policy violation: Submission approval tokens can only be created by direct user tap."
            )
        }

        val now = System.currentTimeMillis()
        val token = ApprovalToken(
            tokenId = generateSecureTokenId(),
            applicationId = applicationId,
            company = company,
            role = role,
            urlOrigin = urlOrigin,
            formStateHash = formStateHash,
            resumeHash = resumeHash,
            issuedAt = now,
            expiresAt = now + validityDurationMs,
            used = false,
            revoked = false,
            revocationReason = null
        )

        tokens[token.tokenId] = token
        return TokenIssuanceResult.Success(token)
    }

    /**
     * Issues a generalised action approval token for CONSEQUENTIAL_WRITE tools.
     * Enforces the hard security rule: tokens can only be created by an explicit direct user tap.
     * Models, subagents, and background processes cannot create or forge approval tokens.
     */
    fun issueActionToken(
        userDirectTapConfirmed: Boolean,
        actionName: String,
        actionTarget: String = "",
        validityDurationMs: Long = 5 * 60 * 1000L
    ): TokenIssuanceResult {
        if (!userDirectTapConfirmed) {
            return TokenIssuanceResult.Rejected(
                "Security policy violation: Approval tokens can only be created by direct user tap."
            )
        }

        val now = System.currentTimeMillis()
        val token = ApprovalToken(
            tokenId = generateSecureTokenId(),
            actionName = actionName,
            actionTarget = actionTarget,
            issuedAt = now,
            expiresAt = now + validityDurationMs,
            used = false,
            revoked = false,
            revocationReason = null
        )

        tokens[token.tokenId] = token
        return TokenIssuanceResult.Success(token)
    }

    /**
     * Validates that the token exists, is not consumed, has not expired, has not been revoked,
     * and strictly matches the target submission bindings.
     */
    fun validateToken(
        tokenId: String,
        company: String,
        role: String,
        urlOrigin: String,
        formStateHash: String,
        resumeHash: String
    ): TokenValidationResult {
        val token = tokens[tokenId] ?: return TokenValidationResult.TokenNotFound

        if (token.used) {
            return TokenValidationResult.AlreadyUsed
        }

        if (token.revoked) {
            return TokenValidationResult.Revoked(token.revocationReason ?: "UNKNOWN")
        }

        if (System.currentTimeMillis() > token.expiresAt) {
            return TokenValidationResult.Expired
        }

        if (!token.company.trim().equals(company.trim(), ignoreCase = true)) {
            return TokenValidationResult.CompanyMismatch(expected = token.company, actual = company)
        }

        if (!token.role.trim().equals(role.trim(), ignoreCase = true)) {
            return TokenValidationResult.RoleMismatch(expected = token.role, actual = role)
        }

        if (!token.urlOrigin.trim().equals(urlOrigin.trim(), ignoreCase = true)) {
            return TokenValidationResult.OriginMismatch(expected = token.urlOrigin, actual = urlOrigin)
        }

        if (token.formStateHash != formStateHash) {
            return TokenValidationResult.FormStateMismatch(expected = token.formStateHash, actual = formStateHash)
        }

        if (token.resumeHash != resumeHash) {
            return TokenValidationResult.ResumeHashMismatch(expected = token.resumeHash, actual = resumeHash)
        }

        return TokenValidationResult.Valid
    }

    /**
     * Validates a generalised action token against [expectedAction].
     */
    fun validateActionToken(
        tokenId: String,
        expectedAction: String
    ): TokenValidationResult {
        val token = tokens[tokenId] ?: return TokenValidationResult.TokenNotFound

        if (token.used) {
            return TokenValidationResult.AlreadyUsed
        }

        if (token.revoked) {
            return TokenValidationResult.Revoked(token.revocationReason ?: "UNKNOWN")
        }

        if (System.currentTimeMillis() > token.expiresAt) {
            return TokenValidationResult.Expired
        }

        if (token.actionName.isNotBlank() && !token.actionName.equals(expectedAction.trim(), ignoreCase = true)) {
            return TokenValidationResult.ActionMismatch(expected = expectedAction, actual = token.actionName)
        }

        return TokenValidationResult.Valid
    }

    /**
     * Consumes the approval token for a single submission attempt.
     * Tokens are one-time use; subsequent attempts will fail.
     */
    fun consumeToken(tokenId: String): Boolean {
        val existing = tokens[tokenId] ?: return false
        if (existing.used || existing.revoked) return false
        tokens[tokenId] = existing.copy(used = true)
        return true
    }

    fun onNavigationOccurred(tokenId: String) {
        revokeToken(tokenId, "NAVIGATION")
    }

    fun onFieldChanged(tokenId: String) {
        revokeToken(tokenId, "FIELD_CHANGE")
    }

    fun onUserCancelled(tokenId: String) {
        revokeToken(tokenId, "USER_CANCELLED")
    }

    fun onTimeout(tokenId: String) {
        revokeToken(tokenId, "TIMEOUT")
    }

    private fun revokeToken(tokenId: String, reason: String) {
        val existing = tokens[tokenId] ?: return
        if (!existing.revoked) {
            tokens[tokenId] = existing.copy(revoked = true, revocationReason = reason)
        }
    }

    /**
     * Computes a deterministic SHA-256 hash of a form state map.
     */
    fun computeFormStateHash(fields: Map<String, String>): String {
        val sortedString = fields.entries
            .sortedBy { it.key }
            .joinToString(separator = "|") { "${it.key.trim()}=${it.value.trim()}" }
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(sortedString.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
