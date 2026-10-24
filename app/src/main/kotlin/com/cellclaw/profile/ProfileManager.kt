package com.cellclaw.profile

/** The only two outcomes callers can use for a requested profile fact. */
sealed interface ConfirmedFactResult {
    data class Confirmed(val fact: ProfileFact) : ConfirmedFactResult
    data object Unknown : ConfirmedFactResult
}

/** A visible before/after record. The proposed fact is always unconfirmed. */
class ProfileFactProposal internal constructor(
    val current: ProfileFact?,
    val proposed: ProfileFact
)

/**
 * Applies the profile confirmation boundary: proposed values are never usable until explicitly
 * confirmed, and fact reads return [ConfirmedFactResult.Unknown] for missing or unconfirmed data.
 */
class ProfileManager(private val repository: ProfileRepository) {
    suspend fun getConfirmedFact(field: String): ConfirmedFactResult {
        val fact = repository.getProfile()
            ?.allFacts()
            ?.firstOrNull { it.field == field && it.confirmed }
        return fact?.let(ConfirmedFactResult::Confirmed) ?: ConfirmedFactResult.Unknown
    }

    fun proposeFactUpdate(current: ProfileFact?, proposed: ProfileFact): ProfileFactProposal {
        require(proposed.field.isNotBlank()) { "Profile fact field cannot be blank" }
        require(proposed.value.isNotBlank()) { "Profile fact value cannot be blank" }
        return ProfileFactProposal(current = current, proposed = proposed.copy(confirmed = false))
    }

    suspend fun confirmProposal(proposal: ProfileFactProposal): ProfileFact {
        val confirmed = proposal.proposed.copy(confirmed = true)
        val existing = repository.getProfile() ?: CandidateProfile()
        repository.saveProfile(existing.replace(confirmed))
        return confirmed
    }

    private fun CandidateProfile.allFacts(): List<ProfileFact> =
        listOfNotNull(
            name, email, phone, college, university, degree, branch, graduationYear, cgpa, cgpaScale,
            marks10th, marks12th
        ) + skills + projects + internships + certificates + links + reusableAnswers

    private fun CandidateProfile.replace(fact: ProfileFact): CandidateProfile = when (fact.field) {
        "name" -> copy(name = fact)
        "email" -> copy(email = fact)
        "phone" -> copy(phone = fact)
        "college" -> copy(college = fact)
        "university" -> copy(university = fact)
        "degree" -> copy(degree = fact)
        "branch" -> copy(branch = fact)
        "graduationYear" -> copy(graduationYear = fact)
        "cgpa" -> copy(cgpa = fact)
        "cgpaScale" -> copy(cgpaScale = fact)
        "marks10th" -> copy(marks10th = fact)
        "marks12th" -> copy(marks12th = fact)
        "skill", "skills" -> copy(skills = skills.replacing(fact))
        "project", "projects" -> copy(projects = projects.replacing(fact))
        "internship", "internships" -> copy(internships = internships.replacing(fact))
        "certificate", "certificates" -> copy(certificates = certificates.replacing(fact))
        "link", "links" -> copy(links = links.replacing(fact))
        "reusableAnswer", "reusableAnswers" -> copy(reusableAnswers = reusableAnswers.replacing(fact))
        else -> error("Unsupported profile field: ${fact.field}")
    }

    private fun List<ProfileFact>.replacing(replacement: ProfileFact): List<ProfileFact> {
        val index = indexOfFirst { it.id == replacement.id }
        return if (index == -1) this + replacement else toMutableList().apply { set(index, replacement) }
    }
}
