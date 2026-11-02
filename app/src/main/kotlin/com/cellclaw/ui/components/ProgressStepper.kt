package com.cellclaw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cellclaw.ui.theme.elixirColors

enum class PlacementStep(
    val stepIndex: Int,
    val title: String,
    val shortTitle: String
) {
    JD_INGESTION(0, "JD Ingestion", "JD"),
    ELIGIBILITY(1, "Eligibility", "Eligibility"),
    TAILORED_RESUME(2, "Tailored Resume", "Resume"),
    SUPERVISED_REVIEW(3, "Supervised Review", "Review");

    companion object {
        val ALL = entries.toList()
    }
}

enum class StepState {
    COMPLETED,
    CURRENT,
    UPCOMING
}

fun getStepState(step: PlacementStep, currentStep: PlacementStep): StepState {
    return when {
        step.stepIndex < currentStep.stepIndex -> StepState.COMPLETED
        step.stepIndex == currentStep.stepIndex -> StepState.CURRENT
        else -> StepState.UPCOMING
    }
}

@Composable
fun ProgressStepper(
    currentStep: PlacementStep,
    modifier: Modifier = Modifier,
    onStepClick: ((PlacementStep) -> Unit)? = null
) {
    val elixirColors = MaterialTheme.elixirColors
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PlacementStep.ALL.forEachIndexed { index, step ->
                val state = getStepState(step, currentStep)
                val isClickable = onStepClick != null && state == StepState.COMPLETED

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .widthIn(min = 44.dp, max = 64.dp)
                        .then(
                            if (isClickable) Modifier.clickable { onStepClick!!.invoke(step) }
                            else Modifier
                        )
                        .semantics {
                            contentDescription =
                                "Step ${step.stepIndex + 1}: ${step.title}, ${state.name}"
                        }
                ) {
                    // Badge circle
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .then(
                                when (state) {
                                    StepState.COMPLETED -> Modifier.background(primary)
                                    StepState.CURRENT -> Modifier
                                        .background(primaryContainer)
                                        .border(2.dp, primary, CircleShape)
                                    StepState.UPCOMING -> Modifier
                                        .background(surfaceVariant)
                                        .border(1.dp, elixirColors.cardBorder, CircleShape)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (state) {
                            StepState.COMPLETED -> Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            StepState.CURRENT -> Text(
                                text = "${step.stepIndex + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = onPrimaryContainer
                            )
                            StepState.UPCOMING -> Text(
                                text = "${step.stepIndex + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Normal,
                                color = onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = step.shortTitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (state == StepState.CURRENT) FontWeight.Bold else FontWeight.Medium,
                        color = when (state) {
                            StepState.CURRENT -> MaterialTheme.colorScheme.onSurface
                            StepState.COMPLETED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            StepState.UPCOMING -> onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Connector between steps
                if (index < PlacementStep.ALL.size - 1) {
                    val passed = step.stepIndex < currentStep.stepIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 13.dp, start = 2.dp, end = 2.dp)
                            .height(2.dp)
                            .background(if (passed) primary else elixirColors.cardBorder)
                    )
                }
            }
        }
    }
}
