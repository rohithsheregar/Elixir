package com.cellclaw.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cellclaw.eligibility.EligibilityEngine
import com.cellclaw.jd.AiJdExtractor
import com.cellclaw.jd.DocumentReadResult
import com.cellclaw.jd.JdDocumentReader
import com.cellclaw.jd.JdParser
import com.cellclaw.jd.JdRequirement
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.ProfileRepository
import com.cellclaw.tracker.ApplicationPayload
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.ApplicationTracker
import com.cellclaw.tracker.toPersisted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.cellclaw.tools.WebFetchTool

data class NewApplicationUiState(
    val jdText: String = "",
    val sourceUrl: String = "",
    val company: String = "",
    val role: String = "",
    val isParsing: Boolean = false,
    val errorMessage: String? = null,
    val parsedRequirement: JdRequirement? = null,
    val createdApplicationId: String? = null,
    val duplicateWarning: String? = null
)

@HiltViewModel
class NewApplicationViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val jdParser: JdParser,
    private val jdDocumentReader: JdDocumentReader,
    private val eligibilityEngine: EligibilityEngine,
    private val profileRepository: ProfileRepository,
    private val applicationTracker: ApplicationTracker,
    private val aiJdExtractor: AiJdExtractor? = null,
    private val webFetchTool: WebFetchTool? = null
) : ViewModel() {

    companion object {
        const val KEY_JD_TEXT = "key_jd_text"
        const val KEY_SOURCE_URL = "key_source_url"
        const val KEY_COMPANY = "key_company"
        const val KEY_ROLE = "key_role"
    }

    private val _uiState = MutableStateFlow(
        NewApplicationUiState(
            jdText = savedStateHandle[KEY_JD_TEXT] ?: "",
            sourceUrl = savedStateHandle[KEY_SOURCE_URL] ?: "",
            company = savedStateHandle[KEY_COMPANY] ?: "",
            role = savedStateHandle[KEY_ROLE] ?: ""
        )
    )
    val uiState: StateFlow<NewApplicationUiState> = _uiState.asStateFlow()

    fun onJdTextChanged(text: String) {
        savedStateHandle[KEY_JD_TEXT] = text
        _uiState.value = _uiState.value.copy(jdText = text, errorMessage = null)
    }

    fun onSourceUrlChanged(url: String) {
        savedStateHandle[KEY_SOURCE_URL] = url
        _uiState.value = _uiState.value.copy(sourceUrl = url)
    }

    fun fetchAndAnalyzeUrl(url: String) {
        val targetUrl = url.trim()
        if (targetUrl.isBlank() || webFetchTool == null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isParsing = true, errorMessage = null, sourceUrl = targetUrl)
            savedStateHandle[KEY_SOURCE_URL] = targetUrl

            val fetchResult = webFetchTool.execute(
                buildJsonObject {
                    put("url", targetUrl)
                    put("max_chars", 8000)
                }
            )

            if (!fetchResult.success) {
                _uiState.value = _uiState.value.copy(
                    isParsing = false,
                    errorMessage = "Could not fetch page: ${fetchResult.error}"
                )
                return@launch
            }

            val pageText: String = (fetchResult.data as? JsonObject)?.get("text")?.jsonPrimitive?.content.orEmpty()
            val parsedReq = if (aiJdExtractor != null) {
                aiJdExtractor.extract(pageText)
            } else {
                jdParser.parse(pageText)
            }

            val comp: String = _uiState.value.company.ifBlank { parsedReq.company.value.orEmpty() }
            val r: String = _uiState.value.role.ifBlank { parsedReq.role.value.orEmpty() }

            savedStateHandle[KEY_JD_TEXT] = pageText
            savedStateHandle[KEY_COMPANY] = comp
            savedStateHandle[KEY_ROLE] = r

            _uiState.value = _uiState.value.copy(
                jdText = pageText,
                company = comp,
                role = r,
                parsedRequirement = parsedReq,
                isParsing = false
            )
        }
    }

    fun onCompanyChanged(company: String) {
        savedStateHandle[KEY_COMPANY] = company
        _uiState.value = _uiState.value.copy(company = company)
    }

    fun onRoleChanged(role: String) {
        savedStateHandle[KEY_ROLE] = role
        _uiState.value = _uiState.value.copy(role = role)
    }

    fun processDocumentStream(inputStream: InputStream, mimeType: String?, fileName: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isParsing = true, errorMessage = null)
            when (val result = jdDocumentReader.read(inputStream, mimeType, fileName)) {
                is DocumentReadResult.Success -> {
                    val parsed = jdParser.parse(result.text)
                    val comp = _uiState.value.company.ifBlank { parsed.company.value.orEmpty() }
                    val r = _uiState.value.role.ifBlank { parsed.role.value.orEmpty() }
                    savedStateHandle[KEY_JD_TEXT] = result.text
                    savedStateHandle[KEY_COMPANY] = comp
                    savedStateHandle[KEY_ROLE] = r
                    _uiState.value = _uiState.value.copy(
                        jdText = result.text,
                        parsedRequirement = parsed,
                        company = comp,
                        role = r,
                        isParsing = false
                    )
                }
                is DocumentReadResult.FallbackToPaste -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.reason,
                        isParsing = false
                    )
                }
            }
        }
    }

    fun parseCurrentJd() {
        val text = _uiState.value.jdText
        if (text.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter or paste JD text.")
            return
        }
        val extractor = aiJdExtractor
        if (extractor == null) {
            val parsed = jdParser.parse(text)
            applyParsedResult(parsed)
        } else {
            _uiState.value = _uiState.value.copy(isParsing = true)
            viewModelScope.launch {
                val parsed = extractor.extract(text)
                applyParsedResult(parsed)
            }
        }
    }

    private fun applyParsedResult(parsed: JdRequirement) {
        val comp = _uiState.value.company.ifBlank { parsed.company.value.orEmpty() }
        val r = _uiState.value.role.ifBlank { parsed.role.value.orEmpty() }
        savedStateHandle[KEY_COMPANY] = comp
        savedStateHandle[KEY_ROLE] = r
        _uiState.value = _uiState.value.copy(
            parsedRequirement = parsed,
            company = comp,
            role = r,
            isParsing = false,
            errorMessage = null
        )
    }

    fun createApplication(onCreated: (String) -> Unit) {
        val text = _uiState.value.jdText
        if (text.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "JD text cannot be empty.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isParsing = true)

            val parsed = _uiState.value.parsedRequirement ?: jdParser.parse(text)
            val profile = profileRepository.getProfile() ?: CandidateProfile()
            val eligibilityReport = eligibilityEngine.evaluate(profile, parsed)

            val appId = UUID.randomUUID().toString()
            val finalCompany = _uiState.value.company.ifBlank { parsed.company.value ?: "Unknown Company" }
            val finalRole = _uiState.value.role.ifBlank { parsed.role.value ?: "Placement Applicant" }
            val jdHash = computeSha256(text.toByteArray())

            val payload = ApplicationPayload(
                eligibilityReport = eligibilityReport,
                jdRequirement = parsed.toPersisted(),
                rawJdText = text
            )

            val record = ApplicationRecord(
                id = appId,
                company = finalCompany,
                role = finalRole,
                jobId = null,
                sourceUrl = _uiState.value.sourceUrl.trim().takeIf { it.isNotBlank() },
                jdHash = jdHash,
                deadline = parsed.deadline.value?.toString(),
                eligibilityResult = eligibilityReport.overall.name,
                resumeVersion = 1,
                state = ApplicationState.DRAFT,
                notes = Json.encodeToString(payload)
            )

            val dupCheck = applicationTracker.registerApplication(record)

            _uiState.value = _uiState.value.copy(
                isParsing = false,
                createdApplicationId = appId,
                duplicateWarning = if (dupCheck.hasLikelyDuplicate) dupCheck.reason else null
            )

            onCreated(appId)
        }
    }

    private fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
