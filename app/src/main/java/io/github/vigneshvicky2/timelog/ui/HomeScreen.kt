package io.github.vigneshvicky2.timelog.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vigneshvicky2.timelog.TARGET_SECONDS
import io.github.vigneshvicky2.timelog.TimelogViewModel
import io.github.vigneshvicky2.timelog.data.CalendarStatus
import io.github.vigneshvicky2.timelog.data.Draft
import io.github.vigneshvicky2.timelog.data.Meeting
import io.github.vigneshvicky2.timelog.data.Worklog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

data class EditorRequest(val day: LocalDate, val draft: Draft? = null, val meeting: Meeting? = null)

private val TIME = DateTimeFormatter.ofPattern("h:mm a")
fun LocalTime.label(): String = format(TIME)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: TimelogViewModel) {
    val snackbar = remember { SnackbarHostState() }
    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    var showQuick by remember { mutableStateOf(false) }
    var quickStart by remember { mutableStateOf(vm.today) }
    var showSettings by remember { mutableStateOf(false) }
    var askedCalendar by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshCalendar() }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(vm.calendarStatus) {
        if (vm.calendarStatus == CalendarStatus.NoPermission && !askedCalendar) {
            askedCalendar = true
            perm.launch(Manifest.permission.READ_CALENDAR)
        }
    }
    LaunchedEffect(vm.celebrate) {
        if (vm.celebrate > 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { SubmitBar(vm) },
            contentWindowInsets = WindowInsets(0),
            containerColor = MaterialTheme.colorScheme.surface,
        ) { pad ->
            PullToRefreshBox(
                isRefreshing = vm.loading && vm.loadedOnce,
                onRefresh = { vm.refresh(); vm.refreshCalendar() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 32.dp),
                ) {
                    item(key = "header") { Header(vm) { showSettings = true } }
                    item(key = "strip") { DateStrip(vm) }
                    item(key = "quick") { QuickActions(vm) }
                    when (vm.calendarStatus) {
                        CalendarStatus.NoPermission -> item(key = "cal") {
                            CalendarHint(
                                "See your meetings here",
                                "Allow calendar access to get meetings as one-tap suggestions.",
                                "Allow",
                            ) { perm.launch(Manifest.permission.READ_CALENDAR) }
                        }
                        CalendarStatus.NoCalendars -> item(key = "cal") {
                            val ctx = LocalContext.current
                            CalendarHint(
                                "Turn on calendar sync",
                                "Your work Google account isn't syncing its calendar to this phone yet. Turn on Calendar sync for it, then pull down to refresh.",
                                "Open sync settings",
                            ) { ctx.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS)) }
                        }
                        else -> {}
                    }
                    if (vm.loading && !vm.loadedOnce) {
                        item(key = "loading") {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    items(vm.selected.sorted(), key = { it.toString() }) { d ->
                        DayCard(
                            vm = vm,
                            date = d,
                            onAdd = { quickStart = d; showQuick = true },
                            onEditDraft = { editor = EditorRequest(it.date, draft = it) },
                            onMeeting = { m ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (!vm.quickAddMeeting(m)) editor = EditorRequest(d, meeting = m)
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = showQuick,
            enter = androidx.compose.animation.slideInHorizontally(spring(0.85f, Spring.StiffnessMediumLow)) { it } + fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally(spring(0.9f, Spring.StiffnessMedium)) { it } + fadeOut(),
        ) {
            EntranceScope { QuickLogScreen(vm, quickStart) { showQuick = false } }
        }
        Confetti(vm.celebrate)
    }

    editor?.let { req -> EntranceScope { EntryEditor(vm, req) { editor = null } } }
    if (showSettings) SettingsDialog(vm) { showSettings = false }
}

// ------------------------------------------------------------------ header

@Composable
private fun Header(vm: TimelogViewModel, onSettings: () -> Unit) {
    val days = vm.selected
    val logged = days.sumOf { vm.loggedSeconds(it) }
    val pending = days.sumOf { vm.pendingSeconds(it) }
    val target = TARGET_SECONDS * days.size
    val left = days.sumOf { maxOf(0L, vm.remaining(it)) }
    val over = days.sumOf { maxOf(0L, -vm.remaining(it)) }
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val shade = if (dark) 0.55f else 0f
    val primary = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.primary, Color.Black, shade)
    val tertiary = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.tertiary, Color.Black, shade)
    val hour = LocalTime.now().hour
    val greeting = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
    val firstName = vm.displayName.split(" ").firstOrNull().orEmpty()
    val progress by animateFloatAsState(
        ((logged + pending).toFloat() / target).coerceIn(0f, 1f), spring(0.7f, Spring.StiffnessVeryLow), label = "hdr"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(Brush.linearGradient(listOf(primary, tertiary)))
    ) {
        AnimatedBlobs(alpha = 0.35f)
        Column(Modifier.statusBarsPadding().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).enter(0)) {
                    Text("$greeting,", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium)
                    Text(
                        firstName.ifBlank { "there" },
                        color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings", tint = Color.White) }
            }
            AnimatedVisibility(vm.sprintName != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                SprintPill(vm.sprintName, vm.suggestions.size, Modifier.padding(top = 10.dp), onGradient = true)
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.enter(1)) {
                RollingText(
                    durationLabel(logged + pending),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "of ${durationLabel(target)} · ${days.size} ${if (days.size == 1) "day" else "days"}",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(Modifier.fillMaxWidth(progress).height(10.dp).clip(CircleShape).background(Color.White))
            }
            Spacer(Modifier.height(10.dp))
            RollingText(
                when {
                    over > 0 && left == 0L -> "All done · ${durationLabel(over)} over 8h"
                    left == 0L -> "All selected days are complete"
                    else -> "${durationLabel(left)} left to fill" + if (over > 0) " · ${durationLabel(over)} over" else ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}

// ------------------------------------------------------------------ date strip

@Composable
private fun DateStrip(vm: TimelogViewModel) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, vm.days.size - 6))
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(vm.days, key = { it.toString() }) { d ->
            DayChip(
                date = d,
                selected = d in vm.selected,
                isToday = d == vm.today,
                fraction = (vm.loggedSeconds(d) + vm.pendingSeconds(d)).toFloat() / TARGET_SECONDS,
                over = vm.remaining(d) < 0,
                leave = vm.isLeave(d),
                onClick = { vm.toggleDay(d) },
                onLongClick = { vm.selectOnly(d) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayChip(
    date: LocalDate, selected: Boolean, isToday: Boolean, fraction: Float, over: Boolean, leave: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    val scale by animateFloatAsState(if (selected) 1f else 0.9f, spring(0.45f, Spring.StiffnessMediumLow), label = "s")
    // selection wins; otherwise leave days are teal and weekends rose
    val bg by animateColorAsState(
        when {
            selected -> cs.primary
            leave -> LeaveColor.copy(alpha = 0.18f)
            weekend -> WeekendColor.copy(alpha = 0.14f)
            else -> cs.surfaceContainerHigh
        },
        label = "bg",
    )
    val fg by animateColorAsState(
        when {
            selected -> cs.onPrimary
            leave -> LeaveColor
            weekend -> WeekendColor
            else -> cs.onSurface
        },
        label = "fg",
    )
    val haptics = LocalHapticFeedback.current
    val elevation by androidx.compose.animation.core.animateDpAsState(if (selected) 8.dp else 0.dp, tween(250), label = "el")
    val press = rememberPress(0.92f)
    Column(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(press.modifier)
            .shadow(elevation, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .combinedClickable(
                interactionSource = press.source,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
            )
            .width(56.dp)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
            style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(6.dp))
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            MiniRing(
                fraction,
                color = when { over -> OverColor; selected -> cs.onPrimary; leave -> LeaveColor; else -> cs.primary },
                track = fg.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxSize(),
            )
            Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
            if (leave) Icon(Icons.Rounded.BeachAccess, "Leave", Modifier.size(12.dp), tint = fg)
            else Box(Modifier.size(5.dp).clip(CircleShape).background(if (isToday) fg else Color.Transparent))
        }
    }
}

@Composable
private fun QuickActions(vm: TimelogViewModel) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.primary)
        AssistChip(onClick = { vm.selectOnly(vm.today) }, label = { Text("Today") },
            leadingIcon = { Icon(Icons.Rounded.Today, null, Modifier.size(18.dp)) }, colors = colors, shape = CircleShape)
        AssistChip(onClick = { vm.selectLastWorkdays(3) }, label = { Text("Last 3 workdays") },
            leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(18.dp)) }, colors = colors, shape = CircleShape)
        AssistChip(onClick = { vm.selectThisWeek() }, label = { Text("This week") },
            leadingIcon = { Icon(Icons.Rounded.ViewWeek, null, Modifier.size(18.dp)) }, colors = colors, shape = CircleShape)
        if (vm.timeOff != null) {
            val allLeave = vm.selected.all { vm.leaveSeconds(it) == TARGET_SECONDS }
            AssistChip(
                onClick = { vm.setLeaveForDays(vm.selected, if (allLeave) 0 else TARGET_SECONDS) },
                label = { Text(if (allLeave) "Remove leave" else "Mark leave") },
                leadingIcon = { Icon(Icons.Rounded.BeachAccess, null, Modifier.size(18.dp)) },
                colors = colors, shape = CircleShape,
            )
        }
    }
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Tap to pick several · long-press for one",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Dot(WeekendColor)
        Spacer(Modifier.width(4.dp))
        Text("Weekend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Dot(LeaveColor)
        Spacer(Modifier.width(4.dp))
        Text("Leave", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CalendarHint(title: String, body: String, action: String, onAction: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().enter(2),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Outlined.Sync, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(action)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ day card

@Composable
private fun DayCard(
    vm: TimelogViewModel,
    date: LocalDate,
    onAdd: () -> Unit,
    onEditDraft: (Draft) -> Unit,
    onMeeting: (Meeting) -> Unit,
    modifier: Modifier = Modifier,
) {
    val logged = vm.loggedSeconds(date)
    val pending = vm.pendingSeconds(date)
    val remaining = TARGET_SECONDS - logged - pending
    val dayDrafts = vm.drafts.filter { it.date == date }
    val logs = vm.worklogs[date].orEmpty()
    val meetings = vm.meetings[date].orEmpty()
    var showLogged by rememberSaveable(date.toString()) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Worklog?>(null) }

    ElevatedCard(
        shape = RoundedCornerShape(30.dp),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().enter(1),
    ) {
        Column(Modifier.animateContentSize(spring(0.8f, Spring.StiffnessMediumLow)).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        relativeLabel(date, vm.today),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEEE, d MMM")),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    StatusText(remaining)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Dot(MaterialTheme.colorScheme.primary)
                        Text("Logged ${durationLabel(logged)}", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(6.dp))
                        Dot(MaterialTheme.colorScheme.tertiary)
                        Text("Pending ${durationLabel(pending)}", style = MaterialTheme.typography.labelMedium)
                    }
                }
                ProgressRing(logged, pending, size = 96.dp, stroke = 11.dp) {
                    RollingText(
                        durationLabel(logged + pending),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text("of 8h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (meetings.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionLabel("From your calendar", Icons.Outlined.Event)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(meetings, key = { _, m -> m.key }) { i, m ->
                        MeetingCard(
                            m,
                            added = vm.isMeetingAdded(m),
                            rememberedKey = vm.meetingTicket(m)?.key,
                            modifier = Modifier.enter(i),
                        ) { onMeeting(m) }
                    }
                }
            }

            AnimatedVisibility(
                dayDrafts.isNotEmpty(),
                enter = fadeIn(tween(250, delayMillis = 80)), exit = fadeOut(tween(120)),
            ) {
                Column {
                    Spacer(Modifier.height(18.dp))
                    SectionLabel("Ready to log · swipe to remove")
                    dayDrafts.forEach { d ->
                        key(d.id) { DraftRow(d, onClick = { onEditDraft(d) }, onRemove = { vm.removeDraft(d.id) }, onPeek = { vm.openPeek(d.issue) }) }
                    }
                }
            }

            if (logs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                val rot by animateFloatAsState(if (showLogged) 180f else 0f, label = "rot")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showLogged = !showLogged }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("Already in Jira · ${logs.size}", modifier = Modifier.weight(1f).padding(top = 8.dp))
                    Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(rot), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedVisibility(showLogged, enter = fadeIn(tween(250, delayMillis = 80)), exit = fadeOut(tween(120))) {
                    Column {
                        logs.forEachIndexed { i, w -> LoggedRow(w, Modifier.enter(i, always = true)) { confirmDelete = w } }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            val addPress = rememberPress(0.97f)
            FilledTonalButton(
                onClick = onAdd,
                interactionSource = addPress.source,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).then(addPress.modifier),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add time")
            }
        }
    }

    confirmDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Outlined.DeleteOutline, null) },
            title = { Text("Delete from Jira?") },
            text = { Text("${durationLabel(w.seconds)} on ${w.issueKey} will be removed from Jira.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteWorklog(w); confirmDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun StatusText(remaining: Long) {
    AnimatedContent(
        targetState = remaining,
        transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 }) },
        label = "status",
    ) { r ->
        val (text, color) = when {
            r > 0 -> "${durationLabel(r)} remaining" to MaterialTheme.colorScheme.onSurfaceVariant
            r == 0L -> "8h complete ✓" to DoneColor
            else -> "${durationLabel(-r)} over 8h" to OverColor
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MeetingCard(m: Meeting, added: Boolean, rememberedKey: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (added) cs.primaryContainer else cs.surfaceContainerHigh, label = "mbg")
    val scale by animateFloatAsState(if (added) 0.96f else 1f, spring(0.4f, Spring.StiffnessMedium), label = "ms")
    val press = rememberPress()
    Surface(
        onClick = onClick,
        interactionSource = press.source,
        color = bg,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.width(176.dp).graphicsLayer { scaleX = scale; scaleY = scale }.then(press.modifier),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${m.start.label()} – ${m.end.label()}",
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                AnimatedContent(
                    added,
                    transitionSpec = { scaleIn(spring(0.4f)) togetherWith scaleOut() },
                    label = "madd",
                ) { isAdded ->
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(if (isAdded) cs.primary else cs.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isAdded) Icons.Rounded.Check else Icons.Rounded.Add, null, Modifier.size(16.dp),
                            tint = if (isAdded) cs.onPrimary else cs.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                m.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = cs.tertiaryContainer, shape = CircleShape) {
                    Text(
                        durationLabel(m.minutes * 60L), style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                if (rememberedKey != null) {
                    Text("→ $rememberedKey", style = MaterialTheme.typography.labelSmall, color = cs.primary, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftRow(d: Draft, onClick: () -> Unit, onRemove: () -> Unit, onPeek: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it != SwipeToDismissBoxValue.Settled) { onRemove(); true } else false
    })
    val cs = MaterialTheme.colorScheme
    SwipeToDismissBox(
        state = state,
        modifier = Modifier.padding(vertical = 4.dp).enter(0, always = true),
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(cs.errorContainer).padding(horizontal = 20.dp),
                contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) { Icon(Icons.Rounded.Delete, "Remove", tint = cs.onErrorContainer) }
        },
    ) {
        val press = rememberPress(0.97f)
        val haptics = LocalHapticFeedback.current
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (d.error != null) cs.errorContainer.copy(alpha = 0.6f) else cs.surfaceContainerHighest,
            modifier = press.modifier
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(
                    interactionSource = press.source,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPeek()
                    },
                ),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KeyChip(d.issue.key)
                        Spacer(Modifier.width(8.dp))
                        Text(d.start.label(), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(d.issue.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (d.comment.isNotBlank()) {
                        Text(
                            d.comment, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    d.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error) }
                }
                Spacer(Modifier.width(10.dp))
                Text(durationLabel(d.seconds), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.DeleteOutline, "Remove", tint = cs.error) }
            }
        }
    }
}

