package com.chomu.aiagent.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chomu.aiagent.domain.model.ApiProvider
import com.chomu.aiagent.ui.theme.*
import com.chomu.aiagent.ui.viewmodel.SettingsViewModel
import com.chomu.aiagent.ui.viewmodel.defaultGeminiModels
import com.chomu.aiagent.ui.viewmodel.defaultNvidiaModels

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showGeminiKey by remember { mutableStateOf(false) }
    var showNvidiaKey by remember { mutableStateOf(false) }

    LaunchedEffect(state.savedSuccess) {
        if (state.savedSuccess) {
            kotlinx.coroutines.delay(2000)
            viewModel.dismissSavedBanner()
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(DarkBackground, Color(0xFF050810)))
        )
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = DarkOnSurface)
                }
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = DarkOnSurface
                )
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // API Provider selection
                SettingsCard(title = "AI Provider") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProviderChip(
                            label = "Gemini",
                            selected = state.apiProvider == ApiProvider.GEMINI,
                            onClick = { viewModel.update { copy(apiProvider = ApiProvider.GEMINI) } },
                            modifier = Modifier.weight(1f)
                        )
                        ProviderChip(
                            label = "NVIDIA NIM",
                            selected = state.apiProvider == ApiProvider.NVIDIA_NIM,
                            onClick = { viewModel.update { copy(apiProvider = ApiProvider.NVIDIA_NIM) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Gemini configuration
                AnimatedVisibility(visible = state.apiProvider == ApiProvider.GEMINI) {
                    SettingsCard(title = "Gemini Configuration") {
                        ApiKeyField(
                            label = "Gemini API Key",
                            value = state.geminiApiKey,
                            visible = showGeminiKey,
                            onValueChange = { viewModel.update { copy(geminiApiKey = it) } },
                            onToggleVisible = { showGeminiKey = !showGeminiKey }
                        )
                        Spacer(Modifier.height(12.dp))
                        ModelDropdown(
                            label = "Model",
                            selected = state.geminiModel,
                            options = state.availableGeminiModels.ifEmpty { defaultGeminiModels },
                            onSelect = { viewModel.update { copy(geminiModel = it) } }
                        )
                        Spacer(Modifier.height(8.dp))
                        FetchModelsButton(
                            onClick = viewModel::fetchModels,
                            isLoading = state.isFetchingModels
                        )
                    }
                }

                // NVIDIA NIM configuration
                AnimatedVisibility(visible = state.apiProvider == ApiProvider.NVIDIA_NIM) {
                    SettingsCard(title = "NVIDIA NIM Configuration") {
                        ApiKeyField(
                            label = "NVIDIA API Key",
                            value = state.nvidiaApiKey,
                            visible = showNvidiaKey,
                            onValueChange = { viewModel.update { copy(nvidiaApiKey = it) } },
                            onToggleVisible = { showNvidiaKey = !showNvidiaKey }
                        )
                        Spacer(Modifier.height(12.dp))
                        SettingsTextField(
                            label = "Base URL",
                            value = state.nvidiaBaseUrl,
                            onValueChange = { viewModel.update { copy(nvidiaBaseUrl = it) } }
                        )
                        Spacer(Modifier.height(12.dp))
                        ModelDropdown(
                            label = "Model",
                            selected = state.nvidiaModel,
                            options = state.availableNvidiaModels.ifEmpty { defaultNvidiaModels },
                            onSelect = { viewModel.update { copy(nvidiaModel = it) } }
                        )
                        Spacer(Modifier.height(8.dp))
                        FetchModelsButton(
                            onClick = viewModel::fetchModels,
                            isLoading = state.isFetchingModels
                        )
                    }
                }

                // Generation parameters
                SettingsCard(title = "Generation Parameters") {
                    Text(
                        "Temperature: ${"%.2f".format(state.temperature)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurface.copy(0.7f)
                    )
                    Slider(
                        value = state.temperature,
                        onValueChange = { viewModel.update { copy(temperature = it) } },
                        valueRange = 0f..2f,
                        colors = SliderDefaults.colors(thumbColor = DarkPrimary, activeTrackColor = DarkPrimary)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Max Tokens: ${state.maxTokens}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurface.copy(0.7f)
                    )
                    Slider(
                        value = state.maxTokens.toFloat(),
                        onValueChange = { viewModel.update { copy(maxTokens = it.toInt()) } },
                        valueRange = 256f..8192f,
                        steps = 30,
                        colors = SliderDefaults.colors(thumbColor = DarkPrimary, activeTrackColor = DarkPrimary)
                    )
                }

                // System prompt
                SettingsCard(title = "System Prompt") {
                    OutlinedTextField(
                        value = state.systemPrompt,
                        onValueChange = { viewModel.update { copy(systemPrompt = it) } },
                        placeholder = {
                            Text(
                                "Leave blank for default...",
                                color = DarkOnSurface.copy(0.4f)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        maxLines = 8,
                        shape = RoundedCornerShape(12.dp),
                        colors = settingsTextFieldColors()
                    )
                }

                // Error display
                state.error?.let { err ->
                    Surface(
                        color = DarkError.copy(0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            err,
                            color = DarkError,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            // Save button
            Box(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
            ) {
                Button(
                    onClick = viewModel::saveSettings,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary)
                ) {
                    AnimatedContent(targetState = state.savedSuccess, label = "save_btn") { saved ->
                        if (saved) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Check, null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Saved!", color = Color.White)
                            }
                        } else {
                            Text("Save Settings", color = Color.White, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = DarkSurface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = DarkPrimary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ProviderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) DarkPrimary else DarkSurfaceVariant,
        border = if (!selected) BorderStroke(1.dp, DarkOutline) else null
    ) {
        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color.White else DarkOnSurface.copy(0.7f)
            )
        }
    }
}

@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    visible: Boolean,
    onValueChange: (String) -> Unit,
    onToggleVisible: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = DarkOnSurface.copy(0.6f)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        shape = RoundedCornerShape(12.dp),
        colors = settingsTextFieldColors(),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    null,
                    tint = DarkOnSurface.copy(0.5f)
                )
            }
        }
    )
}

@Composable
private fun SettingsTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = DarkOnSurface.copy(0.6f)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = settingsTextFieldColors()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = DarkOnSurface.copy(0.6f)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            shape = RoundedCornerShape(12.dp),
            colors = settingsTextFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DarkSurface)
        ) {
            options.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model, color = DarkOnSurface, style = MaterialTheme.typography.bodySmall) },
                    onClick = { onSelect(model); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun FetchModelsButton(onClick: () -> Unit, isLoading: Boolean) {
    TextButton(onClick = onClick, enabled = !isLoading) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = DarkPrimary)
            Spacer(Modifier.width(6.dp))
        }
        Text("Fetch available models", color = DarkPrimary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun settingsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DarkPrimary,
    unfocusedBorderColor = DarkOutline,
    focusedContainerColor = DarkSurfaceVariant,
    unfocusedContainerColor = DarkSurfaceVariant,
    cursorColor = DarkPrimary,
    focusedTextColor = DarkOnSurface,
    unfocusedTextColor = DarkOnSurface
)
