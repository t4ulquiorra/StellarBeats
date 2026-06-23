package com.stellarbeats.app.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * A wavy progress slider for the music player.
 *
 * The progress bar follows a sine wave instead of being a straight line.
 * The wave subtly animates (phase shifts) when music is playing, giving
 * a "living" feel. When paused, the wave is static.
 *
 * The filled portion (0 → progress) uses the primary/accent color.
 * The unfilled portion uses a dim version.
 * A circular thumb sits on the wave at the progress point.
 *
 * @param value Current progress, 0f..1f
 * @param onValueChange Called when the user drags or taps
 * @param isPlaying Whether music is playing (controls wave animation)
 * @param modifier Compose modifier
 * @param activeColor Color for the filled portion
 * @param inactiveColor Color for the unfilled portion
 * @param thumbColor Color for the drag thumb
 * @param waveAmplitude Height of the wave peaks in dp
 * @param waveFrequency Number of complete wave cycles across the width
 * @param thumbRadius Radius of the drag thumb circle
 */
@Composable
fun WavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFFA3E635),
    inactiveColor: Color = Color(0xFF2A2A2A),
    thumbColor: Color = Color.White,
    waveAmplitude: Dp = 3.dp,
    waveFrequency: Float = 3.5f,
    thumbRadius: Dp = 7.dp,
) {
    // Animate wave phase when playing
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    val effectivePhase = if (isPlaying) phase else 0f

    var isDragging by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newValue = (offset.x / size.width).coerceIn(0f, 1f)
                    onValueChange(newValue)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                ) { change, _ ->
                    val newValue = (change.position.x / size.width).coerceIn(0f, 1f)
                    onValueChange(newValue)
                    change.consume()
                }
            }
    ) {
        val amplitudePx = waveAmplitude.toPx()
        val freq = waveFrequency
        val centerY = size.height / 2f
        val waveHeight = 2.dp.toPx()

        /**
         * Compute the Y position on the wave at a given X.
         */
        fun waveY(x: Float): Float {
            return centerY + amplitudePx * sin(
                2 * PI * freq * (x / size.width) + effectivePhase
            ).toFloat()
        }

        // Build the wave path (used for both filled and unfilled)
        fun buildWavePath(startX: Float, endX: Float): Path {
            return Path().apply {
                moveTo(startX, waveY(startX))
                var x = startX
                val step = 2f
                while (x <= endX) {
                    lineTo(x, waveY(x))
                    x += step
                }
                // Ensure we end exactly at endX
                lineTo(endX, waveY(endX))
            }
        }

        // Draw unfilled wave (full width, dim)
        val unfilledPath = buildWavePath(0f, size.width)
        drawPath(
            path = unfilledPath,
            color = inactiveColor,
            style = Stroke(
                width = waveHeight,
                cap = StrokeCap.Round,
            ),
        )

        // Draw filled wave (0 to progress, accent color)
        if (value > 0.005f) {
            val fillEndX = (value * size.width).coerceAtLeast(1f)
            val filledPath = buildWavePath(0f, fillEndX)
            drawPath(
                path = filledPath,
                color = activeColor,
                style = Stroke(
                    width = waveHeight,
                    cap = StrokeCap.Round,
                ),
            )
        }

        // Draw glow behind thumb when playing
        if (isPlaying || isDragging) {
            val thumbX = value * size.width
            val thumbY = waveY(thumbX)
            drawCircle(
                color = activeColor.copy(alpha = 0.3f),
                radius = thumbRadius.toPx() * 2f,
                center = Offset(thumbX, thumbY),
            )
        }

        // Draw thumb
        val thumbX = value * size.width
        val thumbY = waveY(thumbX)
        val radius = if (isDragging) thumbRadius.toPx() * 1.3f else thumbRadius.toPx()
        drawCircle(
            color = thumbColor,
            radius = radius,
            center = Offset(thumbX, thumbY),
        )
        // Inner dot
        drawCircle(
            color = activeColor,
            radius = radius * 0.4f,
            center = Offset(thumbX, thumbY),
        )
    }
}
