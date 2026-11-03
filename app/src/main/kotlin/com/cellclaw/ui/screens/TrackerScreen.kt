package com.cellclaw.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.ui.components.EmptyState
import com.cellclaw.ui.theme.elixirColors
import com.cellclaw.ui.viewmodel.TrackerViewModel
import androidx.compose.material.icons.filled.Description
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    onNavigateToNewApp: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onSelectApplication: (appId: String) -> Unit = {},
    viewModel: TrackerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchVisible by remember { mutableStateOf(false) }
    var applicationToDelete by remember { mutableStateOf<ApplicationRecord?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Applications Tracker") },
                actions = {
                    IconButton(onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) viewModel.setSearchQuery("")
                    }) {
                        Icon(
                            if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searchVisible) "Close Search" else "Search"
                        )
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onNavigateToChat) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Legacy Chat")
                    }
                }
            )
        },

        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToNewApp) {
                Icon(Icons.Default.Add, contentDescription = "New Application")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (searchVisible) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    label = { Text("Search by company or role") },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // State filter chips
            val totalCount = uiState.countsByState.values.sum()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedFilter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text("All ($totalCount)") }
                )
                ApplicationState.values().forEach { state ->
                    val count = uiState.countsByState[state] ?: 0
                    FilterChip(
                        selected = uiState.selectedFilter == state,
                        onClick = {
                            viewModel.setFilter(if (uiState.selectedFilter == state) null else state)
                        },
                        label = { Text("${stateDisplayName(state)} ($count)") }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.applications.isEmpty()) {
                val isFiltered = uiState.selectedFilter != null || uiState.searchQuery.isNotBlank()
                if (isFiltered) {
                    EmptyState(
                        icon = Icons.Default.Search,
                        title = "No matching applications",
                        description = "No applications match your active search or state filter. Clear filters to see all applications.",
                        actionLabel = "Clear Filter",
                        actionIcon = Icons.Default.Close,
                        onAction = {
                            viewModel.setFilter(null)
                            viewModel.setSearchQuery("")
                        },
                        secondaryActionLabel = "New Application",
                        secondaryActionIcon = Icons.Default.Add,
                        onSecondaryAction = onNavigateToNewApp
                    )
                } else {
                    EmptyState(
                        icon = Icons.Default.Description,
                        title = "No applications tracked yet",
                        description = "Start your supervised placement journey by ingesting a job description from campus placements or job portals.",
                        actionLabel = "New Application",
                        actionIcon = Icons.Default.Add,
                        onAction = onNavigateToNewApp,
                        tip = "Tip: You can paste JD text directly or share job documents from external apps."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.applications, key = { it.id }) { app ->
                        ApplicationCard(
                            application = app,
                            modifier = Modifier.animateItem(),
                            onClick = { onSelectApplication(app.id) },
                            onDelete = { applicationToDelete = app }
                        )
                    }
                }
            }
        }
    }

    applicationToDelete?.let { app ->
        AlertDialog(
            onDismissRequest = { applicationToDelete = null },
            title = { Text("Delete Application") },
            text = { Text("Are you sure you want to delete the application for ${app.company} — ${app.role}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteApplication(app.id)
                    applicationToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { applicationToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ApplicationCard(
    application: ApplicationRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = application.company,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = application.role,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // State & Eligibility Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StateBadge(state = application.state)
                EligibilityBadge(result = application.eligibilityResult)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }
                Text(
                    text = "Updated: ${dateFormat.format(Date(application.updatedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (application.deadline != null) {
                    Text(
                        text = "Deadline: ${application.deadline}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (application.confirmationReference != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ref: ${application.confirmationReference}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.elixirColors.success,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun StateBadge(state: ApplicationState) {
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
            text = stateDisplayName(state),
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EligibilityBadge(result: String) {
    val elixirColors = MaterialTheme.elixirColors
    val (bgColor, textColor, label) = when (result.uppercase()) {
        "ELIGIBLE" -> Triple(elixirColors.badgeEligible, elixirColors.onBadgeEligible, "ELIGIBLE")
        "NOT_ELIGIBLE" -> Triple(elixirColors.badgeNotEligible, elixirColors.onBadgeNotEligible, "NOT ELIGIBLE")
        else -> Triple(elixirColors.badgeUnknown, elixirColors.onBadgeUnknown, "UNKNOWN")
    }

    Box(
        modifier = Modifier
            .background(bgColor, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun stateDisplayName(state: ApplicationState): String {
    return when (state) {
        ApplicationState.DRAFT -> "Draft"
        ApplicationState.READY_FOR_REVIEW -> "Ready"
        ApplicationState.SUBMITTING -> "Submitting"
        ApplicationState.SUBMITTED_CONFIRMED -> "Submitted"
        ApplicationState.SUBMISSION_UNKNOWN -> "Unknown"
        ApplicationState.FAILED -> "Failed"
        ApplicationState.CANCELLED -> "Cancelled"
    }
}
