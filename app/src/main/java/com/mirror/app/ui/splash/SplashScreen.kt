package com.mirror.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Ivory = Color(0xFFF7F3EC)
private val Charcoal = Color(0xFF211D19)
private val Terracotta = Color(0xFFB85C38)

/**
 * Brand reveal: a monogram built from two strokes of an "M" - the right stroke thinner and
 * dimmer than the left, so the mark itself reads as an echo of its own reflection. Draws in,
 * a soft terracotta light sweeps across it once, then the wordmark and a progress line settle
 * in. Mirrors the design explored in mirror-loading-screen.html (variant A: diagonal sweep).
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    // The mark itself is drawn fully solid from frame one - it must look pixel-identical to
    // the native Android boot-splash icon (same shape/colors) so the hand-off between the OS
    // splash and this Compose one is invisible. Animating a "draw-in" of the mark here would
    // make it look like the logo resets/redraws itself the moment Compose takes over.
    val sweepProgress = remember { Animatable(0f) }
    val wordmarkAlpha = remember { Animatable(0f) }
    val wordmarkOffset = remember { Animatable(8f) }
    val progressFill = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { sweepProgress.animateTo(1f, tween(900, easing = LinearEasing)) }
        launch {
            delay(300)
            launch { wordmarkAlpha.animateTo(1f, tween(300)) }
            wordmarkOffset.animateTo(0f, tween(300))
        }
        launch {
            delay(400)
            progressFill.animateTo(1f, tween(1200, easing = LinearEasing))
        }
        delay(1750)
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Ivory),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(96.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val scale = size.width / 120f

                    val sweepAlpha = when {
                        sweepProgress.value < 0.18f -> sweepProgress.value / 0.18f
                        sweepProgress.value < 0.82f -> 1f
                        else -> ((1f - sweepProgress.value) / 0.18f).coerceIn(0f, 1f)
                    }
                    if (sweepAlpha > 0f) {
                        val sweepX = (sweepProgress.value * 2.8f - 0.7f) * size.width
                        rotate(18f, pivot = Offset(size.width / 2f, size.height / 2f)) {
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color.Transparent, Terracotta.copy(alpha = 0.16f), Color.Transparent)
                                ),
                                topLeft = Offset(sweepX, -size.height * 0.4f),
                                size = Size(size.width * 0.55f, size.height * 1.8f),
                                alpha = sweepAlpha
                            )
                        }
                    }

                    val legA = Path().apply {
                        moveTo(30f * scale, 90f * scale)
                        lineTo(30f * scale, 30f * scale)
                        lineTo(60f * scale, 65f * scale)
                    }
                    val legB = Path().apply {
                        moveTo(60f * scale, 65f * scale)
                        lineTo(90f * scale, 30f * scale)
                        lineTo(90f * scale, 90f * scale)
                    }
                    drawPath(
                        path = legA,
                        color = Charcoal,
                        style = Stroke(width = 3f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawPath(
                        path = legB,
                        color = Charcoal.copy(alpha = 0.55f),
                        style = Stroke(width = 2f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            Text(
                "mirror",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFF2A2521),
                modifier = Modifier
                    .padding(top = 18.dp)
                    .offset(y = wordmarkOffset.value.dp)
                    .alpha(wordmarkAlpha.value)
            )

            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .width(110.dp)
                    .height(1.dp)
                    .background(Color(0xFFDDD5C7), RoundedCornerShape(50))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFill.value.coerceIn(0f, 1f))
                        .background(Terracotta)
                )
            }
        }
    }
}

