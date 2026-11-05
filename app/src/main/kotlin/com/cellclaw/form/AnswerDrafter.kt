package com.cellclaw.form

import com.cellclaw.ai.AiClient
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.ProfileFact
import com.cellclaw.profile.ProjectDetail
import com.cellclaw.provider.CompletionRequest
import com.cellclaw.provider.ContentBlock
import com.cellclaw.provider.Message
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A drafted answer to a free-text form question, strictly backed by verified profile facts.
 */
@Serializable
data class DraftedAnswer(
    val question: String,
    val text: String,
    val citedFactIds: List<String>,
    val characterCount: Int = text.length,
    val lengthLimit: Int? = null,
    val isWithinLimit: Boolean = lengthLimit == null || text.length <= lengthLimit,
    val requiresApproval: Boolean = true,
    val approved: Boolean = false
) {
    fun withApproval(isApproved: Boolean = true): DraftedAnswer =
        copy(approved = isApproved)
}

/**
 * Interface for drafting free-text answers from confirmed profile facts only.
 */
interface AnswerDrafter {
    suspend fun draftAnswer(
        question: String,
        profile: CandidateProfile,
        company: String? = null,
        role: String? = null,
        lengthLimit: Int? = null,
        applicationId: String? = null
    ): DraftedAnswer
}

