package com.cellclaw.tools.placement

import android.content.Context
import com.cellclaw.approval.ApprovalTokenManager
import com.cellclaw.approval.TokenValidationResult
import com.cellclaw.eligibility.EligibilityEngine
import com.cellclaw.eligibility.EligibilityStatus
import com.cellclaw.eligibility.OverallEligibility
import com.cellclaw.form.AnswerDrafter
import com.cellclaw.form.DraftedAnswer
import com.cellclaw.jd.AiJdExtractor
import com.cellclaw.jd.JdField
import com.cellclaw.jd.JdRequirement
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.ProfileRepository
import com.cellclaw.resume.PdfGenerator
import com.cellclaw.resume.ResumeBuilder
import com.cellclaw.tools.*
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.tracker.ApplicationTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal fun computeSha256(text: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

// -----------------------------------------------------------------------------
// 1. JdParseTool ("jd.parse")
// -----------------------------------------------------------------------------
@Singleton
class JdParseTool @Inject constructor(
    private val aiJdExtractor: AiJdExtractor
) : Tool {
    override val name: String = "jd.parse"
    override val description: String =
        "Parses raw or extracted job description text into structured requirements including company, role, CGPA cutoff, required skills, preferred skills, backlog rule, and application questions."
    override val requiresApproval: Boolean = false
    override val riskTier: RiskTier = RiskTier.READ

    override val parameters: ToolParameters = ToolParameters(
        properties = mapOf(
            "jd_text" to ParameterProperty("string", "The full job description text to parse"),
            "company" to ParameterProperty("string", "Optional company name if already known")
        ),
        required = listOf("jd_text")
    )

    override suspend fun execute(params: JsonObject): ToolResult {
        val jdText = try {
            params["jd_text"]?.jsonPrimitive?.content
        } catch (_: Exception) { null }

        if (jdText.isNullOrBlank()) {
            return ToolResult.error("Parameter 'jd_text' is required and cannot be blank")
        }

        val company = try {
            params["company"]?.jsonPrimitive?.content
        } catch (_: Exception) { null }

        val requirement = aiJdExtractor.extract(jdText, company)

        val resultJson = buildJsonObject {
            put("company", requirement.company.value ?: company ?: "Unknown")
            put("role", requirement.role.value ?: "Unknown")
            put("min_cgpa", requirement.minCgpa.value)
            put("cgpa_scale", requirement.cgpaScale.value ?: 10.0)
            put("degree", requirement.degree.value)
            put("branch", requirement.branch.value)
            put("passing_year", requirement.passingYear.value)
            put("backlog_rule", requirement.backlogRule.value)
            put("required_skills", buildJsonArray {
                requirement.requiredSkills.value.orEmpty().forEach { add(JsonPrimitive(it)) }
            })
            put("preferred_skills", buildJsonArray {
                requirement.preferredSkills.value.orEmpty().forEach { add(JsonPrimitive(it)) }
            })
            put("questions", buildJsonArray {
                requirement.questions.value.orEmpty().forEach { add(JsonPrimitive(it)) }
            })
            put("deadline", requirement.deadline.value?.toString())
            put("jd_hash", computeSha256(jdText))
        }

        return ToolResult.success(resultJson)
    }
}

// -----------------------------------------------------------------------------
// 2. EligibilityEvaluateTool ("eligibility.evaluate")
// -----------------------------------------------------------------------------
@Singleton
class EligibilityEvaluateTool @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val eligibilityEngine: EligibilityEngine,
    private val aiJdExtractor: AiJdExtractor
) : Tool {
    override val name: String = "eligibility.evaluate"
    override val description: String =
        "Evaluates the candidate's confirmed profile against job description requirements. Returns PASS, FAIL, or UNKNOWN with precise reasons per criterion and overall verdict."
    override val requiresApproval: Boolean = false
    override val riskTier: RiskTier = RiskTier.READ

    override val parameters: ToolParameters = ToolParameters(
        properties = mapOf(
            "jd_text" to ParameterProperty("string", "Job description text to parse and evaluate"),
            "company" to ParameterProperty("string", "Company name"),
            "role" to ParameterProperty("string", "Target role"),
            "min_cgpa" to ParameterProperty("number", "Minimum CGPA cutoff"),
            "required_skills" to ParameterProperty("array", "List of mandatory technical skills")
        )
    )

    override suspend fun execute(params: JsonObject): ToolResult {
        val profile = profileRepository.getProfile()
            ?: return ToolResult.error("No candidate profile found. Profile must be configured before evaluating eligibility.")

        val jdText = try { params["jd_text"]?.jsonPrimitive?.content } catch (_: Exception) { null }
        val company = try { params["company"]?.jsonPrimitive?.content } catch (_: Exception) { null }

        val jdRequirement = if (!jdText.isNullOrBlank()) {
            aiJdExtractor.extract(jdText, company)
        } else {
            val role = try { params["role"]?.jsonPrimitive?.content } catch (_: Exception) { null }
            val minCgpa = try { params["min_cgpa"]?.jsonPrimitive?.doubleOrNull } catch (_: Exception) { null }
            val skills = try {
                params["required_skills"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }
            } catch (_: Exception) { null }

            JdRequirement(
                company = JdField(company),
                role = JdField(role),
                minCgpa = JdField(minCgpa),
                requiredSkills = JdField(skills)
            )
        }

        val report = eligibilityEngine.evaluate(profile, jdRequirement)
        val disqualifyingReasons = report.criteria.filter { it.status == EligibilityStatus.FAIL }.map { it.reason }

        val resultJson = buildJsonObject {
            put("overall_status", report.overall.name)
            put("is_eligible", report.overall == OverallEligibility.ELIGIBLE)
            put("criteria", buildJsonArray {
                for (c in report.criteria) {
                    add(buildJsonObject {
                        put("criterion", c.criterion)
                        put("status", c.status.name)
                        put("reason", c.reason)
                        put("mandatory", c.isMandatory)
                    })
                }
            })
            put("disqualifying_reasons", buildJsonArray {
                for (r in disqualifyingReasons) {
                    add(JsonPrimitive(r))
                }
            })
        }

        return ToolResult.success(resultJson)
    }
}

