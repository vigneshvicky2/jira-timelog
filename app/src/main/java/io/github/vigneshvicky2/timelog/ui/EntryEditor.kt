package io.github.vigneshvicky2.timelog.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vigneshvicky2.timelog.TimelogViewModel
import io.github.vigneshvicky2.timelog.data.Issue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val PRESETS = listOf(15, 30, 45, 60, 90, 120, 180, 240)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryEditor(vm: TimelogViewModel, req: EditorRequest, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val editing = req.draft
    val meeting = req.meeting

    var issue by remember { mutableStateOf(editing?.issue ?: meeting?.let { vm.rememberedIssue(it.title) }) }
    var days by remember { mutableStateOf(setOf(editing?.date ?: req.day)) }
    var minutes by remember { mutableIntStateOf(editing?.let { (it.seconds / 60).toInt() } ?: meeting?.minutes ?: 60) }
    var fill by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf(editing?.start ?: meeting?.start ?: LocalTime.of(10, 0)) }
    var comment by remember { mutableStateOf(editing?.comment ?: meeting?.title ?: "") }
    var rememberIt by remember { mutableStateOf(true) }
    var picking by remember { mutableStateOf(issue == null || (meeting != null && editing == null)) }
    var showTime by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }

    // Time still free on a day, not counting the draft being edited.
    fun free(d: LocalDate) = vm.remaining(d) + (if (editing != null && editing.date == d) editing.seconds else 0L)

    fun close() { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() } }

    fun save() {
        val i = issue ?: return
        if (editing != null) {
            val secs = if (fill) maxOf(free(editing.date), 0L) else minutes * 60L
            if (secs > 0) vm.updateDraft(editing.copy(issue = i, seconds = secs, start = start, comment = comment.trim()))
            else vm.removeDraft(editing.id)
        } else {
            vm.addDrafts(days, i, { d -> if (fill) maxOf(free(d), 0L) else minutes * 60L }, start, comment, meeting?.key)
            if (meeting != null && rememberIt) vm.rememberMeeting(meeting.title, i)
        }
        close()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                when { editing != null -> "Edit entry"; meeting != null -> "Log meeting"; else -> "New entry" },
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            )
            if (meeting != null) {
                Text(
                    "${meeting.title} · ${meeting.start.label()} – ${meeting.end.label()}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(18.dp))

            // ---- ticket
            SectionLabel("Ticket", Icons.Outlined.ConfirmationNumber)
            IssueField(issue, picking) { picking = !picking }
            AnimatedVisibility(picking, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                IssueSearch(vm) { issue = it; picking = false }
            }

            // ---- days
            if (editing == null && vm.selected.size > 1) {
                Spacer(Modifier.height(18.dp))
                SectionLabel("Add to days")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    vm.selected.sorted().forEach { d ->
                        val on = d in days
                        FilterChip(
                            selected = on,
                            onClick = { days = if (on) (if (days.size > 1) days - d else days) else days + d },
                            label = {
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    Text(d.format(DateTimeFormatter.ofPattern("EEE d")), fontWeight = FontWeight.SemiBold)
                                    Text(freeLabel(free(d)), style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            leadingIcon = if (on) {{ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }} else null,
                            shape = RoundedCornerShape(14.dp),
                        )
                    }
                }
                TextButton(onClick = { days = vm.selected }) { Text("Select all ${vm.selected.size} days") }
            }

            // ---- duration
            Spacer(Modifier.height(18.dp))
            SectionLabel("Time spent", Icons.Outlined.Schedule)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { minutes = maxOf(15, (if (fill) 60 else minutes) - 15); fill = false }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.Remove, "Less")
                }
                var last by remember { mutableIntStateOf(minutes) }
                val up = minutes >= last
                LaunchedEffect(minutes) { last = minutes }
                AnimatedContent(
                    targetState = if (fill) "Fill the day" else durationLabel(minutes * 60L),
                    transitionSpec = {
                        val dir = if (up) 1 else -1
                        (slideInVertically(spring(0.7f, Spring.StiffnessMedium)) { it * dir } + fadeIn()) togetherWith
                            (slideOutVertically { -it * dir } + fadeOut())
                    },
                    modifier = Modifier.weight(1f),
                    label = "dur",
                ) {
                    Text(
                        it, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
                FilledTonalIconButton(onClick = { minutes = (if (fill) 60 else minutes) + 15; fill = false }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.Add, "More")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESETS.forEach { m ->
                    FilterChip(
                        selected = !fill && minutes == m,
                        onClick = { minutes = m; fill = false },
                        label = { Text(durationLabel(m * 60L)) },
                        shape = CircleShape,
                    )
                }
                FilterChip(
                    selected = fill,
                    onClick = { fill = true },
                    label = { Text("Fill remaining") },
                    shape = CircleShape,
                )
                FilterChip(
                    selected = !fill && minutes !in PRESETS,
                    onClick = { showCustom = true },
                    label = { Text(if (!fill && minutes !in PRESETS) "Custom · ${durationLabel(minutes * 60L)}" else "Custom") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)) },
                    shape = CircleShape,
                )
            }
            if (fill) {
                val targetDays = if (editing != null) setOf(editing.date) else days
                Text(
                    targetDays.sorted().joinToString("  ·  ") { d ->
                        d.format(DateTimeFormatter.ofPattern("EEE d")) + ": " + durationLabel(maxOf(free(d), 0L))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // ---- start + comment
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Started at", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = { showTime = true },
                    label = { Text(start.label()) },
                    leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)) },
                    shape = CircleShape,
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("What did you work on? (optional)") },
                minLines = 2,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            if (meeting != null && editing == null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Always log “${meeting.title}” to this ticket",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    )
                    Switch(rememberIt, { rememberIt = it })
                }
            }

            Spacer(Modifier.height(20.dp))
            val n = if (editing != null) 1 else days.size
            Button(
                onClick = { save() },
                enabled = issue != null,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(58.dp),
            ) {
                Text(
                    when {
                        issue == null -> "Pick a ticket first"
                        editing != null -> "Save changes"
                        n > 1 -> "Add to $n days"
                        else -> "Add entry"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (editing != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { vm.removeDraft(editing.id); close() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete entry", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (showCustom) {
        CustomTimeDialog(if (fill) 0L else minutes * 60L, onDismiss = { showCustom = false }) {
            minutes = (it / 60).toInt(); fill = false; showCustom = false
        }
    }
    if (showTime) {
        val st = rememberTimePickerState(start.hour, start.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = { TextButton(onClick = { start = LocalTime.of(st.hour, st.minute); showTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(st) },
        )
    }
}

private fun freeLabel(free: Long) = when {
    free > 0 -> "${durationLabel(free)} free"
    free == 0L -> "full"
    else -> "${durationLabel(-free)} over"
}

@Composable
private fun IssueField(issue: Issue?, open: Boolean, onClick: () -> Unit) {
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "rot")
    val press = rememberPress(0.98f)
    Surface(
        onClick = onClick,
        interactionSource = press.source,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().then(press.modifier),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(issue, label = "issue", modifier = Modifier.weight(1f)) { i ->
                if (i == null) {
                    Text("Choose a ticket", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column {
                        KeyChip(i.key)
                        Spacer(Modifier.height(4.dp))
                        Text(i.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            androidx.compose.material3.Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(rot))
        }
    }
}

@Composable
private fun IssueSearch(vm: TimelogViewModel, onPick: (Issue) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(vm.suggestions) }
    var remote by remember { mutableStateOf<List<Issue>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val busy = searching || (vm.loading && vm.suggestions.isEmpty())
    LaunchedEffect(query, vm.suggestions) { results = vm.search(query) }
    LaunchedEffect(query) {
        remote = emptyList()
        if (query.trim().length < 2) { searching = false; return@LaunchedEffect }
        searching = true
        try { delay(350); remote = vm.searchJira(query) } finally { searching = false }
    }
    val multiSprint = vm.suggestions.mapNotNull { it.sprint }.distinct().size > 1
    Column(Modifier.padding(top = 10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search sprint or any Jira ticket") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        SprintPill(vm.sprintName, vm.suggestions.size, Modifier.padding(start = 4.dp, bottom = 6.dp))
        if (results.isEmpty() && remote.isEmpty() && !busy) {
            Text(
                if (query.isBlank()) "No tickets assigned to you in the current sprint." else "No sprint tickets match “$query”.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
        (results + remote).take(40).forEachIndexed { i, it ->
            key(it.key) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .enter(i, always = true)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onPick(it) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KeyChip(it.key)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(it.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (multiSprint && it.sprint != null) {
                            Text(it.sprint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
        }
    }
}

