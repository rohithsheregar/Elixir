package com.cellclaw.tools

import com.cellclaw.browser.UrlValidationResult
import com.cellclaw.browser.UrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebFetchTool(
    private val urlValidator: UrlValidator,
    private val client: OkHttpClient
) : Tool {

    @Inject
    constructor(urlValidator: UrlValidator) : this(
        urlValidator = urlValidator,
        client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    )

    override val name: String = "web.fetch"
    override val description: String =
        "Fetches a web page by URL, strips HTML boilerplate (scripts, styles, tags), and extracts clean readable text with title and metadata. Protected against SSRF."
    override val requiresApproval: Boolean = false
    override val riskTier: RiskTier = RiskTier.READ

    override val parameters: ToolParameters = ToolParameters(
        properties = mapOf(
            "url" to ParameterProperty("string", "The HTTP or HTTPS URL to fetch"),
            "max_chars" to ParameterProperty("integer", "Maximum characters of readable text to return (default 8000)")
        ),
        required = listOf("url")
    )

    override suspend fun execute(params: JsonObject): ToolResult {
        val rawUrl = try {
            params["url"]?.jsonPrimitive?.content
        } catch (_: Exception) { null } ?: return ToolResult.error("Missing 'url' parameter")

        val maxChars = try {
            params["max_chars"]?.jsonPrimitive?.intOrNull ?: 8000
        } catch (_: Exception) { 8000 }

        val validation = urlValidator.validate(rawUrl)
        if (validation is UrlValidationResult.Rejected) {
            return ToolResult.error("URL rejected for security: ${validation.reason}")
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(rawUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext ToolResult.error("HTTP ${response.code}: ${response.message}")
                }

                val html = response.body?.string().orEmpty()
                val parsed = extractReadableContent(html, maxChars)

                ToolResult.success(buildJsonObject {
                    put("url", rawUrl)
                    put("title", parsed.title)
                    put("description", parsed.description)
                    put("text", parsed.text)
                    put("character_count", parsed.text.length)
                })
            } catch (e: Exception) {
                ToolResult.error("Failed to fetch URL: ${e.message}")
            }
        }
    }

    data class ExtractedContent(
        val title: String,
        val description: String,
        val text: String
    )

    internal fun extractReadableContent(html: String, maxChars: Int): ExtractedContent {
        if (html.isBlank()) {
            return ExtractedContent(title = "", description = "", text = "")
        }

        // 1. Extract title
        val titleMatch = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
        val title = titleMatch?.groupValues?.get(1)?.let { stripTags(it).trim() }.orEmpty()

        // 2. Extract meta description
        val metaMatch = Regex("""<meta[^>]+name=["']description["'][^>]+content=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(html) ?: Regex("""<meta[^>]+content=["']([^"']*)["'][^>]+name=["']description["']""", RegexOption.IGNORE_CASE)
            .find(html)
        val description = metaMatch?.groupValues?.get(1)?.trim().orEmpty()

        // 3. Remove non-content elements: scripts, styles, noscript, svg, comments
        var cleaned = html
            .replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""<script[^>]*>.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<style[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<noscript[^>]*>.*?</noscript>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<svg[^>]*>.*?</svg>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<header[^>]*>.*?</header>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<footer[^>]*>.*?</footer>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<nav[^>]*>.*?</nav>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")

        // 4. Format blocks
        cleaned = cleaned
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</?(?:p|div|section|article|h[1-6]|tr|blockquote)[^>]*>""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""<li[^>]*>""", RegexOption.IGNORE_CASE), "\n• ")
            .replace(Regex("""</li>""", RegexOption.IGNORE_CASE), "")

        // 5. Strip all other tags and decode entities
        val text = stripTags(cleaned)
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

        val truncated = if (text.length > maxChars) text.take(maxChars) + "...\n[Content truncated]" else text

        return ExtractedContent(
            title = title,
            description = description,
            text = truncated
        )
    }

    private fun stripTags(input: String): String =
        input.replace(Regex("""<[^>]*>"""), " ")
}
