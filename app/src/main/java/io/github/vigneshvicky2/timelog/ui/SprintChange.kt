package io.github.vigneshvicky2.timelog.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val SInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private fun w(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

private const val END = 3.4f

/**
 * Shown once when a new sprint has started since the last visit. The old sprint's name is
 * struck through with the logo's orange slash (one tally closed), it steps back, and the new
 * sprint's name rises in with a burst of confetti. Tap anywhere or "Let's go" to continue.
 */
@Composable
fun SprintChangeOverlay(old: String, new: String, detail: String?, onDone: () -> Unit) {
    val time = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    var confetti by remember { mutableIntStateOf(0) }
    var closing by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            exit.animateTo(1f, tween(380, easing = SInOut))
            onDone()
        }
    }

    LaunchedEffect(Unit) {
        launch {
            delay(900); haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            delay(1500); haptics.performHapticFeedback(HapticFeedbackType.LongPress); confetti++
        }
        time.animateTo(END, tween((END * 1000).toInt(), easing = LinearEasing))
        delay(6000)
        close()
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(Color(0xFF111113).copy(alpha = w(time.value, 0f, 0.35f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() }
            .semantics { contentDescription = "Sprint $old completed. New sprint $new started." },
    ) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp)
                .graphicsLayer {
                    val s = 1f - 0.04f * exit.value
                    scaleX = s; scaleY = s
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- the sprint that just finished ----
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    val t = time.value
                    val inP = SOut.transform(w(t, 0.15f, 0.65f))
                    val back = SOut.transform(w(t, 1.55f, 2.15f))
                    alpha = inP * (1f - 0.45f * back)
                    translationY = (1f - inP) * 24.dp.toPx() - back * 10.dp.toPx()
                    // a small knock when the strike lands, then it steps back
                    val hit = w(t, 1.2f, 1.45f)
                    val knock = if (hit in 0.001f..0.999f) kotlin.math.sin(hit * Math.PI.toFloat()) * 0.035f else 0f
                    val s = (1f - 0.12f * back) * (1f - knock)
                    scaleX = s; scaleY = s
                },
            ) {
                Pill(
                    text = "SPRINT COMPLETE",
                    icon = Icons.Rounded.Check,
                    color = DoneColor,
                    modifier = Modifier.graphicsLayer { alpha = w(time.value, 1.25f, 1.6f) },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    old,
                    fontFamily = Display, fontWeight = FontWeight(800), fontSize = 26.sp, lineHeight = 31.sp,
                    letterSpacing = (-0.5).sp, color = Color(0xFFF4EFE6), textAlign = TextAlign.Center, maxLines = 2,
                    onTextLayout = { layout = it },
                    modifier = Modifier.drawWithContent {
                        drawContent()
                        // The orange slash crosses out each line in turn, rising slightly like the logo's.
                        val l = layout ?: return@drawWithContent
                        val p = SInOut.transform(w(time.value, 0.75f, 1.25f))
                        if (p <= 0f) return@drawWithContent
                        val lines = l.lineCount
                        for (i in 0 until lines) {
                            val lp = ((p * lines) - i).coerceIn(0f, 1f)
                            if (lp <= 0f) break
                            val left = l.getLineLeft(i) - 6.dp.toPx()
                            val right = l.getLineRight(i) + 6.dp.toPx()
                            val mid = (l.getLineTop(i) + l.getLineBottom(i)) / 2f + 2.dp.toPx()
                            val rise = 7.dp.toPx()
                            val start = Offset(left, mid + rise)
                            val end = Offset(right, mid - rise)
                            drawLine(
                                BrandColor, start, start + (end - start) * lp,
                                strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round,
                            )
                        }
                    },
                )
            }

            Spacer(Modifier.height(40.dp))

            // ---- the sprint that just started ----
            Pill(
                text = "NEW SPRINT STARTED",
                icon = Icons.Rounded.Flag,
                color = Color(0xFF9DB2FF),
                modifier = Modifier.graphicsLayer {
                    val p = SOut.transform(w(time.value, 2.0f, 2.45f))
                    alpha = p; translationY = (1f - p) * 10.dp.toPx()
                },
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.clipToBounds()) {
                Text(
                    new,
                    fontFamily = Display, fontWeight = FontWeight(800), fontSize = 40.sp, lineHeight = 44.sp,
                    letterSpacing = (-1).sp, color = Color(0xFFF4EFE6), textAlign = TextAlign.Center, maxLines = 3,
                    modifier = Modifier.graphicsLayer {
                        val p = SOut.transform(w(time.value, 2.15f, 2.8f))
                        translationY = (1f - p) * size.height
                    },
                )
            }
            if (detail != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    detail, fontSize = 15.sp, color = Color(0xFFA29F98), textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = w(time.value, 2.7f, 3.1f) },
                )
            }
        }

        Button(
            onClick = { close() },
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F1EC), contentColor = Color(0xFF111113)),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp).fillMaxWidth().height(58.dp)
                .graphicsLayer {
                    val p = SOut.transform(w(time.value, 2.9f, END))
                    alpha = p; translationY = (1f - p) * 16.dp.toPx()
                },
        ) { Text("Let's go", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }

        Confetti(confetti)
    }
}

@Composable
private fun Pill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
    }
}
