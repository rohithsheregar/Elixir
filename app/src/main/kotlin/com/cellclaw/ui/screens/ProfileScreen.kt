package com.cellclaw.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellclaw.profile.ProfileFact
import com.cellclaw.profile.ProfileFactProposal
import com.cellclaw.ui.components.EmptyState
import com.cellclaw.ui.theme.elixirColors
import com.cellclaw.ui.viewmodel.ProfileViewModel

/**
 * 4 primary placement fact categories for accordion grouping.
 */
enum class ProfileFactCategory(
    val title: String,
    val description: String
) {
    PERSONAL("Personal", "Contact details and basic identity"),
    ACADEMICS("Academics", "Educational background, GPA, and scores"),
    EXPERIENCE("Experience", "Projects, internships, and work history"),
    SKILLS("Skills", "Technical skills, tools, and competencies")
}

/**
 * Maps a profile fact field name to its corresponding accordion category.
 */
fun categorizeProfileFact(field: String): ProfileFactCategory {
    val f = field.lowercase().trim()
    return when {
        f in listOf("name", "email", "phone", "links", "link", "github", "linkedin", "portfolio", "address", "gender") -> ProfileFactCategory.PERSONAL
        f in listOf("college", "university", "degree", "branch", "graduationyear", "graduation_year", "cgpa", "cgpa_scale", "cgpascale", "marks10th", "marks12th", "10th", "12th", "percentage", "backlogs", "education") -> ProfileFactCategory.ACADEMICS
        f in listOf("skills", "skill", "tech_stack", "tools", "languages", "competencies", "frameworks") -> ProfileFactCategory.SKILLS
        f in listOf("internships", "internship", "projects", "project", "certificates", "certificate", "experience", "reusableanswers", "reusable_answers", "work") -> ProfileFactCategory.EXPERIENCE
        f.contains("cgpa") || f.contains("degree") || f.contains("mark") || f.contains("college") || f.contains("school") || f.contains("academ") -> ProfileFactCategory.ACADEMICS
        f.contains("skill") || f.contains("tool") || f.contains("tech") -> ProfileFactCategory.SKILLS
        f.contains("project") || f.contains("intern") || f.contains("exp") || f.contains("cert") || f.contains("work") -> ProfileFactCategory.EXPERIENCE
        else -> ProfileFactCategory.PERSONAL
    }
}

