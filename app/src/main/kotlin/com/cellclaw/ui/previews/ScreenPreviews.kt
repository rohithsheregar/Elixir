package com.cellclaw.ui.previews

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cellclaw.profile.ProfileFact
import com.cellclaw.profile.ProfileFactSource
import com.cellclaw.resume.DiffEntry
import com.cellclaw.resume.DiffType
import com.cellclaw.resume.ResumeDiffResult
import com.cellclaw.tracker.ApplicationRecord
import com.cellclaw.tracker.ApplicationState
import com.cellclaw.ui.components.BoardingPassCard
import com.cellclaw.ui.components.EmptyState
import com.cellclaw.ui.components.PlacementStep
import com.cellclaw.ui.components.ProgressStepper
import com.cellclaw.ui.components.ResumeDiffView
import com.cellclaw.ui.screens.CategoryAccordionCard
import com.cellclaw.ui.screens.ProfileFactCategory
import com.cellclaw.ui.theme.ElixirTheme
import com.cellclaw.ui.theme.elixirColors
import java.time.Instant

private val sampleTime = Instant.parse("2026-01-01T00:00:00Z")

// ─────────────────────────────────────────────────────────────────────────────
// 1. Tracker Screen Preview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TrackerPreviewContent() {
    val elixirColors = MaterialTheme.elixirColors
    val sampleApps = listOf(
        ApplicationRecord(
            id = "app-1",
            company = "Google",
            role = "Android Engineer",
            jdHash = "hash123",
            eligibilityResult = "ELIGIBLE",
            state = ApplicationState.READY_FOR_REVIEW,
            sourceUrl = "https://docs.google.com/forms/d/e/1FAIpQLSc.../viewform"
        ),
        ApplicationRecord(
            id = "app-2",
            company = "Microsoft",
            role = "Cloud Solutions Architect",
            jdHash = "hash456",
            eligibilityResult = "ELIGIBLE",
            state = ApplicationState.SUBMITTED_CONFIRMED,
            confirmationReference = "MSFT-2026-88192"
        )
    )

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Applications Tracker") },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Default.Search, contentDescription = "Search") }
                    IconButton(onClick = {}) { Icon(Icons.Default.Person, contentDescription = "Profile") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {}) {
                Icon(Icons.Default.Add, contentDescription = "New Application")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = true, onClick = {}, label = { Text("All (2)") })
                    FilterChip(selected = false, onClick = {}, label = { Text("Active (1)") })
                    FilterChip(selected = false, onClick = {}, label = { Text("Submitted (1)") })
                }
            }

            items(sampleApps) { app ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = app.company, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (app.state == ApplicationState.SUBMITTED_CONFIRMED) elixirColors.successContainer else elixirColors.infoContainer
                            ) {
                                Text(
                                    text = app.state.name.replace("_", " "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (app.state == ApplicationState.SUBMITTED_CONFIRMED) elixirColors.onSuccessContainer else elixirColors.onInfoContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(text = app.role, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Preview(name = "1. TrackerScreen - Light", showBackground = true)
@Composable
fun TrackerScreenPreviewLight() {
    ElixirTheme(darkTheme = false) {
        TrackerPreviewContent()
    }
}

@Preview(name = "1. TrackerScreen - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun TrackerScreenPreviewDark() {
    ElixirTheme(darkTheme = true) {
        TrackerPreviewContent()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. New Application Screen Preview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NewApplicationPreviewContent() {
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("New Application") },
                navigationIcon = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProgressStepper(currentStep = PlacementStep.JD_INGESTION)

            Text("Paste Job Description", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = "Company: Stripe\nRole: Mobile Platform Engineer\nRequired Skills: Kotlin, Compose, Android SDK\nEligibility: Min 8.0 CGPA, B.Tech 2026",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth().height(160.dp),
                label = { Text("Job Description Text") }
            )

            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Analyze Eligibility & Ingest JD")
            }
        }
    }
}

@Preview(name = "2. NewApplicationScreen - Light", showBackground = true)
@Composable
fun NewApplicationScreenPreviewLight() {
    ElixirTheme(darkTheme = false) {
        NewApplicationPreviewContent()
    }
}

@Preview(name = "2. NewApplicationScreen - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun NewApplicationScreenPreviewDark() {
    ElixirTheme(darkTheme = true) {
        NewApplicationPreviewContent()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. Eligibility Report Screen Preview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EligibilityReportPreviewContent() {
    val elixirColors = MaterialTheme.elixirColors
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Eligibility Report") },
                navigationIcon = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProgressStepper(currentStep = PlacementStep.ELIGIBILITY)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = elixirColors.successContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = elixirColors.onSuccessContainer, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("ELIGIBLE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = elixirColors.onSuccessContainer)
                        Text("All criteria verified against confirmed profile facts.", style = MaterialTheme.typography.bodySmall, color = elixirColors.onSuccessContainer)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Verified Criteria Checklist", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("✓ CGPA: Candidate 8.9 >= Required 7.5", style = MaterialTheme.typography.bodySmall)
                    Text("✓ Degree: B.Tech Computer Science matches requirement", style = MaterialTheme.typography.bodySmall)
                    Text("✓ Skills: Kotlin, Compose confirmed in profile", style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Proceed to Tailored Resume")
            }
        }
    }
}

@Preview(name = "3. EligibilityReportScreen - Light", showBackground = true)
@Composable
fun EligibilityReportScreenPreviewLight() {
    ElixirTheme(darkTheme = false) {
        EligibilityReportPreviewContent()
    }
}

@Preview(name = "3. EligibilityReportScreen - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun EligibilityReportScreenPreviewDark() {
    ElixirTheme(darkTheme = true) {
        EligibilityReportPreviewContent()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. Resume Preview Screen Preview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ResumePreviewPreviewContent() {
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Resume Preview") },
                navigationIcon = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProgressStepper(currentStep = PlacementStep.TAILORED_RESUME)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = true, onClick = {}, label = { Text("Visual Diff (2)") })
                FilterChip(selected = false, onClick = {}, label = { Text("Formatted View") })
            }

            ResumeDiffView(
                diffResult = ResumeDiffResult(
                    isMaterialChange = true,
                    requiresUserApproval = true,
                    entries = listOf(
                        DiffEntry(DiffType.ADDED, "Skills", null, "Kotlin Coroutines"),
                        DiffEntry(DiffType.REORDERED, "Experience", "Mobile Dev; Web Dev", "Web Dev; Mobile Dev")
                    ),
                    summary = "Tailored 2 modifications for Android Engineer JD."
                ),
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I Approve This Resume — Proceed to Review")
            }
        }
    }
}

@Preview(name = "4. ResumePreviewScreen - Light", showBackground = true)
@Composable
fun ResumePreviewScreenPreviewLight() {
    ElixirTheme(darkTheme = false) {
        ResumePreviewPreviewContent()
    }
}

@Preview(name = "4. ResumePreviewScreen - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ResumePreviewScreenPreviewDark() {
    ElixirTheme(darkTheme = true) {
        ResumePreviewPreviewContent()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. Review Screen Preview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ReviewPreviewContent() {
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Application Review") },
                navigationIcon = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProgressStepper(currentStep = PlacementStep.SUPERVISED_REVIEW)

            BoardingPassCard(
                company = "Google",
                role = "Android Engineer",
                state = ApplicationState.READY_FOR_REVIEW,
                domain = "docs.google.com/forms",
                resumeFileName = "resume_google_android_v1.pdf",
                resumeVersion = 1,
                resumeHash = "7f83b1657ff1fc53b92dc18148a1d65d"
            )

            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Approve & Start Supervised Form Fill")
            }
        }
    }
}

