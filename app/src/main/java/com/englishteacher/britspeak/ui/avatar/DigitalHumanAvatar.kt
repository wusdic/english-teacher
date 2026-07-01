package com.englishteacher.britspeak.ui.avatar

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Filename, inside the app's private files dir, of the user-chosen avatar portrait. */
const val AVATAR_PORTRAIT_FILENAME = "avatar_portrait.img"

/** Absolute [File] where the user-chosen avatar portrait is stored (may not exist yet). */
fun avatarPortraitFile(context: android.content.Context): File =
    File(context.filesDir, AVATAR_PORTRAIT_FILENAME)

/**
 * A cute, refined, fully-animated tutor avatar: an **original** green-haired forest-sprite girl
 * (chibi anime style) set in a lush dendro-green scene with drifting light motes and leaves.
 * Blinks, breathes, sways her hair and floats gently; her mouth animates while speaking.
 *
 * Everything is drawn with Compose [Canvas] (no bundled artwork, so it ships clean of any
 * third-party IP). The user can tap the avatar to pick a photo from their phone — it is saved
 * locally (see [avatarPortraitFile]) and rendered here with the same float/breathe animation,
 * fully offline. Passing a non-zero [imageVersion] forces a reload after the file changes.
 *
 * @param imageVersion bump this to reload the portrait after the user picks a new image.
 * @param onClick invoked when the avatar is tapped (used to open the image picker).
 */
@Composable
fun DigitalHumanAvatar(
    mood: AvatarMood,
    modifier: Modifier = Modifier,
    amplitude: Float = 0f,
    size: Dp = 240.dp,
    imageVersion: Int = 0,
    onClick: (() -> Unit)? = null,
) {
    val transition = rememberInfiniteTransition(label = "avatar")

    val breathe by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val bobPhase by transition.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "bob",
    )
    val blinkPhase by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "blink",
    )
    val talkPhase by transition.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(360, easing = LinearEasing), RepeatMode.Restart),
        label = "talk",
    )
    val swayPhase by transition.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "sway",
    )
    val motes by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "motes",
    )

    val context = LocalContext.current
    val portraitId =
        remember {
            runCatching {
                context.resources.getIdentifier("avatar_portrait", "drawable", context.packageName)
            }.getOrDefault(0)
        }

    // User-chosen portrait saved locally (offline). Reloaded whenever [imageVersion] changes.
    val userPortrait =
        remember(imageVersion) {
            runCatching {
                val f = avatarPortraitFile(context)
                if (f.exists() && f.length() > 0) {
                    BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                } else {
                    null
                }
            }.getOrNull()
        }

    val bob = sin(bobPhase) // -1..1
    val sway = sin(swayPhase)

    val boxModifier =
        modifier
            .size(size)
            .clip(RoundedCornerShape(24.dp))
            .let { if (onClick != null) it.clickable { onClick() } else it }

    Box(modifier = boxModifier, contentAlignment = Alignment.Center) {
        // Forest background + drifting motes (always drawn).
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawForestScene(mood = mood, motes = motes, sway = sway)
        }

        if (userPortrait != null) {
            // User-picked photo (from the phone), gently animated. Highest priority.
            Image(
                bitmap = userPortrait,
                contentDescription = "Tutor",
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = bob * this.size.height * 0.02f
                            val s = 0.99f + 0.02f * breathe
                            scaleX = s
                            scaleY = s
                        },
            )
        } else if (portraitId != 0) {
            // Optional bundled drawable portrait, gently animated.
            Image(
                painter = painterResource(id = portraitId),
                contentDescription = "Tutor",
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = bob * this.size.height * 0.02f
                            val s = 0.99f + 0.02f * breathe
                            scaleX = s
                            scaleY = s
                        },
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val eyeOpen = blinkOpenness(blinkPhase)
                val mouthOpen =
                    when (mood) {
                        AvatarMood.SPEAKING ->
                            (0.16f + 0.30f * (0.5f + 0.5f * sin(talkPhase))) *
                                (0.6f + 0.6f * amplitude.coerceIn(0f, 1f))
                        AvatarMood.LISTENING -> 0.10f
                        else -> 0.06f
                    }.coerceIn(0.03f, 0.55f)

                drawCuteGirl(
                    mood = mood,
                    breathe = breathe,
                    bob = bob,
                    sway = sway,
                    eyeOpenness = eyeOpen,
                    mouthOpenness = mouthOpen,
                )
            }
        }

        // "Tap to change the avatar" hint chip.
        if (onClick != null) {
            androidx.compose.foundation.layout.Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x99000000))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                androidx.compose.material3.Text(
                    text = "点击更换形象",
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** Maps a 0..1 cycle to eyelid openness, closing briefly for a natural blink. */
internal fun blinkOpenness(phase: Float): Float {
    val blinkWindow = 0.06f
    return if (phase < blinkWindow) {
        val t = phase / blinkWindow
        val closed = 1f - kotlin.math.abs(0.5f - t) * 2f
        (1f - closed).coerceIn(0f, 1f)
    } else {
        1f
    }
}

private fun moodAccent(mood: AvatarMood): Color =
    when (mood) {
        AvatarMood.IDLE -> Color(0xFF6FCF97)
        AvatarMood.LISTENING -> Color(0xFF2BB7A8)
        AvatarMood.THINKING -> Color(0xFFF2C94C)
        AvatarMood.SPEAKING -> Color(0xFF7BE495)
    }

private fun frac(x: Float): Float = x - floor(x)

/** Draws the lush green backdrop with a mood-tinted glow, drifting light motes and leaves. */
private fun DrawScope.drawForestScene(
    mood: AvatarMood,
    motes: Float,
    sway: Float,
) {
    val w = size.width
    val h = size.height

    // Deep-to-mint vertical gradient.
    drawRect(
        brush =
            Brush.verticalGradient(
                colors = listOf(Color(0xFF1E5B3E), Color(0xFF2E8B57), Color(0xFFAFE6C2)),
                startY = 0f,
                endY = h,
            ),
        size = size,
    )

    // Soft mood glow behind the character.
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(moodAccent(mood).copy(alpha = 0.45f), Color.Transparent),
                center = Offset(w / 2f, h * 0.42f),
                radius = w * 0.55f,
            ),
        radius = w * 0.55f,
        center = Offset(w / 2f, h * 0.42f),
    )

    // Drifting light motes (deterministic scatter, floating upward, twinkling).
    val count = 14
    for (i in 0 until count) {
        val fx = frac(sin(i * 12.9898f) * 43758.545f)
        val speed = 0.6f + frac(sin(i * 7.13f) * 1234.5f) * 0.8f
        val fy = frac((sin(i * 78.233f) * 43758.545f) + motes * speed)
        val x = fx * w
        val y = (1f - fy) * h
        val r = (1.2f + frac(sin(i * 3.7f) * 991.1f) * 3.0f)
        val twinkle = 0.35f + 0.35f * (0.5f + 0.5f * sin((motes * 6.28f * speed) + i))
        drawCircle(
            color = Color(0xFFEFFFF3).copy(alpha = twinkle),
            radius = r,
            center = Offset(x, y),
        )
    }

    // A couple of drifting leaves.
    for (i in 0 until 3) {
        val fx = frac(sin(i * 21.3f) * 4271.1f)
        val fy = frac((sin(i * 4.11f) * 1523.9f) + motes * 0.5f)
        val cx = fx * w
        val cy = (1f - fy) * h
        val leaf = moodAccent(mood).copy(alpha = 0.5f)
        val rot = sway * 0.5f + i
        val lx = cos(rot) * w * 0.02f
        val ly = sin(rot) * w * 0.02f
        drawOval(
            color = leaf,
            topLeft = Offset(cx - w * 0.02f + lx, cy - w * 0.012f + ly),
            size = Size(w * 0.04f, w * 0.024f),
        )
    }
}

