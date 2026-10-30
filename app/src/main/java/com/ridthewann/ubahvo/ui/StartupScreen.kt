package com.ridthewann.ubahvo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class StartupUiState(
    val message: String = "Selamat Datang",
    val showProgress: Boolean = false
)

@Composable
fun StartupScreen(state: StartupUiState) {
    val transition = rememberInfiniteTransition(label = "ubahvo-welcome")
    val ringScale by transition.animateFloat(
        initialValue = 0.84f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1850), RepeatMode.Reverse),
        label = "ring-scale"
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(tween(1850), RepeatMode.Reverse),
        label = "ring-alpha"
    )
    val logoRotation by transition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2300), RepeatMode.Reverse),
        label = "logo-rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF151A22), Color(0xFF0B0D11), Color(0xFF090A0D))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF9D2636).copy(alpha = 0.24f), Color.Transparent),
                    center = center.copy(x = center.x * 0.92f, y = center.y * 0.74f),
                    radius = size.minDimension * 0.72f
                ),
                radius = size.minDimension * 0.72f,
                center = center.copy(x = center.x * 0.92f, y = center.y * 0.74f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(176.dp), contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier
                        .size(170.dp)
                        .graphicsLayer {
                            scaleX = ringScale
                            scaleY = ringScale
                            rotationZ = -logoRotation
                        }
                ) {
                    drawCircle(
                        color = Color(0xFFD14A4E).copy(alpha = ringAlpha),
                        style = Stroke(width = 1.25.dp.toPx()),
                        radius = size.minDimension * 0.46f
                    )
                    drawCircle(
                        color = Color(0xFFD3A15F).copy(alpha = ringAlpha * 0.72f),
                        style = Stroke(width = 1.dp.toPx()),
                        radius = size.minDimension * 0.36f
                    )
                }
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .graphicsLayer { rotationZ = logoRotation }
                        .shadow(26.dp, RoundedCornerShape(26.dp))
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFB52D3A), Color(0xFF731D2A), Color(0xFFC18C4D))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "U",
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-2).sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Selamat Datang",
                color = Color(0xFFF3F0EC),
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "UBAHVO",
                color = Color(0xFFD8AA72),
                fontSize = 33.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.4.sp
            )
            Spacer(Modifier.height(9.dp))
            Text(
                "Satu ruang untuk media lintas platform",
                color = Color(0xFFB5BBC5),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(58.dp))
            AnimatedContent(
                targetState = state.message,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
                label = "startup-stage"
            ) { message ->
                Text(
                    message,
                    color = Color(0xFFD8DCE3),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    minLines = 2
                )
            }
            if (state.showProgress) {
                Spacer(Modifier.height(18.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50)),
                    color = Color(0xFFD45258),
                    trackColor = Color(0xFF30343B)
                )
                Spacer(Modifier.height(13.dp))
                Text(
                    "Mohon tunggu sebentar",
                    color = Color(0xFF838C99),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Text(
            "BY RIDTHEWANN  •  BETA",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            color = Color(0xFF777E88),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp
        )
    }
}
