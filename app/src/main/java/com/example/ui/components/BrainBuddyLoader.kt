package com.example.ui.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AmberGoldBright
import com.example.ui.theme.CobaltPrimary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimalist, ultra-smooth animated splash/loading screen for BrainBuddy.
 * Features fluid harmonic rings, a subtle orbiting spark, and clean typography.
 */
@Composable
fun BrainBuddySplashScreen(
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = EaseInOutSine),
        label = "splash_alpha"
    )

    val bgColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        bgColor,
                        primaryColor.copy(alpha = 0.05f),
                        bgColor
                    )
                )
            )
            .testTag("splash_loading_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .alpha(contentAlpha)
                .padding(horizontal = 32.dp)
        ) {
            FluidNeuralOrbLoader(
                size = 128.dp,
                primaryColor = primaryColor,
                accentColor = accentColor
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.tagline_minimal),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp)
                .alpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.developer_credit_prefix),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.developer_name),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun FluidNeuralOrbLoader(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    primaryColor: Color = CobaltPrimary,
    accentColor: Color = AmberGoldBright
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fluid_orb")

    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )

    val counterAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counter_orbit"
    )

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val baseRadius = this.size.minDimension * 0.34f

        // Soft ambient glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = center,
                radius = baseRadius * 1.65f * breathe
            ),
            center = center,
            radius = baseRadius * 1.65f * breathe
        )

        // Outer fluid ring arc
        rotate(degrees = orbitAngle, pivot = center) {
            drawArc(
                color = primaryColor.copy(alpha = 0.35f),
                startAngle = 0f,
                sweepAngle = 220f,
                useCenter = false,
                topLeft = Offset(
                    center.x - baseRadius * 1.18f * breathe,
                    center.y - baseRadius * 1.18f * breathe
                ),
                size = androidx.compose.ui.geometry.Size(
                    baseRadius * 2.36f * breathe,
                    baseRadius * 2.36f * breathe
                ),
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Inner counter-rotating ring arc
        rotate(degrees = counterAngle, pivot = center) {
            drawArc(
                color = primaryColor.copy(alpha = 0.85f),
                startAngle = 40f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(
                    center.x - baseRadius * 0.85f,
                    center.y - baseRadius * 0.85f
                ),
                size = androidx.compose.ui.geometry.Size(
                    baseRadius * 1.70f,
                    baseRadius * 1.70f
                ),
                style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Orbiting golden spark node
        val rad = Math.toRadians(orbitAngle.toDouble())
        val sparkRadius = baseRadius * 1.18f * breathe
        val sparkOffset = Offset(
            x = center.x + (cos(rad) * sparkRadius).toFloat(),
            y = center.y + (sin(rad) * sparkRadius).toFloat()
        )
        drawCircle(
            color = accentColor,
            radius = 4.5.dp.toPx(),
            center = sparkOffset
        )

        // Minimalist 4-point neural spark diamond at center
        val d = baseRadius * 0.42f * (0.94f + (breathe - 0.86f) * 0.3f)
        val sparkPath = Path().apply {
            moveTo(center.x, center.y - d)
            quadraticTo(center.x, center.y, center.x + d, center.y)
            quadraticTo(center.x, center.y, center.x, center.y + d)
            quadraticTo(center.x, center.y, center.x - d, center.y)
            quadraticTo(center.x, center.y, center.x, center.y - d)
            close()
        }
        drawPath(path = sparkPath, color = primaryColor)
    }
}

@Composable
fun BrainBuddyInlineLoader(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FluidNeuralOrbLoader(
            size = 84.dp,
            primaryColor = MaterialTheme.colorScheme.primary,
            accentColor = MaterialTheme.colorScheme.tertiary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}