@Preview(name = "5. ReviewScreen - Light", showBackground = true)
@Composable
fun ReviewScreenPreviewLight() {
    ElixirTheme(darkTheme = false) {
        ReviewPreviewContent()
    }
}

@Preview(name = "5. ReviewScreen - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ReviewScreenPreviewDark() {
    ElixirTheme(darkTheme = true) {
        ReviewPreviewContent()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. Profile Screen Preview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ProfilePreviewContent() {
    val sampleFacts = listOf(
        ProfileFact("f-1", "name", "Jane Developer", ProfileFactSource.USER_ENTERED, true, sampleTime),
        ProfileFact("f-2", "email", "jane@example.com", ProfileFactSource.USER_ENTERED, true, sampleTime),
        ProfileFact("f-3", "degree", "B.Tech Computer Science", ProfileFactSource.USER_ENTERED, true, sampleTime),
        ProfileFact("f-4", "cgpa", "8.9", ProfileFactSource.USER_ENTERED, true, sampleTime),
        ProfileFact("f-5", "skills", "Kotlin, Jetpack Compose, Coroutines", ProfileFactSource.USER_ENTERED, true, sampleTime),
        ProfileFact("f-6", "projects", "Elixir Autonomous Assistant", ProfileFactSource.USER_ENTERED, true, sampleTime)
    )

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Placement Profile") },
                actions = { IconButton(onClick = {}) { Icon(Icons.Default.Delete, "Wipe", tint = MaterialTheme.colorScheme.error) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {}) { Icon(Icons.Default.Add, "Add Fact") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Confirmed Facts (6)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    FilterChip(selected = true, onClick = {}, label = { Text("Accordion") })
                }
            }

            for (category in ProfileFactCategory.values()) {
                val categoryFacts = sampleFacts.filter {
                    when (category) {
                        ProfileFactCategory.PERSONAL -> it.field in listOf("name", "email")
                        ProfileFactCategory.ACADEMICS -> it.field in listOf("degree", "cgpa")
                        ProfileFactCategory.SKILLS -> it.field == "skills"
                        ProfileFactCategory.EXPERIENCE -> it.field == "projects"
                    }
                }

                item {
                    CategoryAccordionCard(
                        category = category,
                        facts = categoryFacts,
                        isExpanded = true,
                        onToggle = {},
                        onEditFact = {},
                        onAddFactForCategory = {}
                    )
                }
            }
        }
    }
}

@Preview(name = "6. ProfileScreen - Light", showBackground = true)
@Composable
fun ProfileScreenPreviewLight() {
    ElixirTheme(darkTheme = false) {
        ProfilePreviewContent()
    }
}

@Preview(name = "6. ProfileScreen - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ProfileScreenPreviewDark() {
    ElixirTheme(darkTheme = true) {
        ProfilePreviewContent()
    }
}

