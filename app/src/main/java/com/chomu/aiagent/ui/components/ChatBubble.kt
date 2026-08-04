package com.chomu.aiagent.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chomu.aiagent.domain.model.Message
import com.chomu.aiagent.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatBubble(message: Message, modifier: Modifier = Modifier) {
    val isUser = message.isUser
    val timeStr = remember(message.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            CompanionAvatar(Modifier.size(28.dp).padding(end = 6.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isUser) 20.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 20.dp
                        )
                    )
                    .background(
                        if (isUser) Brush.linearGradient(
                            listOf(DarkPrimary, Color(0xFF5B7FFF))
                        ) else Brush.linearGradient(
                            listOf(DarkSurfaceVariant, DarkSurface)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                if (message.isError) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkError
                    )
                } else {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) Color.White else DarkOnSurface
                    )
                }
            }

            // Automation log chip
            if (message.automationLog != null) {
                Spacer(Modifier.height(4.dp))
                AutomationLogChip(log = message.automationLog)
            }

            Spacer(Modifier.height(2.dp))
            Text(
                text = timeStr,
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun AutomationLogChip(log: String) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(8.dp),
        color = GlowWorking.copy(alpha = 0.12f),
        modifier = Modifier.padding(top = 2.dp)
    ) {
        Text(
            text = if (expanded) log else "⚙️ Automation log",
            style = MaterialTheme.typography.labelSmall,
            color = GlowWorking,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CompanionAvatar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                Brush.radialGradient(listOf(DarkPrimary.copy(0.3f), DarkSurface)),
                shape = RoundedCornerShape(50)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text("✦", style = MaterialTheme.typography.labelSmall, color = DarkPrimary)
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val offsets = (0..2).map { i ->
        infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = -6f,
            animationSpec = infiniteRepeatable(
                tween(400, delayMillis = i * 120, easing = EaseInOutSine),
                RepeatMode.Reverse
            ), label = "dot$i"
        )
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(DarkSurfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        offsets.forEach { anim ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(y = anim.value.dp)
                    .background(DarkPrimary.copy(0.7f), RoundedCornerShape(50))
            )
        }
    }
}
