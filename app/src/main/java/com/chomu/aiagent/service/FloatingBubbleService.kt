package com.chomu.aiagent.service

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.view.WindowManager.LayoutParams.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.chomu.aiagent.MainActivity
import com.chomu.aiagent.domain.model.AgentState
import com.chomu.aiagent.domain.model.Message
import com.chomu.aiagent.ui.components.CompanionViewer
import com.chomu.aiagent.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlin.math.abs

@AndroidEntryPoint
class FloatingBubbleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    // Bubble state
    private val bubbleX = mutableStateOf(100)
    private val bubbleY = mutableStateOf(300)
    private val isPanelOpen = mutableStateOf(false)
    private val agentState = mutableStateOf(AgentState.IDLE)
    private val messages = mutableStateOf<List<Message>>(emptyList())
    private val inputText = mutableStateOf("")
    private val isLoading = mutableStateOf(false)

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        return START_STICKY
    }

    private fun addBubble() {
        val params = WindowManager.LayoutParams(
            BUBBLE_SIZE_PX, BUBBLE_SIZE_PX,
            TYPE_APPLICATION_OVERLAY,
            FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleX.value
            y = bubbleY.value
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)
            setContent {
                ChomuTheme {
                    BubbleButton(
                        agentState = agentState.value,
                        onClick = { togglePanel(params) },
                        onDrag = { dx, dy ->
                            bubbleX.value += dx.toInt()
                            bubbleY.value += dy.toInt()
                            params.x = bubbleX.value.coerceAtLeast(0)
                            params.y = bubbleY.value.coerceAtLeast(0)
                            windowManager.updateViewLayout(bubbleView, params)
                        }
                    )
                }
            }
        }
        bubbleView = view
        windowManager.addView(view, params)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    private fun togglePanel(bubbleParams: WindowManager.LayoutParams) {
        if (isPanelOpen.value) {
            closePanel()
        } else {
            openPanel(bubbleParams)
        }
        isPanelOpen.value = !isPanelOpen.value
    }

    private fun openPanel(bubbleParams: WindowManager.LayoutParams) {
        val screenWidth = resources.displayMetrics.widthPixels
        val panelWidth = (screenWidth * 0.88f).toInt()
        val panelHeight = PANEL_HEIGHT_PX

        val px = bubbleParams.x
        val isLeft = px < screenWidth / 2
        val panelX = if (isLeft) bubbleParams.x + BUBBLE_SIZE_PX + 8
                     else bubbleParams.x - panelWidth - 8

        val params = WindowManager.LayoutParams(
            panelWidth, panelHeight,
            TYPE_APPLICATION_OVERLAY,
            FLAG_NOT_TOUCH_MODAL or FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = panelX.coerceIn(0, screenWidth - panelWidth)
            y = bubbleParams.y.coerceAtMost(resources.displayMetrics.heightPixels - panelHeight)
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingBubbleService)
            setViewTreeSavedStateRegistryOwner(this@FloatingBubbleService)
            setContent {
                ChomuTheme {
                    BubblePanel(
                        agentState = agentState.value,
                        messages = messages.value,
                        inputText = inputText.value,
                        isLoading = isLoading.value,
                        onInputChange = { inputText.value = it },
                        onSend = { sendMessage() },
                        onClose = { closePanel(); isPanelOpen.value = false },
                        onOpenApp = {
                            val intent = Intent(this@FloatingBubbleService, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        },
                        onEmergencyStop = {
                            agentState.value = AgentState.IDLE
                            isLoading.value = false
                            sendBroadcast(Intent("com.chomu.aiagent.STOP_AUTOMATION")
                                .setPackage(packageName))
                        }
                    )
                }
            }
        }
        panelView = view
        windowManager.addView(view, params)
    }

    private fun closePanel() {
        panelView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        panelView = null
    }

    private fun sendMessage() {
        val text = inputText.value.trim()
        if (text.isBlank()) return
        inputText.value = ""
        val userMsg = Message(content = text, isUser = true)
        messages.value = messages.value + userMsg
        isLoading.value = true
        agentState.value = AgentState.TALKING
        // Relay to main app via broadcast
        sendBroadcast(Intent("com.chomu.aiagent.BUBBLE_MESSAGE")
            .setPackage(packageName)
            .putExtra("message", text))
    }

    fun updateFromMain(response: String, state: AgentState) {
        messages.value = messages.value + Message(content = response, isUser = false)
        agentState.value = state
        isLoading.value = false
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "CHOMU Assistant", NotificationManager.IMPORTANCE_LOW).apply {
            description = "CHOMU floating bubble assistant"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("CHOMU is active")
            .setContentText("Tap to open")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        closePanel()
        bubbleView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        serviceScope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "chomu_bubble"
        const val NOTIF_ID = 1001
        val BUBBLE_SIZE_PX = 160
        val PANEL_HEIGHT_PX = 600
    }
}

