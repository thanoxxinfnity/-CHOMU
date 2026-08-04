package com.chomu.aiagent.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.chomu.aiagent.ui.theme.GlowListening
import kotlin.math.sin

@Composable
fun VoiceWaveform(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    color: Color = GlowListening,
    barCount: Int = 12
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    val amplitudes = (0 until barCount).map { i ->
        infiniteTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = if (isActive) 0.7f + (i % 3) * 0.15f else 0.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (isActive) 300 + i * 60 else 1200,
                    easing = EaseInOutSine
                ),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(i * 80)
            ),
            label = "bar_$i"
        )
    }

    Canvas(modifier = modifier) {
        drawWaveform(amplitudes.map { it.value }, color, barCount)
    }
}

private fun DrawScope.drawWaveform(amplitudes: List<Float>, color: Color, barCount: Int) {
    val barWidth = size.width / (barCount * 2f)
    val centerY = size.height / 2f
    val maxHeight = size.height * 0.85f

    amplitudes.forEachIndexed { i, amp ->
        val x = barWidth + i * (size.width / barCount)
        val barHeight = maxHeight * amp
        val alpha = 0.5f + amp * 0.5f
        drawLine(
            color = color.copy(alpha = alpha),
            start = androidx.compose.ui.geometry.Offset(x, centerY - barHeight / 2f),
            end = androidx.compose.ui.geometry.Offset(x, centerY + barHeight / 2f),
            strokeWidth = barWidth * 0.7f,
            cap = StrokeCap.Round
        )
    }
}
