package io.github.vigneshvicky2.timelog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.vigneshvicky2.timelog.TimelogViewModel
import io.github.vigneshvicky2.timelog.data.DescBlock
import io.github.vigneshvicky2.timelog.data.Issue
import io.github.vigneshvicky2.timelog.data.IssueDetails
import io.github.vigneshvicky2.timelog.data.Span
import io.github.vigneshvicky2.timelog.data.TicketProgress
import io.github.vigneshvicky2.timelog.formatDuration
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.text.style.TextOverflow

private sealed interface PeekLoad {
    data object Loading : PeekLoad
    data class Ready(val d: IssueDetails) : PeekLoad
    data class Failed(val message: String) : PeekLoad
}

/**
 * Long-press preview of a ticket: the backdrop fades in while the card springs up from slightly
 * below and grows into place; closing reverses it quickly. Tap outside, Back or Close to dismiss.
 */
@Composable
fun IssuePeekHost(vm: TimelogViewModel) {
    val issue = vm.peek
    // keep showing the last ticket while the exit animation runs
    var shown by remember { mutableStateOf<Issue?>(null) }
    if (issue != null) shown = issue

    BackHandler(enabled = issue != null) { vm.closePeek() }

    AnimatedVisibility(
        visible = issue != null,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(200, delayMillis = 40)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.closePeek() },
            contentAlignment = Alignment.Center,
        ) {
            shown?.let { current ->
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 6.dp,
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 24.dp)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .heightIn(max = 640.dp)
                        .animateEnterExit(
                            enter = scaleIn(spring(0.78f, Spring.StiffnessMediumLow), initialScale = 0.86f,
                                transformOrigin = TransformOrigin(0.5f, 0.65f)) +
                                slideInVertically(spring(0.85f, Spring.StiffnessMediumLow)) { it / 10 } +
                                fadeIn(tween(180)),
                            exit = scaleOut(tween(180, easing = FastOutSlowInEasing), targetScale = 0.92f) +
                                slideOutVertically(tween(200)) { it / 14 } +
                                fadeOut(tween(160)),
                        )
                        // swallow taps so they don't reach the backdrop
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                ) {
                    PeekContent(vm, current)
                }
            }
        }
    }
}

