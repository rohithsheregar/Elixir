package com.cellclaw.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.ui.components.PlacementStep
import com.cellclaw.ui.components.ProgressStepper
import com.cellclaw.ui.components.BoardingPassCard
import com.cellclaw.ui.theme.elixirColors
import com.cellclaw.ui.viewmodel.FormFieldReviewItem
import com.cellclaw.ui.viewmodel.ReviewViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    appId: String,
    onBack: () -> Unit = {},
    onApproveSubmission: (appId: String) -> Unit = {},
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showApproveDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var referenceInput by remember { mutableStateOf("") }
    var declaredAccepted by remember { mutableStateOf(false) }

    LaunchedEffect(appId) {
        viewModel.load(appId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Application Review") },
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
                uiState.errorMessage != null && uiState.application == null -> {
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
                        Button(onClick = onBack) {
                            Text("Go Back")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Progress stepper
                        item {
                            ProgressStepper(
                                currentStep = PlacementStep.SUPERVISED_REVIEW
                            )
                        }

                        // Success message banner if any
                        uiState.actionSuccessMessage?.let { msg ->
                            item {
                                val elixirColors = MaterialTheme.elixirColors
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = elixirColors.successContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = elixirColors.success)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(msg, color = elixirColors.onSuccessContainer, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // Boarding-Pass Style Placement Pass
                        item {
                            BoardingPassCard(
                                company = uiState.company,
                                role = uiState.role,
                                state = uiState.currentState,
                                domain = uiState.domain,
                                resumeFileName = uiState.resumeFileName,
                                resumeVersion = uiState.resumeVersion,
                                resumeHash = uiState.resumeHash
                            )
                        }

                        // Uncertainties & Warnings (if any)
                        if (uiState.uncertainties.isNotEmpty()) {
                            item {
                                val elixirColors = MaterialTheme.elixirColors
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = elixirColors.warningContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = elixirColors.warning)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Uncertainties (${uiState.uncertainties.size})",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = elixirColors.warning
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        uiState.uncertainties.forEach { unc ->
                                            Text(
                                                text = "• $unc",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = elixirColors.onWarningContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Unanswered Fields (if any)
                        if (uiState.unansweredFields.isNotEmpty()) {
                            item {
                                val elixirColors = MaterialTheme.elixirColors
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = elixirColors.errorContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Info, contentDescription = null, tint = elixirColors.error)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Missing Profile Fields (${uiState.unansweredFields.size})",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = elixirColors.error
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        uiState.unansweredFields.forEach { f ->
                                            Text(
                                                text = "• $f",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = elixirColors.onErrorContainer
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = { viewModel.openNeedsInputModal() },
                                            colors = ButtonDefaults.buttonColors(containerColor = elixirColors.error)
                                        ) {
                                            Text("Resolve Missing Questions")
                                        }
                                    }
                                }
                            }
                        }

                        // Sensitive & Custom Questions (Needs Input card)
                        if (uiState.needsInputItems.isNotEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Needs Your Input",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val pendingCount = uiState.needsInputItems.count { !it.isResolved }
                                            Text(
                                                text = if (pendingCount > 0)
                                                    "$pendingCount question(s) awaiting explicit approval"
                                                else
                                                    "All questions reviewed & resolved",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (pendingCount > 0) MaterialTheme.elixirColors.warning else MaterialTheme.elixirColors.success
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        OutlinedButton(onClick = { viewModel.openNeedsInputModal() }) {
                                            Text("Review & Answer")
                                        }
                                    }
                                }
                            }
                        }

                        // Form Values to be Filled
                        item {
                            Text(
                                text = "Form Values to Fill (${uiState.filledFields.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(uiState.filledFields) { item ->
                            FormFieldRow(item)
                        }

                        // Declarations & Approvals
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Declarations & Affirmations",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    uiState.declarations.forEach { dec ->
                                        Text(
                                            text = "✓ $dec",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = declaredAccepted,
                                            onCheckedChange = { declaredAccepted = it }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "I have reviewed all fields and confirm they are strictly derived from confirmed facts.",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // Submission Action Gate
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Supervised Submission Gate",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Elixir requires explicit user consent before submitting any application. The final submit tap belongs to you.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { showApproveDialog = true },
                                            enabled = declaredAccepted && uiState.currentState != ApplicationState.SUBMITTED_CONFIRMED,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Approve & Submit")
                                        }

                                        OutlinedButton(
                                            onClick = { showConfirmDialog = true },
                                            enabled = uiState.currentState != ApplicationState.SUBMITTED_CONFIRMED,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Mark Confirmed")
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    if (showApproveDialog) {
        AlertDialog(
            onDismissRequest = { showApproveDialog = false },
            title = { Text("Confirm Supervised Submission") },
            text = {
                Text("Are you ready to initiate submission for ${uiState.company} (${uiState.role})? Elixir will fill the verified form fields and require your supervision.")
            },
            confirmButton = {
                Button(onClick = {
                    showApproveDialog = false
                    viewModel.approveSubmission {
                        onApproveSubmission(appId)
                    }
                }) {
                    Text("Proceed")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApproveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Manual Confirmation Reference") },
            text = {
                Column {
                    Text("Enter confirmation number or acknowledgement reference from the company portal:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = referenceInput,
                        onValueChange = { referenceInput = it },
                        label = { Text("Reference / Confirmation ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    viewModel.markManualConfirmed(referenceInput)
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showAccessibilityPrompt) {
        val context = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { viewModel.dismissAccessibilityPrompt() },
            title = { Text("Enable Elixir Accessibility Service") },
            text = {
                Text(
                    "Supervised form automation requires the Elixir accessibility service to inspect form fields and fill verified details. Final submission will still require your explicit approval."
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.openAccessibilitySettings(context)
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAccessibilityPrompt() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showNeedsInputModal) {
        NeedsInputModalDialog(
            items = uiState.needsInputItems,
            onResolve = { id, value -> viewModel.resolveFieldInput(id, value) },
            onDismiss = { viewModel.dismissNeedsInputModal() }
        )
    }
}

@Composable
private fun NeedsInputModalDialog(
    items: List<com.cellclaw.ui.viewmodel.NeedsInputReviewItem>,
    onResolve: (id: String, value: String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val pendingCount = items.count { !it.isResolved }
            Text(if (pendingCount > 0) "Needs Your Input ($pendingCount Pending)" else "Needs Your Input (All Resolved)")
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "PlacementOS hard rule: answers to sensitive questions (salary, bond, legal terms) and missing profile facts must be reviewed and answered by you directly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(items, key = { it.id }) { item ->
                    NeedsInputItemCard(
                        item = item,
                        onSave = { value -> onResolve(item.id, value) }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun NeedsInputItemCard(
    item: com.cellclaw.ui.viewmodel.NeedsInputReviewItem,
    onSave: (String) -> Unit
) {
    var textValue by remember(item.currentValue) { mutableStateOf(item.currentValue) }

    val elixirColors = MaterialTheme.elixirColors
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isResolved) elixirColors.successContainer else elixirColors.warningContainer
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (item.isResolved) "Resolved" else "Pending",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.isResolved) elixirColors.success else elixirColors.warning,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = item.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (item.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text("Your Answer") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onSave(textValue) },
                    enabled = textValue.trim() != item.currentValue.trim() || !item.isResolved
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun FormFieldRow(item: FormFieldReviewItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (item.sourceFactId != null) {
                    Text(
                        text = "Source: ${item.sourceFactId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.elixirColors.success
                    )
                }
            }

            Box(
                modifier = Modifier
                    .background(MaterialTheme.elixirColors.successContainer, shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "100% Fact",
                    color = MaterialTheme.elixirColors.onSuccessContainer,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StateChip(state: ApplicationState) {
    val elixirColors = MaterialTheme.elixirColors
    val (bgColor, textColor) = when (state) {
        ApplicationState.DRAFT -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        ApplicationState.READY_FOR_REVIEW -> elixirColors.infoContainer to elixirColors.onInfoContainer
        ApplicationState.SUBMITTING -> elixirColors.warningContainer to elixirColors.onWarningContainer
        ApplicationState.SUBMITTED_CONFIRMED -> elixirColors.successContainer to elixirColors.onSuccessContainer
        ApplicationState.SUBMISSION_UNKNOWN -> elixirColors.warningContainer to elixirColors.onWarningContainer
        ApplicationState.FAILED -> elixirColors.errorContainer to elixirColors.onErrorContainer
        ApplicationState.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .background(bgColor, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = state.name,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
