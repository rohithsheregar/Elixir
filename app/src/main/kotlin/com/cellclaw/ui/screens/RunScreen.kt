package com.cellclaw.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellclaw.automation.AutomationStep
import com.cellclaw.ui.viewmodel.RunUiState
import com.cellclaw.ui.viewmodel.RunViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunScreen(
    appId: String,
    onBack: () -> Unit = {},
    onComplete: (appId: String) -> Unit = {},
    viewModel: RunViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(appId) {
        viewModel.load(appId)
    }

    LaunchedEffect(uiState.confirmationReference) {
        if (!uiState.confirmationReference.isNullOrBlank()) {
            // Can auto-navigate or user can click
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Supervised Application Run") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header Card: Target Application Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = uiState.company.ifBlank { "Application Automation" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.role.ifBlank { "Supervised Form Submission" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.sourceUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Target: ${uiState.sourceUrl}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 2. CAPTCHA / OTP / Login Manual Intervention Handoff Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Handoff",
                            tint = Color(0xFFF57F17),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Manual Intervention / CAPTCHA Handoff",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF57F17)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "If Chrome prompts for CAPTCHA, two-factor authentication (OTP), or account login, complete it manually in Chrome. Elixir will pause while you intervene.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5D4037)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!uiState.isPaused) {
                            OutlinedButton(
                                onClick = { viewModel.triggerManualHandoff("User requested pause for CAPTCHA/OTP") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Pause for Login/OTP")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.completeManualHandoff() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57F17)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("I've Completed It — Resume")
                            }
                        }
                    }
                }
            }

            // 3. Live Automation Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (val step = uiState.step) {
                        is AutomationStep.Idle -> {
                            Text("Ready to start supervised application.")
                        }
                        is AutomationStep.OpeningBrowser -> {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Opening target form in browser...", fontWeight = FontWeight.SemiBold)
                            Text(step.url, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        is AutomationStep.WaitingForBrowser -> {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Connecting to browser session...", fontWeight = FontWeight.SemiBold)
                        }
                        is AutomationStep.SessionStarted -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Started", tint = Color(0xFF2E7D32), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Accessibility session active. Analyzing form...", fontWeight = FontWeight.SemiBold)
                        }
                        is AutomationStep.ClassifyingAndFilling -> {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Filling field ${step.index} of ${step.total}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                step.fieldLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is AutomationStep.UploadingResume -> {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Uploading Tailored Resume...", fontWeight = FontWeight.SemiBold)
                            Text(step.fileName, style = MaterialTheme.typography.bodySmall)
                        }
                        is AutomationStep.VerifyingUpload -> {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Verifying visible file upload in browser...", fontWeight = FontWeight.SemiBold)
                        }
                        is AutomationStep.NeedsUserInput -> {
                            Icon(Icons.Default.Warning, contentDescription = "Input Needed", tint = Color(0xFFE65100), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Input Needed for ${step.pendingFields.size} field(s)", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Please enter details directly in Chrome or review unanswered questions.", style = MaterialTheme.typography.bodySmall)
                        }
                        is AutomationStep.AwaitingUserFinalSubmitApproval -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Ready", tint = Color(0xFF2E7D32), modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("All Verified Fields Filled!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "PlacementOS Supervised Gate: Please switch to Chrome and review the complete form. When satisfied, tap Submit below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF2E7D32)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { viewModel.confirmFinalSubmit() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Approve & Submit Application")
                            }
                        }
                        is AutomationStep.Submitting -> {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Tapping Submit and awaiting confirmation receipt...", fontWeight = FontWeight.SemiBold)
                        }
                        is AutomationStep.Completed -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = Color(0xFF2E7D32), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Application Submitted Successfully!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Reference: ${step.confirmationReference}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { onComplete(appId) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("View Submission Receipt")
                            }
                        }
                        is AutomationStep.Paused -> {
                            Icon(Icons.Default.Info, contentDescription = "Paused", tint = Color(0xFF1565C0), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Automation Paused", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(step.reason, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        is AutomationStep.Failed -> {
                            Icon(Icons.Default.Warning, contentDescription = "Failed", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Submission Failed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text(step.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // 4. Action Controls Row: Pause / Resume / Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.isPaused) {
                    Button(
                        onClick = { viewModel.resume() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Resume")
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.pause("User paused run") },
                        modifier = Modifier.weight(1f),
                        enabled = uiState.step !is AutomationStep.Completed && uiState.step !is AutomationStep.Failed
                    ) {
                        Text("Pause")
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.cancelAutomation("Cancelled by user") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                    enabled = uiState.step !is AutomationStep.Completed
                ) {
                    Text("Cancel Run")
                }
            }
        }
    }
}
