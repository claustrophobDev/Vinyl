package com.claustrophobDev.vinyl.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.claustrophobDev.vinyl.R
import com.claustrophobDev.vinyl.ui.theme.VinylColors
import com.claustrophobDev.vinyl.vpn.VpnStatus
import kotlinx.coroutines.isActive

private val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

// кнопка подключения в виде пластинки
// все анимации читаются только внутри graphicsLayer / draw, иначе экран перерисовывался каждый кадр и лагал
@Composable
fun VinylDisc(status: VpnStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val connected = status == VpnStatus.CONNECTED
    val busy = status == VpnStatus.CONNECTING || status == VpnStatus.STOPPING
    val error = status == VpnStatus.ERROR

    // 0 выключено, 1 подключено
    val glow = animateFloatAsState(
        targetValue = if (connected) 1f else if (busy) 0.6f else 0f,
        animationSpec = tween(700),
        label = "glow"
    )

    val rotation = remember { Animatable(0f) }
    LaunchedEffect(connected, busy) {
        if (connected || busy) {
            val turnMs = if (busy) 1300 else 5200
            while (isActive) {
                rotation.snapTo(rotation.value % 360f)
                rotation.animateTo(rotation.value + 360f, tween(turnMs, easing = LinearEasing))
            }
        } else if (rotation.value != 0f) {
            // плавно докручиваем при остановке
            rotation.animateTo(rotation.value + 50f, tween(1000, easing = LinearOutSlowInEasing))
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "press"
    )

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        // свечение вокруг
        Canvas(Modifier.fillMaxSize()) {
            val g = glow.value
            if (g < 0.01f && !error) return@Canvas
            val color = if (error) VinylColors.Danger else VinylColors.Accent
            val strength = if (error) 0.35f else g
            drawCircle(
                brush = Brush.radialGradient(
                    0.55f to color.copy(alpha = 0.40f * strength),
                    0.78f to VinylColors.AccentDeep.copy(alpha = 0.12f * strength),
                    1f to Color.Transparent,
                    center = center,
                    radius = size.minDimension / 2f
                ),
                radius = size.minDimension / 2f
            )
        }

        if (busy) PulseRing(Modifier.fillMaxSize(0.9f))

        Box(
            modifier = Modifier
                .fillMaxSize(0.76f)
                .graphicsLayer {
                    scaleX = pressScale.value
                    scaleY = pressScale.value
                }
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .semantics { contentDescription = if (connected) "Отключить VPN" else "Подключить VPN" }
        ) {
            // сам диск с дорожками, крутится
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = rotation.value }
                    .drawWithCache {
                        val r = size.minDimension / 2f
                        val body = Brush.radialGradient(
                            0f to Color(0xFF1C1A26),
                            0.42f to Color(0xFF0F0E15),
                            1f to Color(0xFF050409),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = r
                        )
                        val grooveStroke = Stroke(width = 0.8.dp.toPx())
                        val rimStroke = Stroke(width = 1.5.dp.toPx())
                        val arcStroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                        onDrawBehind {
                            drawCircle(body, r)

                            // дорожки
                            var grooveR = r * 0.44f
                            var i = 0
                            while (grooveR < r * 0.965f) {
                                val alpha = if (i % 4 == 0) 0.075f else 0.032f
                                drawCircle(Color.White.copy(alpha = alpha), radius = grooveR, style = grooveStroke)
                                grooveR += r * 0.025f
                                i++
                            }
                            drawCircle(Color.White.copy(alpha = 0.10f), r - rimStroke.width / 2f, style = rimStroke)

                            // блик-дуга, по ней видно что диск крутится
                            val g = glow.value
                            val arcColor = if (error) VinylColors.Danger else lerp(Color.White.copy(alpha = 0.12f), VinylColors.AccentBright, g)
                            val arcR = r * 0.8f
                            drawArc(
                                color = arcColor.copy(alpha = if (error) 0.5f else 0.18f + 0.4f * g),
                                startAngle = -65f,
                                sweepAngle = 48f,
                                useCenter = false,
                                topLeft = Offset(center.x - arcR, center.y - arcR),
                                size = Size(arcR * 2, arcR * 2),
                                style = arcStroke
                            )
                        }
                    }
            ) {
                // наклейка в центре с логотипом
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(0.40f)
                        .drawBehind {
                            val g = glow.value
                            val r = size.minDimension / 2f
                            drawCircle(
                                Brush.linearGradient(
                                    listOf(
                                        lerp(Color(0xFF1E1B29), Color(0xFF2B2066), g),
                                        lerp(Color(0xFF131119), Color(0xFF110C28), g)
                                    ),
                                    start = Offset.Zero,
                                    end = Offset(size.width, size.height)
                                ),
                                r
                            )
                            val ring = if (error) {
                                VinylColors.Danger.copy(alpha = 0.6f)
                            } else {
                                lerp(Color.White.copy(alpha = 0.10f), VinylColors.AccentBright.copy(alpha = 0.65f), g)
                            }
                            drawCircle(ring, r - 0.75.dp.toPx(), style = Stroke(1.dp.toPx()))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.vinyl_mark),
                        contentDescription = null,
                        colorFilter = if (connected || busy) null else grayscale,
                        modifier = Modifier
                            .fillMaxWidth(0.64f)
                            .graphicsLayer { alpha = 0.5f + 0.5f * glow.value }
                    )
                }
            }

            // неподвижный отблеск поверх, чтобы было похоже на винил
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.08f to Color.White.copy(alpha = 0.055f),
                        0.16f to Color.Transparent,
                        0.50f to Color.Transparent,
                        0.58f to Color.White.copy(alpha = 0.04f),
                        0.66f to Color.Transparent,
                        1f to Color.Transparent,
                        center = center
                    ),
                    radius = size.minDimension / 2f
                )
            }
        }
    }
}

@Composable
private fun PulseRing(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "pulseProgress"
    )
    Canvas(modifier) {
        val p = progress.value
        drawCircle(
            color = VinylColors.Accent.copy(alpha = 0.35f * (1f - p)),
            radius = size.minDimension / 2f * (0.84f + 0.16f * p),
            style = Stroke(2.dp.toPx())
        )
    }
}
