package com.cellclaw.ui.components

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.ui.theme.elixirColors

/**
 * Boarding-pass style ticket card for ReviewScreen.
 * Features:
 * - Upper flight-ticket header: Company, Role, ApplicationState badge
 * - Perforated dashed divider line with notch cutouts
 * - Metadata grid: Target Domain, Resume File & Version, SHA-256 fingerprint, Verified badge
 */
@Composable
fun BoardingPassCard(
    company: String,
    role: String,
    state: ApplicationState,
    domain: String,
    resumeFileName: String,
    resumeVersion: Int,
    resumeHash: String?,
    modifier: Modifier = Modifier
) {
    val elixirColors = MaterialTheme.elixirColors
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = elixirColors.cardBorder
    val dividerColor = elixirColors.divider

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Upper Section: Flight Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PLACEMENT PASS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = company.ifBlank { "Company Name" },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = role.ifBlank { "Role / Position" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Ticket Status Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (state) {
                            ApplicationState.READY_FOR_REVIEW -> elixirColors.infoContainer
                            ApplicationState.SUBMITTING -> elixirColors.warningContainer
                            ApplicationState.SUBMITTED_CONFIRMED -> elixirColors.successContainer
                            ApplicationState.FAILED -> elixirColors.errorContainer
                            else -> MaterialTheme.colorScheme.surface
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when (state) {
                                ApplicationState.READY_FOR_REVIEW -> elixirColors.info
                                ApplicationState.SUBMITTING -> elixirColors.warning
                                ApplicationState.SUBMITTED_CONFIRMED -> elixirColors.success
                                ApplicationState.FAILED -> elixirColors.error
                                else -> borderColor
                            }
                        )
                    ) {
                        Text(
                            text = state.name.replace("_", " "),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (state) {
                                ApplicationState.READY_FOR_REVIEW -> elixirColors.onInfoContainer
                                ApplicationState.SUBMITTING -> elixirColors.onWarningContainer
                                ApplicationState.SUBMITTED_CONFIRMED -> elixirColors.onSuccessContainer
                                ApplicationState.FAILED -> elixirColors.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }

            // Perforated Divider Line with canvas dashed stroke
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(2.dp)
                ) {
                    drawLine(
                        color = dividerColor,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                    )
                }
            }

            // Lower Section: Metadata Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Grid Cell: Target Domain
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TARGET DOMAIN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Security",
                                modifier = Modifier.size(14.dp),
                                tint = elixirColors.success
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = domain.ifBlank { "google.com" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Right Grid Cell: Attached Resume Version
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "RESUME VERSION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Resume",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${resumeFileName.ifBlank { "Resume" }} (v$resumeVersion)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // SHA-256 Fingerprint Row
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "INTEGRITY HASH (SHA-256)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = resumeHash ?: "Computed on submission authorization",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
