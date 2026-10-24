package com.cellclaw.resume

import kotlinx.serialization.Serializable

@Serializable
enum class DiffType {
    UNCHANGED,
    ADDED,
    REMOVED,
    REWORDED,
    REORDERED
}

@Serializable
data class DiffEntry(
    val type: DiffType,
    val section: String,
    val oldText: String? = null,
    val newText: String? = null
)

@Serializable
data class ResumeDiffResult(
    val isMaterialChange: Boolean,
    val requiresUserApproval: Boolean,
    val entries: List<DiffEntry>,
    val summary: String
)

class ResumeDiffer {

    /**
     * Diffs a tailored [ResumeContent] against a master [ResumeContent].
     * Marks as material change if any lines were added, removed, reworded, or if items were reordered.
     */
    fun diff(master: ResumeContent, tailored: ResumeContent): ResumeDiffResult {
        val entries = mutableListOf<DiffEntry>()

        val masterSections = master.sections.associateBy { it.title }
        val tailoredSections = tailored.sections.associateBy { it.title }

        val allSectionTitles = (master.sections.map { it.title } + tailored.sections.map { it.title }).distinct()

        for (title in allSectionTitles) {
            val masterSec = masterSections[title]
            val tailoredSec = tailoredSections[title]

            when {
                masterSec == null && tailoredSec != null -> {
                    for (line in tailoredSec.lines) {
                        entries.add(DiffEntry(DiffType.ADDED, title, null, line.text))
                    }
                }
                masterSec != null && tailoredSec == null -> {
                    for (line in masterSec.lines) {
                        entries.add(DiffEntry(DiffType.REMOVED, title, line.text, null))
                    }
                }
                masterSec != null && tailoredSec != null -> {
                    val mLines = masterSec.lines.map { it.text }
                    val tLines = tailoredSec.lines.map { it.text }

                    if (mLines != tLines) {
                        val mSet = mLines.toSet()
                        val tSet = tLines.toSet()

                        // Added lines
                        for (line in tLines) {
                            if (line !in mSet) {
                                entries.add(DiffEntry(DiffType.ADDED, title, null, line))
                            }
                        }

                        // Removed lines
                        for (line in mLines) {
                            if (line !in tSet) {
                                entries.add(DiffEntry(DiffType.REMOVED, title, line, null))
                            }
                        }

                        // Reordered lines (same items, different order)
                        if (mSet == tSet && mLines != tLines) {
                            entries.add(
                                DiffEntry(
                                    DiffType.REORDERED,
                                    title,
                                    mLines.joinToString("; "),
                                    tLines.joinToString("; ")
                                )
                            )
                        }
                    }
                }
            }
        }

        val hasChanges = entries.isNotEmpty()
        val isMaterial = entries.any { it.type != DiffType.UNCHANGED }
        val summary = when {
            !hasChanges -> "Tailored resume matches master resume identically."
            isMaterial -> "Material changes detected: ${entries.size} modifications from master resume require user approval."
            else -> "Minor adjustments detected."
        }

        return ResumeDiffResult(
            isMaterialChange = isMaterial,
            requiresUserApproval = isMaterial,
            entries = entries,
            summary = summary
        )
    }

    /**
     * Tags the tailored resume with [ResumeContent.isMaterialChangeFromMaster] based on diff.
     */
    fun attachDiffStatus(master: ResumeContent, tailored: ResumeContent): ResumeContent {
        val diffResult = diff(master, tailored)
        return tailored.copy(isMaterialChangeFromMaster = diffResult.isMaterialChange)
    }
}