@Composable
private fun BubbleButton(
    agentState: AgentState,
    onClick: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val stateColor = when (agentState) {
        AgentState.IDLE      -> GlowIdle
        AgentState.LISTENING -> GlowListening
        AgentState.TALKING   -> GlowTalking
        AgentState.WORKING   -> GlowWorking
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bubble_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            tween(when (agentState) {
                AgentState.IDLE -> 2000; AgentState.LISTENING -> 600
                AgentState.TALKING -> 400; AgentState.WORKING -> 800
            }, easing = EaseInOutSine),
            RepeatMode.Reverse
        ), label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .scale(pulseScale)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.radialGradient(
                    listOf(stateColor.copy(0.4f), Color(0xFF0A0E1F))
                )
            )
            .border(2.dp, stateColor.copy(0.6f), RoundedCornerShape(50))
            .pointerInput(Unit) {
                var totalDragX = 0f
                var totalDragY = 0f
                detectDragGestures(
                    onDragStart = { totalDragX = 0f; totalDragY = 0f },
                    onDrag = { _, drag ->
                        totalDragX += drag.x
                        totalDragY += drag.y
                        onDrag(drag.x, drag.y)
                    },
                    onDragEnd = {
                        if (abs(totalDragX) < 10 && abs(totalDragY) < 10) onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text("✦", style = MaterialTheme.typography.headlineMedium, color = stateColor)
    }
}

@Composable
private fun BubblePanel(
    agentState: AgentState,
    messages: List<Message>,
    inputText: String,
    isLoading: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
    onEmergencyStop: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xEE0A0E1F),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(Modifier.fillMaxSize()) {
            // Panel header
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini 3D viewer
                CompanionViewer(
                    agentState = agentState,
                    modifier = Modifier.size(56.dp),
                    isCompact = true
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("CHOMU ✦", style = MaterialTheme.typography.titleSmall, color = DarkOnSurface)
                    Text(agentState.name.lowercase(), style = MaterialTheme.typography.labelSmall, color = when (agentState) {
                        AgentState.IDLE -> GlowIdle; AgentState.LISTENING -> GlowListening
                        AgentState.TALKING -> GlowTalking; AgentState.WORKING -> GlowWorking
                    })
                }
                IconButton(onClick = onOpenApp, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.OpenInFull, "Open app", tint = DarkOnSurface.copy(0.6f), modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, "Close", tint = DarkOnSurface.copy(0.6f), modifier = Modifier.size(16.dp))
                }
            }

            HorizontalDivider(color = DarkOutline.copy(0.4f))

            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Ask me anything!", color = DarkOnSurface.copy(0.4f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                items(messages) { msg ->
                    BubbleChatMessage(msg)
                }
                if (isLoading) {
                    item {
                        Row(Modifier.padding(8.dp)) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = DarkPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("Thinking...", style = MaterialTheme.typography.labelSmall, color = DarkOnSurface.copy(0.5f))
                        }
                    }
                }
            }

            HorizontalDivider(color = DarkOutline.copy(0.4f))

            // Emergency stop (only in WORKING state)
            AnimatedVisibility(visible = agentState == AgentState.WORKING) {
                Button(
                    onClick = onEmergencyStop,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkError),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Stop, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("STOP", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }

            // Input
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    placeholder = { Text("Type...", color = DarkOnSurface.copy(0.35f), style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = DarkOnSurface),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkPrimary,
                        unfocusedBorderColor = DarkOutline,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { onSend() })
                )
                Spacer(Modifier.width(6.dp))
                FilledIconButton(
                    onClick = onSend,
                    enabled = inputText.isNotBlank() && !isLoading,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = DarkPrimary)
                ) {
                    Icon(Icons.Rounded.Send, "Send", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun BubbleChatMessage(message: Message) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.isUser) DarkPrimary.copy(0.8f) else DarkSurfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 240.dp)
        ) {
            Text(
                message.content,
                style = MaterialTheme.typography.bodySmall,
                color = if (message.isUser) Color.White else DarkOnSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
