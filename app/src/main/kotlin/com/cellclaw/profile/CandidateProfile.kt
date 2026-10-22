package com.cellclaw.profile

import java.time.Instant

enum class ProfileFactSource { USER_ENTERED, IMPORTED_FROM_RESUME }

data class ProfileFact(
    val id: String,
    val field: String,
    val value: String,
    val source: ProfileFactSource,
    val confirmed: Boolean,
    val updatedAt: Instant
)

data class CandidateProfile(
    val name: ProfileFact? = null,
    val email: ProfileFact? = null,
    val phone: ProfileFact? = null,
    val college: ProfileFact? = null,
    val university: ProfileFact? = null,
    val degree: ProfileFact? = null,
    val branch: ProfileFact? = null,
    val graduationYear: ProfileFact? = null,
    val cgpa: ProfileFact? = null,
    val cgpaScale: ProfileFact? = null,
    val marks10th: ProfileFact? = null,
    val marks12th: ProfileFact? = null,
    val skills: List<ProfileFact> = emptyList(),
    val projects: List<ProfileFact> = emptyList(),
    val internships: List<ProfileFact> = emptyList(),
    val certificates: List<ProfileFact> = emptyList(),
    val links: List<ProfileFact> = emptyList(),
    val reusableAnswers: List<ProfileFact> = emptyList()
)
