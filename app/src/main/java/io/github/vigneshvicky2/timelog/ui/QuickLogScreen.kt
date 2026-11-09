package io.github.vigneshvicky2.timelog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vigneshvicky2.timelog.TARGET_SECONDS
import io.github.vigneshvicky2.timelog.TimelogViewModel
import io.github.vigneshvicky2.timelog.data.Issue
import io.github.vigneshvicky2.timelog.data.Meeting
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val TAB = DateTimeFormatter.ofPattern("EEE d")
private val LONG = DateTimeFormatter.ofPattern("EEEE, d MMM")
private val SIDE = 20.dp

/**
 * One screen to fill a whole day: every sprint ticket has inline +time buttons and meetings are
 * one-tap toggles. The remaining time stays pinned at the top while scrolling.
 */
@Composable
fun QuickLogScreen(vm: TimelogViewModel, startDay: LocalDate, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val days = vm.selected.sorted()
    var day by remember { mutableStateOf(if (startDay in days) startDay else days.first()) }
    var pickFor by remember { mutableStateOf<Meeting?>(null) }
    val haptics = LocalHapticFeedback.current
    var query by remember { mutableStateOf("") }
    var remote by remember { mutableStateOf<List<Issue>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        remote = emptyList()
        if (query.trim().length < 2) { searching = false; return@LaunchedEffect }
        searching = true
        try {
            kotlinx.coroutines.delay(350)
            remote = vm.searchJira(query)
        } finally { searching = false }
    }
    val search = SearchState(query, { query = it }, remote, searching)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 4.dp) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        Modifier.padding(start = 4.dp, end = SIDE, top = 6.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Add time", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            RollingText(
                                day.format(LONG),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (days.size > 1) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = SIDE, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(days, key = { it.toString() }) { d ->
                                DayTab(vm, d, d == day) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    day = d
                                }
                            }
                        }
                    }
                    RemainingHeader(vm, day)
                }
            }
        },
        bottomBar = { DoneBar(vm, onDone) },
    ) { pad ->
        AnimatedContent(
            targetState = day,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(320)) { it * dir / 3 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it * dir / 3 } + fadeOut(tween(200)))
            },
            label = "day",
        ) { d ->
            DayPage(vm, d, pad, search, onPickTicket = { pickFor = it })
        }
    }

    pickFor?.let { m ->
        AlertDialog(
            onDismissRequest = { pickFor = null },
            icon = { Icon(Icons.Outlined.Event, null) },
            title = { Text("Which ticket for this meeting?") },
            text = {
                Column {
                    Text(m.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    val last = vm.rememberedIssue(m.title)
                    val options = listOfNotNull(last?.let { l -> vm.suggestions.firstOrNull { it.key == l.key } ?: l }) +
                        vm.suggestions.filter { it.key != last?.key }
                    options.forEach { issue ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .clickable { vm.addMeeting(m, issue); pickFor = null }
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            KeyChip(issue.key)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(issue.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (issue.key == last?.key) {
                                    Text("Last used", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    Text(
                        "Your last choice for this meeting is shown first.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickFor = null }) { Text("Cancel") } },
        )
    }
}

/** Pinned summary: big remaining time, a two-tone progress bar and a legend. */
@Composable
private fun RemainingHeader(vm: TimelogViewModel, day: LocalDate) {
    val cs = MaterialTheme.colorScheme
    val logged = vm.loggedSeconds(day)
    val pending = vm.pendingSeconds(day)
    val total = logged + pending
    val remaining = TARGET_SECONDS - total
    val over = remaining < 0
    val loggedF by animateFloatAsState(
        (logged.toFloat() / TARGET_SECONDS).coerceIn(0f, 1f), spring(0.7f, Spring.StiffnessLow), label = "lf"
    )
    val totalF by animateFloatAsState(
        (total.toFloat() / TARGET_SECONDS).coerceIn(0f, 1f), spring(0.7f, Spring.StiffnessLow), label = "tf"
    )
    val accent by animateColorAsState(
        when { over -> OverColor; remaining == 0L -> DoneColor; else -> cs.primary }, label = "acc"
    )

    Column(Modifier.padding(start = SIDE, end = SIDE, top = 8.dp, bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            RollingText(
                when { remaining > 0 -> durationLabel(remaining); remaining == 0L -> "8h"; else -> "+" + durationLabel(-remaining) },
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = accent,
            )
            Spacer(Modifier.width(10.dp))
            RollingText(
                when { remaining > 0 -> "left today"; remaining == 0L -> "complete ✓"; else -> "over 8h" },
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${durationLabel(total)} / 8h",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
            Box(
                Modifier.fillMaxWidth(totalF).height(12.dp).clip(CircleShape)
                    .background(if (over) OverColor.copy(alpha = 0.5f) else cs.tertiary)
            )
            Box(Modifier.fillMaxWidth(loggedF).height(12.dp).clip(CircleShape).background(accent))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(accent)
            Spacer(Modifier.width(6.dp))
            Text("In Jira ${durationLabel(logged)}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.width(18.dp))
            Dot(if (over) OverColor.copy(alpha = 0.5f) else cs.tertiary)
            Spacer(Modifier.width(6.dp))
            Text("Adding ${durationLabel(pending)}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
    }
}

private class SearchState(
    val query: String,
    val onQuery: (String) -> Unit,
    val remote: List<Issue>,
    val searching: Boolean,
)

@Composable
private fun DayPage(
    vm: TimelogViewModel,
    day: LocalDate,
    pad: PaddingValues,
    search: SearchState,
    onPickTicket: (Meeting) -> Unit,
) {
    val meetings = vm.meetings[day].orEmpty()
    val remaining = vm.remaining(day)
    val others = vm.selected.size - 1
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SIDE, end = SIDE,
            top = pad.calculateTopPadding() + 20.dp,
            bottom = pad.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (vm.timeOff != null) {
            item(key = "leave") { Box(Modifier.animateItem().enter(0)) { LeaveCard(vm, day) } }
        }
        if (meetings.isNotEmpty()) {
            item(key = "mh") { SectionHeader("Meetings", "Tap to include", Icons.Outlined.Event) }
            itemsIndexed(meetings, key = { _, m -> m.key }) { i, m ->
                MeetingRow(vm, m, Modifier.animateItem().enter(i)) {
                    if (!vm.quickAddMeeting(m)) onPickTicket(m)
                }
            }
            item(key = "gap") { Spacer(Modifier.height(10.dp)) }
        }

        item(key = "search") {
            OutlinedTextField(
                value = search.query,
                onValueChange = search.onQuery,
                placeholder = { Text("Search any Jira ticket or key") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    when {
                        search.searching -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        search.query.isNotEmpty() -> IconButton(onClick = { search.onQuery("") }) { Icon(Icons.Rounded.Close, "Clear") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            )
        }

        val searchingNow = search.query.isNotBlank()
        val sprintShown = if (searchingNow) vm.search(search.query) else vm.suggestions
        val extras = vm.extraTickets(day).filter { e -> search.remote.none { it.key == e.key } }

        item(key = "th") { SectionHeader("Sprint tickets", vm.sprintName ?: "Current sprint", Icons.Rounded.DirectionsRun) }
        if (searchingNow && sprintShown.isEmpty()) {
            item(key = "nosprint") {
                Text(
                    "No sprint tickets match.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 30.dp),
                )
            }
        }
        if (!searchingNow && vm.suggestions.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    if (vm.loading) CircularProgressIndicator()
                    else Text("No tickets assigned to you in the current sprint.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        itemsIndexed(sprintShown, key = { _, it -> it.key }) { i, issue ->
            TicketCard(
                issue = issue,
                seconds = vm.ticketSeconds(day, issue.key),
                comment = vm.ticketComment(day, issue.key),
                free = remaining,
                onSet = { vm.setTicketTime(day, issue, it) },
                onComment = { vm.setTicketComment(day, issue.key, it) },
                modifier = Modifier.animateItem().enter(i + meetings.size),
            )
        }

        if (extras.isNotEmpty()) {
            item(key = "xh") { SectionHeader("Other tickets", "Outside the sprint, added by you", Icons.Rounded.Search) }
            itemsIndexed(extras, key = { _, it -> "x-" + it.key }) { i, issue ->
                TicketCard(
                    issue = issue,
                    seconds = vm.ticketSeconds(day, issue.key),
                    comment = vm.ticketComment(day, issue.key),
                    free = remaining,
                    onSet = { vm.setTicketTime(day, issue, it) },
                    onComment = { vm.setTicketComment(day, issue.key, it) },
                    modifier = Modifier.animateItem().enter(i, always = true),
                )
            }
        }
        if (searchingNow) {
            item(key = "rh") { SectionHeader("All of Jira", "Results for “${search.query.trim()}”", Icons.Rounded.Search) }
            if (search.remote.isEmpty()) {
                item(key = "rempty") {
                    Text(
                        when {
                            search.searching -> "Searching…"
                            search.query.trim().length < 2 -> "Type at least 2 characters."
                            else -> "No other tickets found."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 30.dp),
                    )
                }
            }
            itemsIndexed(search.remote, key = { _, it -> "r-" + it.key }) { i, issue ->
                TicketCard(
                    issue = issue,
                    seconds = vm.ticketSeconds(day, issue.key),
                    comment = vm.ticketComment(day, issue.key),
                    free = remaining,
                    onSet = { vm.setTicketTime(day, issue, it) },
                    onComment = { vm.setTicketComment(day, issue.key, it) },
                    modifier = Modifier.animateItem().enter(i, always = true),
                )
            }
        }

        if (others > 0) {
            item(key = "copy") {
                OutlinedButton(
                    onClick = { vm.copyDayToOthers(day) },
                    enabled = vm.drafts.any { it.date == day && it.meetingKey == null },
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Copy ${day.format(TAB)}'s tickets to the other $others ${if (others == 1) "day" else "days"}")
                }
            }
        }
    }
}

@Composable
private fun LeaveCard(vm: TimelogViewModel, day: LocalDate) {
    val t = vm.timeOff ?: return
    val cs = MaterialTheme.colorScheme
    val secs = vm.leaveSeconds(day)
    val active = secs > 0
    val half = TARGET_SECONDS / 2
    val bg by animateColorAsState(if (active) cs.primaryContainer else cs.surfaceContainerHigh, label = "lv")
    val haptics = LocalHapticFeedback.current
    val count = vm.selected.size
    fun pick(v: Long) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        vm.setLeave(day, if (secs == v) 0 else v)
    }

    Surface(shape = RoundedCornerShape(26.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.BeachAccess, null, Modifier.size(24.dp), tint = cs.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("On leave?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Logs to ${t.key} · ${t.summary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                val delA by animateFloatAsState(if (active) 1f else 0f, tween(200), label = "lda")
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = { vm.setLeave(day, 0) },
                        enabled = active,
                        modifier = Modifier.graphicsLayer {
                            alpha = delA
                            val sc = 0.7f + 0.3f * delA
                            scaleX = sc; scaleY = sc
                        },
                    ) { Icon(Icons.Outlined.DeleteOutline, "Remove leave", tint = cs.error) }
                }
            }
            Spacer(Modifier.height(16.dp))
            var customLeave by remember { mutableStateOf(false) }
            val isCustom = active && secs != TARGET_SECONDS && secs != half
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LeaveOption("Full day", "8h", secs == TARGET_SECONDS, Modifier.weight(1f)) { pick(TARGET_SECONDS) }
                LeaveOption("Half day", "4h", secs == half, Modifier.weight(1f)) { pick(half) }
                LeaveOption("Custom", if (isCustom) durationLabel(secs) else "set time", isCustom, Modifier.weight(1f)) {
                    customLeave = true
                }
            }
            if (customLeave) {
                CustomTimeDialog(if (isCustom) secs else 0L, onDismiss = { customLeave = false }) {
                    vm.setLeave(day, it); customLeave = false
                }
            }
            AnimatedVisibility(active, enter = SmoothExpand, exit = SmoothCollapse) {
                Column {
                    OutlinedTextField(
                        value = vm.ticketComment(day, t.key),
                        onValueChange = { vm.setTicketComment(day, t.key, it) },
                        placeholder = { Text("Reason / description (optional)") },
                        maxLines = 3,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                    if (count > 1) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.setLeaveForDays(vm.selected, secs, vm.ticketComment(day, t.key)) }) {
                            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Apply ${durationLabel(secs)} leave to all $count days")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaveOption(title: String, sub: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) cs.primary else cs.surface, tween(220), label = "lo")
    val fg by animateColorAsState(if (selected) cs.onPrimary else cs.onSurface, tween(220), label = "lof")
    val press = rememberPress()
    Surface(
        onClick = onClick,
        interactionSource = press.source,
        shape = RoundedCornerShape(18.dp),
        color = bg,
        contentColor = fg,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant),
        modifier = modifier.then(press.modifier),
    ) {
        Column(Modifier.padding(vertical = 14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(selected) {
                    Row { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
                }
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(sub, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.padding(top = 4.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 30.dp, top = 2.dp),
        )
    }
}

@Composable
private fun DayTab(vm: TimelogViewModel, d: LocalDate, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val r = vm.remaining(d)
    val bg by animateColorAsState(if (selected) cs.primary else cs.surfaceContainerHighest, label = "tb")
    val fg by animateColorAsState(if (selected) cs.onPrimary else cs.onSurface, label = "tf")
    val scale by animateFloatAsState(if (selected) 1f else 0.95f, spring(0.5f, Spring.StiffnessMediumLow), label = "ts")
    val press = rememberPress(0.92f)
    Column(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(press.modifier)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(
                interactionSource = press.source,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(d.format(TAB), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = fg)
        Spacer(Modifier.height(2.dp))
        RollingText(
            when { r > 0 -> "${durationLabel(r)} left"; r == 0L -> "done ✓"; else -> "+${durationLabel(-r)}" },
            style = MaterialTheme.typography.labelSmall,
            color = if (r < 0 && !selected) OverColor else fg.copy(alpha = 0.85f),
        )
    }
}

private val SmoothExpand = expandVertically(spring(1f, Spring.StiffnessMediumLow), expandFrom = Alignment.Top) +
    fadeIn(tween(220, delayMillis = 60))
private val SmoothCollapse = shrinkVertically(spring(1f, Spring.StiffnessMedium), shrinkTowards = Alignment.Top) +
    fadeOut(tween(120))

@Composable
private fun MeetingRow(vm: TimelogViewModel, m: Meeting, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val added = vm.isMeetingAdded(m)
    val ticket = vm.meetingTicket(m)
    val bg by animateColorAsState(if (added) cs.primaryContainer else cs.surfaceContainerHigh, tween(220), label = "mr")
    val circle by animateColorAsState(if (added) cs.primary else cs.surfaceContainerHighest, tween(220), label = "mrc")
    val haptics = LocalHapticFeedback.current
    val press = rememberPress(0.97f)
    Surface(
        onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        interactionSource = press.source,
        shape = RoundedCornerShape(24.dp),
        color = bg,
        modifier = modifier.fillMaxWidth().then(press.modifier),
    ) {
        Column {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(circle), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        added,
                        transitionSpec = { (scaleIn(tween(180)) + fadeIn(tween(180))) togetherWith (scaleOut(tween(120)) + fadeOut(tween(120))) },
                        label = "mc",
                    ) { on ->
                        Icon(if (on) Icons.Rounded.Check else Icons.Rounded.Add, null, Modifier.size(20.dp),
                            tint = if (on) cs.onPrimary else cs.primary)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${m.start.label()} – ${m.end.label()}" + (ticket?.let { "  ·  ${it.key}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                RollingText(
                    durationLabel(vm.meetingSeconds(m).takeIf { it > 0 } ?: (m.minutes * 60L)),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            AnimatedVisibility(added, enter = SmoothExpand, exit = SmoothCollapse) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) {
                    DurationChips(
                        seconds = vm.meetingSeconds(m),
                        free = null,
                        onSet = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.setMeetingSeconds(m, it) },
                    )
                    OutlinedTextField(
                        value = vm.meetingComment(m),
                        onValueChange = { vm.setMeetingComment(m, it) },
                        placeholder = { Text("Description (optional)") },
                        maxLines = 3,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TicketCard(
    issue: Issue,
    seconds: Long,
    comment: String,
    free: Long,
    onSet: (Long) -> Unit,
    onComment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val active = seconds > 0
    val bg by animateColorAsState(if (active) cs.primaryContainer else cs.surfaceContainerHigh, tween(220), label = "tc")
    val numColor by animateColorAsState(
        if (active) cs.onPrimaryContainer else cs.onSurfaceVariant.copy(alpha = 0.45f), tween(220), label = "nc"
    )
    val activeF by animateFloatAsState(if (active) 1f else 0f, tween(200), label = "af")
    val haptics = LocalHapticFeedback.current

    // gentle pop on increase only (no snapping)
    val bump = remember { Animatable(1f) }
    var last by remember { mutableStateOf(seconds) }
    LaunchedEffect(seconds) {
        if (seconds > last) {
            bump.animateTo(1.07f, tween(90))
            bump.animateTo(1f, spring(0.55f, Spring.StiffnessMediumLow))
        }
        last = seconds
    }
    fun set(v: Long) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSet(maxOf(0L, v)) }

    Surface(shape = RoundedCornerShape(26.dp), color = bg, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    KeyChip(issue.key)
                    Spacer(Modifier.height(8.dp))
                    Text(issue.summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                AnimatedContent(
                    targetState = seconds,
                    contentAlignment = Alignment.CenterEnd,
                    transitionSpec = {
                        val up = targetState > initialState
                        ((slideInVertically(tween(220)) { h -> if (up) h / 2 else -h / 2 } + fadeIn(tween(220))) togetherWith
                            (slideOutVertically(tween(160)) { h -> if (up) -h / 2 else h / 2 } + fadeOut(tween(140))))
                            .using(androidx.compose.animation.SizeTransform(clip = false))
                    },
                    label = "secs",
                ) { s ->
                    Text(
                        if (s > 0) durationLabel(s) else "0m",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = numColor,
                        modifier = Modifier.graphicsLayer { scaleX = bump.value; scaleY = bump.value },
                    )
                }
                // fixed-size slot so the title never reflows when the delete button appears
                Box(Modifier.padding(start = 4.dp).size(40.dp), contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = { set(0) },
                        enabled = active,
                        modifier = Modifier.graphicsLayer {
                            alpha = activeF
                            val sc = 0.7f + 0.3f * activeF
                            scaleX = sc; scaleY = sc
                        },
                    ) { Icon(Icons.Outlined.DeleteOutline, "Remove", tint = cs.error) }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))

            DurationChips(seconds = seconds, free = free, onSet = { set(it) })

            AnimatedVisibility(active, enter = SmoothExpand, exit = SmoothCollapse) {
                OutlinedTextField(
                    value = comment,
                    onValueChange = onComment,
                    placeholder = { Text("What did you work on? (optional)") },
                    maxLines = 3,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
        }
    }
}

private val PRESET_MINUTES = listOf(15, 30, 45, 60, 90, 120, 180, 240)

/** Custom · presets · Fill day. Presets set the exact time; Custom opens an hours/minutes input. */
@Composable
private fun DurationChips(seconds: Long, free: Long?, onSet: (Long) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    val isPreset = PRESET_MINUTES.any { it * 60L == seconds }
    val customOn = seconds > 0 && !isPreset
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeChip(
            icon = Icons.Rounded.Edit,
            label = if (customOn) durationLabel(seconds) else "Custom",
            selected = customOn,
            onClick = { custom = true },
        )
        PRESET_MINUTES.forEach { m ->
            TimeChip(label = durationLabel(m * 60L), selected = seconds == m * 60L, onClick = { onSet(m * 60L) })
        }
        if (free != null) {
            TimeChip(label = "Fill day", strong = true, enabled = free > 0, onClick = { onSet(seconds + free) })
        }
    }
    if (custom) {
        CustomTimeDialog(if (customOn) seconds else 0L, onDismiss = { custom = false }) { onSet(it); custom = false }
    }
}

/** Hours + minutes input. */
@Composable
internal fun CustomTimeDialog(initial: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var h by remember { mutableStateOf(if (initial > 0) (initial / 3600).toString() else "") }
    var m by remember { mutableStateOf(if (initial > 0) ((initial % 3600) / 60).toString() else "") }
    val hours = h.toIntOrNull() ?: 0
    val mins = m.toIntOrNull() ?: 0
    val total = hours * 3600L + mins * 60L
    val valid = total > 0 && mins < 60 && hours <= 24
    val numbers = androidx.compose.foundation.text.KeyboardOptions(
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Schedule, null) },
        title = { Text("Custom time") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = h, onValueChange = { h = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hours") }, singleLine = true, keyboardOptions = numbers,
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = m, onValueChange = { m = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minutes") }, singleLine = true, keyboardOptions = numbers,
                        isError = mins >= 60,
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        mins >= 60 -> "Minutes must be under 60"
                        valid -> "Logs ${durationLabel(total)}"
                        else -> "Enter hours and/or minutes"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mins >= 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(total) }, enabled = valid) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TimeChip(
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    strong: Boolean = false,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val alpha by animateFloatAsState(if (enabled) 1f else 0.38f, tween(200), label = "chip")
    val bg by animateColorAsState(
        when { selected -> cs.primary; strong -> cs.tertiary; else -> cs.surface }, tween(200), label = "cbg"
    )
    val fg by animateColorAsState(
        when { selected -> cs.onPrimary; strong -> cs.onTertiary; else -> cs.onSurface }, tween(200), label = "cfg"
    )
    val press = rememberPress(0.9f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = press.source,
        shape = CircleShape,
        color = bg,
        contentColor = fg,
        modifier = Modifier.graphicsLayer { this.alpha = alpha }.then(press.modifier),
    ) {
        Row(
            Modifier.height(44.dp).padding(horizontal = if (label != null) 18.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(20.dp))
            if (icon != null && label != null) Spacer(Modifier.width(4.dp))
            if (label != null) Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DoneBar(vm: TimelogViewModel, onDone: () -> Unit) {
    val count = vm.drafts.size
    val total = vm.drafts.sumOf { it.seconds }
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 12.dp,
        modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                RollingText(
                    if (count == 0) "Nothing added yet" else "$count ${if (count == 1) "entry" else "entries"} · ${durationLabel(total)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(2.dp))
                Text("Review and log on the home screen", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            val donePress = rememberPress(0.94f)
            Button(
                onClick = onDone,
                interactionSource = donePress.source,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.height(52.dp).then(donePress.modifier),
            ) {
                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Done")
            }
        }
    }
}
