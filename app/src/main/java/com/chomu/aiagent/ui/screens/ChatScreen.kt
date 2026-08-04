package com.chomu.aiagent.ui.screens

import android.Manifest
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chomu.aiagent.domain.model.AgentState
import com.chomu.aiagent.ui.components.*
import com.chomu.aiagent.ui.theme.*
import com.chomu.aiagent.ui.viewmodel.ChatViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val text = words?.firstOrNull()
        if (!text.isNullOrBlank()) {
            viewModel.sendMessage(text)
        }
        viewModel.setVoiceListening(false)
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBackground, Color(0xFF050810), DarkBackground)
                )
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top bar with companion viewer
            ChatTopBar(
                agentState = uiState.agentState,
                onSettingsClick = onNavigateToSettings,
                onClearClick = { viewModel.clearHistory() },
                onBubbleClick = { viewModel.startFloatingBubble() }
            )

            // Companion 3D viewer (mini version in chat)
            AnimatedVisibility(
                visible = uiState.agentState != AgentState.IDLE || uiState.messages.isEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                CompanionViewer(
                    agentState = uiState.agentState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (uiState.messages.isEmpty()) {
                    item { WelcomeHints(onHintClick = { viewModel.sendMessage(it) }) }
                }
                items(uiState.messages, key = { it.id }) { message ->
                    ChatBubble(message = message)
                }
                if (uiState.isLoading) {
                    item {
                        Row(Modifier.padding(start = 16.dp, top = 4.dp)) {
                            TypingIndicator()
                        }
                    }
                }
            }

            // Error banner
            AnimatedVisibility(visible = uiState.error != null) {
                uiState.error?.let { err ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        color = DarkError.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = err,
                            color = DarkError,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Input bar
            ChatInputBar(
                text = uiState.inputText,
                onTextChange = viewModel::onInputChange,
                onSend = { viewModel.sendMessage() },
                onVoice = {
                    if (micPermission.status.isGranted) {
                        viewModel.setVoiceListening(true)
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to CHOMU...")
                        }
                        speechLauncher.launch(intent)
                    } else {
                        micPermission.launchPermissionRequest()
                    }
                },
                isLoading = uiState.isLoading,
                isListening = uiState.isVoiceListening
            )
        }
    }
}

@Composable
private fun ChatTopBar(
    agentState: AgentState,
    onSettingsClick: () -> Unit,
    onClearClick: () -> Unit,
    onBubbleClick: () -> Unit
) {
    val stateColor = when (agentState) {
        AgentState.IDLE -> GlowIdle
        AgentState.LISTENING -> GlowListening
        AgentState.TALKING -> GlowTalking
        AgentState.WORKING -> GlowWorking
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(stateColor.copy(0.15f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("✦", color = stateColor, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("CHOMU", style = MaterialTheme.typography.titleMedium, color = DarkOnSurface)
            Text(
                agentState.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = stateColor
            )
        }
        IconButton(onClick = onBubbleClick) {
            Icon(Icons.Rounded.BubbleChart, "Floating bubble", tint = DarkOnSurface.copy(0.7f))
        }
        IconButton(onClick = onClearClick) {
            Icon(Icons.Rounded.DeleteOutline, "Clear", tint = DarkOnSurface.copy(0.7f))
        }
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Rounded.Settings, "Settings", tint = DarkOnSurface.copy(0.7f))
        }
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    isLoading: Boolean,
    isListening: Boolean
) {
    val focusRequester = remember { FocusRequester() }

    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        color = DarkSurface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text("Ask CHOMU anything...", color = DarkOnSurface.copy(0.4f))
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkPrimary,
                    unfocusedBorderColor = DarkOutline,
                    focusedContainerColor = DarkSurfaceVariant,
                    unfocusedContainerColor = DarkSurfaceVariant,
                    cursorColor = DarkPrimary,
                    focusedTextColor = DarkOnSurface,
                    unfocusedTextColor = DarkOnSurface
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                trailingIcon = {
                    if (text.isNotBlank()) {
                        IconButton(onClick = onTextChange.let { { it("") } }) {
                            Icon(Icons.Rounded.Clear, "Clear", tint = DarkOnSurface.copy(0.5f))
                        }
                    }
                }
            )
            Spacer(Modifier.width(8.dp))

            // Voice button
            val micScale by animateFloatAsState(
                targetValue = if (isListening) 1.2f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "mic_scale"
            )
            FilledIconButton(
                onClick = onVoice,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isListening) GlowListening else DarkSurfaceVariant
                )
            ) {
                Icon(
                    if (isListening) Icons.Rounded.MicNone else Icons.Rounded.Mic,
                    "Voice",
                    tint = if (isListening) Color.White else DarkOnSurface.copy(0.7f)
                )
            }

            Spacer(Modifier.width(6.dp))

            // Send button
            FilledIconButton(
                onClick = onSend,
                enabled = text.isNotBlank() && !isLoading,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = DarkPrimary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Rounded.Send, "Send", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun WelcomeHints(onHintClick: (String) -> Unit) {
    val hints = listOf(
        "✨ Tell me about yourself",
        "📱 Open WhatsApp and message Mom",
        "🔍 Search Python tutorials on YouTube",
        "🎵 Play my favorite songs",
        "⏰ Set alarm for 7 AM tomorrow"
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Hi! I'm CHOMU ✦",
            style = MaterialTheme.typography.headlineSmall,
            color = DarkOnSurface
        )
        Text(
            "Your AI companion. Chat with me or let me control your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurface.copy(0.6f)
        )
        Spacer(Modifier.height(8.dp))
        hints.forEach { hint ->
            SuggestionChip(
                onClick = { onHintClick(hint.drop(2).trim()) },
                label = { Text(hint, style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = DarkSurfaceVariant,
                    labelColor = DarkOnSurface.copy(0.8f)
                )
            )
        }
    }
}
