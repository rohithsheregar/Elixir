package com.cellclaw.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellclaw.resume.ResumeContent
import com.cellclaw.resume.ResumeSection
import com.cellclaw.ui.viewmodel.ResumePreviewViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumePreviewScreen(
    appId: String,
    onBack: () -> Unit = {},
    onApproveResume: (appId: String) -> Unit = {},
    viewModel: ResumePreviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showApproveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(appId) {
        viewModel.load(appId)
    }

    LaunchedEffect(uiState.approved) {
        if (uiState.approved) {
            onApproveResume(appId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resume Preview") },
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
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                        Button(onClick = { viewModel.load(appId) }) { Text("Retry") }
                    }
                }
                uiState.resume != null && uiState.application != null -> {
                    val resume = uiState.resume!!
                    val app = uiState.application!!
                    val pdf = uiState.pdf

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        // Application header
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
                                    text = "${app.company} — ${app.role}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (pdf != null) {
                                    Text(
                                        text = "File: ${pdf.fileName}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "SHA-256: ${pdf.sha256.take(16)}…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Pages: ${pdf.pageCount}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        // Material change warning
                        if (uiState.materialChangeWarning) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFF57F17),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "This resume differs materially from your master resume. Review carefully before approving.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFF57F17)
                                    )
                                }
                            }
                        }

                        // Evidence integrity warning
                        if (!uiState.evidenceIntegrityOk) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = "⚠ Evidence integrity check failed — some resume lines may not map to confirmed profile facts.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Text(
                            text = "Resume Content",
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
                            // Contact header
                            item {
                                ResumeHeaderCard(resume = resume)
                            }

                            items(resume.sections) { section ->
                                ResumeSectionCard(
                                    section = section,
                                    evidenceMap = resume.evidenceMap.lineToFactId
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { showApproveDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("I Approve This Resume — Proceed to Review")
                        }
                    }
                }
            }
        }
    }

    if (showApproveDialog) {
        AlertDialog(
            onDismissRequest = { showApproveDialog = false },
            title = { Text("Approve Resume?") },
            text = {
                Text(
                    "By tapping Approve you confirm all content reflects your confirmed profile facts. " +
                    "This resume will be attached to your application."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showApproveDialog = false
                    viewModel.approve()
                }) { Text("Approve") }
            },
            dismissButton = {
                TextButton(onClick = { showApproveDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ResumeHeaderCard(resume: ResumeContent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = resume.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(text = resume.email, style = MaterialTheme.typography.bodyMedium)
            if (!resume.phone.isNullOrBlank()) {
                Text(text = resume.phone, style = MaterialTheme.typography.bodyMedium)
            }
            if (resume.links.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = resume.links.joinToString(" | "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (resume.education.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Education",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                resume.education.forEach { edu ->
                    Text(text = edu, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ResumeSectionCard(
    section: ResumeSection,
    evidenceMap: Map<String, String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            HorizontalDivider(
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            section.lines.forEach { line ->
                val factId = evidenceMap[line.id]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "• ${line.text}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (factId != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
