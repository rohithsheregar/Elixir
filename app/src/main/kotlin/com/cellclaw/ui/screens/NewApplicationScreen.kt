package com.cellclaw.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellclaw.ui.components.PlacementStep
import com.cellclaw.ui.components.ProgressStepper
import com.cellclaw.ui.viewmodel.NewApplicationViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewApplicationScreen(
    onBack: () -> Unit = {},
    onApplicationCreated: (appId: String) -> Unit = {},
    initialJdText: String? = null,
    initialStreamUri: Uri? = null,
    viewModel: NewApplicationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(initialJdText, initialStreamUri) {
        if (!initialJdText.isNullOrBlank()) {
            val text = initialJdText.trim()
            if ((text.startsWith("http://") || text.startsWith("https://")) && !text.contains("\n") && !text.contains(" ")) {
                viewModel.onSourceUrlChanged(text)
            } else {
                viewModel.onJdTextChanged(text)
                viewModel.parseCurrentJd()
            }
        } else if (initialStreamUri != null) {
            val mimeType = context.contentResolver.getType(initialStreamUri)
            val fileName = queryFileName(context, initialStreamUri)
            context.contentResolver.openInputStream(initialStreamUri)?.use { inputStream ->
                viewModel.processDocumentStream(inputStream, mimeType, fileName)
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)
            val fileName = queryFileName(context, uri)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                viewModel.processDocumentStream(inputStream, mimeType, fileName)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Placement Application") },
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
            ProgressStepper(
                currentStep = PlacementStep.JD_INGESTION,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = "1. Ingest Job Description (JD)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Pick Document or Paste from Clipboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        documentPicker.launch(
                            arrayOf(
                                "application/pdf",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "text/plain"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Pick File (PDF/DOCX/TXT)")
                }

                OutlinedButton(
                    onClick = {
                        clipboardManager.getText()?.text?.let { clipText ->
                            viewModel.onJdTextChanged(clipText)
                            viewModel.parseCurrentJd()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Paste Clipboard")
                }
            }

            // Error or Fallback Notice
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error)
                        Text(text = error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // Application URL (e.g. Google Form link)
            OutlinedTextField(
                value = uiState.sourceUrl,
                onValueChange = { viewModel.onSourceUrlChanged(it) },
                label = { Text("Application Link / Google Form URL (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Raw JD Text
            OutlinedTextField(
                value = uiState.jdText,
                onValueChange = { viewModel.onJdTextChanged(it) },
                label = { Text("Job Description (JD) Text") },
                placeholder = { Text("Paste requirements, eligibility criteria, and job details here...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 260.dp)
            )

            // Parse Trigger Button
            Button(
                onClick = { viewModel.parseCurrentJd() },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.jdText.isNotBlank() && !uiState.isParsing
            ) {
                if (uiState.isParsing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Parsing...")
                } else {
                    Text("Extract Requirements")
                }
            }

            // Parsed Requirements Card Preview
            uiState.parsedRequirement?.let { req ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Extracted JD Details",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = uiState.company,
                                onValueChange = { viewModel.onCompanyChanged(it) },
                                label = { Text("Company Name") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = uiState.role,
                                onValueChange = { viewModel.onRoleChanged(it) },
                                label = { Text("Role") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        req.minCgpa.value?.let { cgpa ->
                            Text(
                                text = "Min CGPA / Percentage: $cgpa (Scale: ${req.cgpaScale.value ?: 10.0})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        req.degree.value?.let { deg ->
                            Text(text = "Eligible Degree: $deg", style = MaterialTheme.typography.bodyMedium)
                        }

                        req.branch.value?.let { br ->
                            Text(text = "Eligible Branches: $br", style = MaterialTheme.typography.bodyMedium)
                        }

                        if (!req.requiredSkills.value.isNullOrEmpty()) {
                            Text(text = "Required Skills:", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                req.requiredSkills.value?.forEach { skill ->
                                    SuggestionChip(onClick = {}, label = { Text(skill) })
                                }
                            }
                        }

                        if (!req.preferredSkills.value.isNullOrEmpty()) {
                            Text(text = "Preferred Skills:", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                req.preferredSkills.value?.forEach { skill ->
                                    AssistChip(onClick = {}, label = { Text(skill) })
                                }
                            }
                        }
                    }
                }

                // Proceed to Eligibility & Create Application
                Button(
                    onClick = {
                        viewModel.createApplication { createdId ->
                            onApplicationCreated(createdId)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Create Application")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Evaluate Eligibility & Save Application")
                }
            }
        }
    }
}

private fun queryFileName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    return cursor.getString(nameIndex)
                }
            }
        }
    }
    return uri.path?.substringAfterLast('/')
}
