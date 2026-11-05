package com.cellclaw.form

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An approved answer saved in the candidate's personal library for immediate reuse across applications.
 *
 * Guarantees zero AI calls, zero token consumption, and zero latency for recurring questions.
 */
@Serializable
data class ApprovedAnswer(
    val id: String = UUID.randomUUID().toString(),
    val questionCluster: String,
    val promptQuestion: String,
    val answerText: String,
    val keywords: Set<String> = emptySet(),
    val citedFactIds: List<String> = emptyList(),
    val characterCount: Int = answerText.length,
    val approvedAt: Long = System.currentTimeMillis(),
    val useCount: Int = 0
)

/**
 * Result of querying the approved answers library for a matching reusable answer.
 */
data class ApprovedAnswerMatch(
    val approvedAnswer: ApprovedAnswer,
    val similarityScore: Double,
    val fittedText: String,
    val isWithinLimit: Boolean
)

/**
 * Persistence layer abstraction for the approved answers library.
 */
interface ApprovedAnswersStore {
    suspend fun loadAnswers(): List<ApprovedAnswer>
    suspend fun saveAnswers(answers: List<ApprovedAnswer>)
}

/**
 * In-memory storage implementation for JVM tests and ephemeral usage.
 */
class InMemoryApprovedAnswersStore(
    initialAnswers: List<ApprovedAnswer> = emptyList()
) : ApprovedAnswersStore {
    private val mutex = Mutex()
    private val answers = initialAnswers.toMutableList()

    override suspend fun loadAnswers(): List<ApprovedAnswer> = mutex.withLock {
        answers.toList()
    }

    override suspend fun saveAnswers(answers: List<ApprovedAnswer>) {
        mutex.withLock {
            this.answers.clear()
            this.answers.addAll(answers)
        }
    }
}

/**
 * Keystore-backed encrypted storage for approved answers on Android.
 */
