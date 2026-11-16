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
val DoneColor = Color(0xFF3DD68C)
val WeekendColor = Color(0xFFF2708A)
val LeaveColor = Color(0xFF2DD4BF)
/** The orange slash in the logo; used sparingly for the add button. */
val BrandColor = Color(0xFFFF6B35)
val TodoColor = WeekendColor

private val StintDark = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF7D9BFF), onPrimary = Color(0xFF0D1430),
    primaryContainer = Color(0xFF232B47), onPrimaryContainer = Color(0xFFC3D0FF),
    inversePrimary = Color(0xFF2F55E4),
    secondary = Color(0xFFC9C6BF), onSecondary = Color(0xFF111113),
    secondaryContainer = Color(0xFF2A2A31), onSecondaryContainer = Color(0xFFF3F1EC),
    tertiary = Color(0xFF3E4E86), onTertiary = Color(0xFFF3F1EC),
    tertiaryContainer = Color(0xFF1F2236), onTertiaryContainer = Color(0xFFC3D0FF),
    background = Color(0xFF111113), onBackground = Color(0xFFF3F1EC),
    surface = Color(0xFF111113), onSurface = Color(0xFFF3F1EC),
    surfaceVariant = Color(0xFF24242A), onSurfaceVariant = Color(0xFFA29F98),
    surfaceContainerLowest = Color(0xFF0C0C0E), surfaceContainerLow = Color(0xFF18181C),
    surfaceContainer = Color(0xFF1B1B1F), surfaceContainerHigh = Color(0xFF212127),
    surfaceContainerHighest = Color(0xFF2A2A31),
    inverseSurface = Color(0xFFF3F1EC), inverseOnSurface = Color(0xFF111113),
    outline = Color(0xFF4A4A52), outlineVariant = Color(0xFF2E2E35),
    error = Color(0xFFFF6B6B), onError = Color(0xFF2A0A0A),
    errorContainer = Color(0xFF3A1A1E), onErrorContainer = Color(0xFFFFC7C7),
)

private val StintLight = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF2F55E4), onPrimary = Color.White,
    primaryContainer = Color(0xFFE7ECFD), onPrimaryContainer = Color(0xFF1E3DB0),
    inversePrimary = Color(0xFF7D9BFF),
    secondary = Color(0xFF4A4742), onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE8DF), onSecondaryContainer = Color(0xFF16151F),
    tertiary = Color(0xFFA9B9F3), onTertiary = Color(0xFF16151F),
    tertiaryContainer = Color(0xFFE7ECFD), onTertiaryContainer = Color(0xFF1E3DB0),
    background = Color(0xFFF6F4EF), onBackground = Color(0xFF16151F),
    surface = Color(0xFFF6F4EF), onSurface = Color(0xFF16151F),
    surfaceVariant = Color(0xFFECE8DF), onSurfaceVariant = Color(0xFF66625C),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFFDF9),
    surfaceContainer = Color(0xFFFFFDF9), surfaceContainerHigh = Color(0xFFF3F0E9),
    surfaceContainerHighest = Color(0xFFECE8DF),
    inverseSurface = Color(0xFF16151F), inverseOnSurface = Color(0xFFF3F1EC),
    outline = Color(0xFFB9B3A7), outlineVariant = Color(0xFFE6E1D7),
    error = Color(0xFFC62E3C), onError = Color.White,
    errorContainer = Color(0xFFFBE4E8), onErrorContainer = Color(0xFF8E1E37),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun bricolage(w: Int) = androidx.compose.ui.text.font.Font(
    io.github.vigneshvicky2.timelog.R.font.bricolage,
    weight = FontWeight(w),
    variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
        androidx.compose.ui.text.font.FontVariation.weight(w),
    ),
)

/** Bricolage Grotesque for headings and big numbers; body text stays on the system sans. */
val Display = androidx.compose.ui.text.font.FontFamily(bricolage(600), bricolage(700), bricolage(800))

private fun stintType(): androidx.compose.material3.Typography {
    val t = androidx.compose.material3.Typography()
    fun androidx.compose.ui.text.TextStyle.d(w: FontWeight = FontWeight.ExtraBold, ls: Float = -0.02f) =
        copy(fontFamily = Display, fontWeight = w, letterSpacing = androidx.compose.ui.unit.TextUnit(ls, androidx.compose.ui.unit.TextUnitType.Em))
    return t.copy(
        displayLarge = t.displayLarge.d(), displayMedium = t.displayMedium.d(), displaySmall = t.displaySmall.d(ls = -0.03f),
        headlineLarge = t.headlineLarge.d(), headlineMedium = t.headlineMedium.d(), headlineSmall = t.headlineSmall.d(),
        titleLarge = t.titleLarge.d(), titleMedium = t.titleMedium.d(FontWeight.Bold, -0.01f),
    )
}

@Composable
fun TimelogTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) StintDark else StintLight
    MaterialTheme(colorScheme = scheme, typography = stintType(), content = content)
}

/** The Stint mark: four tally strokes crossed by an orange slash, on a dark tile. */
@Composable
fun StintLogo(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).clip(androidx.compose.foundation.shape.RoundedCornerShape(size * 0.28f)).background(Color(0xFF1E1E24))) {
        val k = this.size.width / 108f
        val cream = Color(0xFFF4EFE6)
        listOf(floatArrayOf(33f, 30.5f, 32f, 77f), floatArrayOf(45.5f, 28f, 45f, 79f), floatArrayOf(58f, 30f, 58.5f, 77.5f), floatArrayOf(70.5f, 28.5f, 70f, 79f))
            .forEach { l -> drawLine(cream, Offset(l[0] * k, l[1] * k), Offset(l[2] * k, l[3] * k), strokeWidth = 7.5f * k, cap = StrokeCap.Round) }
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(22f * k, 66f * k); cubicTo(42f * k, 58f * k, 62f * k, 47f * k, 86f * k, 36f * k) }
        drawPath(path, BrandColor, style = Stroke(8.5f * k, cap = StrokeCap.Round))
    }
}

/** "Built by" credit shown on setup and in settings. */
@Composable
fun BuiltBy(modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        "Built by Vignesh Kodiswaran",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
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
