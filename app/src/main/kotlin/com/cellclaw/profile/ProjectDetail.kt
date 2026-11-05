package com.cellclaw.profile

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Structured project details stored in the candidate's profile.
 * Provides verifiable ground truth for:
 * - Specific role played by candidate
 * - Problem solved
 * - Tools/technologies utilized
 * - Key technical challenge overcome
 * - Quantifiable/verified results
 * - Optional short story/narrative for behavioral interviews
 */
@Serializable
data class ProjectDetail(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val problem: String,
    val role: String,
    val tools: List<String> = emptyList(),
    val challenge: String,
    val verifiedResults: String,
    val shortStory: String? = null,
    val confirmed: Boolean = true
) {
    init {
        require(title.isNotBlank()) { "Project title cannot be blank" }
        require(problem.isNotBlank()) { "Problem statement cannot be blank" }
        require(role.isNotBlank()) { "Candidate role cannot be blank" }
        require(challenge.isNotBlank()) { "Technical challenge cannot be blank" }
        require(verifiedResults.isNotBlank()) { "Verified results cannot be blank" }
    }

    /**
     * Converts structured project details into a human-readable profile summary
     * for display, keyword matching, and resume tailoring.
     */
    fun toSummary(): String {
        val toolsStr = if (tools.isNotEmpty()) " (${tools.joinToString(", ")})" else ""
        return "$title: $role — $problem. $verifiedResults$toolsStr"
    }

    /**
     * Converts structured project details into standard verified ProfileFacts
     * so that the existing evidence map and provenance verification system
     * (#profileFactId) can verify individual facts without fabrication.
     */
    fun toProfileFacts(baseId: String = id): List<ProfileFact> {
        val now = Instant.now()
        val facts = mutableListOf<ProfileFact>()
        facts.add(ProfileFact("$baseId-title", "project_title", title, ProfileFactSource.USER_ENTERED, confirmed, now))
        facts.add(ProfileFact("$baseId-role", "project_role", role, ProfileFactSource.USER_ENTERED, confirmed, now))
        facts.add(ProfileFact("$baseId-problem", "project_problem", problem, ProfileFactSource.USER_ENTERED, confirmed, now))
        facts.add(ProfileFact("$baseId-challenge", "project_challenge", challenge, ProfileFactSource.USER_ENTERED, confirmed, now))
        facts.add(ProfileFact("$baseId-results", "project_results", verifiedResults, ProfileFactSource.USER_ENTERED, confirmed, now))
        if (tools.isNotEmpty()) {
            facts.add(ProfileFact("$baseId-tools", "project_tools", tools.joinToString(", "), ProfileFactSource.USER_ENTERED, confirmed, now))
        }
        if (!shortStory.isNullOrBlank()) {
            facts.add(ProfileFact("$baseId-story", "project_story", shortStory, ProfileFactSource.USER_ENTERED, confirmed, now))
        }
        return facts
    }
}

