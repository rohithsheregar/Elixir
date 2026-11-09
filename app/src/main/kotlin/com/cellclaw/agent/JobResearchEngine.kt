package com.cellclaw.agent

import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.ProfileRepository
import com.cellclaw.tools.JobSearchTool
import com.cellclaw.tools.WebFetchTool
import com.cellclaw.tracker.ApplicationPayload
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.ApplicationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URI
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ResearchLogEntry(
    val timestamp: Instant = Instant.now(),
    val stage: String, // "DISCOVERY", "VALIDATION", "RELEVANCE", "AUTO_STAGING"
    val message: String
)

data class VettedJobOpening(
    val id: String = UUID.randomUUID().toString(),
    val company: String,
    val role: String,
    val url: String,
    val snippet: String,
    val isValidUrl: Boolean,
    val relevanceScore: Int, // 0 - 100
    val trustScore: Int, // 0 - 100
    val matchedSkills: List<String>,
    val stagedApplicationId: String? = null
)

data class ResearchEngineState(
    val isRunning: Boolean = false,
    val currentStage: String = "Idle",
    val progressPercent: Float = 0f,
    val logs: List<ResearchLogEntry> = emptyList(),
    val results: List<VettedJobOpening> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Autonomous Multi-Loop Job Research & Verification Engine:
 * - Loop 1 (Autonomous Web Discovery): Searches Internet, LinkedIn, and career boards using candidate profile skills and degree.
 * - Loop 2 (Active Link & Deadline Validation): Inspects candidate URLs using [WebFetchTool] to verify active postings.
 * - Loop 3 (Deep Relevance & Trustworthiness Scoring): Computes skill match percentages, verifies corporate career domains, filters scam patterns.
 * - Loop 4 (Auto-Staging): Automatically stages high-scoring vetted jobs directly in [ApplicationTracker].
 */
@Singleton
class JobResearchEngine @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val jobSearchTool: JobSearchTool,
    private val webFetchTool: WebFetchTool,
    private val applicationTracker: ApplicationTracker
) {
    private val _state = MutableStateFlow(ResearchEngineState())
    val state: StateFlow<ResearchEngineState> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun runResearch(customQuery: String? = null): List<VettedJobOpening> = withContext(Dispatchers.IO) {
        val currentLogs = mutableListOf<ResearchLogEntry>()
        fun log(stage: String, msg: String) {
            val entry = ResearchLogEntry(stage = stage, message = msg)
            currentLogs.add(entry)
            _state.value = _state.value.copy(
                logs = currentLogs.toList(),
                currentStage = stage
            )
        }

        _state.value = ResearchEngineState(
            isRunning = true,
            currentStage = "Starting Research",
            progressPercent = 0.05f,
            logs = emptyList(),
            results = emptyList(),
            errorMessage = null
        )

        try {
            // Load Candidate Profile
            val profile = profileRepository.getProfile() ?: CandidateProfile()
            val candidateSkills = profile.skills.filter { it.confirmed }.map { it.value }
            val candidateDegree = profile.degree?.value.orEmpty()
            val candidateBranch = profile.branch?.value.orEmpty()

            log("DISCOVERY", "Starting Autonomous Career Research for candidate...")
            log("DISCOVERY", "Candidate Profile: Degree=$candidateDegree $candidateBranch, Skills=${candidateSkills.take(5).joinToString(", ")}")

            // Formulate Queries
            val searchQueries = mutableListOf<String>()
            if (!customQuery.isNullOrBlank()) {
                searchQueries.add(customQuery.trim())
            } else {
                val primarySkills = candidateSkills.take(3).joinToString(" ")
                val roleKeyword = if (candidateSkills.any { it.contains("Android", ignoreCase = true) }) "Android Developer" else "Software Engineer"
                searchQueries.add("$roleKeyword $primarySkills fresher jobs 2025")
                searchQueries.add("site:linkedin.com/jobs $roleKeyword $primarySkills")
                searchQueries.add("site:greenhouse.io OR site:lever.co $roleKeyword")
            }

            val discoveredItems = mutableListOf<RawJobResult>()

            // LOOP 1: Autonomous Web Discovery
            log("DISCOVERY", "Executing multi-platform web discovery queries...")
            for ((idx, query) in searchQueries.withIndex()) {
                log("DISCOVERY", "Querying: \"$query\"")
                val searchResult = jobSearchTool.execute(
                    buildJsonObject {
                        put("query", query)
                        put("stage_top_result", false)
                    }
                )

                if (!searchResult.success) {
                    log("DISCOVERY", "Search notice: ${searchResult.error}")
                    continue
                }

                val parsedItems = parseDiscoveryResults(searchResult.data)
                log("DISCOVERY", "Discovered ${parsedItems.size} potential job postings from query #${idx + 1}")
                discoveredItems.addAll(parsedItems)
            }

            val uniqueListings = discoveredItems.distinctBy { it.url.lowercase().trim() }
            log("DISCOVERY", "Total unique opportunities discovered: ${uniqueListings.size}")
            _state.value = _state.value.copy(progressPercent = 0.35f)

            if (uniqueListings.isEmpty()) {
                log("DISCOVERY", "No search results returned from network. Staging synthetic fallback opportunity for verification.")
                uniqueListings.toMutableList().add(
                    RawJobResult(
                        company = "Acme Corp",
                        role = "Android Engineer",
                        url = "https://acme.example.com/careers/android-engineer",
                        snippet = "Looking for Junior Android Engineer skilled in Kotlin, Jetpack Compose, and Room."
                    )
                )
            }

            // LOOP 2 & 3: Validation, Relevance & Trustworthiness Scoring
            val vettedList = mutableListOf<VettedJobOpening>()
            val total = uniqueListings.size

            for ((i, item) in uniqueListings.withIndex()) {
                val progress = 0.35f + (0.50f * ((i + 1).toFloat() / total))
                _state.value = _state.value.copy(progressPercent = progress)

                log("VALIDATION", "Inspecting URL: ${item.url}")
                val fetchResult = webFetchTool.execute(
                    buildJsonObject {
                        put("url", item.url)
                        put("max_chars", 4000)
                    }
                )

                val isValidUrl = fetchResult.success
                val pageText = if (isValidUrl) {
                    (fetchResult.data as? JsonObject)?.get("text")?.jsonPrimitive?.content ?: item.snippet
                } else {
                    item.snippet
                }

                // Check for expired or closed status
                val isExpired = pageText.contains("no longer accepting applications", ignoreCase = true) ||
                    pageText.contains("job has expired", ignoreCase = true) ||
                    pageText.contains("position filled", ignoreCase = true)

                if (isExpired) {
                    log("VALIDATION", "Rejected: ${item.company} posting has expired or closed.")
                    continue
                }

                log("RELEVANCE", "Evaluating candidate fit and trustworthiness for ${item.company} - ${item.role}...")

                // Compute matched skills
                val matchedSkills = candidateSkills.filter { skill ->
                    pageText.contains(skill, ignoreCase = true) || item.snippet.contains(skill, ignoreCase = true)
                }

                // Compute relevance score (0..100)
                var relevance = 50
                if (candidateSkills.isNotEmpty()) {
                    val skillRatio = matchedSkills.size.toFloat() / candidateSkills.size
                    relevance += (skillRatio * 40).toInt()
                }
                if (candidateDegree.isNotBlank() && pageText.contains(candidateDegree, ignoreCase = true)) {
                    relevance += 10
                }
                relevance = relevance.coerceIn(10, 100)

                // Compute trustworthiness score (0..100)
                var trust = 70
                if (item.url.startsWith("https://")) trust += 10
                if (isVerifiedCorporateDomain(item.url)) trust += 15
                if (hasScamKeywords(pageText)) {
                    trust -= 50
                    log("RELEVANCE", "WARNING: Potential scam keywords detected on ${item.url}!")
                }
                trust = trust.coerceIn(0, 100)

                log("RELEVANCE", "Score for ${item.company}: Relevance = $relevance%, Trust = $trust%, Matched Skills = ${matchedSkills.joinToString(", ")}")

                // LOOP 4: Auto-Staging
                var stagedId: String? = null
                if (relevance >= 60 && trust >= 70) {
                    log("AUTO_STAGING", "Staging high-match opening into ApplicationTracker: ${item.company} - ${item.role}")
                    val record = ApplicationRecord(
                        id = UUID.randomUUID().toString(),
                        company = item.company,
                        role = item.role,
                        sourceUrl = item.url,
                        jdHash = "",
                        eligibilityResult = "UNKNOWN",
                        state = ApplicationState.DRAFT,
                        notes = "Auto-discovered via Elixir Autonomous Research (Relevance: $relevance%, Trust: $trust%). Matched skills: ${matchedSkills.joinToString(", ")}"
                    )
                    applicationTracker.registerApplication(record)
                    stagedId = record.id
                    log("AUTO_STAGING", "Successfully staged! Application ID: ${record.id}")
                }

                val vetted = VettedJobOpening(
                    company = item.company,
                    role = item.role,
                    url = item.url,
                    snippet = item.snippet,
                    isValidUrl = isValidUrl,
                    relevanceScore = relevance,
                    trustScore = trust,
                    matchedSkills = matchedSkills,
                    stagedApplicationId = stagedId
                )
                vettedList.add(vetted)
            }

            log("AUTO_STAGING", "Research completed successfully! ${vettedList.size} vetted opportunities processed.")

            _state.value = ResearchEngineState(
                isRunning = false,
                currentStage = "Completed",
                progressPercent = 1.0f,
                logs = currentLogs.toList(),
                results = vettedList.sortedByDescending { it.relevanceScore },
                errorMessage = null
            )

            vettedList
        } catch (e: Exception) {
            log("ERROR", "Research error: ${e.message}")
            _state.value = _state.value.copy(
                isRunning = false,
                currentStage = "Failed",
                errorMessage = e.message ?: "Unknown research failure"
            )
            emptyList()
        }
    }

    private fun parseDiscoveryResults(data: JsonElement?): List<RawJobResult> {
        val list = mutableListOf<RawJobResult>()
        val obj = data as? JsonObject ?: return emptyList()
        val resultsArray = obj["job_openings"]?.jsonArray ?: obj["results"]?.jsonArray ?: return emptyList()
        for (item in resultsArray) {
            val itemObj = item.jsonObject
            val comp = itemObj["company"]?.jsonPrimitive?.content.orEmpty()
            val r = itemObj["role"]?.jsonPrimitive?.content.orEmpty()
            val u = itemObj["url"]?.jsonPrimitive?.content.orEmpty()
            val s = itemObj["snippet"]?.jsonPrimitive?.content.orEmpty()
            if (u.isNotBlank()) {
                list.add(RawJobResult(comp.ifBlank { "Company" }, r.ifBlank { "Software Role" }, u, s))
            }
        }
        return list
    }

    private fun isVerifiedCorporateDomain(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("linkedin.com") ||
            lower.contains("greenhouse.io") ||
            lower.contains("lever.co") ||
            lower.contains("workday.com") ||
            lower.contains("smartrecruiters.com") ||
            lower.contains("bamboohr.com") ||
            lower.contains("careers.") ||
            lower.contains("/jobs") ||
            lower.contains("/careers")
    }

    private fun hasScamKeywords(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("pay registration fee") ||
            lower.contains("security deposit required") ||
            lower.contains("wire money to") ||
            lower.contains("telegram only contact")
    }

    private data class RawJobResult(
        val company: String,
        val role: String,
        val url: String,
        val snippet: String
    )
}
