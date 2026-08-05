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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.provider.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chomu.aiagent.domain.model.AgentState
import com.chomu.aiagent.domain.model.Message
import com.chomu.aiagent.ui.components.*
import com.chomu.aiagent.ui.theme.*
import com.chomu.aiagent.ui.viewmodel.ChatViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showChatPanel by remember { mutableStateOf(true) }
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val text = words?.firstOrNull()
        if (!text.isNullOrBlank()) viewModel.sendMessage(text)
        viewModel.setVoiceListening(false)
    }

    val accentColor = when (uiState.agentState) {
        AgentState.IDLE      -> Color(0xFF4A90D9)
        AgentState.LISTENING -> Color(0xFF00BFFF)
        AgentState.TALKING   -> Color(0xFF00E676)
        AgentState.WORKING   -> Color(0xFFFF9800)
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── 1. Dark gradient base ──────────────────────────────────────
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF080C1A), Color(0xFF020408)),
                    radius = 1800f
                )
            )
        )

        // ── 2. Holographic network canvas ─────────────────────────────
        HolographicBackground(Modifier.fillMaxSize(), accentColor = accentColor)

        // ── 3. Full-screen 3D companion ───────────────────────────────
        CompanionViewer(
            agentState = uiState.agentState,
            modifier = Modifier.fillMaxSize(),
            pendingAnimJson = uiState.pendingAnimJson
        )

        // ── 4. Top bar ────────────────────────────────────────────────
        HoloTopBar(
            agentState = uiState.agentState,
            accentColor = accentColor,
            onNewChat = { viewModel.newChat() },
            onSettings = onNavigateToSettings,
            onBubble = { viewModel.startFloatingBubble() },
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
        )

        // ── 5. Animation state panel (left side) ─────────────────────
        AnimationStatePanel(
            currentState = uiState.agentState,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp)
        )

        // ── 6. Right floating buttons (voice + chat toggle) ───────────
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HoloFab(
                icon = if (uiState.isVoiceListening) Icons.Rounded.MicNone else Icons.Rounded.Mic,
                tint = if (uiState.isVoiceListening) Color.White else accentColor,
                containerColor = if (uiState.isVoiceListening) accentColor else accentColor.copy(0.15f),
                borderColor = accentColor,
                onClick = {
                    if (micPermission.status.isGranted) {
                        viewModel.setVoiceListening(true)
                        speechLauncher.launch(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to CHOMU...")
                            }
                        )
                    } else {
                        micPermission.launchPermissionRequest()
                    }
                }
            )
            HoloFab(
                icon = if (showChatPanel) Icons.Rounded.ChatBubble else Icons.Rounded.ChatBubbleOutline,
                tint = accentColor,
                containerColor = accentColor.copy(if (showChatPanel) 0.25f else 0.12f),
                borderColor = accentColor,
                onClick = { showChatPanel = !showChatPanel },
                badge = if (uiState.messages.isNotEmpty()) uiState.messages.size else null
            )
        }

        // ── 7. Chat overlay panel (bottom right) ──────────────────────
        AnimatedVisibility(
            visible = showChatPanel,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 72.dp, bottom = 20.dp)
                .navigationBarsPadding()
        ) {
            ChatOverlayPanel(
                messages = uiState.messages,
                inputText = uiState.inputText,
                isLoading = uiState.isLoading,
                error = uiState.error,
                accentColor = accentColor,
                onTextChange = viewModel::onInputChange,
                onSend = { viewModel.sendMessage() },
                onClose = { showChatPanel = false },
                onReplay = { viewModel.replayMessage(it) }
            )
        }

        // ── 8. Working mode stop button (bottom center) ───────────────
        AnimatedVisibility(
            visible = uiState.agentState == AgentState.WORKING,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            OutlinedButton(
                onClick = { viewModel.stopTask() },
                border = BorderStroke(1.dp, Color(0xFFFF5252)),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
            ) {
                Icon(Icons.Rounded.Stop, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("EMERGENCY STOP", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar — minimal transparent sci-fi HUD style
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HoloTopBar(
    agentState: AgentState,
    accentColor: Color,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
    onBubble: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // CHOMU branding
        Column(Modifier.weight(1f)) {
            Text(
                "CHOMU",
                style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 3.sp),
                color = accentColor
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).background(accentColor, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(
                    agentState.name,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = Color.White.copy(0.5f)
                )
            }
        }
        // Icon actions — borderless
        HoloIconBtn(Icons.Rounded.AddComment, "New Chat", accentColor, onClick = onNewChat)
        Spacer(Modifier.width(2.dp))
        HoloIconBtn(Icons.Rounded.BubbleChart, "Float", accentColor, onClick = onBubble)
        Spacer(Modifier.width(2.dp))
        HoloIconBtn(Icons.Rounded.Settings, "Settings", accentColor, onClick = onSettings)
    }
}

@Composable
private fun HoloIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = Color.White.copy(0.6f), modifier = Modifier.size(18.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Left: animation state panel
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AnimationStatePanel(
    currentState: AgentState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "STATES.",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 2.sp
            ),
            color = Color.White.copy(0.35f),
            modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
        )
        AgentState.entries.forEach { state ->
            AnimStateCard(state = state, isActive = state == currentState)
        }
    }
}

