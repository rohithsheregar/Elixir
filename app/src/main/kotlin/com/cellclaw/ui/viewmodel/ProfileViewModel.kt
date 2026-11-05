package com.cellclaw.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellclaw.profile.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class ProfileUiState(
    val profile: CandidateProfile? = null,
    val confirmedFacts: List<ProfileFact> = emptyList(),
    val unconfirmedFacts: List<ProfileFact> = emptyList(),
    val proposals: List<ProfileFactProposal> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val profileManager: ProfileManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val profile = profileRepository.getProfile()
            updateUiWithProfile(profile)
        }
    }

    private fun updateUiWithProfile(profile: CandidateProfile?) {
        if (profile == null) {
            _uiState.value = ProfileUiState(profile = null, isLoading = false)
            return
        }

        val all = listOfNotNull(
            profile.name, profile.email, profile.phone, profile.college,
            profile.university, profile.degree, profile.branch, profile.graduationYear,
            profile.cgpa, profile.cgpaScale, profile.marks10th, profile.marks12th
        ) + profile.skills + profile.projects + profile.internships +
            profile.certificates + profile.links + profile.reusableAnswers +
            profile.projectDetails.flatMap { it.toProfileFacts() }

        val confirmed = all.filter { it.confirmed }
        val unconfirmed = all.filter { !it.confirmed }

        val proposals = unconfirmed.map { unconfirmedFact ->
            val matchingCurrent = confirmed.firstOrNull { it.field == unconfirmedFact.field }
            ProfileFactProposal(current = matchingCurrent, proposed = unconfirmedFact)
        }

        _uiState.value = ProfileUiState(
            profile = profile,
            confirmedFacts = confirmed,
            unconfirmedFacts = unconfirmed,
            proposals = proposals,
            isLoading = false
        )
    }

    fun approveProposal(proposal: ProfileFactProposal) {
        viewModelScope.launch {
            profileManager.confirmProposal(proposal)
            loadProfile()
        }
    }

    fun rejectProposal(proposal: ProfileFactProposal) {
        viewModelScope.launch {
            val current = profileRepository.getProfile() ?: return@launch
            val updated = removeFact(current, proposal.proposed.id)
            profileRepository.saveProfile(updated)
            loadProfile()
        }
    }

    fun editFact(fact: ProfileFact, newValue: String) {
        viewModelScope.launch {
            val updatedFact = fact.copy(
                value = newValue,
                confirmed = true,
                updatedAt = Instant.now()
            )
            val current = profileRepository.getProfile() ?: CandidateProfile()
            val updatedProfile = replaceFact(current, updatedFact)
            profileRepository.saveProfile(updatedProfile)
            loadProfile()
        }
    }

    fun addFact(field: String, value: String) {
        viewModelScope.launch {
            val newFact = ProfileFact(
                id = UUID.randomUUID().toString(),
                field = field,
                value = value,
                source = ProfileFactSource.USER_ENTERED,
                confirmed = true,
                updatedAt = Instant.now()
            )
            val current = profileRepository.getProfile() ?: CandidateProfile()
            val updatedProfile = replaceFact(current, newFact)
            profileRepository.saveProfile(updatedProfile)
            loadProfile()
        }
    }

    fun fullWipe() {
        viewModelScope.launch {
            profileManager.fullWipe()
            loadProfile()
        }
    }

    private fun replaceFact(profile: CandidateProfile, fact: ProfileFact): CandidateProfile = when (fact.field) {
        "name" -> profile.copy(name = fact)
        "email" -> profile.copy(email = fact)
        "phone" -> profile.copy(phone = fact)
        "college" -> profile.copy(college = fact)
        "university" -> profile.copy(university = fact)
        "degree" -> profile.copy(degree = fact)
        "branch" -> profile.copy(branch = fact)
        "graduationYear" -> profile.copy(graduationYear = fact)
        "cgpa" -> profile.copy(cgpa = fact)
        "cgpaScale" -> profile.copy(cgpaScale = fact)
        "marks10th" -> profile.copy(marks10th = fact)
        "marks12th" -> profile.copy(marks12th = fact)
        "skills", "skill" -> profile.copy(skills = (profile.skills.filterNot { it.id == fact.id } + fact))
        "projects", "project" -> profile.copy(projects = (profile.projects.filterNot { it.id == fact.id } + fact))
        "internships", "internship" -> profile.copy(internships = (profile.internships.filterNot { it.id == fact.id } + fact))
        "certificates", "certificate" -> profile.copy(certificates = (profile.certificates.filterNot { it.id == fact.id } + fact))
        "links", "link" -> profile.copy(links = (profile.links.filterNot { it.id == fact.id } + fact))
        else -> profile.copy(reusableAnswers = (profile.reusableAnswers.filterNot { it.id == fact.id } + fact))
    }

    private fun removeFact(profile: CandidateProfile, factId: String): CandidateProfile = CandidateProfile(
        name = profile.name?.takeIf { it.id != factId },
        email = profile.email?.takeIf { it.id != factId },
        phone = profile.phone?.takeIf { it.id != factId },
        college = profile.college?.takeIf { it.id != factId },
        university = profile.university?.takeIf { it.id != factId },
        degree = profile.degree?.takeIf { it.id != factId },
        branch = profile.branch?.takeIf { it.id != factId },
        graduationYear = profile.graduationYear?.takeIf { it.id != factId },
        cgpa = profile.cgpa?.takeIf { it.id != factId },
        cgpaScale = profile.cgpaScale?.takeIf { it.id != factId },
        marks10th = profile.marks10th?.takeIf { it.id != factId },
        marks12th = profile.marks12th?.takeIf { it.id != factId },
        skills = profile.skills.filterNot { it.id == factId },
        projects = profile.projects.filterNot { it.id == factId },
        internships = profile.internships.filterNot { it.id == factId },
        certificates = profile.certificates.filterNot { it.id == factId },
        links = profile.links.filterNot { it.id == factId },
        reusableAnswers = profile.reusableAnswers.filterNot { it.id == factId }
    )
}
