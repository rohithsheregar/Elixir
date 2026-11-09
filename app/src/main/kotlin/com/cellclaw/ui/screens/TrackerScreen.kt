package com.cellclaw.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.ui.components.EmptyState
import com.cellclaw.ui.theme.elixirColors
import com.cellclaw.ui.viewmodel.TrackerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    onNavigateToNewApp: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToResearch: () -> Unit = {},
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
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    Brush.linearGradient(
                                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Explore,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                "Elixir",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Applications Radar",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
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
                    IconButton(onClick = onNavigateToResearch) {
                        Icon(Icons.Default.Explore, contentDescription = "Job Research")
                    }
                    val themeMode by viewModel.themeMode.collectAsState()
                    IconButton(onClick = { viewModel.toggleTheme() }) {
                        Icon(
                            imageVector = when (themeMode) {
                                "DARK" -> Icons.Default.DarkMode
                                "LIGHT" -> Icons.Default.LightMode
                                else -> Icons.Default.BrightnessAuto
                            },
                            contentDescription = "Toggle Theme (Current: $themeMode)"
                        )
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
            ExtendedFloatingActionButton(
                onClick = onNavigateToNewApp,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Application", fontWeight = FontWeight.SemiBold) }
            )
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
                    placeholder = { Text("Search by company, role or notes...") },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Quick Stats Metric Bar
            val totalCount = uiState.countsByState.values.sum()
            val draftCount = uiState.countsByState[ApplicationState.DRAFT] ?: 0
            val readyCount = uiState.countsByState[ApplicationState.READY_FOR_REVIEW] ?: 0
            val submittedCount = uiState.countsByState[ApplicationState.SUBMITTED_CONFIRMED] ?: 0

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPill(label = "Total", count = totalCount, modifier = Modifier.weight(1f), isPrimary = true)
                MetricPill(label = "Draft", count = draftCount, modifier = Modifier.weight(1f))
                MetricPill(label = "Ready", count = readyCount, modifier = Modifier.weight(1f))
                MetricPill(label = "Submitted", count = submittedCount, modifier = Modifier.weight(1f), isSuccess = true)
            }

            // State filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedFilter == null,
                    onClick = { viewModel.setFilter(null) },
                    shape = RoundedCornerShape(12.dp),
                    label = { Text("All ($totalCount)") }
                )
                ApplicationState.values().forEach { state ->
                    val count = uiState.countsByState[state] ?: 0
                    FilterChip(
                        selected = uiState.selectedFilter == state,
                        onClick = {
                            viewModel.setFilter(if (uiState.selectedFilter == state) null else state)
                        },
                        shape = RoundedCornerShape(12.dp),
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
            shape = RoundedCornerShape(20.dp),
            title = { Text("Delete Application") },
            text = { Text("Are you sure you want to delete the application for ${app.company} — ${app.role}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteApplication(app.id)
                    applicationToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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
private fun MetricPill(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isSuccess: Boolean = false
) {
    val bgColor = when {
        isPrimary -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        isSuccess -> MaterialTheme.elixirColors.successContainer.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = when {
        isPrimary -> MaterialTheme.colorScheme.primary
        isSuccess -> MaterialTheme.elixirColors.success
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ApplicationCard(
    application: ApplicationRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initial = application.company.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "C"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Column {
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
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // State & Eligibility Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StateBadge(state = application.state)
                EligibilityBadge(result = application.eligibilityResult)
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            // Metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateFormat.format(Date(application.updatedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.elixirColors.successContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Confirmation: ${application.confirmationReference}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.elixirColors.onSuccessContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun StateBadge(state: ApplicationState) {
    val elixirColors = MaterialTheme.elixirColors
    val (bgColor, textColor, dotColor) = when (state) {
        ApplicationState.DRAFT -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        ApplicationState.READY_FOR_REVIEW -> Triple(elixirColors.infoContainer, elixirColors.onInfoContainer, elixirColors.info)
        ApplicationState.SUBMITTING -> Triple(elixirColors.warningContainer, elixirColors.onWarningContainer, elixirColors.warning)
        ApplicationState.SUBMITTED_CONFIRMED -> Triple(elixirColors.successContainer, elixirColors.onSuccessContainer, elixirColors.success)
        ApplicationState.SUBMISSION_UNKNOWN -> Triple(elixirColors.warningContainer, elixirColors.onWarningContainer, elixirColors.warning)
        ApplicationState.FAILED -> Triple(elixirColors.errorContainer, elixirColors.onErrorContainer, elixirColors.error)
        ApplicationState.CANCELLED -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(dotColor, shape = CircleShape)
            )
            Text(
                text = stateDisplayName(state),
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
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

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

private fun stateDisplayName(state: ApplicationState): String = when (state) {
    ApplicationState.DRAFT -> "Draft"
    ApplicationState.READY_FOR_REVIEW -> "Ready"
    ApplicationState.SUBMITTING -> "Submitting"
    ApplicationState.SUBMITTED_CONFIRMED -> "Submitted"
    ApplicationState.SUBMISSION_UNKNOWN -> "Unknown"
    ApplicationState.FAILED -> "Failed"
    ApplicationState.CANCELLED -> "Cancelled"
}
