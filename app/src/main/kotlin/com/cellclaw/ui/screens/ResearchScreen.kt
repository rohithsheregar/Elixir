package com.cellclaw.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Autonomous Job Research") },
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
            // Section 1: Research Control Card
            item(key = "research_control") {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Multi-Platform Deep Search",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "Autonomous AI research engine traverses LinkedIn, company career portals, and search engines. It validates link freshness, scores candidate relevance, checks trust, and auto-stages verified jobs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = customQuery,
                            onValueChange = { customQuery = it },
                            label = { Text("Optional Custom Keywords / Role") },
                            placeholder = { Text("Leave blank to deduce from candidate profile") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !state.isRunning
                        )

                        Button(
                            onClick = {
                                viewModel.startResearch(customQuery.ifBlank { null })
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isRunning
                        ) {
                            if (state.isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("AI Researching Internet...")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Run Autonomous Job Research")
                            }
                        }

                        if (state.isRunning) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { state.progressPercent },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "Current Loop: ${state.currentStage}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Section 2: Real-Time AI Brain Terminal / Logs
            if (state.logs.isNotEmpty()) {
                item(key = "research_logs") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "AI Brain Telemetry Logs (${state.logs.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { showLogs = !showLogs }) {
                                    Text(if (showLogs) "Hide" else "Show")
                                }
                            }

                            AnimatedVisibility(visible = showLogs) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        state.logs.takeLast(10).forEach { entry ->
                                            Text(
                                                text = "[${entry.stage}] ${entry.message}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = when (entry.stage) {
                                                    "DISCOVERY" -> MaterialTheme.colorScheme.primary
                                                    "VALIDATION" -> MaterialTheme.colorScheme.secondary
                                                    "RELEVANCE" -> MaterialTheme.elixirColors.warning
                                                    "AUTO_STAGING" -> MaterialTheme.elixirColors.success
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
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
                Text(
                    text = "Vetted Opportunities (${state.results.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Section 4: Vetted Opportunities Cards
            items(state.results, key = { it.id }) { opening ->
                VettedJobCard(
                    opening = opening,
                    onOpenApplication = { appId -> onNavigateToApplication(appId) }
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VettedJobCard(
    opening: VettedJobOpening,
    onOpenApplication: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("${opening.relevanceScore}% Match") }
                    )
                    SuggestionChip(
                        onClick = {},
                        label = { Text("${opening.trustScore}% Trust") }
                    )
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
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    opening.matchedSkills.forEach { skill ->
                        AssistChip(
                            onClick = {},
                            label = { Text(skill) }
                        )
                    }
                }
            }

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
                        onClick = { onOpenApplication(opening.stagedApplicationId) }
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Staged (Review)")
                    }
                }
            }
        }
    }
}