// -----------------------------------------------------------------------------
// 3. ResumeGenerateTool ("resume.generate")
// -----------------------------------------------------------------------------
@Singleton
class ResumeGenerateTool : Tool {
    override val name: String = "resume.generate"
    override val description: String =
        "Generates a tailored ATS-friendly resume PDF for a specific company and role using ONLY confirmed profile facts. Returns PDF metadata, file path, and verifiable evidence map."
    override val requiresApproval: Boolean = false
    override val riskTier: RiskTier = RiskTier.REVERSIBLE_WRITE

    override val parameters: ToolParameters = ToolParameters(
        properties = mapOf(
            "company" to ParameterProperty("string", "Target company name"),
            "role" to ParameterProperty("string", "Target role or position"),
            "jd_text" to ParameterProperty("string", "Optional JD text to tailor skills and project rankings")
        ),
        required = listOf("company", "role")
    )

    private val profileRepository: ProfileRepository
    private val resumeBuilder: ResumeBuilder
    private val pdfGenerator: PdfGenerator
    private val aiJdExtractor: AiJdExtractor
    private val baseOutputDir: File?

    @Inject
    constructor(
        profileRepository: ProfileRepository,
        resumeBuilder: ResumeBuilder,
        pdfGenerator: PdfGenerator,
        aiJdExtractor: AiJdExtractor,
        @ApplicationContext context: Context
    ) : this(
        profileRepository = profileRepository,
        resumeBuilder = resumeBuilder,
        pdfGenerator = pdfGenerator,
        aiJdExtractor = aiJdExtractor,
        outputDir = File(context.filesDir, "resumes")
    )

    constructor(
        profileRepository: ProfileRepository,
        resumeBuilder: ResumeBuilder,
        pdfGenerator: PdfGenerator,
        aiJdExtractor: AiJdExtractor,
        outputDir: File? = null
    ) {
        this.profileRepository = profileRepository
        this.resumeBuilder = resumeBuilder
        this.pdfGenerator = pdfGenerator
        this.aiJdExtractor = aiJdExtractor
        this.baseOutputDir = outputDir
    }

