package com.cellclaw.resume

import kotlinx.serialization.Serializable

@Serializable
data class ResumeLine(
    val id: String,
    val text: String,
    val profileFactId: String
)

@Serializable
data class ResumeSection(
    val title: String,
    val lines: List<ResumeLine>
)

@Serializable
data class EvidenceMap(
    val lineToFactId: Map<String, String> = emptyMap()
) {
    fun factIdForLine(lineId: String): String? = lineToFactId[lineId]
}

@Serializable
data class ResumeContent(
    val name: String,
    val email: String,
    val phone: String? = null,
    val links: List<String> = emptyList(),
    val education: List<String> = emptyList(),
    val sections: List<ResumeSection> = emptyList(),
    val evidenceMap: EvidenceMap = EvidenceMap(),
    val isMaterialChangeFromMaster: Boolean = false
) {
    val totalLines: Int get() = sections.sumOf { it.lines.size }

    /**
     * Verifies that every single line in the resume maps to an authentic confirmed profile fact ID.
     */
    fun verifyEvidenceIntegrity(): Boolean {
        if (sections.isEmpty() && totalLines == 0) return true
        return sections.all { section ->
            section.lines.all { line ->
                evidenceMap.factIdForLine(line.id) == line.profileFactId && line.profileFactId.isNotBlank()
            }
        }
    }
}
