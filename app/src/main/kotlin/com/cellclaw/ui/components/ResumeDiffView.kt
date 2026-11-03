package com.cellclaw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cellclaw.resume.DiffEntry
import com.cellclaw.resume.DiffType
import com.cellclaw.resume.ResumeContent
import com.cellclaw.resume.ResumeDiffResult
import com.cellclaw.ui.theme.elixirColors

/**
 * Visual Resume Diff viewer that compares a tailored resume against the candidate's master resume.
 * Displays highlighted additions (+), omissions (-), reorderings (~), and provenance fact tags
 * (#profile, #jd-tailored, #omitted) to give the user complete verification before approving.
 */
@Composable
fun ResumeDiffView(
    diffResult: ResumeDiffResult,
    modifier: Modifier = Modifier,
    tailored: ResumeContent? = null,
    master: ResumeContent? = null
) {
    val elixirColors = MaterialTheme.elixirColors
    val addedCount = diffResult.entries.count { it.type == DiffType.ADDED }
    val removedCount = diffResult.entries.count { it.type == DiffType.REMOVED }
    val reorderedCount = diffResult.entries.count { it.type == DiffType.REORDERED }
    val totalChanges = diffResult.entries.size

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Visual Resume Diff",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = when {
                                diffResult.isMaterialChange -> elixirColors.warningContainer
                                totalChanges > 0 -> elixirColors.infoContainer
                                else -> elixirColors.successContainer
                            }
                        ) {
                            Text(
                                text = when {
                                    diffResult.isMaterialChange -> "Material Changes"
                                    totalChanges > 0 -> "Minor Changes"
                                    else -> "Identical to Master"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    diffResult.isMaterialChange -> elixirColors.onWarningContainer
                                    totalChanges > 0 -> elixirColors.onInfoContainer
                                    else -> elixirColors.onSuccessContainer
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = diffResult.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Change count indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DiffStatBadge(
                            label = "+$addedCount Added",
                            containerColor = elixirColors.successContainer,
                            contentColor = elixirColors.onSuccessContainer
                        )
                        DiffStatBadge(
                            label = "-$removedCount Omitted",
                            containerColor = elixirColors.errorContainer,
                            contentColor = elixirColors.onErrorContainer
                        )
                        DiffStatBadge(
                            label = "~$reorderedCount Reordered",
                            containerColor = elixirColors.warningContainer,
                            contentColor = elixirColors.onWarningContainer
                        )
                    }
                }
            }
        }

        // Zero differences state
        if (totalChanges == 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = elixirColors.successContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = elixirColors.onSuccessContainer.copy(alpha = 0.15f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = elixirColors.onSuccessContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "No Tailoring Modifications",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = elixirColors.onSuccessContainer
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "This tailored resume matches your master profile identically. Every line directly reflects your confirmed baseline facts without modification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = elixirColors.onSuccessContainer
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SourceTagBadge(tag = "#profile", color = elixirColors.onSuccessContainer)
                            SourceTagBadge(tag = "#zero-fabrication", color = elixirColors.onSuccessContainer)
                        }
                    }
                }
            }
        } else {
            // Group entries by section
            val groupedBySection = diffResult.entries.groupBy { it.section }
            for ((section, entries) in groupedBySection) {
                item {
                    Text(
                        text = section.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                items(entries) { entry ->
                    DiffEntryCard(entry = entry)
                }
            }
        }

        // Provenance & Transparency Footer Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Provenance & Verification Tags",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "#profile: Sourced directly from your verified profile facts\n" +
                                "#jd-tailored: Re-ranked or emphasized to match the target job description\n" +
                                "#omitted: Deprioritized from this application to fit length constraints\n" +
                                "Zero Fabrication: No statements, credentials, or metrics are ever invented.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DiffStatBadge(
    label: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DiffEntryCard(entry: DiffEntry) {
    val elixirColors = MaterialTheme.elixirColors

    val (badgeText, containerColor, contentColor, tagList) = when (entry.type) {
        DiffType.ADDED -> Quadruple(
            "+ ADDED",
            elixirColors.successContainer,
            elixirColors.onSuccessContainer,
            listOf("#jd-tailored", "#profile")
        )
        DiffType.REMOVED -> Quadruple(
            "- OMITTED",
            elixirColors.errorContainer,
            elixirColors.onErrorContainer,
            listOf("#omitted")
        )
        DiffType.REORDERED -> Quadruple(
            "~ REORDERED",
            elixirColors.warningContainer,
            elixirColors.onWarningContainer,
            listOf("#jd-tailored")
        )
        DiffType.REWORDED -> Quadruple(
            "✎ REWORDED",
            elixirColors.infoContainer,
            elixirColors.onInfoContainer,
            listOf("#profile")
        )
        DiffType.UNCHANGED -> Quadruple(
            "= UNCHANGED",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            listOf("#profile")
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = contentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tagList.forEach { tag ->
                        SourceTagBadge(tag = tag, color = contentColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (entry.type) {
                DiffType.ADDED -> {
                    Text(
                        text = entry.newText ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = contentColor
                    )
                }
                DiffType.REMOVED -> {
                    Text(
                        text = entry.oldText ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        textDecoration = TextDecoration.LineThrough
                    )
                }
                DiffType.REORDERED -> {
                    Text(
                        text = "Tailored Order: ${entry.newText ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Base Order: ${entry.oldText ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
                DiffType.REWORDED -> {
                    Text(
                        text = "Tailored: ${entry.newText ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Master: ${entry.oldText ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f),
                        textDecoration = TextDecoration.LineThrough
                    )
                }
                DiffType.UNCHANGED -> {
                    Text(
                        text = entry.newText ?: entry.oldText ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceTagBadge(tag: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

