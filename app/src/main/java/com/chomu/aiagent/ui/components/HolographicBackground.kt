package com.chomu.aiagent.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.*

private val nodePositionsRel = listOf(
    Offset(0.08f, 0.12f), Offset(0.88f, 0.08f),
    Offset(0.04f, 0.45f), Offset(0.93f, 0.40f),
    Offset(0.12f, 0.72f), Offset(0.85f, 0.75f),
    Offset(0.18f, 0.90f), Offset(0.78f, 0.92f),
    Offset(0.35f, 0.05f), Offset(0.62f, 0.06f),
    Offset(0.02f, 0.26f), Offset(0.96f, 0.60f),
    Offset(0.25f, 0.96f), Offset(0.72f, 0.97f),
)

private val connections = listOf(
    0 to 2, 0 to 8, 1 to 3, 1 to 9,
    2 to 4, 3 to 5, 4 to 6, 5 to 7,
    6 to 12, 7 to 13, 8 to 10, 9 to 11,
    10 to 12, 11 to 13, 0 to 10, 1 to 11,
)

@Composable
fun HolographicBackground(modifier: Modifier = Modifier, accentColor: Color = Color(0xFF00BFFF)) {
    val inf = rememberInfiniteTransition(label = "holo")
    val time by inf.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "holo_t"
    )
    val ring1 by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "ring1"
    )
    val ring2 by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6500, easing = LinearEasing), initialStartOffset = StartOffset(2000)),
        label = "ring2"
    )

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.48f

        val nodes = nodePositionsRel.map { Offset(it.x * w, it.y * h) }

        // Network connection lines
        connections.forEach { (a, b) ->
            val alpha = (0.04f + 0.025f * sin(time + a * 0.6f + b * 0.4f)).coerceIn(0f, 1f)
            drawLine(
                color = accentColor.copy(alpha = alpha),
                start = nodes[a], end = nodes[b],
                strokeWidth = 0.8f, cap = StrokeCap.Round
            )
        }

        // Network nodes
        nodes.forEachIndexed { i, p ->
            val pulse = 0.25f + 0.12f * sin(time + i * 0.85f)
            drawCircle(accentColor.copy(alpha = pulse), radius = 3f, center = p)
            drawCircle(accentColor.copy(alpha = pulse * 0.3f), radius = 7f, center = p, style = Stroke(0.5f))
        }

        // Expanding ripple rings around character center
        val minR = minOf(w, h) * 0.18f
        val maxR = minOf(w, h) * 0.52f

        fun drawRipple(t: Float, baseAlpha: Float) {
            val r = minR + (maxR - minR) * t
            val a = baseAlpha * (1f - t) * (1f - t)
            if (a > 0.004f) {
                drawCircle(accentColor.copy(alpha = a), radius = r, center = Offset(cx, cy), style = Stroke(1.2f))
            }
        }
        drawRipple(ring1, 0.22f)
        drawRipple((ring1 + 0.33f) % 1f, 0.16f)
        drawRipple((ring1 + 0.66f) % 1f, 0.10f)
        drawRipple(ring2, 0.18f)
        drawRipple((ring2 + 0.5f) % 1f, 0.12f)

        // Static inner + outer decorative rings
        drawCircle(accentColor.copy(alpha = 0.09f), radius = minOf(w, h) * 0.43f, center = Offset(cx, cy), style = Stroke(1f))
        drawCircle(accentColor.copy(alpha = 0.05f), radius = minOf(w, h) * 0.33f, center = Offset(cx, cy), style = Stroke(0.6f))

        // Subtle hex grid dots (background texture)
        val hexStep = 55f
        var row = 0
        var gy = 0f
        while (gy < h) {
            val offset = if (row % 2 == 0) 0f else hexStep * 0.5f
            var gx = offset
            while (gx < w) {
                val dist = hypot(gx - cx, gy - cy)
                val gridAlpha = (0.025f * (1f - (dist / (maxR * 1.4f)).coerceIn(0f, 1f))).coerceAtLeast(0f)
                if (gridAlpha > 0.003f) {
                    drawCircle(accentColor.copy(alpha = gridAlpha), radius = 1.2f, center = Offset(gx, gy))
                }
                gx += hexStep
            }
            gy += hexStep * 0.87f
            row++
        }

        // Scan line moving downward
        val scanY = cy + sin(time * 0.4f) * h * 0.35f
        drawLine(
            color = accentColor.copy(alpha = 0.07f),
            start = Offset(0f, scanY), end = Offset(w, scanY),
            strokeWidth = 1.5f
        )
    }
}
