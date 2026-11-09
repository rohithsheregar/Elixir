package com.cellclaw.tools

import com.cellclaw.browser.UrlValidationResult
import com.cellclaw.browser.UrlValidator
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.ApplicationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JobSearchTool(
    private val urlValidator: UrlValidator,
    private val applicationTracker: ApplicationTracker? = null,
    private val client: OkHttpClient
) : Tool {

    @Inject
    constructor(
        urlValidator: UrlValidator,
        applicationTracker: ApplicationTracker
    ) : this(
        urlValidator = urlValidator,
        applicationTracker = applicationTracker,
        client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    )

    override val name: String = "job.search"
    override val description: String =
        "Searches the internet for open job roles, internships, and campus/off-campus placement opportunities matching keywords, skills, and target locations. Returns structured job listings."
    override val requiresApproval: Boolean = false
    override val riskTier: RiskTier = RiskTier.READ

    override val parameters: ToolParameters = ToolParameters(
        properties = mapOf(
            "query" to ParameterProperty("string", "Search query (e.g. 'Software Engineer Android fresher' or 'B.Tech Kotlin campus drive 2025')"),
            "location" to ParameterProperty("string", "Optional target location (e.g. 'Bangalore', 'Remote', 'Hyderabad')"),
            "stage_top_result" to ParameterProperty("boolean", "If true, automatically creates a draft application in the tracker for the top job link")
        ),
        required = listOf("query")
    )

    override suspend fun execute(params: JsonObject): ToolResult {
        val rawQuery = try {
            params["query"]?.jsonPrimitive?.content
        } catch (_: Exception) { null } ?: return ToolResult.error("Missing 'query' parameter")

        val location = try {
            params["location"]?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) { null }

        val stageTopResult = try {
            params["stage_top_result"]?.jsonPrimitive?.booleanOrNull ?: false
        } catch (_: Exception) { false }

        val fullQuery = if (!location.isNullOrBlank() && !rawQuery.contains(location, ignoreCase = true)) {
            "$rawQuery $location job hiring"
        } else {
            "$rawQuery job hiring"
        }

        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(fullQuery, StandardCharsets.UTF_8.name())
                val url = "https://html.duckduckgo.com/html/?q=$encodedQuery"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val html = response.body?.string().orEmpty()

                val results = parseJobResults(html)

                // Optional: auto-stage top result into ApplicationTracker
                var stagedAppId: String? = null
                if (stageTopResult && results.isNotEmpty() && applicationTracker != null) {
                    val top = results.first()
                    val candidateUrl = top["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val candidateCompany = top["company"]?.jsonPrimitive?.contentOrNull ?: "Discovered Company"
                    val candidateRole = top["role"]?.jsonPrimitive?.contentOrNull ?: rawQuery

                    if (candidateUrl.isNotBlank() && urlValidator.validate(candidateUrl) !is UrlValidationResult.Rejected) {
                        val record = ApplicationRecord(
                            id = UUID.randomUUID().toString(),
                            company = candidateCompany,
                            role = candidateRole,
                            sourceUrl = candidateUrl,
                            jdHash = "",
                            eligibilityResult = "UNKNOWN",
                            state = ApplicationState.DRAFT,
                            notes = "Discovered via automated internet research query: $rawQuery"
                        )
                        applicationTracker.registerApplication(record)
                        stagedAppId = record.id
                    }
                }

                ToolResult.success(buildJsonObject {
                    put("query", fullQuery)
                    put("result_count", results.size)
                    putJsonArray("job_openings") {
                        results.forEach { add(it) }
                    }
                    if (stagedAppId != null) {
                        put("staged_application_id", stagedAppId)
                    }
                })
            } catch (e: Exception) {
                ToolResult.error("Job search failed: ${e.message}")
            }
        }
    }

    internal fun parseJobResults(html: String): List<JsonObject> {
        val results = mutableListOf<JsonObject>()
        // DuckDuckGo HTML layout
        val resultPattern = Regex(
            """<a[^>]*class="result__a"[^>]*href="([^"]*)"[^>]*>(.*?)</a>.*?<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        for (match in resultPattern.findAll(html)) {
            val rawLink = match.groupValues[1]
            val actualUrl = Regex("""uddg=([^&]+)""").find(rawLink)?.groupValues?.get(1)?.let {
                java.net.URLDecoder.decode(it, StandardCharsets.UTF_8.name())
            } ?: rawLink

            val rawTitle = match.groupValues[2].replace(Regex("<[^>]*>"), "").trim()
            val rawSnippet = match.groupValues[3].replace(Regex("<[^>]*>"), "").trim()

            if (rawTitle.isNotBlank()) {
                val (company, role) = extractCompanyAndRole(rawTitle)
                results.add(buildJsonObject {
                    put("title", rawTitle)
                    put("company", company)
                    put("role", role)
                    put("url", actualUrl)
                    put("snippet", rawSnippet)
                })
            }
        }
        return results.take(10)
    }

    private fun extractCompanyAndRole(title: String): Pair<String, String> {
        // Common title formats: "Role at Company", "Company hiring Role", "Role - Company"
        val atSplit = title.split(Regex("""\s+at\s+""", RegexOption.IGNORE_CASE))
        if (atSplit.size >= 2) {
            return Pair(atSplit[1].split(Regex("""[\-–|]""")).first().trim(), atSplit[0].trim())
        }

        val dashSplit = title.split(Regex("""\s*[\-–|]\s*"""))
        if (dashSplit.size >= 2) {
            return Pair(dashSplit[1].trim(), dashSplit[0].trim())
        }

        return Pair("Unknown Company", title)
    }
}
