package com.cellclaw.jd

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic parser for job descriptions (JDs) using regex and heuristics.
 * Treats JD text strictly as untrusted data, never as instructions.
 */
@Singleton
class JdParser @Inject constructor() {

    fun parse(rawText: String?): JdRequirement {
        if (rawText.isNullOrBlank()) {
            return JdRequirement()
        }

        val text = rawText.trim()

        val company = extractCompany(text)
        val role = extractRole(text)
        val jobType = extractJobType(text)
        val location = extractLocation(text)
        val (requiredSkills, preferredSkills) = extractSkills(text)
        val degree = extractDegree(text)
        val branch = extractBranch(text)
        val passingYear = extractPassingYear(text)
        val (minCgpa, cgpaScale) = extractCgpa(text)
        val backlogRule = extractBacklogRule(text)
        val deadline = extractDeadline(text)
        val questions = extractQuestions(text)

        return JdRequirement(
            company = company,
            role = role,
            jobType = jobType,
            location = location,
            requiredSkills = requiredSkills,
            preferredSkills = preferredSkills,
            degree = degree,
            branch = branch,
            passingYear = passingYear,
            minCgpa = minCgpa,
            cgpaScale = cgpaScale,
            backlogRule = backlogRule,
            deadline = deadline,
            questions = questions
        )
    }