@Composable
private fun PeekContent(vm: TimelogViewModel, issue: Issue) {
    var load by remember(issue.key) { mutableStateOf<PeekLoad>(PeekLoad.Loading) }
    LaunchedEffect(issue.key, vm.peekRefresh) {
        load = vm.issueDetails(issue.key).fold({ PeekLoad.Ready(it) }, { PeekLoad.Failed(it.message ?: "Couldn't load") })
    }
    val uri = LocalUriHandler.current
    val cs = MaterialTheme.colorScheme
    val details = (load as? PeekLoad.Ready)?.d

    Column {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyChip(issue.key)
                AnimatedContent(details, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "meta") { d ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (d != null) {
                            if (d.type.isNotBlank()) Text(d.type, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            if (d.status.isNotBlank()) StatusPill(d.status, d.statusCategory)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                details?.summary?.ifBlank { null } ?: issue.summary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            AnimatedContent(
                targetState = load,
                transitionSpec = {
                    (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(tween(300)) { it / 24 }) togetherWith fadeOut(tween(120))
                },
                contentKey = { it::class },
                label = "body",
            ) { state ->
                when (state) {
                    PeekLoad.Loading -> Shimmer()
                    is PeekLoad.Failed -> Row(
                        Modifier.padding(top = 20.dp).clip(RoundedCornerShape(16.dp)).background(cs.errorContainer).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = cs.onErrorContainer)
                        Spacer(Modifier.width(10.dp))
                        Text(state.message, color = cs.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                    is PeekLoad.Ready -> DetailsBody(state.d, vm.pendingFor(issue.key)) { vm.moveToInProgress(issue) }
                }
            }
        }

        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { uri.openUri(details?.url ?: "https://${vm.site}/browse/${issue.key}") }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open in Jira")
            }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = { vm.closePeek() }, shape = RoundedCornerShape(16.dp)) { Text("Close") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsBody(d: IssueDetails, pending: Long, onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column {
        // people & priority
        FlowRow(
            Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            d.assignee?.let { MetaChip(Icons.Outlined.Person, it) }
            d.priority?.let { MetaChip(Icons.Outlined.Flag, it) }
            d.labels.take(4).forEach { MetaChip(null, it) }
        }

        Spacer(Modifier.height(16.dp))
        ProgressCard(d, pending, onStart)

        Spacer(Modifier.height(18.dp))
        SectionLabel("Description")
        if (d.description.isEmpty()) {
            Text(
                "No description on this ticket.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                d.description.forEachIndexed { i, b -> Block(b, Modifier.enter(i, always = true)) }
            }
        }
        d.reporter?.let {
            Spacer(Modifier.height(18.dp))
            Text("Reported by $it", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Status, logged vs estimate, remaining time, and warnings for over-logging or To Do tickets. */
@Composable
private fun ProgressCard(d: IssueDetails, pending: Long, onStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val p = TicketProgress.of(d.tracking, d.statusCategory, pending, ::formatDuration)
    val todo = d.statusCategory == "new"
    val accent = when {
        p.over > 0 || p.afterOver > 0 -> OverColor
        todo && (p.spent > 0 || pending > 0) -> WeekendColor
        d.statusCategory == "done" -> DoneColor
        else -> cs.primary
    }
    val flagged = accent == OverColor || accent == WeekendColor
    val anim = remember(d.key) { Animatable(0f) }
    LaunchedEffect(d.key, p.fraction) { anim.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cs.surfaceContainer,
        border = BorderStroke(1.dp, if (flagged) accent.copy(alpha = 0.55f) else cs.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionLabel("Progress")
            Row(Modifier.fillMaxWidth()) {
                Stat("Status", d.status.ifBlank { "—" }, Modifier.weight(1.3f))
                Stat("Logged", formatDuration(p.spent), Modifier.weight(1f))
                Stat("Estimate", p.estimate?.let(::formatDuration) ?: "—", Modifier.weight(1f))
                if (p.over > 0) Stat("Over by", formatDuration(p.over), Modifier.weight(1f), OverColor)
                else Stat("Left", p.remaining?.let(::formatDuration) ?: "—", Modifier.weight(1f))
            }
            if (p.total != null && p.total > 0) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
                    val fill = p.fraction * anim.value
                    val pend = p.pendingFraction * anim.value
                    if (fill > 0f) Box(Modifier.fillMaxWidth(fill).height(8.dp).clip(CircleShape).background(accent))
                    if (pend > 0f) Box(
                        Modifier.fillMaxWidth((pend / (1f - fill).coerceAtLeast(0.001f)).coerceAtMost(1f)).height(8.dp)
                            .background(cs.primary.copy(alpha = 0.35f))
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val caption = buildString {
                if (p.total != null && p.total > 0) append("${(p.spent * 100 / p.total)}% of ${formatDuration(p.total)} used")
                else append("No estimate set")
                if (pending > 0) {
                    append(" · ${formatDuration(pending)} pending")
                    p.afterRemaining?.let { append(", ${formatDuration(it)} left after logging") }
                }
            }
            Text(caption, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            p.warnings.forEach { (kind, text) ->
                val c = when (kind) {
                    TicketProgress.WarnKind.Over -> OverColor
                    TicketProgress.WarnKind.Status -> WeekendColor
                    TicketProgress.WarnKind.Info -> cs.onSurfaceVariant
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.14f)).padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        if (kind == TicketProgress.WarnKind.Info) Icons.Outlined.Info else Icons.Outlined.Flag,
                        null, Modifier.size(16.dp).padding(top = 1.dp), tint = c,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                }
            }
            if (todo && (p.spent > 0 || pending > 0)) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.Button(
                    onClick = onStart,
                    shape = RoundedCornerShape(16.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = TodoColor, contentColor = Color(0xFF2A0A12)),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Move to In Progress", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Compact status · remaining · logged/estimate line with a thin bar, for ticket cards. */
@Composable
fun TicketProgressStrip(status: String, statusCategory: String, tracking: io.github.vigneshvicky2.timelog.data.TimeTracking, pending: Long) {
    val cs = MaterialTheme.colorScheme
    val p = TicketProgress.of(tracking, statusCategory, pending, ::formatDuration)
    val todo = statusCategory == "new"
    val accent = when {
        p.over > 0 || p.afterOver > 0 -> OverColor
        todo -> WeekendColor
        statusCategory == "done" -> DoneColor
        else -> cs.primary
    }
    val fill by androidx.compose.animation.core.animateFloatAsState(p.fraction, tween(600), label = "fill")
    val pend by androidx.compose.animation.core.animateFloatAsState(p.pendingFraction, tween(600), label = "pend")
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = accent.copy(alpha = 0.16f), contentColor = accent, shape = CircleShape) {
                Text(
                    status.ifBlank { "—" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 130.dp).padding(horizontal = 8.dp, vertical = 3.dp),
                    softWrap = false,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    p.over > 0 -> "${formatDuration(p.over)} over"
                    p.remaining != null -> "${formatDuration(p.remaining)} left"
                    else -> "No estimate"
                },
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = if (p.over > 0) OverColor else cs.onSurface,
                maxLines = 1, softWrap = false,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                p.estimate?.let { "${formatDuration(p.spent)} of ${formatDuration(it)}" }
                    ?: if (p.spent > 0) "${formatDuration(p.spent)} logged" else "",
                style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        if (p.total != null && p.total > 0) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
                if (fill > 0f) Box(Modifier.fillMaxWidth(fill).height(4.dp).background(accent))
                if (pend > 0f) Box(
                    Modifier.fillMaxWidth((pend / (1f - fill).coerceAtLeast(0.001f)).coerceAtMost(1f)).height(4.dp)
                        .background(cs.primary.copy(alpha = 0.35f))
                )
            }
        }
        if (todo && (p.spent > 0 || pending > 0)) {
            Spacer(Modifier.height(6.dp))
            Text("Ticket is still To Do", style = MaterialTheme.typography.labelSmall, color = WeekendColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, color: Color = Color.Unspecified) {
    Column(modifier.padding(end = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Block(b: DescBlock, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val body = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface)
    @Composable
    fun rich(spans: List<Span>, style: androidx.compose.ui.text.TextStyle, m: Modifier = Modifier) {
        val text = annotated(spans, cs.primary, cs.surfaceContainerHighest)
        @Suppress("DEPRECATION")
        ClickableText(text, style = style, modifier = m) { offset ->
            text.getStringAnnotations("url", offset, offset).firstOrNull()?.let { runCatching { uri.openUri(it.item) } }
        }
    }
    when (b) {
        is DescBlock.Heading -> rich(
            b.spans,
            (if (b.level <= 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
                .copy(fontWeight = FontWeight.Bold, color = cs.onSurface),
            modifier.padding(top = 6.dp),
        )
        is DescBlock.Paragraph -> rich(b.spans, body, modifier)
        is DescBlock.Item -> Row(modifier.padding(start = (b.depth * 18).dp)) {
            Text(b.marker, style = body.copy(color = cs.primary, fontWeight = FontWeight.Bold), modifier = Modifier.width(26.dp))
            rich(b.spans, body, Modifier.weight(1f))
        }
        is DescBlock.Code -> Text(
            b.text,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = cs.onSurface),
            modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHighest).padding(14.dp),
        )
        is DescBlock.Quote -> Row(modifier) {
            Box(Modifier.width(3.dp).height(22.dp).clip(CircleShape).background(cs.primary))
            Spacer(Modifier.width(12.dp))
            rich(b.spans, body.copy(color = cs.onSurfaceVariant), Modifier.weight(1f))
        }
        DescBlock.Rule -> HorizontalDivider(modifier.padding(vertical = 4.dp), color = cs.outlineVariant)
    }
}

private fun annotated(spans: List<Span>, link: Color, codeBg: Color): AnnotatedString = buildAnnotatedString {
    spans.forEach { s ->
        val style = SpanStyle(
            fontWeight = if (s.bold) FontWeight.Bold else null,
            fontStyle = if (s.italic) FontStyle.Italic else null,
            fontFamily = if (s.code) FontFamily.Monospace else null,
            background = if (s.code) codeBg else Color.Unspecified,
            color = if (s.link != null) link else Color.Unspecified,
            textDecoration = when {
                s.link != null -> TextDecoration.Underline
                s.strike -> TextDecoration.LineThrough
                else -> null
            },
        )
        if (s.link != null) pushStringAnnotation("url", s.link)
        withStyle(style) { append(s.text) }
        if (s.link != null) pop()
    }
}

@Composable
private fun StatusPill(name: String, category: String) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (category) {
        "done" -> DoneColor.copy(alpha = 0.18f) to DoneColor
        "indeterminate" -> cs.primaryContainer to cs.onPrimaryContainer
        else -> cs.surfaceContainerHighest to cs.onSurfaceVariant
    }
    Surface(color = bg, contentColor = fg, shape = CircleShape) {
        Text(
            name.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MetaChip(icon: androidx.compose.ui.graphics.vector.ImageVector?, text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Placeholder lines that gently pulse while the ticket loads. */
@Composable
private fun Shimmer() {
    val a by rememberInfiniteTransition(label = "shimmer").animateFloat(
        0.35f, 0.8f, infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a"
    )
    val c = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(110, 80).forEach { w ->
                Box(Modifier.width(w.dp).height(28.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(c))
            }
        }
        Spacer(Modifier.height(8.dp))
        listOf(1f, 0.92f, 0.97f, 0.6f).forEach { f ->
            Box(Modifier.fillMaxWidth(f).height(14.dp).graphicsLayer { alpha = a }.clip(RoundedCornerShape(7.dp)).background(c))
        }
    }
}
