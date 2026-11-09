package io.github.vigneshvicky2.timelog.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.vigneshvicky2.timelog.TARGET_SECONDS
import io.github.vigneshvicky2.timelog.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val OverColor = Color(0xFFFF8A3D)
val DoneColor = Color(0xFF2EBD85)
val WeekendColor = Color(0xFFE8687A)
val LeaveColor = Color(0xFF14B8A6)

@Composable
fun TimelogTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialTheme(colorScheme = scheme, content = content)
}

/** When the current screen started showing; entrance animations only play shortly after this. */
val LocalEntranceStart = androidx.compose.runtime.compositionLocalOf { 0L }

/** Marks the start of a screen so its children's [enter] animations play once, on open. */
@Composable
fun EntranceScope(content: @Composable () -> Unit) {
    val start = remember { android.os.SystemClock.uptimeMillis() }
    androidx.compose.runtime.CompositionLocalProvider(LocalEntranceStart provides start, content = content)
}

/**
 * Fades and slides an element up into place, staggered by [index]. Only animates while the
 * screen is opening (or when [always] is set), so scrolling back never replays it.
 */
@Composable
fun Modifier.enter(index: Int = 0, always: Boolean = false): Modifier {
    val start = LocalEntranceStart.current
    val p = remember {
        val fresh = always || android.os.SystemClock.uptimeMillis() - start < 900
        Animatable(if (fresh) 0f else 1f)
    }
    LaunchedEffect(Unit) {
        if (p.value < 1f) {
            delay(minOf(index, 8) * 40L)
            p.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
        }
    }
    return graphicsLayer {
        val v = p.value
        alpha = v
        translationY = (1f - v) * 22.dp.toPx()
    }
}

/** Press feedback: the element gently shrinks while held. Pass [source] to the clickable. */
class Press(val source: androidx.compose.foundation.interaction.MutableInteractionSource, val modifier: Modifier)

@Composable
fun rememberPress(pressedScale: Float = 0.95f): Press {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s = animateFloatAsState(if (pressed) pressedScale else 1f, spring(0.55f, Spring.StiffnessMedium), label = "press")
    return Press(source, Modifier.graphicsLayer { scaleX = s.value; scaleY = s.value })
}

/** Day ring: solid = logged in Jira, lighter = pending, orange when the day goes past 8h. */
@Composable
fun ProgressRing(
    logged: Long,
    pending: Long,
    size: Dp,
    stroke: Dp = 10.dp,
    target: Long = TARGET_SECONDS,
    content: @Composable () -> Unit = {},
) {
    val total = logged + pending
    val over = total > target
    val done = total == target
    val loggedF by animateFloatAsState(
        (logged.toFloat() / target).coerceIn(0f, 1f), spring(0.65f, Spring.StiffnessVeryLow), label = "logged"
    )
    val totalF by animateFloatAsState(
        (total.toFloat() / target).coerceIn(0f, 1f), spring(0.65f, Spring.StiffnessVeryLow), label = "total"
    )
    val main by animateColorAsState(
        when { over -> OverColor; done -> DoneColor; else -> MaterialTheme.colorScheme.primary }, tween(600), label = "main"
    )
    val pendingColor by animateColorAsState(
        if (over) OverColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.tertiary, tween(600), label = "pend"
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val tl = Offset(s / 2, s / 2)
            val arc = Size(this.size.width - s, this.size.height - s)
            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(s))
            if (totalF > 0f) drawArc(pendingColor, -90f, 360f * totalF, false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
            if (loggedF > 0f) drawArc(main, -90f, 360f * loggedF, false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
fun MiniRing(fraction: Float, color: Color, track: Color, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(0.7f, Spring.StiffnessLow), label = "mini")
    Canvas(modifier) {
        val s = 3.dp.toPx()
        val tl = Offset(s / 2, s / 2)
        val arc = Size(size.width - s, size.height - s)
        drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(s))
        if (f > 0f) drawArc(color, -90f, 360f * f, false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
    }
}

/** Text that rolls vertically whenever its value changes. */
@Composable
fun RollingText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            ((slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(220))) togetherWith
                (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(140))))
                .using(androidx.compose.animation.SizeTransform(clip = false))
        },
        modifier = modifier,
        label = "roll",
    ) { Text(it, style = style, color = color) }
}