enum class ProfileViewMode {
    ACCORDION,
    FLAT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var defaultFieldHint by remember { mutableStateOf("") }
    var editingFact by remember { mutableStateOf<ProfileFact?>(null) }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(ProfileViewMode.ACCORDION) }

    var expandedCategories by remember {
        mutableStateOf(
            setOf(
                ProfileFactCategory.PERSONAL,
                ProfileFactCategory.ACADEMICS,
                ProfileFactCategory.EXPERIENCE,
                ProfileFactCategory.SKILLS
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Placement Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showWipeConfirmDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Wipe Profile", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                defaultFieldHint = ""
                showAddDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Fact")
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Unconfirmed Proposals (Diff View)
                if (uiState.proposals.isNotEmpty()) {
                    item {
                        Text(
                            text = "Pending Proposals (${uiState.proposals.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = "Review extracted facts before confirming. Only confirmed facts are ever used in applications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(uiState.proposals, key = { it.proposed.id }) { proposal ->
                        ProposalDiffCard(
                            proposal = proposal,
                            modifier = Modifier.animateItem(),
                            onApprove = { viewModel.approveProposal(proposal) },
                            onReject = { viewModel.rejectProposal(proposal) }
                        )
                    }
                }

                // Section 2: Confirmed Profile Facts
                item {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Confirmed Facts (${uiState.confirmedFacts.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (uiState.confirmedFacts.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = viewMode == ProfileViewMode.ACCORDION,
                                        onClick = { viewMode = ProfileViewMode.ACCORDION },
                                        label = { Text("Grouped") }
                                    )
                                    FilterChip(
                                        selected = viewMode == ProfileViewMode.FLAT,
                                        onClick = { viewMode = ProfileViewMode.FLAT },
                                        label = { Text("All Facts") }
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.confirmedFacts.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Person,
                            title = "No confirmed profile facts",
                            description = "Your profile facts serve as the zero-fabrication ground truth for placement eligibility checks and resume tailoring.",
                            actionLabel = "Add Profile Fact",
                            actionIcon = Icons.Default.Add,
                            onAction = {
                                defaultFieldHint = ""
                                showAddDialog = true
                            },
                            tip = "Zero-fabrication: Elixir strictly refuses to fabricate or hallucinate unconfirmed facts."
                        )
                    }
                } else if (viewMode == ProfileViewMode.ACCORDION) {
                    val grouped = uiState.confirmedFacts.groupBy { categorizeProfileFact(it.field) }

                    for (category in ProfileFactCategory.values()) {
                        val categoryFacts = grouped[category] ?: emptyList()
                        val isExpanded = expandedCategories.contains(category)

                        item(key = "category_${category.name}") {
                            CategoryAccordionCard(
                                category = category,
                                facts = categoryFacts,
                                isExpanded = isExpanded,
                                onToggle = {
                                    expandedCategories = if (isExpanded) {
                                        expandedCategories - category
                                    } else {
                                        expandedCategories + category
                                    }
                                },
                                onEditFact = { editingFact = it },
                                onAddFactForCategory = {
                                    defaultFieldHint = when (category) {
                                        ProfileFactCategory.PERSONAL -> "phone"
                                        ProfileFactCategory.ACADEMICS -> "cgpa"
                                        ProfileFactCategory.EXPERIENCE -> "projects"
                                        ProfileFactCategory.SKILLS -> "skills"
                                    }
                                    showAddDialog = true
                                }
                            )
                        }
                    }
                } else {
                    items(uiState.confirmedFacts, key = { it.id }) { fact ->
                        ConfirmedFactCard(
                            fact = fact,
                            modifier = Modifier.animateItem(),
                            onEdit = { editingFact = fact }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(64.dp))
                }
            }
        }
    }

    // Dialog for adding a new fact
    if (showAddDialog) {
        var fieldName by remember { mutableStateOf(defaultFieldHint) }
        var fieldValue by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Confirmed Fact") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fieldName,
                        onValueChange = { fieldName = it },
                        label = { Text("Field (e.g. cgpa, skills, degree)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fieldValue,
                        onValueChange = { fieldValue = it },
                        label = { Text("Value") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (fieldName.isNotBlank() && fieldValue.isNotBlank()) {
                            viewModel.addFact(fieldName.trim(), fieldValue.trim())
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog for editing an existing fact
    editingFact?.let { fact ->
        var editedValue by remember { mutableStateOf(fact.value) }

        AlertDialog(
            onDismissRequest = { editingFact = null },
            title = { Text("Edit ${fact.field}") },
            text = {
                OutlinedTextField(
                    value = editedValue,
                    onValueChange = { editedValue = it },
                    label = { Text("Value") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editedValue.isNotBlank()) {
                            viewModel.editFact(fact, editedValue.trim())
                            editingFact = null
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingFact = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Wipe confirmation dialog
    if (showWipeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmDialog = false },
            title = { Text("Wipe Profile Data") },
            text = { Text("Are you sure you want to completely erase all confirmed facts and profile storage? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.fullWipe()
                        showWipeConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Wipe All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CategoryAccordionCard(
    category: ProfileFactCategory,
    facts: List<ProfileFact>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onEditFact: (ProfileFact) -> Unit,
    onAddFactForCategory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val elixirColors = MaterialTheme.elixirColors
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "accordion_rotation"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (category) {
                                    ProfileFactCategory.PERSONAL -> Icons.Default.Person
                                    ProfileFactCategory.ACADEMICS -> Icons.Default.Star
                                    ProfileFactCategory.EXPERIENCE -> Icons.Default.Build
                                    ProfileFactCategory.SKILLS -> Icons.Default.Check
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (facts.isNotEmpty()) elixirColors.infoContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "${facts.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (facts.isNotEmpty()) elixirColors.onInfoContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = category.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(rotationState)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    if (facts.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "No ${category.title.lowercase()} facts yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = onAddFactForCategory) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add")
                            }
                        }
                    } else {
                        facts.forEach { fact ->
                            ConfirmedFactCard(
                                fact = fact,
                                onEdit = { onEditFact(fact) }
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ProposalDiffCard(
    proposal: ProfileFactProposal,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = proposal.proposed.field.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                AssistChip(
                    onClick = {},
                    label = { Text("Proposed (${proposal.proposed.source.name})") }
                )
            }

            // Current vs Proposed Diff
            if (proposal.current != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Current: ${proposal.current.value}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val elixirColors = MaterialTheme.elixirColors
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(elixirColors.successContainer)
                    .padding(8.dp)
            ) {
                Text(
                    text = "Proposed: ${proposal.proposed.value}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = elixirColors.onSuccessContainer
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onReject) {
                    Icon(Icons.Default.Close, contentDescription = "Reject", tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reject", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onApprove) {
                    Icon(Icons.Default.Check, contentDescription = "Approve")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve")
                }
            }
        }
    }
}

@Composable
fun ConfirmedFactCard(
    fact: ProfileFact,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = fact.field.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Confirmed",
                        tint = MaterialTheme.elixirColors.success,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = fact.value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Fact")
            }
        }
    }
}