@Composable
private fun AnimStateCard(state: AgentState, isActive: Boolean) {
    val color = when (state) {
        AgentState.IDLE      -> Color(0xFF4A90D9)
        AgentState.LISTENING -> Color(0xFF00BFFF)
        AgentState.TALKING   -> Color(0xFF00E676)
        AgentState.WORKING   -> Color(0xFFFF9800)
    }
    // Sci-fi silhouette symbols for each state
    val symbol = when (state) {
        AgentState.IDLE      -> "⬡"
        AgentState.LISTENING -> "◎"
        AgentState.TALKING   -> "◈"
        AgentState.WORKING   -> "⊛"
    }
    val pose = when (state) {
        AgentState.IDLE      -> "IDLE"
        AgentState.LISTENING -> "LISTEN"
        AgentState.TALKING   -> "TALK"
        AgentState.WORKING   -> "WORK"
    }

    val borderAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.9f else 0.2f,
        animationSpec = tween(300), label = "border"
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.22f else 0.07f,
        animationSpec = tween(300), label = "bg"
    )
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(bgAlpha),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 0.7.dp,
            color = color.copy(borderAlpha)
        ),
        modifier = Modifier.width(72.dp).height(80.dp).scale(scale)
    ) {
        Column(
            Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: state indicator dot
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .size(5.dp)
                        .background(color.copy(if (isActive) 1f else 0.3f), CircleShape)
                )
            }
            // Center: large symbol
            Text(
                symbol,
                style = MaterialTheme.typography.titleLarge,
                color = color.copy(if (isActive) 1f else 0.5f)
            )
            // Bottom: label
            Text(
                pose,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = color.copy(if (isActive) 0.9f else 0.4f),
                maxLines = 1
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Floating action button — large double-ring sci-fi style (matches reference)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HoloFab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    containerColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    badge: Int? = null
) {
    val inf = rememberInfiniteTransition(label = "fab_pulse")
    val pulse by inf.animateFloat(
        initialValue = 0.85f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "fab_p"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer decorative ring
        Box(
            Modifier
                .size(90.dp)
                .background(Color.Transparent, CircleShape)
                .border(0.8.dp, borderColor.copy(0.25f * pulse), CircleShape)
        )
        // Middle ring
        Box(
            Modifier
                .size(78.dp)
                .background(Color.Transparent, CircleShape)
                .border(1.2.dp, borderColor.copy(0.5f * pulse), CircleShape)
        )
        // Inner filled button
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = containerColor,
            border = BorderStroke(2.dp, borderColor.copy(0.85f)),
            modifier = Modifier.size(62.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
            }
        }
        // Badge
        if (badge != null && badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(18.dp)
                    .background(borderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    badge.coerceAtMost(99).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chat overlay panel (reference image "CHANNEL: ALL" style)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ChatOverlayPanel(
    messages: List<Message>,
    inputText: String,
    isLoading: Boolean,
    error: String?,
    accentColor: Color,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
    onReplay: (String) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF060A14).copy(alpha = 0.92f),
        border = BorderStroke(1.dp, accentColor.copy(0.45f)),
        modifier = Modifier
            .width(290.dp)
            .heightIn(min = 200.dp, max = 440.dp),
        tonalElevation = 8.dp
    ) {
        Column {
            // Header: CHANNEL: ALL
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(accentColor.copy(0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(accentColor, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "CHANNEL: ALL",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp), tint = DarkOnSurface.copy(0.5f))
                }
            }

            // Messages list
            if (messages.isEmpty() && !isLoading) {
                Box(
                    Modifier.fillMaxWidth().weight(1f).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✦", color = accentColor, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Hi! I'm CHOMU", color = DarkOnSurface, style = MaterialTheme.typography.bodySmall)
                        Text("Chat with me or automate your phone", color = DarkOnSurface.copy(0.5f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        CompactChatBubble(
                            message = msg,
                            accentColor = accentColor,
                            onReplay = if (!msg.isUser) {{ onReplay(msg.content) }} else null
                        )
                    }
                    if (isLoading) {
                        item { Row(Modifier.padding(start = 8.dp)) { TypingIndicator() } }
                    }
                }
            }

            // Error
            AnimatedVisibility(visible = error != null) {
                val ctx = LocalContext.current
                error?.let { errMsg ->
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(
                            errMsg,
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkError
                        )
                        if (errMsg.contains("Accessibility", ignoreCase = true)) {
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                onClick = {
                                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    })
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFF5252).copy(0.15f),
                                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(0.5f))
                            ) {
                                Text(
                                    "Open Accessibility Settings →",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF5252),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Input bar
            HorizontalDivider(color = accentColor.copy(0.2f), thickness = 0.5.dp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text("Type message...", color = DarkOnSurface.copy(0.3f), style = MaterialTheme.typography.labelSmall)
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor.copy(0.7f),
                        unfocusedBorderColor = DarkOutline.copy(0.3f),
                        focusedContainerColor = DarkSurfaceVariant.copy(0.4f),
                        unfocusedContainerColor = DarkSurfaceVariant.copy(0.2f),
                        focusedTextColor = DarkOnSurface,
                        unfocusedTextColor = DarkOnSurface,
                        cursorColor = accentColor
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { onSend() })
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    onClick = onSend,
                    shape = RoundedCornerShape(10.dp),
                    color = if (inputText.isNotBlank() && !isLoading) accentColor.copy(0.25f) else Color.Transparent,
                    border = BorderStroke(1.dp, accentColor.copy(if (inputText.isNotBlank()) 0.8f else 0.2f)),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isLoading) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = accentColor, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Send, null, tint = accentColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Compact chat bubble for overlay
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CompactChatBubble(
    message: Message,
    accentColor: Color,
    onReplay: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!message.isUser) {
            Text("✦ ", color = accentColor.copy(0.7f), style = MaterialTheme.typography.labelSmall)
        }
        Column(
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 220.dp)
        ) {
            Box(
                Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (message.isUser) 10.dp else 3.dp,
                            topEnd = if (message.isUser) 3.dp else 10.dp,
                            bottomStart = 10.dp, bottomEnd = 10.dp
                        )
                    )
                    .background(
                        if (message.isUser) accentColor.copy(0.25f)
                        else DarkSurfaceVariant.copy(0.55f)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isError) DarkError else DarkOnSurface.copy(0.95f),
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!message.isUser && onReplay != null) {
                IconButton(
                    onClick = onReplay,
                    modifier = Modifier.size(24.dp).padding(start = 2.dp)
                ) {
                    Icon(
                        Icons.Rounded.PlayCircleOutline, "Replay",
                        tint = accentColor.copy(0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