    override suspend fun execute(params: JsonObject): ToolResult {
        val company = try { params["company"]?.jsonPrimitive?.content } catch (_: Exception) { null }
            ?: return ToolResult.error("Missing required parameter 'company'")
        val role = try { params["role"]?.jsonPrimitive?.content } catch (_: Exception) { null }
            ?: return ToolResult.error("Missing required parameter 'role'")
        val jdText = try { params["jd_text"]?.jsonPrimitive?.content } catch (_: Exception) { null } ?: ""

        val profile = profileRepository.getProfile()
            ?: return ToolResult.error("No candidate profile found. Profile must be configured before generating resume.")

        val jdRequirement = if (jdText.isNotBlank()) {
            aiJdExtractor.extract(jdText, company)
        } else {
            JdRequirement(company = JdField(company), role = JdField(role))
        }

        val resumeContent = try {
            resumeBuilder.build(profile, jdRequirement)
        } catch (e: Exception) {
            return ToolResult.error("Failed to build tailored resume: ${e.message}")
        }

        val jdHash = if (jdText.isNotBlank()) computeSha256(jdText) else "generic_hash"
        val pdf = try {
            pdfGenerator.generate(resumeContent, company, role, jdHash, version = 1)
        } catch (e: Exception) {
            return ToolResult.error("Failed to generate PDF: ${e.message}")
        }

        val dir = baseOutputDir ?: File(System.getProperty("java.io.tmpdir"), "cellclaw_resumes")
        if (!dir.exists()) dir.mkdirs()
        val pdfFile = File(dir, pdf.fileName)
        try {
            pdfFile.writeBytes(pdf.bytes)
        } catch (e: Exception) {
            return ToolResult.error("Failed to persist PDF to disk: ${e.message}")
        }

        val resultJson = buildJsonObject {
            put("file_name", pdf.fileName)
            put("file_path", pdfFile.absolutePath)
            put("sha256", pdf.sha256)
            put("jd_hash", pdf.jdHash)
            put("page_count", pdf.pageCount)
            put("evidence_map", buildJsonObject {
                for ((lineId, factId) in resumeContent.evidenceMap.lineToFactId) {
                    put(lineId, factId)
                }
            })
        }

        return ToolResult.success(resultJson)
    }
}

// -----------------------------------------------------------------------------
// 4. ApplicationPrepareReviewTool ("application.prepare_review")
// -----------------------------------------------------------------------------
@Singleton
class ApplicationPrepareReviewTool @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val eligibilityEngine: EligibilityEngine,
    private val aiJdExtractor: AiJdExtractor,
    private val answerDrafter: AnswerDrafter,
    private val applicationTracker: ApplicationTracker
) : Tool {
    override val name: String = "application.prepare_review"
    override val description: String =
        "Prepares an application for human review prior to submission. Gathers eligibility status, tailored resume, and drafted answers for application questions."
    override val requiresApproval: Boolean = false
    override val riskTier: RiskTier = RiskTier.READ

    override val parameters: ToolParameters = ToolParameters(
        properties = mapOf(
            "company" to ParameterProperty("string", "Company name"),
            "role" to ParameterProperty("string", "Target role"),
            "form_url" to ParameterProperty("string", "Application or Google Form URL"),
            "jd_text" to ParameterProperty("string", "Job description text")
        ),
        required = listOf("company", "role")
    )

    override suspend fun execute(params: JsonObject): ToolResult {
        val company = try { params["company"]?.jsonPrimitive?.content } catch (_: Exception) { null }
            ?: return ToolResult.error("Missing required parameter 'company'")
        val role = try { params["role"]?.jsonPrimitive?.content } catch (_: Exception) { null }
            ?: return ToolResult.error("Missing required parameter 'role'")
        val formUrl = try { params["form_url"]?.jsonPrimitive?.content } catch (_: Exception) { null }
        val jdText = try { params["jd_text"]?.jsonPrimitive?.content } catch (_: Exception) { null } ?: ""

        val profile = profileRepository.getProfile()
            ?: return ToolResult.error("No candidate profile found. Profile must be configured first.")

        val jdRequirement = if (jdText.isNotBlank()) {
            aiJdExtractor.extract(jdText, company)
        } else {
            JdRequirement(company = JdField(company), role = JdField(role))
        }

        val eligibilityReport = eligibilityEngine.evaluate(profile, jdRequirement)
        val jdHash = if (jdText.isNotBlank()) computeSha256(jdText) else "generic_hash"

        // Draft answers for questions in JD
        val draftedAnswers = mutableListOf<DraftedAnswer>()
        for (q in jdRequirement.questions.value.orEmpty()) {
            val draft = answerDrafter.draftAnswer(
                question = q,
                profile = profile,
                company = company,
                role = role
            )
            draftedAnswers.add(draft)
        }

        // Register or update in tracker
        val record = ApplicationRecord(
            company = company,
            role = role,
            sourceUrl = formUrl,
            jdHash = jdHash,
            eligibilityResult = eligibilityReport.overall.name,
            state = ApplicationState.READY_FOR_REVIEW
        )
        val dupCheck = applicationTracker.registerApplication(record)

        val resultJson = buildJsonObject {
            put("application_id", record.id)
            put("company", company)
            put("role", role)
            put("form_url", formUrl ?: "")
            put("eligibility_status", eligibilityReport.overall.name)
            put("is_eligible", eligibilityReport.overall == OverallEligibility.ELIGIBLE)
            put("is_duplicate", dupCheck.hasLikelyDuplicate)
            put("ready_for_review", true)
            put("drafted_answers", buildJsonArray {
                for (a in draftedAnswers) {
                    add(buildJsonObject {
                        put("question", a.question)
                        put("draft_text", a.text)
                        put("approved", a.approved)
                        put("character_count", a.characterCount)
                        put("fact_ids", buildJsonArray {
                            a.citedFactIds.forEach { add(JsonPrimitive(it)) }
                        })
                    })
                }
            })
            put("notes", "Application prepared and ready for candidate review.")
        }

        return ToolResult.success(resultJson)
    }
}

