package com.cellclaw.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cellclaw.agent.PermissionProfile
import com.cellclaw.agent.ToolApprovalPolicy
import com.cellclaw.service.overlay.OverlayService
import com.cellclaw.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val permissionProfile by viewModel.permissionProfile.collectAsState()
    val activeProvider by viewModel.activeProvider.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val model by viewModel.model.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val autoStartOnBoot by viewModel.autoStartOnBoot.collectAsState()
    val policies by viewModel.policies.collectAsState()
    val voiceEnabled by viewModel.voiceEnabled.collectAsState()
    val autoSpeakResponses by viewModel.autoSpeakResponses.collectAsState()
    val overlayEnabled by viewModel.overlayEnabled.collectAsState()
    val maxIterations by viewModel.maxIterations.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val oneClickSubmitEnabled by viewModel.oneClickSubmitEnabled.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance / Theme Section
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Theme Mode", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Choose between System default, Light theme, or Dark theme.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (modeKey, modeLabel) ->
                            FilterChip(
                                selected = themeMode == modeKey,
                                onClick = { viewModel.setThemeMode(modeKey) },
                                label = { Text(modeLabel) },
                                leadingIcon = {
                                    if (themeMode == modeKey) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Placement Automation Section
            Text("Placement Automation", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("One-Click Supervised Submission", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "When enabled, tapping Approve on the Review screen performs form-fill, attachment verification, and user-supervised submission in one smooth flow.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = oneClickSubmitEnabled,
                            onCheckedChange = { viewModel.setOneClickSubmitEnabled(it) }
                        )
                    }
                }
            }

            // Provider section
            Text("AI Provider", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    for (provider in providers) {
                        val isActive = activeProvider == provider.type

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.switchProvider(provider.type) }
                            ) {
                                Text(
                                    provider.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    if (provider.hasKey) "API key configured"
                                    else "No API key (tap to configure)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (provider.hasKey)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isActive) {
                                    Icon(
                                        Icons.Default.Check,
                                        "Active",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                RadioButton(
                                    selected = isActive,
                                    onClick = { viewModel.switchProvider(provider.type) }
                                )
                            }
                        }

                        if (isActive || !provider.hasKey) {
                            ApiKeyInput(
                                providerType = provider.type,
                                hasKey = provider.hasKey,
                                onSave = { key -> viewModel.saveApiKey(provider.type, key) },
                                onRemove = { viewModel.removeApiKey(provider.type) }
                            )
                        }

                        if (provider != providers.last()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }

            // Model section
            Text("Model", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (availableModels.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            OutlinedTextField(
                                value = model,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Model") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                availableModels.forEach { modelOption ->
                                    DropdownMenuItem(
                                        text = { Text(modelOption) },
                                        onClick = {
                                            viewModel.setModel(modelOption)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        var modelInput by remember { mutableStateOf(model) }
                        OutlinedTextField(
                            value = modelInput,
                            onValueChange = { modelInput = it },
                            label = { Text("Model name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        if (modelInput != model) {
                            TextButton(onClick = { viewModel.setModel(modelInput) }) {
                                Text("Save")
                            }
                        }
                    }
                }
            }

            // User section
            Text("User", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    var nameInput by remember { mutableStateOf(userName) }
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Your Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (nameInput != userName) {
                        TextButton(onClick = { viewModel.setUserName(nameInput) }) {
                            Text("Save")
                        }
                    }
                }
            }

            // Agent section
            Text("Agent", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    var iterInput by remember { mutableStateOf(if (maxIterations == 0) "" else maxIterations.toString()) }
                    Column {
                        Text("Max iterations", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Limit how many turns the agent can take. Leave empty for unlimited.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = iterInput,
                            onValueChange = { value ->
                                iterInput = value.filter { it.isDigit() }
                            },
                            label = { Text("Max iterations") },
                            placeholder = { Text("Unlimited") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        val parsed = iterInput.toIntOrNull() ?: 0
                        if (parsed != maxIterations) {
                            TextButton(onClick = { viewModel.setMaxIterations(parsed) }) {
                                Text("Save")
                            }
                        }
                    }
                }
            }

            // Service section
            Text("Service", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Start on boot")
                        Switch(
                            checked = autoStartOnBoot,
                            onCheckedChange = { viewModel.setAutoStartOnBoot(it) }
                        )
                    }
                }
            }

            // Overlay section
            Text("Overlay", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Floating overlay")
                            Text(
                                "Draggable bubble for quick actions over other apps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = overlayEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !Settings.canDrawOverlays(context)) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    viewModel.setOverlayEnabled(enabled)
                                    if (enabled) {
                                        context.startForegroundService(
                                            Intent(context, OverlayService::class.java)
                                        )
                                    } else {
                                        context.stopService(
                                            Intent(context, OverlayService::class.java)
                                        )
                                    }
                                }
                            }
                        )
                    }
                    if (overlayEnabled && !Settings.canDrawOverlays(context)) {
                        Text(
                            "Overlay permission required. Tap the toggle to grant.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Voice section
            Text("Voice", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable voice input")
                        Switch(
                            checked = voiceEnabled,
                            onCheckedChange = { viewModel.setVoiceEnabled(it) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Auto-speak responses")
                        Switch(
                            checked = autoSpeakResponses,
                            onCheckedChange = { viewModel.setAutoSpeakResponses(it) }
                        )
                    }
                }
            }


            // Permission profile
            Text("Permission Profile", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    for (profile in PermissionProfile.entries) {
                        val isActive = permissionProfile == profile
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    profile.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    profile.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = isActive,
                                onClick = { viewModel.setPermissionProfile(profile) }
                            )
                        }
                    }
                }
            }

            // Approval policies
            Text("Tool Approval Policies", style = MaterialTheme.typography.titleMedium)
            Text(
                "Fine-tune individual tools. Switching profiles above resets these.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    for ((tool, policy) in policies.entries.sortedBy { it.key }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                tool,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            PolicyChip(
                                policy = policy,
                                onToggle = { viewModel.togglePolicy(tool) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyInput(
    providerType: String,
    hasKey: Boolean,
    onSave: (String) -> Unit,
    onRemove: () -> Unit
) {
    var newKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(!hasKey) }

    val hintText = when (providerType) {
        "gemini" -> "Requires a Google AI Studio API key (AIzaSy... from aistudio.google.com/app/apikey)"
        "anthropic" -> "Requires an Anthropic API key (sk-ant-...)"
        "openai" -> "Requires an OpenAI API key (sk-...)"
        "openrouter" -> "Requires an OpenRouter API key (sk-or-...)"
        else -> "Enter API key for $providerType"
    }

    if (editing || !hasKey) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = hintText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    label = { Text("API Key") },
                    leadingIcon = { Icon(Icons.Default.Key, null) },
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                "Toggle"
                            )
                        }
                    },
                    visualTransformation = if (showKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = {
                        if (newKey.isNotBlank()) {
                            onSave(newKey.trim())
                            newKey = ""
                            editing = false
                        }
                    },
                    enabled = newKey.trim().length >= 8
                ) {
                    Icon(Icons.Default.Check, "Save", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { editing = true }) {
                Text("Change Key")
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PolicyChip(policy: ToolApprovalPolicy, onToggle: () -> Unit) {
    FilterChip(
        selected = policy == ToolApprovalPolicy.AUTO,
        onClick = onToggle,
        label = {
            Text(
                when (policy) {
                    ToolApprovalPolicy.AUTO -> "Auto"
                    ToolApprovalPolicy.ASK -> "Ask"
                    ToolApprovalPolicy.DENY -> "Deny"
                }
            )
        }
    )
}