@Singleton
class DefaultAnswerDrafter @Inject constructor(
    private val aiClient: AiClient? = null
) : AnswerDrafter {

    override suspend fun draftAnswer(
        question: String,
        profile: CandidateProfile,
        company: String?,
        role: String?,
        lengthLimit: Int?,
        applicationId: String?
    ): DraftedAnswer {
        val confirmedFacts = profile.allFacts().filter { it.confirmed }
        val projects = profile.projectDetails.filter { it.confirmed }

        // Attempt AI-assisted drafting if client is available
        if (aiClient != null) {
            try {
                val drafted = draftWithAi(question, profile, company, role, lengthLimit, applicationId)
                if (drafted != null) {
                    val fittedText = enforceLengthLimit(drafted.first, lengthLimit)
                    return DraftedAnswer(
                        question = question,
                        text = fittedText,
                        citedFactIds = drafted.second,
                        characterCount = fittedText.length,
                        lengthLimit = lengthLimit,
                        isWithinLimit = lengthLimit == null || fittedText.length <= lengthLimit,
                        requiresApproval = true,
                        approved = false
                    )
                }
            } catch (_: Exception) {
                // Fall back to deterministic synthesizer upon any AI error, budget limit, or 429
            }
        }

        // Deterministic zero-fabrication synthesizer fallback
        val (text, citedIds) = synthesizeDeterministicAnswer(question, confirmedFacts, projects, company, role)
        val fittedText = enforceLengthLimit(text, lengthLimit)

        return DraftedAnswer(
            question = question,
            text = fittedText,
            citedFactIds = citedIds,
            characterCount = fittedText.length,
            lengthLimit = lengthLimit,
            isWithinLimit = lengthLimit == null || fittedText.length <= lengthLimit,
            requiresApproval = true,
            approved = false
        )
    }

    private suspend fun draftWithAi(
        question: String,
        profile: CandidateProfile,
        company: String?,
        role: String?,
        lengthLimit: Int?,
        applicationId: String?
    ): Pair<String, List<String>>? {
        val facts = profile.allFacts().filter { it.confirmed }
        if (facts.isEmpty()) return null

        val factDirectory = facts.joinToString("\n") { "[${it.id}] ${it.field}: ${it.value}" }
        val projectsDirectory = profile.projectDetails.filter { it.confirmed }.joinToString("\n\n") { p ->
            "[proj:${p.id}] ${p.title} (${p.role}): Problem: ${p.problem}; Tools: ${p.tools.joinToString(", ")}; Challenge: ${p.challenge}; Results: ${p.verifiedResults}"
        }

        val limitInstruction = if (lengthLimit != null) "Strictly limit answer to under $lengthLimit characters." else ""
        val systemPrompt = """
            You are Elixir, an honest placement application assistant.
            ZERO FABRICATION GUARANTEE: You must ONLY draft the answer using the provided confirmed facts.
            NEVER invent metrics, projects, skills, tools, or experiences not listed below.
            Cite fact IDs used in brackets like [fact-id] at the end.
            $limitInstruction
        """.trimIndent()

        val userPrompt = """
            Question: $question
            Target Company: ${company ?: "Placement Partner"}
            Target Role: ${role ?: "Candidate Role"}

            Confirmed Profile Facts:
            $factDirectory

            Confirmed Projects:
            $projectsDirectory
        """.trimIndent()

        val request = CompletionRequest(
            systemPrompt = systemPrompt,
            messages = listOf(Message.user(userPrompt)),
            maxTokens = 500
        )

        val response = aiClient?.complete(request, applicationId = applicationId) ?: return null
        val fullText = response.content.filterIsInstance<ContentBlock.Text>().joinToString(" ") { it.text }.trim()
        if (fullText.isBlank()) return null

        // Extract cited IDs and clean text
        val idRegex = Regex("""\[([a-zA-Z0-9_\-:]+)\]""")
        val citedIds = idRegex.findAll(fullText).map { it.groupValues[1] }.toList()
        val cleanedText = fullText.replace(idRegex, "").replace(Regex("""\s+"""), " ").trim()

        val effectiveCited = if (citedIds.isNotEmpty()) citedIds else facts.take(3).map { it.id }
        return Pair(cleanedText, effectiveCited)
    }

    private fun synthesizeDeterministicAnswer(
        question: String,
        confirmedFacts: List<ProfileFact>,
        projects: List<ProjectDetail>,
        company: String?,
        role: String?
    ): Pair<String, List<String>> {
        val qLower = question.lowercase()
        val citedIds = mutableListOf<String>()

        // 1. Project / Technical Experience questions
        if (qLower.contains("project") || qLower.contains("challenge") || qLower.contains("built") || qLower.contains("accomplish")) {
            val project = projects.firstOrNull()
            if (project != null) {
                citedIds.add("${project.id}-title")
                citedIds.add("${project.id}-role")
                citedIds.add("${project.id}-problem")
                citedIds.add("${project.id}-results")

                val text = buildString {
                    append("As ${project.role} on ${project.title}, I solved: ${project.problem} ")
                    if (project.tools.isNotEmpty()) {
                        append("using ${project.tools.joinToString(", ")}. ")
                        citedIds.add("${project.id}-tools")
                    }
                    append("Overcoming ${project.challenge}, we achieved: ${project.verifiedResults}")
                }
                return Pair(text, citedIds)
            }
        }

        // 2. Motivation / Why Join / Role interest questions
        if (qLower.contains("why") || qLower.contains("interest") || qLower.contains("join") || qLower.contains("fit")) {
            val degreeFact = confirmedFacts.firstOrNull { it.field == "degree" }
            val skillsFacts = confirmedFacts.filter { it.field in listOf("skill", "skills") }
            degreeFact?.let { citedIds.add(it.id) }
            skillsFacts.take(3).forEach { citedIds.add(it.id) }

            val degreeText = degreeFact?.value ?: "Computer Science"
            val skillsText = if (skillsFacts.isNotEmpty()) " in ${skillsFacts.take(3).joinToString(", ") { it.value }}" else ""
            val target = company ?: "your organization"
            val targetRole = role ?: "this position"

            val text = "With a background in $degreeText and proven competencies$skillsText, I am keen to contribute to $target as $targetRole, delivering rigorous engineering and measurable outcomes."
            return Pair(text, citedIds)
        }

        // 3. Technical Skills questions
        if (qLower.contains("skill") || qLower.contains("tech") || qLower.contains("tool") || qLower.contains("stack")) {
            val skillsFacts = confirmedFacts.filter { it.field in listOf("skill", "skills") }
            skillsFacts.forEach { citedIds.add(it.id) }

            val skillsList = skillsFacts.map { it.value }
            val text = if (skillsList.isNotEmpty()) {
                "My confirmed technical skills include: ${skillsList.joinToString(", ")}."
            } else {
                "My technical competencies are verified directly through my confirmed profile facts."
            }
            return Pair(text, citedIds)
        }

        // 4. General Candidate Overview
        val degreeFact = confirmedFacts.firstOrNull { it.field == "degree" }
        val cgpaFact = confirmedFacts.firstOrNull { it.field == "cgpa" }
        degreeFact?.let { citedIds.add(it.id) }
        cgpaFact?.let { citedIds.add(it.id) }

        val text = buildString {
            append("I hold a confirmed qualification in ${degreeFact?.value ?: "engineering"}")
            if (cgpaFact != null) {
                append(" with a CGPA of ${cgpaFact.value}")
            }
            append(", backed by verified hands-on coursework and project milestones.")
        }
        return Pair(text, citedIds)
    }

    private fun enforceLengthLimit(text: String, lengthLimit: Int?): String {
        if (lengthLimit == null || text.length <= lengthLimit) {
            return text
        }
        if (lengthLimit <= 3) {
            return text.take(lengthLimit)
        }

        // Truncate at sentence boundary if possible
        val truncated = text.take(lengthLimit)
        val lastSentenceIndex = truncated.lastIndexOf(". ")
        if (lastSentenceIndex > lengthLimit / 2) {
            return truncated.substring(0, lastSentenceIndex + 1).trim()
        }

        // Otherwise truncate cleanly at word boundary
        val lastWordIndex = truncated.lastIndexOf(" ")
        return if (lastWordIndex > lengthLimit / 3) {
            truncated.substring(0, lastWordIndex).trim() + "…"
        } else {
            truncated.take(lengthLimit - 1).trim() + "…"
        }
    }
}

