package com.cellclaw.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

sealed class UrlValidationResult {
    data class Valid(val normalizedUrl: String) : UrlValidationResult()
    data class WarningHttp(val url: String, val message: String) : UrlValidationResult()
    data class Rejected(val reason: String) : UrlValidationResult()
}

sealed class BrowserOpenResult {
    data class Success(val openedUrl: String, val packageName: String?) : BrowserOpenResult()
    data class WarningOpened(val openedUrl: String, val warning: String, val packageName: String?) : BrowserOpenResult()
    data class Rejected(val reason: String) : BrowserOpenResult()
    data class Error(val message: String) : BrowserOpenResult()
}

class UrlValidator {
    companion object {
        private val DANGEROUS_SCHEMES = setOf(
            "javascript", "file", "intent", "data", "content", "ftp", "blob", "about"
        )
        private val PRIVATE_IP_PATTERN = Pattern.compile(
            "^(10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" +
            "192\\.168\\.\\d{1,3}\\.\\d{1,3}|" +
            "172\\.(1[6-9]|2\\d|3[0-1])\\.\\d{1,3}\\.\\d{1,3}|" +
            "127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" +
            "0\\.0\\.0\\.0)$"
        )
    }

    fun validate(input: String?): UrlValidationResult {
        if (input.isNullOrBlank()) {
            return UrlValidationResult.Rejected("URL cannot be empty")
        }
        val trimmed = input.trim()

        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            return UrlValidationResult.Rejected("Malformed URL: ${e.message}")
        }

        val scheme = uri.scheme?.lowercase() ?: return UrlValidationResult.Rejected("Missing URL scheme")

        if (scheme in DANGEROUS_SCHEMES) {
            return UrlValidationResult.Rejected("Rejected dangerous scheme: $scheme:")
        }

        if (scheme != "http" && scheme != "https") {
            return UrlValidationResult.Rejected("Unsupported scheme: $scheme. Only https is supported.")
        }

        val host = uri.host?.lowercase() ?: return UrlValidationResult.Rejected("Missing host in URL")

        if (host == "localhost" || host.endsWith(".localhost")) {
            return UrlValidationResult.Rejected("Localhost URLs are rejected for security")
        }

        if (host == "::1" || host == "[::1]") {
            return UrlValidationResult.Rejected("IPv6 loopback host is rejected")
        }

        if (PRIVATE_IP_PATTERN.matcher(host).matches()) {
            return UrlValidationResult.Rejected("Private network or loopback address host is rejected: $host")
        }

        return if (scheme == "http") {
            UrlValidationResult.WarningHttp(
                url = trimmed,
                message = "Insecure HTTP connection: form submission should use HTTPS"
            )
        } else {
            UrlValidationResult.Valid(trimmed)
        }
    }
}

@Singleton
open class BrowserOpener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val validator: UrlValidator = UrlValidator()
) {
    companion object {
        const val CHROME_PACKAGE = "com.android.chrome"
        const val ACTION_VIEW = "android.intent.action.VIEW"
    }

    open fun validateUrl(url: String): UrlValidationResult = validator.validate(url)

    /**
     * Opens [url] in the user's real browser (preferring Chrome) via Android intent.
     * Hard rule (S3): never load in an embedded WebView.
     */
    open fun open(url: String, preferChrome: Boolean = true): BrowserOpenResult {
        val validation = validator.validate(url)
        val (targetUrl, warning) = when (validation) {
            is UrlValidationResult.Rejected -> return BrowserOpenResult.Rejected(validation.reason)
            is UrlValidationResult.WarningHttp -> validation.url to validation.message
            is UrlValidationResult.Valid -> validation.normalizedUrl to null
        }

        val intent = Intent(ACTION_VIEW, Uri.parse(targetUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (preferChrome) {
                setPackage(CHROME_PACKAGE)
            }
        }

        return try {
            context.startActivity(intent)
            if (warning != null) {
                BrowserOpenResult.WarningOpened(targetUrl, warning, if (preferChrome) CHROME_PACKAGE else null)
            } else {
                BrowserOpenResult.Success(targetUrl, if (preferChrome) CHROME_PACKAGE else null)
            }
        } catch (e: ActivityNotFoundException) {
            // Chrome not installed; fall back to generic browser intent without package restriction
            if (preferChrome) {
                try {
                    val fallbackIntent = Intent(ACTION_VIEW, Uri.parse(targetUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                    if (warning != null) {
                        BrowserOpenResult.WarningOpened(targetUrl, warning, null)
                    } else {
                        BrowserOpenResult.Success(targetUrl, null)
                    }
                } catch (fallbackEx: Exception) {
                    BrowserOpenResult.Error("No browser available to handle URL: ${fallbackEx.message}")
                }
            } else {
                BrowserOpenResult.Error("Failed to open browser: ${e.message}")
            }
        } catch (e: Exception) {
            BrowserOpenResult.Error("Error opening browser: ${e.message}")
        }
    }
}
