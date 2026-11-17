package io.github.vigneshvicky2.timelog.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private val Ink = Color(0xFF111113)
private val TileColor = Color(0xFF1E1E24)
private val Cream = Color(0xFFF4EFE6)
private val Muted = Color(0xFFA29F98)

private val Out = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val InOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/** Progress of [t] through the window [a]..[b], clamped to 0..1. */
private fun win(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

/** Ease-out-back: overshoots a little before settling, for the tile's spring. */
private fun back(x: Float): Float {
    val c = 1.9f
    val y = x - 1f
    return 1f + (c + 1f) * y * y * y + c * y * y
}

private const val TOTAL = 3.95f
private const val EXIT = 3.45f

/**
 * Launch splash: the tally tile springs in, its four strokes draw, the orange slash swipes
 * across, then "Stint" rises and the credit is revealed behind a caret. Tap to skip.
 * Plays on the same #111113 as the system splash so the handoff is seamless.
 */
@Composable
fun SplashOverlay(onDone: () -> Unit) {
    val context = LocalContext.current
    val noMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val time = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (noMotion) { onDone(); return@LaunchedEffect }
        time.animateTo(TOTAL, tween((TOTAL * 1000).toInt(), easing = LinearEasing))
        onDone()
    }
    val skip = {
        if (time.value < EXIT) scope.launch {
            time.snapTo(EXIT)
            time.animateTo(TOTAL, tween(((TOTAL - EXIT) * 1000).toInt(), easing = LinearEasing))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val e = InOut.transform(win(time.value, EXIT, TOTAL))
                alpha = 1f - e
                val s = 1f + 0.08f * e
                scaleX = s; scaleY = s
            }
            .background(Ink)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { skip() },
    ) {
        // Warm glow that blooms when the slash lands, then breathes.
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val bloom = win(t, 1.45f, 1.75f)
            val settle = win(t, 1.75f, 2.6f)
            val breathe = if (t > 2.6f) 0.04f * sin((t - 2.6f) * 2f * PI.toFloat() / 1.6f) else 0f
            val a = (0.55f * bloom - 0.25f * settle + breathe).coerceAtLeast(0f)
            if (a > 0f) {
                val lift = 56.dp.toPx() * Out.transform(win(t, 1.55f, 2.15f))
                val c = Offset(size.width / 2, size.height / 2 - lift)
                drawCircle(
                    Brush.radialGradient(listOf(BrandColor.copy(alpha = a * 0.55f), Color.Transparent), c, size.width * 0.62f),
                    radius = size.width * 0.62f, center = c,
                )
            }
        }

        // The tile, centred, lifting to make room for the words.
        Canvas(
            Modifier
                .align(Alignment.Center)
                .size(116.dp)
                .graphicsLayer {
                    val t = time.value
                    val inP = win(t, 0f, 0.9f)
                    val s = 0.35f + 0.65f * back(inP)
                    // Squash on the slash's impact.
                    val hit = win(t, 1.45f, 1.75f)
                    val bump = if (hit in 0.001f..0.999f) sin(hit * PI.toFloat()) * 0.07f else 0f
                    scaleX = s * (1f + bump); scaleY = s * (1f - bump)
                    rotationZ = -14f * (1f - Out.transform(inP))
                    alpha = win(t, 0f, 0.18f)
                    translationY = -56.dp.toPx() * Out.transform(win(t, 1.55f, 2.15f))
                },
        ) {
            val t = time.value
            val k = size.width / 108f
            drawRoundRect(TileColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(30f * k))
            listOf(
                floatArrayOf(33f, 30.5f, 32f, 77f), floatArrayOf(45.5f, 28f, 45f, 79f),
                floatArrayOf(58f, 30f, 58.5f, 77.5f), floatArrayOf(70.5f, 28.5f, 70f, 79f),
            ).forEachIndexed { i, l ->
                val p = Out.transform(win(t, 0.63f + 0.11f * i, 0.95f + 0.11f * i))
                if (p > 0f) {
                    val a = Offset(l[0] * k, l[1] * k)
                    val b = Offset(l[2] * k, l[3] * k)
                    drawLine(Cream, a, a + (b - a) * p, strokeWidth = 7.5f * k, cap = StrokeCap.Round)
                }
            }
            val sp = InOut.transform(win(t, 1.19f, 1.47f))
            if (sp > 0f) {
                val full = Path().apply { moveTo(22f * k, 66f * k); cubicTo(42f * k, 58f * k, 62f * k, 47f * k, 86f * k, 36f * k) }
                val m = PathMeasure().apply { setPath(full, false) }
                val part = Path()
                m.getSegment(0f, m.length * sp, part, true)
                drawPath(part, BrandColor, style = Stroke(8.5f * k, cap = StrokeCap.Round))
            }
        }

        // "Stint" rising out of a slot, then the credit wiped in behind a caret.
        Column(
            Modifier.align(Alignment.Center).offset(y = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.clipToBounds()) {
                Text(
                    "Stint",
                    fontFamily = Display,
                    fontWeight = FontWeight(800),
                    fontSize = 54.sp,
                    letterSpacing = (-1.5).sp,
                    color = Cream,
                    modifier = Modifier.graphicsLayer {
                        val p = Out.transform(win(time.value, 1.67f, 2.21f))
                        translationY = (1f - p) * size.height
                    },
                )
            }
            Text(
                "Built by Vignesh Kodiswaran",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
                color = Muted,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .drawWithContent {
                        val t = time.value
                        val p = InOut.transform(win(t, 2.25f, 2.9f))
                        val x = size.width * p
                        clipRect(right = x) { this@drawWithContent.drawContent() }
                        val caretAlpha = win(t, 2.2f, 2.3f) * (1f - win(t, 2.9f, 3.15f))
                        if (caretAlpha > 0f) {
                            val w = 2.5.dp.toPx()
                            drawRoundRect(
                                BrandColor.copy(alpha = caretAlpha),
                                topLeft = Offset(x + 2.dp.toPx(), -2.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(w, size.height + 4.dp.toPx()),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2),
                            )
                        }
                    },
            )
        }
    }
}