class EncryptedSharedPreferencesApprovedAnswersStore(
    context: Context,
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
) : ApprovedAnswersStore {
    private val appContext = context.applicationContext

    private val preferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFERENCES_NAME,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun loadAnswers(): List<ApprovedAnswer> {
        val jsonStr = preferences.getString(KEY_ANSWERS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<ApprovedAnswer>>(jsonStr)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun saveAnswers(answers: List<ApprovedAnswer>) {
        val jsonStr = json.encodeToString(answers)
        preferences.edit().putString(KEY_ANSWERS, jsonStr).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "elixir_approved_answers_encrypted"
        private const val KEY_ANSWERS = "approved_answers_json"
    }
}

/**
 * Contract for managing and querying approved answers.
 */
interface ApprovedAnswersLibrary {
    suspend fun saveAnswer(answer: ApprovedAnswer): ApprovedAnswer
    suspend fun findBestMatch(question: String, lengthLimit: Int? = null, threshold: Double = 0.35): ApprovedAnswerMatch?
    suspend fun getAllAnswers(): List<ApprovedAnswer>
    suspend fun getAnswersByCluster(cluster: String): List<ApprovedAnswer>
    suspend fun getAnswerById(id: String): ApprovedAnswer?
    suspend fun recordUsage(id: String)
    suspend fun deleteAnswer(id: String): Boolean
    suspend fun clear()

    suspend fun offerToSave(
        draftedAnswer: DraftedAnswer,
        questionCluster: String? = null,
        customKeywords: Set<String> = emptySet()
    ): ApprovedAnswer
}

/**
 * Default implementation of [ApprovedAnswersLibrary] featuring smart keyword clustering,
 * Jaccard similarity scoring with category weighting, clean length-boundary truncation,
 * and usage metrics.
 */
@Singleton
class DefaultApprovedAnswersLibrary @Inject constructor(
    private val store: ApprovedAnswersStore
) : ApprovedAnswersLibrary {

    private val mutex = Mutex()

    override suspend fun saveAnswer(answer: ApprovedAnswer): ApprovedAnswer = mutex.withLock {
        val current = store.loadAnswers().toMutableList()
        val index = current.indexOfFirst { it.id == answer.id }
        val enrichedKeywords = if (answer.keywords.isEmpty()) {
            extractKeywords(answer.promptQuestion)
        } else {
            answer.keywords
        }
        val enrichedCluster = if (answer.questionCluster.isBlank() || answer.questionCluster == "general") {
            inferCluster(answer.promptQuestion)
        } else {
            answer.questionCluster
        }

        val toSave = answer.copy(
            keywords = enrichedKeywords,
            questionCluster = enrichedCluster,
            characterCount = answer.answerText.length
        )

        if (index >= 0) {
            current[index] = toSave
        } else {
            current.add(toSave)
        }
        store.saveAnswers(current)
        toSave
    }

    override suspend fun findBestMatch(
        question: String,
        lengthLimit: Int?,
        threshold: Double
    ): ApprovedAnswerMatch? = mutex.withLock {
        val answers = store.loadAnswers()
        if (answers.isEmpty() || question.isBlank()) return@withLock null

        val queryKeywords = extractKeywords(question)
        val queryCluster = inferCluster(question)

        var bestMatch: ApprovedAnswer? = null
        var bestScore = 0.0

        for (candidate in answers) {
            val score = calculateSimilarity(question, queryKeywords, queryCluster, candidate)
            if (score >= threshold && score > bestScore) {
                bestScore = score
                bestMatch = candidate
            } else if (score >= threshold && score == bestScore && bestMatch != null) {
                // Tie-breaker: prioritize higher useCount, then more recent
                if (candidate.useCount > bestMatch.useCount ||
                    (candidate.useCount == bestMatch.useCount && candidate.approvedAt > bestMatch.approvedAt)) {
                    bestMatch = candidate
                }
            }
        }

        val chosen = bestMatch ?: return@withLock null
        val fittedText = enforceLengthLimit(chosen.answerText, lengthLimit)
        val isWithinLimit = lengthLimit == null || chosen.answerText.length <= lengthLimit

        ApprovedAnswerMatch(
            approvedAnswer = chosen,
            similarityScore = bestScore,
            fittedText = fittedText,
            isWithinLimit = isWithinLimit
        )
    }

    override suspend fun getAllAnswers(): List<ApprovedAnswer> = mutex.withLock {
        store.loadAnswers()
    }

    override suspend fun getAnswersByCluster(cluster: String): List<ApprovedAnswer> = mutex.withLock {
        store.loadAnswers().filter { it.questionCluster.equals(cluster, ignoreCase = true) }
    }

    override suspend fun getAnswerById(id: String): ApprovedAnswer? = mutex.withLock {
        store.loadAnswers().firstOrNull { it.id == id }
    }

    override suspend fun recordUsage(id: String) = mutex.withLock {
        val current = store.loadAnswers().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            val updated = current[index].copy(useCount = current[index].useCount + 1)
            current[index] = updated
            store.saveAnswers(current)
        }
    }

    override suspend fun deleteAnswer(id: String): Boolean = mutex.withLock {
        val current = store.loadAnswers().toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) {
            store.saveAnswers(current)
        }
        removed
    }

    override suspend fun clear() = mutex.withLock {
        store.saveAnswers(emptyList())
    }

    override suspend fun offerToSave(
        draftedAnswer: DraftedAnswer,
        questionCluster: String?,
        customKeywords: Set<String>
    ): ApprovedAnswer {
        val cluster = questionCluster?.takeIf { it.isNotBlank() } ?: inferCluster(draftedAnswer.question)
        val extracted = extractKeywords(draftedAnswer.question)
        val allKeywords = extracted + customKeywords

        val approvedAnswer = ApprovedAnswer(
            id = UUID.randomUUID().toString(),
            questionCluster = cluster,
            promptQuestion = draftedAnswer.question,
            answerText = draftedAnswer.text,
            keywords = allKeywords,
            citedFactIds = draftedAnswer.citedFactIds,
            characterCount = draftedAnswer.text.length,
            approvedAt = System.currentTimeMillis(),
            useCount = 0
        )

        return saveAnswer(approvedAnswer)
    }

    companion object {
        private val STOPWORDS = setOf(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are",
            "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "briefly",
            "but", "by", "can", "could", "did", "do", "does", "doing", "down", "during", "each", "explain",
            "few", "for", "from", "further", "had", "has", "have", "having", "he", "her", "here", "hers",
            "herself", "him", "himself", "his", "how", "i", "if", "in", "into", "is", "it", "its", "itself",
            "me", "more", "most", "my", "myself", "no", "nor", "not", "of", "off", "on", "once", "only",
            "or", "other", "our", "ours", "ourselves", "out", "over", "own", "please", "same", "share",
            "should", "so", "some", "such", "tell", "than", "that", "the", "their", "theirs", "them",
            "themselves", "then", "there", "these", "they", "this", "those", "through", "to", "too", "under",
            "until", "up", "us", "very", "was", "we", "were", "what", "when", "where", "which", "while",
            "who", "whom", "why", "will", "with", "would", "you", "your", "yours", "yourself", "yourselves",
            "give", "describe"
        )

        fun extractKeywords(text: String): Set<String> {
            val cleaned = text.lowercase(Locale.ROOT)
                .replace(Regex("""[^a-z0-9\s]"""), " ")
            return cleaned.split(Regex("""\s+"""))
                .map { it.trim() }
                .filter { it.length >= 3 && it !in STOPWORDS }
                .toSet()
        }

        fun inferCluster(question: String): String {
            val q = question.lowercase(Locale.ROOT)
            return when {
                q.contains("challenge") || q.contains("difficult") || q.contains("obstacle") ||
                        q.contains("bug") || q.contains("problem") || q.contains("overcome") || q.contains("solve") -> "technical_challenge"

                q.contains("project") || q.contains("built") || q.contains("architecture") ||
                        q.contains("developed") || q.contains("designed") || q.contains("accomplish") -> "project_experience"

                q.contains("why") || q.contains("interest") || q.contains("join") ||
                        q.contains("fit") || q.contains("choose") || q.contains("culture") -> "why_company"

                q.contains("strength") || q.contains("weakness") || q.contains("improve") ||
                        q.contains("failure") -> "strengths_weaknesses"

                q.contains("relocate") || q.contains("relocation") || q.contains("location") ||
                        q.contains("travel") || q.contains("remote") || q.contains("shift") -> "relocation"

                q.contains("lead") || q.contains("leadership") || q.contains("team") ||
                        q.contains("conflict") || q.contains("collaborat") -> "leadership"

                q.contains("skill") || q.contains("tech") || q.contains("stack") ||
                        q.contains("tool") || q.contains("proficiency") || q.contains("language") -> "technical_skills"

                else -> "general"
            }
        }

        fun calculateSimilarity(
            rawQuestion: String,
            queryKeywords: Set<String>,
            queryCluster: String,
            candidate: ApprovedAnswer
        ): Double {
            val qClean = rawQuestion.trim().lowercase(Locale.ROOT)
            val promptClean = candidate.promptQuestion.trim().lowercase(Locale.ROOT)

            // Exact match
            if (qClean == promptClean) return 1.0

            val candidateKeywords = if (candidate.keywords.isNotEmpty()) {
                candidate.keywords
            } else {
                extractKeywords(candidate.promptQuestion)
            }

            if (queryKeywords.isEmpty() || candidateKeywords.isEmpty()) {
                return if (queryCluster == candidate.questionCluster && queryCluster != "general") 0.4 else 0.0
            }

            // Jaccard similarity: |intersection| / |union|
            val intersection = queryKeywords.intersect(candidateKeywords)
            val union = queryKeywords.union(candidateKeywords)
            val jaccard = intersection.size.toDouble() / union.size.toDouble()

            // Bonus for cluster match
            val clusterBonus = if (queryCluster == candidate.questionCluster && queryCluster != "general") 0.25 else 0.0

            val totalScore = (jaccard * 0.75) + clusterBonus
            return totalScore.coerceIn(0.0, 1.0)
        }

        fun enforceLengthLimit(text: String, lengthLimit: Int?): String {
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
}

