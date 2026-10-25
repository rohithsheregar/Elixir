package com.cellclaw.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
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
import com.cellclaw.eligibility.EligibilityReport
import com.cellclaw.eligibility.EligibilityResult
import com.cellclaw.eligibility.EligibilityStatus
import com.cellclaw.eligibility.OverallEligibility
import com.cellclaw.ui.viewmodel.EligibilityReportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EligibilityReportScreen(
    appId: String,
    onBack: () -> Unit = {},
    onProceedToResume: (appId: String) -> Unit = {},
    viewModel: EligibilityReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(appId) {
        viewModel.load(appId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Eligibility Report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.load(appId) }) {
                            Text("Retry")
                        }
                    }
                }
                uiState.application != null -> {
                    val app = uiState.application!!
                    val report = uiState.report

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = app.company,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = app.role,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!app.jobId.isNullOrBlank()) {
                                    Text(
                                        text = "Job ID: ${app.jobId}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (!app.deadline.isNullOrBlank()) {
                                    Text(
                                        text = "Deadline: ${app.deadline}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (report != null) {
                            OverallStatusBanner(overall = report.overall)
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Evaluation Criteria (${report.criteria.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(report.criteria) { criterion ->
                                    CriterionCard(criterion = criterion)
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No eligibility criteria evaluation recorded for this application.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        // Hard Stop Gate for NOT_ELIGIBLE applications
                        if (uiState.isNotEligible) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            tint = Color(0xFFC62828),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Eligibility Hard Stop",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFC62828)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "One or more mandatory criteria were not satisfied. PlacementOS enforces a strict stop to avoid sending non-compliant applications to campus recruiters.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB71C1C)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = uiState.userOverrideConfirmed,
                                            onCheckedChange = { viewModel.setOverrideConfirmed(it) }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "I understand the eligibility risks and explicitly confirm my override to proceed anyway.",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFB71C1C)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onProceedToResume(appId) },
                            enabled = uiState.canProceed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            colors = if (uiState.isNotEligible && uiState.userOverrideConfirmed)
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            else
                                ButtonDefaults.buttonColors()
                        ) {
                            val buttonText = when {
                                uiState.isNotEligible && !uiState.userOverrideConfirmed ->
                                    "Proceed to Tailored Resume (Override Required)"
                                uiState.isNotEligible && uiState.userOverrideConfirmed ->
                                    "Proceed to Tailored Resume (Overridden)"
                                else ->
                                    "Proceed to Tailored Resume"
                            }
                            Text(buttonText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverallStatusBanner(overall: OverallEligibility) {
    val (bgColor, contentColor, icon, text) = when (overall) {
        OverallEligibility.ELIGIBLE -> Quad(
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
            Icons.Default.CheckCircle,
            "ELIGIBLE — All mandatory requirements met"
        )
        OverallEligibility.NOT_ELIGIBLE -> Quad(
            Color(0xFFFFEBEE),
            Color(0xFFC62828),
            Icons.Default.Close,
            "NOT ELIGIBLE — One or more mandatory requirements failed"
        )
        OverallEligibility.UNKNOWN -> Quad(
            Color(0xFFFFF8E1),
            Color(0xFFF57F17),
            Icons.Default.HelpOutline,
            "UNKNOWN — Insufficient profile facts to verify all criteria"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CriterionCard(criterion: EligibilityResult) {
    val (badgeBg, badgeText, statusLabel) = when (criterion.status) {
        EligibilityStatus.PASS -> Triple(
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
            "PASS"
        )
        EligibilityStatus.FAIL -> Triple(
            Color(0xFFFFEBEE),
            Color(0xFFC62828),
            "FAIL"
        )
        EligibilityStatus.UNKNOWN -> Triple(
            Color(0xFFFFF8E1),
            Color(0xFFF57F17),
            "UNKNOWN"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = criterion.criterion,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeBg,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = criterion.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