@Composable
private fun LoggedRow(w: Worklog, modifier: Modifier = Modifier, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp, 36.dp).clip(CircleShape).background(cs.primary))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.issueKey, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = cs.primary)
                Spacer(Modifier.width(8.dp))
                Text(w.start.label(), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            Text(
                w.comment.ifBlank { w.issueSummary }, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(durationLabel(w.seconds), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Delete", tint = cs.onSurfaceVariant) }
    }
}

// ------------------------------------------------------------------ submit bar

@Composable
private fun SubmitBar(vm: TimelogViewModel) {
    val count = vm.drafts.size
    AnimatedVisibility(
        visible = count > 0 || vm.submitting,
        enter = slideInVertically(spring(0.7f, Spring.StiffnessMediumLow)) { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        val total = vm.drafts.sumOf { it.seconds }
        val dayCount = vm.drafts.map { it.date }.distinct().size
        val pulseScale = pulse(1f, 1.04f, 900)
        val logPress = rememberPress(0.94f)
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 12.dp,
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
        ) {
            Column {
                if (vm.submitting) {
                    val p by animateFloatAsState(vm.submitProgress, label = "sp")
                    LinearProgressIndicator(
                        progress = { p }, modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.inversePrimary, trackColor = Color.Transparent,
                    )
                }
                Row(Modifier.padding(start = 22.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        RollingText(
                            if (vm.submitting) "Logging…" else "$count ${if (count == 1) "entry" else "entries"} ready",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            "${durationLabel(total)} across $dayCount ${if (dayCount == 1) "day" else "days"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = { vm.submitAll() },
                        enabled = !vm.submitting,
                        interactionSource = logPress.source,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.height(52.dp)
                            .graphicsLayer {
                                val s = if (vm.submitting) 1f else pulseScale.value
                                scaleX = s; scaleY = s
                            }
                            .then(logPress.modifier),
                    ) {
                        if (vm.submitting) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Log all")
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(vm: TimelogViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.changeReminderEnabled(true) else vm.toast("Allow notifications to get reminders")
    }
    fun enable(on: Boolean) {
        val granted = ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (on && !granted) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.changeReminderEnabled(on)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Settings, null) },
        title = { Text(vm.displayName.ifBlank { "Settings" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(vm.email, style = MaterialTheme.typography.bodyMedium)
                Text(vm.site, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Calendar: " + when (vm.calendarStatus) {
                        CalendarStatus.Ok -> "connected"
                        CalendarStatus.NoCalendars -> "no synced calendars"
                        CalendarStatus.NoPermission -> "permission not granted"
                        CalendarStatus.Unknown -> "checking…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 18.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Daily reminder", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("A fun nudge to log your time", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(vm.reminderEnabled, { enable(it) })
                }
                AnimatedVisibility(vm.reminderEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(Modifier.padding(top = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Remind me at", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            AssistChip(
                                onClick = { pickTime = true },
                                label = { Text(vm.reminderTime.label()) },
                                leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)) },
                                shape = CircleShape,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Weekdays only", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            androidx.compose.material3.Switch(vm.reminderWeekdaysOnly, { vm.changeReminderWeekdaysOnly(it) })
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Next reminder: ${vm.nextReminderLabel()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = { vm.sendTestReminder() }, contentPadding = PaddingValues(0.dp)) {
                            Text("Send a test reminder")
                        }
                    }
                }

                if (confirm) {
                    Spacer(Modifier.height(8.dp))
                    Text("Tap again to remove your API key from this phone.", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = { if (confirm) { vm.disconnect(); onDismiss() } else confirm = true }) {
                Text("Disconnect", color = MaterialTheme.colorScheme.error)
            }
        },
    )

    if (pickTime) {
        val st = androidx.compose.material3.rememberTimePickerState(vm.reminderTime.hour, vm.reminderTime.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Reminder time") },
            text = { androidx.compose.material3.TimePicker(st) },
            confirmButton = {
                TextButton(onClick = { vm.changeReminderTime(LocalTime.of(st.hour, st.minute)); pickTime = false }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
        )
    }
}

fun relativeLabel(d: LocalDate, today: LocalDate): String {
    val diff = ChronoUnit.DAYS.between(d, today)
    return when {
        diff == 0L -> "TODAY"
        diff == 1L -> "YESTERDAY"
        diff == -1L -> "TOMORROW"
        diff > 1 -> "$diff DAYS AGO"
        else -> "IN ${-diff} DAYS"
    }
}
