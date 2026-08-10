package com.chomu.app.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.chomu.app.vm.ChatMessage
import com.chomu.app.vm.ChatViewModel
import com.chomu.app.vm.CompanionState
import kotlinx.coroutines.launch

private val Bg = Color(0xFF0A0A0F)
private val Accent = Color(0xFF7B61FF)
private val TextPrimary = Color(0xFFE8E8F0)
private val TextMuted = Color(0xFF888898)
private val CardBg = Color(0xCC131318)
private val UserBubble = Color(0xFF1A0D44)
private val AiBubble = Color(0xFF111120)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HomeScreen(vm: ChatViewModel, onSettings: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val messages = vm.messages
    val companionState = vm.state.value
    val loading = vm.isLoading.value

    var inputText by remember { mutableStateOf("") }
    var showMessages by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        // Full-screen 3D WebView
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccessFromFileURLs = true
                        allowUniversalAccessFromFileURLs = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }
                    webViewClient = WebViewClient()
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) =
                            request.grant(request.resources)
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface fun onModelLoaded() {}
                        @JavascriptInterface fun onViewerReady() {}
                        @JavascriptInterface fun onCameraFrame(b64: String) { vm.sendCameraFrame(b64) }
                        @JavascriptInterface fun log(msg: String) { android.util.Log.d("ChomViewer", msg) }
                    }, "ChomBridge")
                    loadUrl("file:///android_asset/html/viewer.html")
                    vm.webView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top bar
        TopBar(
            name = vm.prefs.companionName,
            state = companionState,
            onSettings = onSettings,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
        )

        // Chat button (top-left of bottom area)
        RoundBtn(
            icon = if (showMessages) Icons.Rounded.Chat else Icons.Rounded.ChatBubbleOutline,
            active = showMessages,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 90.dp),
            onClick = { showMessages = !showMessages }
        )

        // Messages overlay
        AnimatedVisibility(
            visible = showMessages && messages.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 80.dp, start = 12.dp, end = 12.dp, bottom = 160.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardBg)
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(messages) { msg -> MsgBubble(msg) }
                }
            }
        }

        // Bottom chat bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mic / stop button
                if (loading) {
                    RoundBtn(
                        icon = Icons.Rounded.Stop,
                        active = true,
                        onClick = { vm.stopSpeaking() }
                    )
                } else {
                    RoundBtn(
                        icon = Icons.Rounded.Mic,
                        active = false,
                        onClick = { /* STT placeholder */ }
                    )
                }

                // Text field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            if (loading) "Wait kar..." else "Kuch bolo...",
                            color = TextMuted, fontSize = 14.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedBorderColor = Accent.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                        cursorColor = Accent,
                    ),
                    enabled = !loading,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) {
                            vm.sendMessage(inputText); inputText = ""; keyboard?.hide()
                        }
                    }),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = TextPrimary)
                )

                // Send button
                RoundBtn(
                    icon = Icons.Rounded.Send,
                    active = inputText.isNotBlank() && !loading,
                    onClick = {
                        if (inputText.isNotBlank() && !loading) {
                            vm.sendMessage(inputText); inputText = ""; keyboard?.hide()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    name: String,
    state: CompanionState,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (stateText, stateColor) = when (state) {
        CompanionState.IDLE -> "Ready" to TextMuted
        CompanionState.THINKING -> "Thinking…" to Accent
        CompanionState.SPEAKING -> "Speaking" to Color(0xFF4CAF50)
        CompanionState.LISTENING -> "Listening…" to Color(0xFFFF6B6B)
        CompanionState.ERROR -> "Error" to Color(0xFFFF5252)
    }
    Row(
        modifier = modifier.statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(CardBg)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(stateColor))
            Column {
                Text(name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(stateText, color = stateColor, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        RoundBtn(icon = Icons.Rounded.Settings, active = false, onClick = onSettings)
    }
}

@Composable
private fun MsgBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ))
                .background(if (isUser) UserBubble else AiBubble)
                .border(
                    1.dp,
                    if (isUser) Accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            Text(msg.content, color = TextPrimary, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun RoundBtn(
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (active) Accent.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.55f))
            .border(1.dp, if (active) Accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (active) Accent else Color.White.copy(0.7f), modifier = Modifier.size(19.dp))
    }
}
