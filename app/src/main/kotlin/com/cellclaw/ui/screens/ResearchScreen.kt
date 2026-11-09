package com.cellclaw.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellclaw.agent.VettedJobOpening
import com.cellclaw.ui.theme.elixirColors
import com.cellclaw.ui.viewmodel.ResearchViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ResearchScreen(
    onBack: () -> Unit = {},
    onNavigateToApplication: (String) -> Unit = {},
    viewModel: ResearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var customQuery by remember { mutableStateOf("") }
    var showLogs by remember { mutableStateOf(true) }

    val quickQueries = listOf("Android Engineer", "Kotlin Developer", "AI Engineer Intern", "Software Graduate 2026")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Job Radar & Research", fontWeight = FontWeight.Bold)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "Autonomous",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Research Control Card (Bento Style)
            item(key = "research_control") {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    "Multi-Platform Deep Search",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Autonomous discovery across LinkedIn, Greenhouse, and career portals",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Quick Keyword Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickQueries.forEach { query ->
                                FilterChip(
                                    selected = customQuery == query,
                                    onClick = { customQuery = if (customQuery == query) "" else query },
                                    shape = RoundedCornerShape(12.dp),
                                    label = { Text(query, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = customQuery,
                            onValueChange = { customQuery = it },
                            placeholder = { Text("Custom keywords (leave empty to deduce from profile)") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !state.isRunning
                        )

                        Button(
                            onClick = {
                                viewModel.startResearch(customQuery.ifBlank { null })
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = !state.isRunning
                        ) {
                            if (state.isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Autonomous Radar Active...", fontWeight = FontWeight.SemiBold)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Run Autonomous Job Research", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (state.isRunning) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(
                                    progress = { state.progressPercent },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                )
                                Text(
                                    text = "Current Stage: ${state.currentStage}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Section 2: Real-Time AI Brain Telemetry Console
            if (state.logs.isNotEmpty()) {
                item(key = "research_logs") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                        border = BorderStroke(1.dp, Color(0xFF30363D))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF00E676), shape = CircleShape)
                                    )
                                    Text(
                                        "AI Telemetry Logs (${state.logs.size})",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE6EDF3)
                                    )
                                }
                                TextButton(onClick = { showLogs = !showLogs }) {
                                    Text(if (showLogs) "Hide" else "Show", color = Color(0xFF58A6FF))
                                }
                            }

                            AnimatedVisibility(visible = showLogs) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    state.logs.takeLast(10).forEach { entry ->
                                        val stageColor = when (entry.stage) {
                                            "DISCOVERY" -> Color(0xFF58A6FF)
                                            "VALIDATION" -> Color(0xFFFFA657)
                                            "RELEVANCE" -> Color(0xFF7EE787)
                                            else -> Color(0xFFD2A8FF)
                                        }
                                        Row {
                                            Text(
                                                text = "[${entry.stage}] ",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = stageColor
                                            )
                                            Text(
                                                text = entry.message,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = Color(0xFFC9D1D9)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Vetted Opportunities Header
            item(key = "vetted_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Vetted Opportunities (${state.results.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Section 4: Vetted Job Cards
            if (state.results.isEmpty() && !state.isRunning) {
                item(key = "empty_vetted") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Explore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "No vetted opportunities found yet",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Run the autonomous job research engine to discover and score active openings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(state.results, key = { it.id }) { opening ->
                    VettedJobCard(
                        opening = opening,
                        onOpenApplication = onNavigateToApplication
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VettedJobCard(
    opening: VettedJobOpening,
    onOpenApplication: (String) -> Unit
) {
    val initial = opening.company.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "J"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                            .size(42.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
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
                            text = opening.company,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = opening.role,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "${opening.relevanceScore}% Fit",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.elixirColors.successContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "${opening.trustScore}% Trust",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.elixirColors.success,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (opening.snippet.isNotBlank()) {
                Text(
                    text = opening.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }

            if (opening.matchedSkills.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    opening.matchedSkills.forEach { skill ->
                        SuggestionChip(
                            onClick = {},
                            shape = RoundedCornerShape(8.dp),
                            label = { Text(skill, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = opening.url.take(38) + "...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (opening.stagedApplicationId != null) {
                    FilledTonalButton(
                        onClick = { onOpenApplication(opening.stagedApplicationId) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Staged Application", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
