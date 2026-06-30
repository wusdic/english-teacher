package com.englishteacher.britspeak.ui.avatar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * A polished, asset-free animated tutor face drawn with Compose [Canvas].
 *
 * It renders a friendly stylised British tutor with a soft gradient halo, breathing motion,
 * periodic blinking, and a mouth that animates while [mood] is [AvatarMood.SPEAKING] (driven by
 * [amplitude] when available, e.g. from TTS progress).
 *
 * The component is purely declarative; swap it for a Live2D/3D renderer later without touching
 * the rest of the UI (see docs/PLAN.md §5).
 */
@Composable
fun DigitalHumanAvatar(
    mood: AvatarMood,
    modifier: Modifier = Modifier,
    amplitude: Float = 0f,
    size: Dp = 220.dp,
) {
    val transition = rememberInfiniteTransition(label = "avatar")

    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(3200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "breathe",
    )

    // Blink: mostly open, brief close near the top of each cycle.
    val blinkPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(4200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "blink",
    )

    val talk by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec =
            infiniteRepeatable(
                animation = tween(420, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "talk",
    )

    val haloPulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "halo",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val eyeOpen = blinkOpenness(blinkPhase)
            val mouthOpen =
                when (mood) {
                    AvatarMood.SPEAKING ->
                        (0.18f + 0.32f * (0.5f + 0.5f * sin(talk))) * (0.4f + 0.6f * amplitude.coerceIn(0f, 1f) + 0.4f)
                    AvatarMood.LISTENING -> 0.08f
                    else -> 0.05f
                }.coerceIn(0.02f, 0.6f)

            drawAvatar(
                mood = mood,
                breathe = breathe,
                eyeOpenness = eyeOpen,
                mouthOpenness = mouthOpen,
                haloScale = haloPulse,
            )
        }
    }
}

/** Maps a 0..1 cycle to eyelid openness, closing briefly for a natural blink. */
internal fun blinkOpenness(phase: Float): Float {
    // Closed only in a short window (~6% of the cycle).
    val blinkWindow = 0.06f
    return if (phase < blinkWindow) {
        val t = phase / blinkWindow // 0..1 across the blink
        // Down then up.
        val closed = 1f - kotlin.math.abs(0.5f - t) * 2f // 0 at edges, 1 at middle
        (1f - closed).coerceIn(0f, 1f)
    } else {
        1f
    }
}

private fun DrawScope.drawAvatar(
    mood: AvatarMood,
    breathe: Float,
    eyeOpenness: Float,
    mouthOpenness: Float,
    haloScale: Float,
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f

    val moodColor =
        when (mood) {
            AvatarMood.IDLE -> Color(0xFF5B57C9)
            AvatarMood.LISTENING -> Color(0xFF2BB7A8)
            AvatarMood.THINKING -> Color(0xFFFFB020)
            AvatarMood.SPEAKING -> Color(0xFFFF6F61)
        }

    // Soft halo behind the head, tinted by mood.
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(moodColor.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(cx, cy),
                radius = w * 0.5f * haloScale,
            ),
        radius = w * 0.5f * haloScale,
        center = Offset(cx, cy),
    )

    // Gentle breathing scale applied to the head group.
    val scale = 0.98f + 0.02f * breathe
    val headRadius = w * 0.30f * scale
    val headCenter = Offset(cx, cy + h * 0.02f * (1f - breathe))

    // Hair (behind face).
    drawCircle(
        color = Color(0xFF6D4C3D),
        radius = headRadius * 1.18f,
        center = headCenter.copy(y = headCenter.y - headRadius * 0.12f),
    )

    // Face.
    drawCircle(
        brush =
            Brush.verticalGradient(
                colors = listOf(Color(0xFFFFE3CF), Color(0xFFF7C9AE)),
                startY = headCenter.y - headRadius,
                endY = headCenter.y + headRadius,
            ),
        radius = headRadius,
        center = headCenter,
    )

    // Hair fringe (front).
    val fringe =
        Path().apply {
            moveTo(headCenter.x - headRadius, headCenter.y - headRadius * 0.1f)
            quadraticBezierTo(
                headCenter.x,
                headCenter.y - headRadius * 1.25f,
                headCenter.x + headRadius,
                headCenter.y - headRadius * 0.1f,
            )
            quadraticBezierTo(
                headCenter.x + headRadius * 0.4f,
                headCenter.y - headRadius * 0.55f,
                headCenter.x,
                headCenter.y - headRadius * 0.5f,
            )
            quadraticBezierTo(
                headCenter.x - headRadius * 0.4f,
                headCenter.y - headRadius * 0.55f,
                headCenter.x - headRadius,
                headCenter.y - headRadius * 0.1f,
            )
            close()
        }
    drawPath(fringe, Color(0xFF6D4C3D))

    // Eyes.
    val eyeY = headCenter.y - headRadius * 0.10f
    val eyeDx = headRadius * 0.42f
    val eyeRx = headRadius * 0.16f
    val eyeRy = (headRadius * 0.20f) * eyeOpenness.coerceIn(0.05f, 1f)
    listOf(-1, 1).forEach { sign ->
        val ex = headCenter.x + sign * eyeDx
        // Eye white.
        drawOval(
            color = Color.White,
            topLeft = Offset(ex - eyeRx, eyeY - eyeRy),
            size = Size(eyeRx * 2, eyeRy * 2),
        )
        // Iris/pupil (hidden when nearly closed).
        if (eyeOpenness > 0.25f) {
            drawCircle(
                color = Color(0xFF3A2C25),
                radius = eyeRx * 0.55f,
                center = Offset(ex, eyeY),
            )
        }
    }

    // Eyebrows — raise slightly when thinking.
    val browLift = if (mood == AvatarMood.THINKING) headRadius * 0.06f else 0f
    listOf(-1, 1).forEach { sign ->
        val ex = headCenter.x + sign * eyeDx
        drawLine(
            color = Color(0xFF5B463B),
            start = Offset(ex - eyeRx, eyeY - eyeRy - headRadius * 0.18f - browLift),
            end = Offset(ex + eyeRx, eyeY - eyeRy - headRadius * 0.22f - browLift),
            strokeWidth = headRadius * 0.05f,
        )
    }

    // Cheeks (a touch of blush).
    listOf(-1, 1).forEach { sign ->
        drawCircle(
            color = Color(0xFFFFB3A0).copy(alpha = 0.5f),
            radius = headRadius * 0.12f,
            center = Offset(headCenter.x + sign * headRadius * 0.5f, headCenter.y + headRadius * 0.28f),
        )
    }

    // Mouth — an arc that opens vertically based on mouthOpenness.
    val mouthCx = headCenter.x
    val mouthCy = headCenter.y + headRadius * 0.45f
    val mouthW = headRadius * 0.5f
    val mouthH = headRadius * mouthOpenness
    val mouthRect = Rect(mouthCx - mouthW / 2, mouthCy - mouthH / 2, mouthCx + mouthW / 2, mouthCy + mouthH / 2)
    drawOval(
        color = Color(0xFFB5485A),
        topLeft = Offset(mouthRect.left, mouthRect.top),
        size = Size(mouthRect.width, mouthRect.height.coerceAtLeast(headRadius * 0.04f)),
    )

    // Mood ring accent at the base.
    drawArc(
        color = moodColor,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(cx - headRadius * 1.25f, cy - headRadius * 1.25f + h * 0.02f),
        size = Size(headRadius * 2.5f, headRadius * 2.5f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = headRadius * 0.06f),
    )
}
