package com.cellclaw.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.ui.viewmodel.ResultUiState
import com.cellclaw.ui.viewmodel.ResultViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    appId: String,
    onBack: () -> Unit = {},
    onReturnToTracker: () -> Unit = {},
    onRetry: (appId: String) -> Unit = {},
    viewModel: ResultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(appId) {
        viewModel.load(appId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Application Result") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Status Banner
                StatusBannerCard(uiState = uiState)

                // 2. Action success or error alerts
                uiState.actionSuccessMessage?.let { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                uiState.errorMessage?.let { err ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = err,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // 3. Confirmation Reference Card (if confirmed)
                if (!uiState.confirmationReference.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Confirmation Reference",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = uiState.confirmationReference.orEmpty(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Keep this reference code for future follow-up with campus placement coordinators.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 4. Manual Confirmation Section (if submission state unknown)
                if (uiState.state == ApplicationState.SUBMISSION_UNKNOWN) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Manual Response Verification",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF57F17)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Browser timed out or confirmation screen lacked an automated receipt. If you verified the submission succeeded in Chrome, enter the confirmation reference or confirm manually below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5D4037)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = uiState.manualReferenceInput,
                                onValueChange = { viewModel.updateManualReferenceInput(it) },
                                label = { Text("Reference ID (optional)") },
                                placeholder = { Text("e.g. CONF-123456") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.confirmSubmissionManually() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Confirm Submission")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.markAsFailed("User marked as failed after manual review") },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Mark Failed")
                                }
                            }
                        }
                    }
                }

                // 5. Application Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Submission Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider()

                        DetailRow(label = "Company", value = uiState.company.ifBlank { "N/A" })
                        DetailRow(label = "Role", value = uiState.role.ifBlank { "N/A" })
                        if (!uiState.sourceUrl.isNullOrBlank()) {
                            DetailRow(label = "Application URL", value = uiState.sourceUrl.orEmpty())
                        }
                        DetailRow(
                            label = "Resume Version",
                            value = "v${uiState.resumeVersion} (${uiState.resumeHash?.take(12) ?: "no hash"})"
                        )
                        if (uiState.updatedAt > 0) {
                            val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                .format(Date(uiState.updatedAt))
                            DetailRow(label = "Last Updated", value = formattedDate)
                        }
                        if (!uiState.notes.isNullOrBlank()) {
                            DetailRow(label = "Notes / Outcome", value = uiState.notes.orEmpty())
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f, fill = false))

                // 6. Action CTAs
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState.state == ApplicationState.FAILED || uiState.state == ApplicationState.SUBMISSION_UNKNOWN) {
                        Button(
                            onClick = {
                                viewModel.retrySubmission { newId ->
                                    onRetry(newId)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry Application Review & Submit")
                        }
                    }

                    FilledTonalButton(
                        onClick = onReturnToTracker,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Return to Application Tracker")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBannerCard(uiState: ResultUiState) {
    val (bgColor, iconColor, title, desc, icon) = when (uiState.state) {
        ApplicationState.SUBMITTED_CONFIRMED -> StatusBannerConfig(
            bgColor = Color(0xFFE8F5E9),
            iconColor = Color(0xFF2E7D32),
            title = "Application Submitted & Confirmed",
            desc = "Your application was submitted with user approval and confirmed by response receipt.",
            icon = Icons.Default.CheckCircle
        )
        ApplicationState.SUBMISSION_UNKNOWN -> StatusBannerConfig(
            bgColor = Color(0xFFFFF3E0),
            iconColor = Color(0xFFE65100),
            title = "Submission Status Unknown",
            desc = "Automated receipt was not detected. Please verify your browser and confirm manually.",
            icon = Icons.Default.Warning
        )
        ApplicationState.FAILED -> StatusBannerConfig(
            bgColor = Color(0xFFFFEBEE),
            iconColor = Color(0xFFC62828),
            title = "Submission Failed",
            desc = uiState.notes ?: "Automation encountered an error before submission completion.",
            icon = Icons.Default.Warning
        )
        ApplicationState.CANCELLED -> StatusBannerConfig(
            bgColor = Color(0xFFECEFF1),
            iconColor = Color(0xFF455A64),
            title = "Application Cancelled",
            desc = "This application run was cancelled by user.",
            icon = Icons.Default.Info
        )
        else -> StatusBannerConfig(
            bgColor = Color(0xFFE3F2FD),
            iconColor = Color(0xFF1565C0),
            title = "Status: ${uiState.state}",
            desc = "Application is currently in ${uiState.state} state.",
            icon = Icons.Default.Info
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = iconColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black.copy(alpha = 0.75f)
                )
            }
        }
    }
}

private data class StatusBannerConfig(
    val bgColor: Color,
    val iconColor: Color,
    val title: String,
    val desc: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}