@Composable
fun KeyChip(key: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Text(
            key,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun SectionLabel(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun Dot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

/** Slowly drifting, blurred colour blobs used behind the setup screen and header. */
@Composable
fun AnimatedBlobs(modifier: Modifier = Modifier, alpha: Float = 0.45f) {
    val t = rememberInfiniteTransition(label = "blobs")
    val phase by t.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "phase"
    )
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
    )
    Canvas(modifier.fillMaxSize()) {
        colors.forEachIndexed { i, c ->
            val p = phase + i * 2.1f
            val center = Offset(
                size.width * (0.5f + 0.32f * cos(p * (1 + i * 0.3f))),
                size.height * (0.35f + 0.25f * sin(p * (0.8f + i * 0.2f))),
            )
            val r = size.minDimension * (0.55f + 0.08f * i)
            drawCircle(
                Brush.radialGradient(
                    0f to c.copy(alpha = alpha),
                    0.45f to c.copy(alpha = alpha * 0.45f),
                    1f to Color.Transparent,
                    center = center, radius = r,
                ),
                r, center,
            )
        }
    }
}

@Composable
fun pulse(min: Float = 1f, max: Float = 1.06f, millis: Int = 1100): androidx.compose.runtime.State<Float> {
    val t = rememberInfiniteTransition(label = "pulse")
    return t.animateFloat(
        min, max, infiniteRepeatable(tween(millis, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p"
    )
}

private class Particle(
    val vx: Float, val vy: Float, val rot: Float, val vr: Float,
    val color: Int, val w: Float, val h: Float,
)

/** Confetti burst; fires every time [trigger] increments. */
@Composable
fun Confetti(trigger: Int) {
    if (trigger == 0) return
    val palette = listOf(
        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, OverColor,
        DoneColor, Color(0xFFFFD60A), Color(0xFFFF5D8F),
    )
    val particles = remember(trigger) {
        List(150) {
            val angle = Random.nextFloat() * PI.toFloat() * 2
            val speed = 0.35f + Random.nextFloat() * 0.9f
            Particle(
                vx = cos(angle) * speed * 0.7f,
                vy = sin(angle) * speed - 0.9f,
                rot = Random.nextFloat() * 360f,
                vr = (Random.nextFloat() - 0.5f) * 900f,
                color = Random.nextInt(6),
                w = 10f + Random.nextFloat() * 12f,
                h = 6f + Random.nextFloat() * 8f,
            )
        }
    }
    var t by remember(trigger) { mutableFloatStateOf(0f) }
    LaunchedEffect(trigger) {
        val start = withFrameNanos { it }
        while (t < 3f) {
            val now = withFrameNanos { it }
            t = (now - start) / 1_000_000_000f
        }
    }
    if (t >= 3f) return
    Canvas(Modifier.fillMaxSize()) {
        val origin = Offset(size.width / 2, size.height * 0.42f)
        val fade = if (t > 2.2f) 1f - (t - 2.2f) / 0.8f else 1f
        particles.forEach { p ->
            val x = origin.x + p.vx * size.width * t
            val y = origin.y + p.vy * size.height * 0.55f * t + 0.5f * size.height * 0.9f * t * t
            val c = palette[p.color].copy(alpha = fade.coerceIn(0f, 1f))
            rotate(p.rot + p.vr * t, Offset(x, y)) {
                drawRect(c, Offset(x - p.w / 2, y - p.h / 2), Size(p.w, p.h))
            }
        }
    }
}

fun durationLabel(seconds: Long) = formatDuration(seconds)

/** Sprint name + ticket count pill. */
@Composable
fun SprintPill(name: String?, count: Int, modifier: Modifier = Modifier, onGradient: Boolean = false) {
    val bg = if (onGradient) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.tertiaryContainer
    val fg = if (onGradient) Color.White else MaterialTheme.colorScheme.onTertiaryContainer
    Surface(color = bg, contentColor = fg, shape = CircleShape, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.DirectionsRun, null, Modifier.size(16.dp))
            Text(
                (name ?: "Current sprint") + " · $count ${if (count == 1) "ticket" else "tickets"}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
