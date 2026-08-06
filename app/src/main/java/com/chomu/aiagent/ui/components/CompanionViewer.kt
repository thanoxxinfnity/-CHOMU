package com.chomu.aiagent.ui.components

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.chomu.aiagent.domain.model.AgentState

@Composable
fun CompanionViewer(
    agentState: AgentState,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    pendingAnimJson: String? = null,
    pendingBuiltinAnim: String? = null
) {
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val animController = remember { AnimationController() }
    val renderer = remember {
        GlbCompanionRenderer().also { it.animController = animController }
    }

    // Keep renderer + controller in sync with agent state
    LaunchedEffect(agentState) {
        renderer.agentState = agentState
        animController.agentState = agentState
    }

    // Load GLB model once
    LaunchedEffect(Unit) {
        isLoading = true
        loadError = null
        try {
            val glbModel = GlbLoader.load(context, "models/companion.glb")
            if (glbModel != null) {
                renderer.model = glbModel
                animController.model = glbModel
                isLoading = false
            } else {
                loadError = "Model parse failed"
                isLoading = false
            }
        } catch (e: Exception) {
            loadError = "Load error: ${e.message}"
            isLoading = false
        }
    }

    // Apply built-in clip immediately when ViewModel requests one
    LaunchedEffect(pendingBuiltinAnim) {
        pendingBuiltinAnim ?: return@LaunchedEffect
        val clip = AnimationController.builtinClipFor(pendingBuiltinAnim)
        if (clip != null) animController.setAiClip(clip)
    }

    // Replace with AI-generated clip if NVIDIA NIM produces one
    LaunchedEffect(pendingAnimJson) {
        pendingAnimJson ?: return@LaunchedEffect
        val clip = AnimationController.parseAiClip(pendingAnimJson)
        if (clip != null) animController.setAiClip(clip)
    }

    // Glow ring responds to agent state
    val glowAlpha by animateFloatAsState(
        targetValue = when (agentState) {
            AgentState.IDLE      -> 0.3f
            AgentState.LISTENING -> 0.8f
            AgentState.TALKING   -> 1.0f
            AgentState.WORKING   -> 0.9f
        },
        animationSpec = tween(500), label = "glow"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = when (agentState) {
            AgentState.IDLE      -> 1.02f
            AgentState.LISTENING -> 1.06f
            AgentState.TALKING   -> 1.08f
            AgentState.WORKING   -> 1.05f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (agentState) {
                    AgentState.IDLE      -> 2000
                    AgentState.LISTENING -> 800
                    AgentState.TALKING   -> 400
                    AgentState.WORKING   -> 600
                },
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    val stateGlowColor = when (agentState) {
        AgentState.IDLE      -> Color(0xFF4A90D9)
        AgentState.LISTENING -> Color(0xFF00BFFF)
        AgentState.TALKING   -> Color(0xFF00E676)
        AgentState.WORKING   -> Color(0xFFFF9800)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (isCompact) 16.dp else 28.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        stateGlowColor.copy(alpha = 0.08f * glowAlpha),
                        Color(0xFF0A0A1A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx -> createGlbSurfaceView(ctx, renderer) },
            modifier = Modifier.fillMaxSize(),
            update  = { renderer.agentState = agentState }
        )

        if (isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = stateGlowColor,
                    modifier = Modifier.size(if (isCompact) 24.dp else 40.dp)
                )
                if (!isCompact) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Loading companion...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }

        if (loadError != null && !isLoading) {
            FallbackCompanionAvatar(agentState = agentState, isCompact = isCompact)
        }
    }
}

private fun createGlbSurfaceView(context: Context, renderer: GlbCompanionRenderer): GLSurfaceView {
    return object : GLSurfaceView(context) {
        private var lastX = 0f
        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> lastX = event.x
                MotionEvent.ACTION_MOVE -> {
                    renderer.userRotationY += (event.x - lastX) * 0.5f
                    lastX = event.x
                }
            }
            return true
        }
    }.apply {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(renderer)
        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }
}

@Composable
private fun FallbackCompanionAvatar(agentState: AgentState, isCompact: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "fallback")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue  = 1.05f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = EaseInOutSine),
            RepeatMode.Reverse
        ), label = "scale"
    )

    val emoji = when (agentState) {
        AgentState.IDLE      -> "😊"
        AgentState.LISTENING -> "👂"
        AgentState.TALKING   -> "💬"
        AgentState.WORKING   -> "⚙️"
    }

    Text(
        text  = emoji,
        style = if (isCompact) MaterialTheme.typography.headlineMedium
                else           MaterialTheme.typography.displayMedium
    )
}