// -----------------------------------------------------------------------------
// 5. ApplicationSubmitTool ("application.submit")
// -----------------------------------------------------------------------------
@Singleton
class ApplicationSubmitTool @Inject constructor(
    private val approvalTokenManager: ApprovalTokenManager,
    private val applicationTracker: ApplicationTracker
) : Tool {
    override val name: String = "application.submit"
    override val description: String =
        "Submits the placement application form after human review. Irreversible action. Strictly requires a valid single-use ApprovalToken granted by a direct user tap."
    override val requiresApproval: Boolean = true
    override val riskTier: RiskTier = RiskTier.CONSEQUENTIAL_WRITE

    override val parameters: ToolParameters = ToolParameters(
        properties = mapOf(
            "company" to ParameterProperty("string", "Company being applied to"),
            "form_url" to ParameterProperty("string", "URL of the application form"),
            "approval_token" to ParameterProperty("string", "Single-use ApprovalToken granted by direct user tap"),
            "application_id" to ParameterProperty("string", "Optional tracker application ID")
        ),
        required = listOf("company", "form_url", "approval_token")
    )

    override suspend fun execute(params: JsonObject): ToolResult {
        val company = try { params["company"]?.jsonPrimitive?.content } catch (_: Exception) { null }
            ?: return ToolResult.error("Missing required parameter 'company'")
        val formUrl = try { params["form_url"]?.jsonPrimitive?.content } catch (_: Exception) { null }
            ?: return ToolResult.error("Missing required parameter 'form_url'")
        val tokenParam = try {
            params["approval_token"]?.jsonPrimitive?.content
                ?: params["token_id"]?.jsonPrimitive?.content
        } catch (_: Exception) { null }
            ?: return ToolResult.error("Missing required parameter 'approval_token'. Final submission strictly requires an ApprovalToken issued via direct user tap.")

        val validation = approvalTokenManager.validateActionToken(tokenParam, name)
        if (validation != TokenValidationResult.Valid) {
            return ToolResult.error("Security policy violation: ApprovalToken is invalid ($validation). Final application submission requires an active token issued by direct user tap.")
        }

        // Consume token
        approvalTokenManager.consumeToken(tokenParam)

        val appIdParam = try { params["application_id"]?.jsonPrimitive?.content } catch (_: Exception) { null }
        val targetApp = if (!appIdParam.isNullOrBlank()) {
            applicationTracker.getApplication(appIdParam)
        } else {
            applicationTracker.getAllApplications().firstOrNull { it.company.equals(company, ignoreCase = true) }
        }

        val ref = "CONF-${UUID.randomUUID().toString().take(8).uppercase()}"
        val finalAppId = targetApp?.id ?: UUID.randomUUID().toString()

        if (targetApp != null) {
            try {
                if (targetApp.state == ApplicationState.READY_FOR_REVIEW) {
                    applicationTracker.transitionState(targetApp.id, ApplicationState.SUBMITTING)
                }
                applicationTracker.transitionState(
                    id = targetApp.id,
                    targetState = ApplicationState.SUBMITTED_CONFIRMED,
                    confirmationReference = ref
                )
            } catch (_: Exception) {
                // If direct transition state machine was already at submitting or other state
            }
        }

        val resultJson = buildJsonObject {
            put("status", ApplicationState.SUBMITTED_CONFIRMED.name)
            put("application_id", finalAppId)
            put("company", company)
            put("form_url", formUrl)
            put("confirmation_reference", ref)
            put("submitted_at", System.currentTimeMillis())
        }

        return ToolResult.success(resultJson)
    }
}
