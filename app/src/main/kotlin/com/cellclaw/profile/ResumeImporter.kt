package com.cellclaw.profile

import java.time.Instant
import java.util.UUID

/**
 * Extracts only explicitly labelled resume facts. Every extracted fact remains a proposal until
 * the user confirms it through [ProfileManager]. Unlabelled prose is intentionally ignored.
 */
class ResumeImporter(
    private val now: () -> Instant = Instant::now,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    fun import(text: String, currentProfile: CandidateProfile = CandidateProfile()): List<ProfileFactProposal> {
        val existingFacts = currentProfile.factsByField()
        return text.lineSequence()
            .flatMap(::factsFromLine)
            .distinctBy { it.field to it.value.trim().lowercase() }
            .map { fact ->
                ProfileFactProposal(
                    current = existingFacts[fact.field]?.firstOrNull(),
                    proposed = fact
                )
            }
            .toList()
    }

    private fun factsFromLine(line: String): Sequence<ProfileFact> {
        val match = LABELLED_LINE.matchEntire(line.trim()) ?: return emptySequence()
        val label = match.groupValues[1].lowercase()
        val value = match.groupValues[2].trim()
        if (value.isEmpty()) return emptySequence()

        return when (label) {
            "name", "email", "phone", "college", "university", "degree", "branch" ->
                sequenceOf(fact(label, value))
            "graduation year" -> sequenceOf(fact("graduationYear", value))
            "10th", "10th marks" -> sequenceOf(fact("marks10th", value))
            "12th", "12th marks" -> sequenceOf(fact("marks12th", value))
            "cgpa" -> cgpaFacts(value)
            "skills" -> value.split(',').asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { skill -> fact("skill", skill) }
            "github", "linkedin", "portfolio", "link" -> sequenceOf(fact("link", value))
            else -> emptySequence()
        }
    }

    private fun cgpaFacts(value: String): Sequence<ProfileFact> {
        val parts = value.split('/', limit = 2).map(String::trim)
        if (parts.firstOrNull().isNullOrEmpty()) return emptySequence()
        return sequence {
            yield(fact("cgpa", parts.first()))
            val scale = parts.getOrNull(1)
            if (!scale.isNullOrEmpty()) {
                yield(fact("cgpaScale", scale))
            }
        }
    }

    private fun fact(field: String, value: String) = ProfileFact(
        id = newId(),
        field = field,
        value = value,
        source = ProfileFactSource.IMPORTED_FROM_RESUME,
        confirmed = false,
        updatedAt = now()
    )

    private fun CandidateProfile.factsByField(): Map<String, List<ProfileFact>> =
        listOfNotNull(
            name, email, phone, college, university, degree, branch, graduationYear, cgpa, cgpaScale,
            marks10th, marks12th
        ).groupBy(ProfileFact::field) +
            mapOf(
                "skill" to skills.filter { it.field == "skill" || it.field == "skills" },
                "project" to projects.filter { it.field == "project" || it.field == "projects" },
                "internship" to internships.filter { it.field == "internship" || it.field == "internships" },
                "certificate" to certificates.filter { it.field == "certificate" || it.field == "certificates" },
                "link" to links.filter { it.field == "link" || it.field == "links" },
                "reusableAnswer" to reusableAnswers.filter {
                    it.field == "reusableAnswer" || it.field == "reusableAnswers"
                }
            )

    private companion object {
        val LABELLED_LINE = Regex("^([A-Za-z0-9 ]+):\\s*(.+)$")
    }
}
