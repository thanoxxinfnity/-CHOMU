package com.chomu.aiagent.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chomu.aiagent.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsScreen(onPermissionsGranted: () -> Unit) {
    val context = LocalContext.current
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val notifPermission = if (Build.VERSION.SDK_INT >= 33)
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    else null

    var overlayGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { overlayGranted = Settings.canDrawOverlays(context) }

    val allGranted = micPermission.status.isGranted && overlayGranted &&
            (notifPermission == null || notifPermission.status.isGranted)

    LaunchedEffect(allGranted) {
        if (allGranted) onPermissionsGranted()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(DarkPrimary.copy(glowRadius * 0.3f), DarkBackground),
                radius = 800f
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("✦", style = MaterialTheme.typography.displayMedium, color = DarkPrimary)
            Text(
                "Welcome to CHOMU",
                style = MaterialTheme.typography.headlineMedium,
                color = DarkOnSurface,
                textAlign = TextAlign.Center
            )
            Text(
                "Grant these permissions to unlock the full AI agent experience",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurface.copy(0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // Microphone
            PermissionItem(
                icon = Icons.Rounded.Mic,
                title = "Microphone",
                description = "For voice commands",
                granted = micPermission.status.isGranted,
                onRequest = { micPermission.launchPermissionRequest() }
            )

            // Overlay
            PermissionItem(
                icon = Icons.Rounded.Layers,
                title = "Display over other apps",
                description = "For floating bubble assistant",
                granted = overlayGranted,
                onRequest = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    overlayLauncher.launch(intent)
                }
            )

            // Notifications (Android 13+)
            if (notifPermission != null) {
                PermissionItem(
                    icon = Icons.Rounded.Notifications,
                    title = "Notifications",
                    description = "For task status updates",
                    granted = notifPermission.status.isGranted,
                    onRequest = { notifPermission.launchPermissionRequest() }
                )
            }

            // Accessibility (manual)
            PermissionItem(
                icon = Icons.Rounded.Accessibility,
                title = "Accessibility Service",
                description = "For phone automation (optional)",
                granted = false,
                optional = true,
                onRequest = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onPermissionsGranted,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary)
            ) {
                Text(
                    if (allGranted) "Let's go! ✦" else "Continue anyway",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    optional: Boolean = false,
    onRequest: () -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (granted) GlowTalking else DarkPrimary,
                modifier = Modifier.size(24.dp)
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = DarkOnSurface)
                    if (optional) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = DarkOutline,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Optional",
                                style = MaterialTheme.typography.labelSmall,
                                color = DarkOnSurface.copy(0.5f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(description, style = MaterialTheme.typography.bodySmall, color = DarkOnSurface.copy(0.55f))
            }
            if (granted) {
                Icon(Icons.Rounded.CheckCircle, "Granted", tint = GlowTalking, modifier = Modifier.size(20.dp))
            } else {
                TextButton(onClick = onRequest, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Grant", color = DarkPrimary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