    private fun extractCompany(text: String): JdField<String> {
        val patterns = listOf(
            Pattern.compile("(?i)(?:company(?:\\s+name)?|organization|employer)\\s*[:\\-–]\\s*([^\\n\\r,]+)"),
            Pattern.compile("(?i)(?:about|hiring\\s+for|welcome\\s+to)\\s+([A-Z][A-Za-z0-9&., ]{2,30})"),
            Pattern.compile("(?i)^([A-Z][A-Za-z0-9&. ]{2,30})\\s+(?:is\\s+hiring|is\\s+looking\\s+for|campus\\s+recruitment)")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val value = matcher.group(1)?.trim()?.trimEnd('.', ',')
                if (!value.isNullOrBlank() && value.length < 50) {
                    val snippet = matcher.group(0)?.trim() ?: value
                    return JdField(value = value, sourceSnippet = snippet, confidence = 0.9f)
                }
            }
        }
        return JdField()
    }

    private fun extractRole(text: String): JdField<String> {
        val patterns = listOf(
            Pattern.compile("(?i)(?:role|position|job\\s+title|designation|profile)\\s*[:\\-–]\\s*([^\\n\\r,]+)"),
            Pattern.compile("(?i)(?:hiring\\s+for\\s+(?:the\\s+role\\s+of\\s+)?|looking\\s+for\\s+(?:an?\\s+)?)([A-Z][A-Za-z0-9/ ]{2,40}\\b(?:Engineer|Developer|Analyst|Intern|Consultant|Associate|Scientist|Architect|Specialist))")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val value = matcher.group(1)?.trim()?.trimEnd('.', ',')
                if (!value.isNullOrBlank() && value.length < 60) {
                    val snippet = matcher.group(0)?.trim() ?: value
                    return JdField(value = value, sourceSnippet = snippet, confidence = 0.9f)
                }
            }
        }
        return JdField()
    }

    private fun extractJobType(text: String): JdField<JdJobType> {
        val explicitPattern = Pattern.compile("(?i)(?:job\\s+type|employment\\s+type)\\s*[:\\-–]\\s*([^\\n\\r]+)")
        val explicitMatcher = explicitPattern.matcher(text)
        if (explicitMatcher.find()) {
            val valueStr = explicitMatcher.group(1)?.lowercase() ?: ""
            val snippet = explicitMatcher.group(0)?.trim() ?: ""
            val type = when {
                "intern" in valueStr -> JdJobType.INTERNSHIP
                "full" in valueStr || "fte" in valueStr -> JdJobType.FULL_TIME
                "apprentice" in valueStr -> JdJobType.APPRENTICESHIP
                "contract" in valueStr -> JdJobType.CONTRACT
                else -> JdJobType.UNKNOWN
            }
            if (type != JdJobType.UNKNOWN) {
                return JdField(value = type, sourceSnippet = snippet, confidence = 0.95f)
            }
        }

        // Heuristic detection in text
        val lower = text.lowercase()
        return when {
            "internship" in lower || "intern" in lower -> {
                val line = text.lines().firstOrNull { it.contains("intern", ignoreCase = true) }?.trim() ?: "internship"
                JdField(value = JdJobType.INTERNSHIP, sourceSnippet = line, confidence = 0.85f)
            }
            "full time" in lower || "full-time" in lower || "fte" in lower -> {
                val line = text.lines().firstOrNull {
                    it.contains("full-time", ignoreCase = true) || it.contains("full time", ignoreCase = true)
                }?.trim() ?: "full-time"
                JdField(value = JdJobType.FULL_TIME, sourceSnippet = line, confidence = 0.85f)
            }
            "apprenticeship" in lower || "apprentice" in lower -> {
                val line = text.lines().firstOrNull { it.contains("apprentice", ignoreCase = true) }?.trim() ?: "apprenticeship"
                JdField(value = JdJobType.APPRENTICESHIP, sourceSnippet = line, confidence = 0.85f)
            }
            "contract" in lower -> {
                val line = text.lines().firstOrNull { it.contains("contract", ignoreCase = true) }?.trim() ?: "contract"
                JdField(value = JdJobType.CONTRACT, sourceSnippet = line, confidence = 0.8f)
            }
            else -> JdField()
        }
    }

    private fun extractLocation(text: String): JdField<String> {
        val pattern = Pattern.compile("(?i)(?:location|work\\s+location|job\\s+location|base\\s+location)\\s*[:\\-–]\\s*([^\\n\\r]+)")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            val value = matcher.group(1)?.trim()?.trimEnd('.', ',') ?: ""
            if (value.isNotBlank() && value.length < 100) {
                val snippet = matcher.group(0)?.trim() ?: value
                return JdField(value = value, sourceSnippet = snippet, confidence = 0.9f)
            }
        }
        return JdField()
    }

    private fun extractSkills(text: String): Pair<JdField<List<String>>, JdField<List<String>>> {
        var reqField: JdField<List<String>> = JdField()
        var prefField: JdField<List<String>> = JdField()

        val reqHeaderPattern = Pattern.compile("(?i)(?:required\\s+skills|must\\s+have|requirements|mandatory\\s+skills|skills\\s+required)\\s*[:\\-–]?")
        val prefHeaderPattern = Pattern.compile("(?i)(?:preferred\\s+skills|good\\s+to\\s+have|nice\\s+to\\s+have|desired\\s+skills)\\s*[:\\-–]?")

        val reqMatcher = reqHeaderPattern.matcher(text)
        if (reqMatcher.find()) {
            val startIndex = reqMatcher.end()
            val remaining = text.substring(startIndex)
            val extracted = extractSkillList(remaining)
            if (extracted.first.isNotEmpty()) {
                val snippet = text.substring(reqMatcher.start(), minOf(text.length, startIndex + extracted.second)).trim()
                reqField = JdField(value = extracted.first, sourceSnippet = snippet, confidence = 0.9f)
            }
        }

        val prefMatcher = prefHeaderPattern.matcher(text)
        if (prefMatcher.find()) {
            val startIndex = prefMatcher.end()
            val remaining = text.substring(startIndex)
            val extracted = extractSkillList(remaining)
            if (extracted.first.isNotEmpty()) {
                val snippet = text.substring(prefMatcher.start(), minOf(text.length, startIndex + extracted.second)).trim()
                prefField = JdField(value = extracted.first, sourceSnippet = snippet, confidence = 0.9f)
            }
        }

        // Fallback for general "Skills:" section if required skills not found
        if (reqField.value == null) {
            val generalSkillsPattern = Pattern.compile("(?i)(?:skills|technologies|tech\\s+stack)\\s*[:\\-–]\\s*([^\\n\\r]+)")
            val genMatcher = generalSkillsPattern.matcher(text)
            if (genMatcher.find()) {
                val skillsRaw = genMatcher.group(1)?.trim() ?: ""
                val skillsList = parseCommaOrDelimitedList(skillsRaw)
                if (skillsList.isNotEmpty()) {
                    val snippet = genMatcher.group(0)?.trim() ?: skillsRaw
                    reqField = JdField(value = skillsList, sourceSnippet = snippet, confidence = 0.8f)
                }
            }
        }

        return Pair(reqField, prefField)
    }

    private fun extractSkillList(sectionText: String): Pair<List<String>, Int> {
        val lines = sectionText.lines()
        val skills = mutableListOf<String>()
        var charsConsumed = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) {
                charsConsumed += line.length + 1
                continue
            }
            // Stop if another major section header is encountered
            if (trimmed.endsWith(":") || trimmed.matches(Regex("(?i)^[A-Z][A-Za-z ]{2,30}:.*"))) {
                if (skills.isNotEmpty()) break
            }

            val cleaned = trimmed.replace(Regex("^[•\\-*\\d+.)\\s]+"), "").trim()
            if (cleaned.isNotBlank()) {
                val parts = parseCommaOrDelimitedList(cleaned)
                skills.addAll(parts)
            }
            charsConsumed += line.length + 1
            if (skills.size >= 15) break
        }

        return Pair(skills.distinct(), charsConsumed)
    }

    private fun parseCommaOrDelimitedList(raw: String): List<String> {
        return raw.split(Regex("[,/|;•]"))
            .map { it.trim().trimEnd('.', ',') }
            .filter { it.isNotBlank() && it.length in 2..40 }
    }

    private fun extractDegree(text: String): JdField<String> {
        val explicitPattern = Pattern.compile("(?i)(?:degree|qualification|eligibility(?:\\s+criteria)?|education)\\s*[:\\-–]\\s*([^\\n\\r]+)")
        val matcher = explicitPattern.matcher(text)
        if (matcher.find()) {
            val value = matcher.group(1)?.trim()?.trimEnd('.', ',') ?: ""
            if (value.isNotBlank() && value.length < 100) {
                val snippet = matcher.group(0)?.trim() ?: value
                return JdField(value = value, sourceSnippet = snippet, confidence = 0.9f)
            }
        }

        // Keyword search
        val degreePattern = Pattern.compile("(?i)\\b(B\\.?Tech|B\\.?E|M\\.?Tech|BCA|MCA|B\\.?Sc|M\\.?Sc|Bachelor'?s|Master'?s)\\b")
        val degMatcher = degreePattern.matcher(text)
        if (degMatcher.find()) {
            val matchedDegree = degMatcher.group(0) ?: ""
            val line = text.lines().firstOrNull { it.contains(matchedDegree, ignoreCase = true) }?.trim()
                ?: matchedDegree
            return JdField(value = matchedDegree, sourceSnippet = line, confidence = 0.8f)
        }
        return JdField()
    }

    private fun extractBranch(text: String): JdField<String> {
        val pattern = Pattern.compile("(?i)(?:branch(?:es)?|stream|specialization|disciplines?)\\s*[:\\-–]\\s*([^\\n\\r]+)")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            val value = matcher.group(1)?.trim()?.trimEnd('.', ',') ?: ""
            if (value.isNotBlank() && value.length < 100) {
                val snippet = matcher.group(0)?.trim() ?: value
                return JdField(value = value, sourceSnippet = snippet, confidence = 0.9f)
            }
        }
        return JdField()
    }

    private fun extractPassingYear(text: String): JdField<Int> {
        val pattern = Pattern.compile("(?i)(?:batch|passing\\s+year|graduation\\s+year|year\\s+of\\s+passing|batch\\s+of)\\s*[:\\-–]?\\s*(\\b20\\d{2}\\b)")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            val year = matcher.group(1)?.toIntOrNull()
            if (year != null) {
                val snippet = matcher.group(0)?.trim() ?: year.toString()
                return JdField(value = year, sourceSnippet = snippet, confidence = 0.9f)
            }
        }

        val trailingPattern = Pattern.compile("(\\b20\\d{2}\\b)\\s*(?:passouts?|batch|graduates?)", Pattern.CASE_INSENSITIVE)
        val trailMatcher = trailingPattern.matcher(text)
        if (trailMatcher.find()) {
            val year = trailMatcher.group(1)?.toIntOrNull()
            if (year != null) {
                val snippet = trailMatcher.group(0)?.trim() ?: year.toString()
                return JdField(value = year, sourceSnippet = snippet, confidence = 0.85f)
            }
        }
        return JdField()
    }

    private fun extractCgpa(text: String): Pair<JdField<Double>, JdField<Double>> {
        val cgpaPattern = Pattern.compile("(?i)(?:min(?:imum)?\\s*)?(?:cgpa|gpa)\\s*[:\\-–]?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(?:/\\s*([0-9]+(?:\\.[0-9]+)?))?")
        val matcher = cgpaPattern.matcher(text)
        if (matcher.find()) {
            val cgpaVal = matcher.group(1)?.toDoubleOrNull()
            if (cgpaVal != null) {
                val scaleVal = matcher.group(2)?.toDoubleOrNull() ?: 10.0
                val snippet = matcher.group(0)?.trim() ?: cgpaVal.toString()
                return Pair(
                    JdField(value = cgpaVal, sourceSnippet = snippet, confidence = 0.95f),
                    JdField(value = scaleVal, sourceSnippet = snippet, confidence = 0.95f)
                )
            }
        }

        // Percentage check
        val percentPattern = Pattern.compile("(?i)(?:min(?:imum)?\\s*)?(?:percentage|marks)\\s*[:\\-–]?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%")
        val pMatcher = percentPattern.matcher(text)
        if (pMatcher.find()) {
            val pVal = pMatcher.group(1)?.toDoubleOrNull()
            if (pVal != null) {
                val snippet = pMatcher.group(0)?.trim() ?: "$pVal%"
                return Pair(
                    JdField(value = pVal, sourceSnippet = snippet, confidence = 0.9f),
                    JdField(value = 100.0, sourceSnippet = snippet, confidence = 0.9f)
                )
            }
        }

        return Pair(JdField(), JdField())
    }

    private fun extractBacklogRule(text: String): JdField<String> {
        val pattern = Pattern.compile("(?i)(?:backlogs?|arrears?|standing\\s+arrears?)\\s*[:\\-–]\\s*([^\\n\\r]+)")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            val value = matcher.group(1)?.trim()?.trimEnd('.', ',') ?: ""
            if (value.isNotBlank() && value.length < 80) {
                val snippet = matcher.group(0)?.trim() ?: value
                return JdField(value = value, sourceSnippet = snippet, confidence = 0.9f)
            }
        }

        val noBacklogPattern = Pattern.compile("(?i)\\b(no\\s+(?:active\\s+)?backlogs?|0\\s+backlogs?|zero\\s+backlogs?)\\b")
        val nbMatcher = noBacklogPattern.matcher(text)
        if (nbMatcher.find()) {
            val snippet = nbMatcher.group(0)?.trim() ?: ""
            if (snippet.isNotBlank()) {
                return JdField(value = snippet, sourceSnippet = snippet, confidence = 0.85f)
            }
        }

        return JdField()
    }

    private fun extractDeadline(text: String): JdField<LocalDate> {
        val pattern = Pattern.compile("(?i)(?:deadline|apply\\s+by|last\\s+date(?:\\s+to\\s+apply)?)\\s*[:\\-–]\\s*([^\\n\\r]+)")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            val dateStr = matcher.group(1)?.trim()?.trimEnd('.', ',') ?: ""
            val parsedDate = parseDate(dateStr)
            if (parsedDate != null) {
                val snippet = matcher.group(0)?.trim() ?: dateStr
                return JdField(value = parsedDate, sourceSnippet = snippet, confidence = 0.9f)
            }
        }
        return JdField()
    }

    private fun parseDate(str: String): LocalDate? {
        val formatters = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
        )

        for (formatter in formatters) {
            try {
                val matcher = Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b|\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}\\b|\\b\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4}\\b|\\b[A-Za-z]+\\s+\\d{1,2},\\s*\\d{4}\\b").find(str)
                val target = matcher?.value ?: str
                return LocalDate.parse(target.trim(), formatter)
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun extractQuestions(text: String): JdField<List<String>> {
        val pattern = Pattern.compile("(?i)(?:application\\s+questions?|questions?\\s+to\\s+answer)\\s*[:\\-–]?\\s*\\n([\\s\\S]+?)(?=\\n\\n|\\Z)")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            val content = matcher.group(1)?.trim() ?: ""
            val questions = content.lines()
                .map { it.replace(Regex("^[•\\-*\\d+.)\\s]+"), "").trim() }
                .filter { it.isNotBlank() && it.length > 5 }

            if (questions.isNotEmpty()) {
                val snippet = matcher.group(0)?.trim() ?: content
                return JdField(value = questions, sourceSnippet = snippet, confidence = 0.85f)
            }
        }
        return JdField()
    }
}