/** Draws the original chibi green-haired girl, animated. */
private fun DrawScope.drawCuteGirl(
    mood: AvatarMood,
    breathe: Float,
    bob: Float,
    sway: Float,
    eyeOpenness: Float,
    mouthOpenness: Float,
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    // Gentle float + breathing.
    val cy = h * 0.46f + bob * h * 0.012f
    val scale = 0.99f + 0.02f * breathe
    val headR = w * 0.26f * scale

    val hairBack = Color(0xFF4FB477)
    val hairFront = Color(0xFF6FCF97)
    val hairShade = Color(0xFF3E9B66)
    val skin = Color(0xFFFFE7D2)
    val skinShade = Color(0xFFF6C9A8)

    // --- Body / dress hint ---
    val bodyTop = cy + headR * 0.9f
    val dress =
        Path().apply {
            moveTo(cx - headR * 0.62f, bodyTop)
            quadraticBezierTo(cx - headR * 1.0f, h * 0.98f, cx - headR * 1.05f, h)
            lineTo(cx + headR * 1.05f, h)
            quadraticBezierTo(cx + headR * 1.0f, h * 0.98f, cx + headR * 0.62f, bodyTop)
            close()
        }
    drawPath(dress, Color(0xFFF3FBF5))
    drawPath(dress, moodAccent(mood).copy(alpha = 0.20f))
    // Collar
    drawCircle(Color(0xFFEFF8F1), headR * 0.30f, Offset(cx, bodyTop + headR * 0.05f))

    // --- Back hair (behind head) ---
    drawCircle(hairBack, headR * 1.22f, Offset(cx, cy - headR * 0.05f))
    // Long side strands that sway.
    listOf(-1, 1).forEach { s ->
        val strand =
            Path().apply {
                val bx = cx + s * headR * 0.95f
                moveTo(bx, cy - headR * 0.2f)
                quadraticBezierTo(
                    bx + s * headR * (0.35f + 0.08f * sway),
                    cy + headR * 0.9f,
                    bx - s * headR * 0.1f,
                    cy + headR * 1.5f,
                )
                quadraticBezierTo(
                    bx - s * headR * 0.4f,
                    cy + headR * 0.9f,
                    bx - s * headR * 0.5f,
                    cy - headR * 0.2f,
                )
                close()
            }
        drawPath(strand, hairShade)
    }

    // --- Face ---
    drawCircle(
        brush =
            Brush.verticalGradient(
                colors = listOf(skin, skinShade),
                startY = cy - headR,
                endY = cy + headR,
            ),
        radius = headR,
        center = Offset(cx, cy),
    )

    // --- Side hair puffs ---
    listOf(-1, 1).forEach { s ->
        drawCircle(hairFront, headR * 0.42f, Offset(cx + s * headR * 0.92f, cy - headR * 0.35f))
        drawCircle(hairShade.copy(alpha = 0.5f), headR * 0.42f, Offset(cx + s * headR * 0.98f, cy - headR * 0.28f))
    }

    // --- Front fringe ---
    val fringe =
        Path().apply {
            moveTo(cx - headR, cy - headR * 0.05f)
            quadraticBezierTo(cx - headR * 0.5f, cy - headR * 1.25f, cx, cy - headR * 1.05f)
            quadraticBezierTo(cx + headR * 0.5f, cy - headR * 1.25f, cx + headR, cy - headR * 0.05f)
            // inner cut for bangs
            quadraticBezierTo(cx + headR * 0.55f, cy - headR * 0.30f, cx + headR * 0.28f, cy - headR * 0.55f)
            quadraticBezierTo(cx + headR * 0.14f, cy - headR * 0.30f, cx, cy - headR * 0.42f)
            quadraticBezierTo(cx - headR * 0.14f, cy - headR * 0.30f, cx - headR * 0.28f, cy - headR * 0.55f)
            quadraticBezierTo(cx - headR * 0.55f, cy - headR * 0.30f, cx - headR, cy - headR * 0.05f)
            close()
        }
    drawPath(fringe, hairFront)

    // Little leaf accessory on the hair.
    val leafBase = Offset(cx + headR * 0.55f, cy - headR * 0.85f)
    drawOval(Color(0xFF7BE495), Offset(leafBase.x, leafBase.y - headR * 0.06f), Size(headR * 0.30f, headR * 0.16f))
    drawOval(Color(0xFF5BC57C), Offset(leafBase.x + headR * 0.05f, leafBase.y - headR * 0.02f), Size(headR * 0.24f, headR * 0.13f))

    // Floating hair strands above (sway).
    listOf(-1, 1).forEach { s ->
        val ax = cx + s * headR * 0.2f
        drawLine(
            color = hairFront,
            start = Offset(ax, cy - headR * 1.02f),
            end = Offset(ax + s * headR * (0.2f + 0.12f * sway), cy - headR * 1.35f),
            strokeWidth = headR * 0.05f,
        )
    }

    // --- Eyes ---
    val eyeY = cy + headR * 0.12f
    val eyeDx = headR * 0.44f
    val eyeRx = headR * 0.22f
    val eyeRy = headR * 0.28f * eyeOpenness.coerceIn(0.05f, 1f)
    listOf(-1, 1).forEach { s ->
        val ex = cx + s * eyeDx
        // Eye white
        drawOval(Color.White, Offset(ex - eyeRx, eyeY - eyeRy), Size(eyeRx * 2, eyeRy * 2))
        if (eyeOpenness > 0.25f) {
            // Iris (green gradient)
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF9BE7B0), Color(0xFF2E8B57)),
                        center = Offset(ex, eyeY - eyeRy * 0.1f),
                        radius = eyeRx * 0.95f,
                    ),
                radius = eyeRx * 0.86f,
                center = Offset(ex, eyeY),
            )
            // Pupil
            drawCircle(Color(0xFF204D33), eyeRx * 0.42f, Offset(ex, eyeY))
            // Sparkle highlights
            drawCircle(Color.White, eyeRx * 0.24f, Offset(ex - eyeRx * 0.28f, eyeY - eyeRy * 0.35f))
            drawCircle(Color.White.copy(alpha = 0.85f), eyeRx * 0.12f, Offset(ex + eyeRx * 0.25f, eyeY + eyeRy * 0.2f))
        }
        // Upper eyelash
        drawArc(
            color = Color(0xFF3A2C25),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(ex - eyeRx, eyeY - eyeRy),
            size = Size(eyeRx * 2, eyeRy * 2),
            style = Stroke(width = headR * 0.045f),
        )
    }

    // --- Blush ---
    listOf(-1, 1).forEach { s ->
        drawCircle(
            Color(0xFFFFA6A6).copy(alpha = 0.45f),
            headR * 0.15f,
            Offset(cx + s * headR * 0.55f, cy + headR * 0.42f),
        )
    }

    // --- Mouth ---
    val mouthY = cy + headR * 0.6f
    val mouthW = headR * 0.34f
    val mouthH = headR * mouthOpenness
    drawOval(
        color = Color(0xFFC65B70),
        topLeft = Offset(cx - mouthW / 2, mouthY - mouthH / 2),
        size = Size(mouthW, mouthH.coerceAtLeast(headR * 0.03f)),
    )
}
