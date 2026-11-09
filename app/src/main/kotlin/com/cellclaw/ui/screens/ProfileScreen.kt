package com.cellclaw.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellclaw.profile.ExtractedProfileData
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var showPasteResumeDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)
            val fileName = queryFileName(context, uri)
            val bytes = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) {
                null
            }
            if (bytes != null && bytes.isNotEmpty()) {
                viewModel.importResumeFromBytes(bytes, mimeType, fileName)
            } else {
                Toast.makeText(context, "Could not open or read the selected file", Toast.LENGTH_LONG).show()
            }
        }
    }

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
                title = { Text("Placement Profile", fontWeight = FontWeight.Bold) },
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
            FloatingActionButton(
                onClick = {
                    defaultFieldHint = ""
                    showAddDialog = true
                },
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Fact")
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Dialog(onDismissRequest = {}) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = CircleShape
                               ),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(52.dp),
                                strokeWidth = 3.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Text(
                            text = "AI Resume Engine Active",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = uiState.message ?: "Extracting candidate profile facts and verifying integrity...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 00: Hero Candidate Identity & Readiness Meter
            item(key = "hero_candidate_identity") {
                val candidateName = uiState.profile?.name?.value ?: "Candidate Profile"
                val candidateDegree = uiState.profile?.degree?.value ?: "Placement Ready"
                val candidateBranch = uiState.profile?.branch?.value
                val initials = candidateName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").ifBlank { "CP" }

                val keyFields = listOfNotNull(
                    uiState.profile?.name,
                    uiState.profile?.email,
                    uiState.profile?.phone,
                    uiState.profile?.college,
                    uiState.profile?.degree,
                    uiState.profile?.branch,
                    uiState.profile?.cgpa
                )
                val completeness = (keyFields.size.toFloat() / 7f).coerceIn(0f, 1f)

                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                                        ),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = candidateName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (candidateBranch != null) "$candidateDegree • $candidateBranch" else candidateDegree,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Profile Readiness Meter
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Profile Completeness",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${(completeness * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (completeness >= 0.8f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            LinearProgressIndicator(
                                progress = { completeness },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = if (completeness >= 0.8f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            // Section 0A: AI Resume Auto-Setup Card (Bento Style)
            item(key = "resume_auto_setup") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "AI Resume Setup",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Auto-extract your verified details with zero typing",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    documentPicker.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "text/plain",
                                            "*/*"
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Upload Resume", fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = { showPasteResumeDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text("Paste Text")
                            }
                        }
                    }
                }
            }

            // Section 0B: AI Extracted Profile Preview (when resume is analyzed)
            if (uiState.extractedData != null) {
                val data = uiState.extractedData!!
                item(key = "extracted_preview") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "Extracted Profile Preview",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        "AI Scanned",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Text(
                                text = data.name ?: "Name not detected",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Email", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(data.email ?: "Not specified", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Phone", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(data.phone ?: "Not specified", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Education", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${data.degree ?: "Degree"} in ${data.branch ?: "Branch"}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("CGPA / Score", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${data.cgpa ?: "—"} / ${data.cgpaScale ?: "10.0"}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            if (data.skills.isNotEmpty()) {
                                Text("Extracted Skills (${data.skills.size}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    data.skills.take(15).forEach { skill ->
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(skill, style = MaterialTheme.typography.bodySmall) },
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                            }

                            if (data.missingEssentialFields.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                                        Text(
                                            "Missing placement fields: ${data.missingEssentialFields.joinToString(", ")}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.confirmExtractedProfile(data) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Check, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Confirm & Apply Profile")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.dismissExtractedData() },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }

            // Section 0C: Missing Placement Facts Checklist (if regular profile is missing facts)
                if (uiState.extractedData == null && uiState.missingEssentialFields.isNotEmpty() && uiState.confirmedFacts.isNotEmpty()) {
                    item(key = "missing_fields_checklist") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.tertiary)
                                    Text(
                                        "Placement Readiness Notice",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "Your profile is missing: ${uiState.missingEssentialFields.joinToString(", ")}. Tap below to quickly complete:",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    uiState.missingEssentialFields.forEach { missingField ->
                                        val fieldKey = when {
                                            missingField.contains("Name") -> "name"
                                            missingField.contains("Email") -> "email"
                                            missingField.contains("Phone") -> "phone"
                                            missingField.contains("Degree") -> "degree"
                                            missingField.contains("Branch") -> "branch"
                                            missingField.contains("Year") -> "graduationYear"
                                            missingField.contains("CGPA") -> "cgpa"
                                            else -> "skill"
                                        }
                                        AssistChip(
                                            onClick = {
                                                defaultFieldHint = fieldKey
                                                showAddDialog = true
                                            },
                                            label = { Text("+ Add $missingField") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

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

    // Paste resume dialog
    if (showPasteResumeDialog) {
        PasteResumeDialog(
            onDismiss = { showPasteResumeDialog = false },
            onConfirm = { text ->
                viewModel.importResumeFromText(text)
                showPasteResumeDialog = false
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

@Composable
fun PasteResumeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste Resume Text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste your resume text below. Elixir's AI will parse contact details, degree, GPA, and skills automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Resume Text") },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    maxLines = 15
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) onConfirm(text.trim())
                },
                enabled = text.isNotBlank()
            ) {
                Text("Process with AI")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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

