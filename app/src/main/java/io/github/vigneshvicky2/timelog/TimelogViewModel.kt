package io.github.vigneshvicky2.timelog

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.vigneshvicky2.timelog.data.CalendarRepo
import io.github.vigneshvicky2.timelog.data.CalendarStatus
import io.github.vigneshvicky2.timelog.data.Draft
import io.github.vigneshvicky2.timelog.data.Issue
import io.github.vigneshvicky2.timelog.data.JiraApi
import io.github.vigneshvicky2.timelog.data.JiraException
import io.github.vigneshvicky2.timelog.data.Meeting
import io.github.vigneshvicky2.timelog.data.Store
import io.github.vigneshvicky2.timelog.data.Worklog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

const val TARGET_SECONDS = 8 * 3600L

class TimelogViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val calendar = CalendarRepo(app)
    private val cache = io.github.vigneshvicky2.timelog.data.Cache(app)
    private val zone: ZoneId = ZoneId.systemDefault()
    private var api: JiraApi? = null
    private var nextId = 1L

    val today: LocalDate = LocalDate.now()
    /** Four weeks back plus a few days ahead. */
    val days: List<LocalDate> = (-27..3).map { today.plusDays(it.toLong()) }

    var configured by mutableStateOf(false); private set
    var displayName by mutableStateOf(""); private set
    var selected by mutableStateOf(setOf(today)); private set
    var worklogs by mutableStateOf<Map<LocalDate, List<Worklog>>>(emptyMap()); private set
    var loading by mutableStateOf(false); private set
    var loadedOnce by mutableStateOf(false); private set
    var meetings by mutableStateOf<Map<LocalDate, List<Meeting>>>(emptyMap()); private set
    var calendarStatus by mutableStateOf(CalendarStatus.Unknown); private set
    val drafts = mutableStateListOf<Draft>()
    var submitting by mutableStateOf(false); private set
    var submitProgress by mutableFloatStateOf(0f); private set
    /** Entries sent so far / in this batch, for the "Logging 3 of 5" count. */
    var submitDone by mutableIntStateOf(0); private set
    var submitTotal by mutableIntStateOf(0); private set
    /** Bumped when entries fail to log, so the UI can buzz. */
    var failures by mutableIntStateOf(0); private set
    /** Set by the launcher shortcut or the reminder's "Log time" action to open Add time. */
    var quickLogRequest by mutableIntStateOf(0); private set
    fun requestQuickLog() { quickLogRequest++ }
    /** Tickets outside the sprint the user keeps handy. */
    var pinned by mutableStateOf(store.pinned); private set
    /** Deletions waiting out their undo window; hidden from the lists meanwhile. */
    private val pendingDeletes = mutableMapOf<String, kotlinx.coroutines.Job>()
    var hiddenWorklogs by mutableStateOf(setOf<String>()); private set
    val undoables = MutableSharedFlow<Undoable>(extraBufferCapacity = 4)
    /** Only tickets assigned to the user in the active sprint. */
    var suggestions by mutableStateOf<List<Issue>>(emptyList()); private set
    var sprintName by mutableStateOf<String?>(null); private set
    /** The active sprint with its dates, for the countdown. */
    var sprint by mutableStateOf<io.github.vigneshvicky2.timelog.data.SprintInfo?>(null); private set
    /** Old and new sprint names when a new sprint started since the last visit; cleared once shown. */
    var sprintChange by mutableStateOf<Pair<String, String>?>(null); private set
    /** False while the launch splash is playing, so celebrations wait for it. */
    var splashDone by mutableStateOf(false); private set
    fun onSplashDone() { splashDone = true }
    fun dismissSprintChange() { sprintChange = null }
    /** The sprint's shared leave ticket (e.g. "Time off"). */
    var timeOff by mutableStateOf<Issue?>(null); private set
    var celebrate by mutableIntStateOf(0); private set
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    var reminderEnabled by mutableStateOf(store.reminderEnabled); private set
    var reminderTime by mutableStateOf(LocalTime.of(store.reminderHour, store.reminderMinute)); private set
    var reminderWeekdaysOnly by mutableStateOf(store.reminderWeekdaysOnly); private set

    fun changeReminderEnabled(on: Boolean) {
        store.reminderEnabled = on; reminderEnabled = on
        io.github.vigneshvicky2.timelog.reminder.Reminders.schedule(getApplication())
        if (on) messages.tryEmit("Daily reminder set for ${nextReminderLabel()}")
    }

    fun changeReminderTime(t: LocalTime) {
        store.reminderHour = t.hour; store.reminderMinute = t.minute; reminderTime = t
        io.github.vigneshvicky2.timelog.reminder.Reminders.schedule(getApplication())
    }

    fun changeReminderWeekdaysOnly(on: Boolean) {
        store.reminderWeekdaysOnly = on; reminderWeekdaysOnly = on
        io.github.vigneshvicky2.timelog.reminder.Reminders.schedule(getApplication())
    }

    fun nextReminderLabel(): String =
        io.github.vigneshvicky2.timelog.reminder.Reminders.nextTrigger(store)
            ?.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")) ?: "off"

    fun sendTestReminder() =
        io.github.vigneshvicky2.timelog.reminder.Reminders.show(getApplication(), kotlin.random.Random.nextInt(30))

    fun toast(text: String) { messages.tryEmit(text) }

    /** Ticket shown in the long-press preview (null = closed). */
    var peek by mutableStateOf<Issue?>(null); private set
    /** Bumped when the open ticket changed in Jira, so the preview reloads. */
    var peekRefresh by mutableStateOf(0); private set
    private val detailsCache = mutableMapOf<String, io.github.vigneshvicky2.timelog.data.IssueDetails>()

    fun openPeek(issue: Issue) { peek = issue }
    fun closePeek() { peek = null }

    suspend fun issueDetails(key: String): Result<io.github.vigneshvicky2.timelog.data.IssueDetails> {
        detailsCache[key]?.let { return Result.success(it) }
        val a = api ?: return Result.failure(IllegalStateException("Not connected"))
        return try {
            val d = a.issueDetails(key)
            detailsCache[key] = d
            Result.success(d)
        } catch (e: Exception) {
            Result.failure(Exception(friendly(e)))
        }
    }

    val site get() = store.site
    val email get() = store.email

    init {
        store.token?.let { token ->
            api = JiraApi(store.site, store.email, token)
            displayName = store.displayName
            configured = true
            // Show the last known state straight away, then refresh quietly.
            cache.loadSnapshot()?.let { applySnapshot(it); loadedOnce = true }
            cache.loadDrafts().let { saved ->
                drafts.addAll(saved)
                nextId = (saved.maxOfOrNull { it.id } ?: 0L) + 1
            }
            refresh()
        }
        viewModelScope.launch {
            androidx.compose.runtime.snapshotFlow { drafts.toList() }
                .collect { list ->
                    withContext(Dispatchers.IO) { cache.saveDrafts(list) }
                    io.github.vigneshvicky2.timelog.widget.TodayWidget.update(getApplication(), todaySummary())
                }
        }
        refreshCalendar()
        io.github.vigneshvicky2.timelog.reminder.Reminders.schedule(app)
    }

    // ---------- account ----------

    /** Returns an error message, or null on success. */
    suspend fun connect(token: String, site: String, email: String): String? {
        val cleanSite = site.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        if (cleanSite.isBlank() || email.isBlank()) return "Enter your Jira site and email."
        if (token.isBlank()) return "Paste your Jira API key first."
        return try {
            val a = JiraApi(cleanSite, email.trim(), token.trim())
            val (id, name) = a.myself()
            store.site = cleanSite
            store.email = email.trim()
            store.token = token.trim()
            store.accountId = id
            store.displayName = name
            api = a
            displayName = name
            configured = true
            refresh()
            refreshCalendar()
            null
        } catch (e: Exception) {
            friendly(e)
        }
    }

    fun disconnect() {
        store.clearAccount()
        cache.clear()
        api = null
        configured = false
        drafts.clear()
        worklogs = emptyMap()
        suggestions = emptyList()
        sprintName = null
        timeOff = null
        loadedOnce = false
    }

    // ---------- loading ----------

    fun refresh() {
        val a = api ?: return
        detailsCache.clear()
        viewModelScope.launch {
            loading = true
            try {
                val logs = a.worklogs(store.accountId, days.first(), days.last(), zone)
                worklogs = logs.groupBy { it.date }.mapValues { (_, v) -> v.sortedBy { it.start } }
                loadedOnce = true
                runCatching { a.currentSprintIssues() }
                    .onSuccess { list ->
                        suggestions = list
                        sprintName = list.mapNotNull { it.sprint }.distinct().joinToString(" · ").ifBlank { null }
                        // The sprint ending soonest is the one the countdown follows.
                        sprint = a.activeSprints.minByOrNull { it.end ?: java.time.Instant.MAX }
                        noteSprint(sprint?.name)
                    }
                    .onFailure { messages.tryEmit("Couldn't load sprint tickets: ${it.message}") }
                val projects = suggestions.map { it.key.substringBefore('-') }.distinct()
                timeOff = a.timeOffIssue(projects)
                val snap = io.github.vigneshvicky2.timelog.data.Cache.Snapshot(logs, suggestions, sprintName, timeOff, sprint)
                withContext(Dispatchers.IO) { cache.saveSnapshot(snap) }
                io.github.vigneshvicky2.timelog.widget.TodayWidget.update(getApplication(), todaySummary())
                loadMeetings(force = true)
            } catch (e: Exception) {
                messages.tryEmit(friendly(e))
            } finally {
                loading = false
            }
        }
    }

    /** Moves a To Do ticket to In Progress in Jira, then reloads so the warning clears. */
    fun moveToInProgress(issue: Issue) {
        val a = api ?: return
        val before = suggestions
        // Update straight away; roll back if Jira refuses.
        suggestions = suggestions.map {
            if (it.key == issue.key) it.copy(status = "In Progress", statusCategory = "indeterminate") else it
        }
        viewModelScope.launch {
            try {
                val status = a.startProgress(issue.key)
                if (status == null) {
                    suggestions = before
                    failures++
                    messages.tryEmit("${issue.key} has no In Progress step in its workflow")
                } else {
                    detailsCache.remove(issue.key)
                    suggestions = suggestions.map {
                        if (it.key == issue.key) it.copy(status = status, statusCategory = "indeterminate") else it
                    }
                    peekRefresh++
                    messages.tryEmit("${issue.key} moved to $status")
                }
            } catch (e: Exception) {
                suggestions = before
                failures++
                messages.tryEmit(friendly(e))
            }
        }
    }

    fun refreshCalendar() {
        viewModelScope.launch {
            calendarStatus = withContext(Dispatchers.IO) { calendar.status() }
            loadMeetings(force = true)
        }
    }

    private fun loadMeetings(force: Boolean = false) {
        if (calendarStatus != CalendarStatus.Ok) return
        val wanted = if (force) selected else selected.filter { it !in meetings.keys }.toSet()
        if (wanted.isEmpty()) return
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { wanted.associateWith { calendar.meetings(it, zone, store.email) } }
            meetings = meetings + loaded
            autoAddMeetings(loaded.values.flatten())
        }
    }

    // ---------- day selection ----------

    fun toggleDay(d: LocalDate) {
        selected = if (d in selected) (if (selected.size > 1) selected - d else selected) else selected + d
        loadMeetings()
    }

    fun selectOnly(d: LocalDate) { selected = setOf(d); loadMeetings() }

    fun selectThisWeek() {
        val monday = today.with(DayOfWeek.MONDAY)
        selected = (0..4).map { monday.plusDays(it.toLong()) }.filter { it <= today }.toSet().ifEmpty { setOf(today) }
        loadMeetings()
    }

    /** The last [n] working days up to and including today. */
    fun selectLastWorkdays(n: Int) {
        val out = mutableListOf<LocalDate>()
        var d = today
        while (out.size < n) {
            if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) out += d
            d = d.minusDays(1)
        }
        selected = out.toSet()
        loadMeetings()
    }

    // ---------- totals ----------

    fun loggedSeconds(d: LocalDate) = logsOn(d).sumOf { it.seconds }
    /** Jira worklogs on [d], minus any waiting out an undo window. */
    fun logsOn(d: LocalDate) = worklogs[d].orEmpty().filter { it.id !in hiddenWorklogs }
    /** Time queued for a ticket across all days that hasn't been sent to Jira yet. */
    fun pendingFor(key: String) = drafts.filter { it.issue.key == key }.sumOf { it.seconds }

    fun pendingSeconds(d: LocalDate) = drafts.filter { it.date == d }.sumOf { it.seconds }
    fun remaining(d: LocalDate) = TARGET_SECONDS - loggedSeconds(d) - pendingSeconds(d)

    // ---------- drafts ----------

    fun addDrafts(
        dates: Collection<LocalDate>,
        issue: Issue,
        secondsFor: (LocalDate) -> Long,
        start: LocalTime,
        comment: String,
        meetingKey: String?,
    ): Int {
        var added = 0
        dates.sorted().forEach { d ->
            val s = secondsFor(d)
            if (s > 0) {
                drafts += Draft(nextId++, d, issue, s, start, comment.trim(), meetingKey)
                added++
            }
        }
        return added
    }

    fun updateDraft(d: Draft) {
        val i = drafts.indexOfFirst { it.id == d.id }
        if (i >= 0) drafts[i] = d.copy(error = null)
    }

    fun removeDraft(id: Long) {
        drafts.firstOrNull { it.id == id }?.meetingKey?.let { dismissedMeetings += it }
        drafts.removeAll { it.id == id }
    }

    /** Meetings the user removed or already sent this session, so auto-add leaves them alone. */
    private val dismissedMeetings = mutableSetOf<String>()

    /** Searches all of Jira (exact key or text), excluding tickets already in the sprint list. */
    suspend fun searchJira(query: String): List<Issue> {
        val a = api ?: return emptyList()
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val out = mutableListOf<Issue>()
        if (Regex("^[A-Za-z][A-Za-z0-9]+-\\d+$").matches(q)) {
            runCatching { a.issue(q.uppercase()) }.getOrNull()?.let { out += it }
        }
        out += runCatching { a.pickIssues(q) }.getOrDefault(emptyList())
        val sprintKeys = suggestions.map { it.key }.toSet()
        return out.distinctBy { it.key }.filter { it.key !in sprintKeys }
    }

    // ---------- leave ----------

    fun isLeave(d: LocalDate): Boolean {
        if (leaveSeconds(d) > 0) return true
        val key = timeOff?.key
        return worklogs[d].orEmpty().any {
            it.issueKey == key || it.issueSummary.replace(" ", "").contains("timeoff", ignoreCase = true)
        }
    }

    fun leaveSeconds(d: LocalDate) = timeOff?.let { ticketSeconds(d, it.key) } ?: 0L

    fun setLeave(d: LocalDate, seconds: Long) {
        val t = timeOff ?: return
        setTicketTime(d, t, seconds)
    }

    fun setLeaveForDays(days: Collection<LocalDate>, seconds: Long, comment: String = "") {
        val t = timeOff ?: run { messages.tryEmit("Couldn't find this sprint's Time off ticket."); return }
        days.forEach {
            setTicketTime(it, t, seconds)
            if (seconds > 0 && comment.isNotBlank()) setTicketComment(it, t.key, comment)
        }
        messages.tryEmit(
            if (seconds > 0) "Marked ${days.size} ${if (days.size == 1) "day" else "days"} as leave on ${t.key}"
            else "Leave removed"
        )
    }

    /** Non-sprint tickets that already have time on [d] (so they stay visible after a search). */
    fun extraTickets(d: LocalDate): List<Issue> {
        val sprintKeys = suggestions.map { it.key }.toSet() + listOfNotNull(timeOff?.key)
        return drafts.filter { it.date == d && it.meetingKey == null && it.issue.key !in sprintKeys }
            .map { it.issue }.distinctBy { it.key }
    }

    // ---------- quick log: one draft per (day, ticket) ----------

    private fun ticketIndex(d: LocalDate, key: String) =
        drafts.indexOfFirst { it.date == d && it.issue.key == key && it.meetingKey == null }

    fun ticketSeconds(d: LocalDate, key: String) = drafts.getOrNull(ticketIndex(d, key))?.seconds ?: 0L
    fun ticketComment(d: LocalDate, key: String) = drafts.getOrNull(ticketIndex(d, key))?.comment ?: ""

    fun setTicketTime(d: LocalDate, issue: Issue, seconds: Long) {
        val i = ticketIndex(d, issue.key)
        when {
            seconds <= 0 -> if (i >= 0) drafts.removeAt(i)
            i >= 0 -> drafts[i] = drafts[i].copy(seconds = seconds, error = null)
            else -> drafts += Draft(nextId++, d, issue, seconds, LocalTime.of(10, 0), "", null)
        }
    }

    fun setTicketComment(d: LocalDate, key: String, text: String) {
        val i = ticketIndex(d, key)
        if (i >= 0) drafts[i] = drafts[i].copy(comment = text)
    }

    /** Replaces the ticket entries on every other selected day with this day's ticket entries. */
    fun copyDayToOthers(from: LocalDate) {
        val src = drafts.filter { it.date == from && it.meetingKey == null }
        val targets = selected.filter { it != from }
        targets.forEach { d ->
            drafts.removeAll { it.date == d && it.meetingKey == null }
            src.forEach { s -> drafts += s.copy(id = nextId++, date = d, error = null) }
        }
        messages.tryEmit("Copied ${src.size} ${if (src.size == 1) "ticket" else "tickets"} to ${targets.size} more ${if (targets.size == 1) "day" else "days"}")
    }

    fun addMeeting(m: Meeting, issue: Issue) {
        rememberMeeting(m.title, issue)
        addDrafts(listOf(m.date), issue, { m.minutes * 60L }, m.start, m.title, m.key)
    }

    fun meetingSeconds(m: Meeting) = drafts.firstOrNull { it.meetingKey == m.key }?.seconds ?: 0L

    fun setMeetingSeconds(m: Meeting, seconds: Long) {
        val i = drafts.indexOfFirst { it.meetingKey == m.key }
        if (i >= 0 && seconds > 0) drafts[i] = drafts[i].copy(seconds = seconds, error = null)
    }

    fun meetingComment(m: Meeting) = drafts.firstOrNull { it.meetingKey == m.key }?.comment ?: ""

    fun setMeetingComment(m: Meeting, text: String) {
        val i = drafts.indexOfFirst { it.meetingKey == m.key }
        if (i >= 0) drafts[i] = drafts[i].copy(comment = text)
    }

    fun rememberedIssue(title: String): Issue? = store.meetingIssue(title)
    fun rememberMeeting(title: String, issue: Issue) = store.rememberMeeting(title, issue)
    fun isMeetingAdded(m: Meeting) = drafts.any { it.meetingKey == m.key }

    /** One-tap add when we already know which ticket this meeting goes to. */
    fun quickAddMeeting(m: Meeting): Boolean {
        if (isMeetingAdded(m)) {
            dismissedMeetings += m.key
            drafts.removeAll { it.meetingKey == m.key }
            return true
        }
        return false // always let the user choose the ticket
    }

    fun meetingTicket(m: Meeting): Issue? = drafts.firstOrNull { it.meetingKey == m.key }?.issue

    /** Filters the current sprint's tickets by key or words in the summary. */
    fun search(query: String): List<Issue> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return suggestions
        return suggestions.filter { i ->
            val hay = "${i.key} ${i.summary}".lowercase()
            words.all { it in hay }
        }
    }

    // ---------- submit ----------

    fun submitAll() {
        val a = api ?: return
        if (submitting || drafts.isEmpty()) return
        viewModelScope.launch {
            submitting = true
            submitProgress = 0f
            val batch = drafts.sortedWith(compareBy({ it.date }, { it.start })).toList()
            submitDone = 0
            submitTotal = batch.size
            var ok = 0
            var total = 0L
            batch.forEachIndexed { i, d ->
                try {
                    a.addWorklog(d, zone)
                    detailsCache.remove(d.issue.key)
                    d.meetingKey?.let { dismissedMeetings += it }
                    drafts.removeAll { it.id == d.id }
                    ok++
                    total += d.seconds
                } catch (e: Exception) {
                    val idx = drafts.indexOfFirst { it.id == d.id }
                    if (idx >= 0) drafts[idx] = d.copy(error = friendly(e))
                }
                submitProgress = (i + 1f) / batch.size
                submitDone = i + 1
            }
            submitting = false
            if (ok == batch.size) {
                celebrate++
                messages.tryEmit("Logged $ok ${if (ok == 1) "entry" else "entries"} · ${formatDuration(total)}")
            } else {
                failures++
                messages.tryEmit("$ok of ${batch.size} logged. Tap Retry on the ones marked in red.")
            }
            refresh()
        }
    }

    /** Sends one failed entry again. */
    fun retryDraft(id: Long) {
        val a = api ?: return
        val d = drafts.firstOrNull { it.id == id } ?: return
        val i = drafts.indexOf(d)
        drafts[i] = d.copy(error = null)
        viewModelScope.launch {
            try {
                a.addWorklog(d, zone)
                detailsCache.remove(d.issue.key)
                drafts.removeAll { it.id == id }
                messages.tryEmit("Logged ${formatDuration(d.seconds)} on ${d.issue.key}")
                refresh()
            } catch (e: Exception) {
                val j = drafts.indexOfFirst { it.id == id }
                if (j >= 0) drafts[j] = d.copy(error = friendly(e))
                failures++
            }
        }
    }

    /**
     * Hides the worklog at once and offers Undo; Jira is only changed once the undo window
     * passes, so an accidental tap costs nothing.
     */
    fun deleteWorklog(w: Worklog) {
        val a = api ?: return
        hiddenWorklogs = hiddenWorklogs + w.id
        undoables.tryEmit(Undoable("Removed ${formatDuration(w.seconds)} from ${w.issueKey}", w.id))
        pendingDeletes[w.id] = viewModelScope.launch {
            kotlinx.coroutines.delay(UNDO_MILLIS)
            pendingDeletes.remove(w.id)
            try {
                a.deleteWorklog(w.issueKey, w.id)
                detailsCache.remove(w.issueKey)
                worklogs = worklogs.mapValues { (_, v) -> v.filterNot { it.id == w.id } }
            } catch (e: Exception) {
                failures++
                messages.tryEmit("Couldn't remove it from Jira. ${friendly(e)}")
            } finally {
                hiddenWorklogs = hiddenWorklogs - w.id
            }
        }
    }

    fun undoDelete(id: String) {
        pendingDeletes.remove(id)?.cancel()
        hiddenWorklogs = hiddenWorklogs - id
    }

    /** Sends any deletions still waiting out their undo window (the user left the app). */
    override fun onCleared() {
        val a = api
        val waiting = pendingDeletes.keys.toList()
        pendingDeletes.values.forEach { it.cancel() }
        if (a != null && waiting.isNotEmpty()) {
            val all = worklogs.values.flatten().filter { it.id in waiting }
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                all.forEach { w -> runCatching { a.deleteWorklog(w.issueKey, w.id) } }
            }
        }
        super.onCleared()
    }

    // ---------- smart logging ----------

    private fun isWorkday(d: LocalDate) = d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY

    private fun isLeaveLog(w: Worklog) =
        w.issueKey == timeOff?.key || w.issueSummary.replace(" ", "").contains("timeoff", ignoreCase = true)

    /** The most recent workday before [d] with tickets logged, and what was logged per ticket. */
    fun lastWorkdayPattern(d: LocalDate): Pair<LocalDate, List<Pair<Issue, Long>>>? {
        var day = d.minusDays(1)
        repeat(14) {
            if (isWorkday(day)) {
                val logs = logsOn(day).filterNot { isLeaveLog(it) }
                if (logs.isNotEmpty()) {
                    val perTicket = logs.groupBy { it.issueKey }.map { (key, v) ->
                        val issue = suggestions.firstOrNull { it.key == key } ?: Issue(key, v.first().issueSummary)
                        issue to v.sumOf { it.seconds }
                    }.sortedByDescending { it.second }
                    return day to perTicket
                }
            }
            day = day.minusDays(1)
        }
        return null
    }

    /** Adds entries on each of [days] matching the last workday, skipping tickets already there. */
    fun copyLastWorkday(days: Collection<LocalDate>) {
        var added = 0
        days.filter { isWorkday(it) && !isLeave(it) }.forEach { d ->
            val (_, pattern) = lastWorkdayPattern(d) ?: return@forEach
            val have = logsOn(d).map { it.issueKey }.toSet() + drafts.filter { it.date == d }.map { it.issue.key }
            pattern.filter { it.first.key !in have }.forEach { (issue, s) ->
                val room = remaining(d)
                if (room > 0) { setTicketTime(d, issue, minOf(s, room)); added++ }
            }
        }
        messages.tryEmit(if (added > 0) "Added $added ${if (added == 1) "entry" else "entries"} from your last workday. Check them before logging." else "Nothing new to copy from your last workday")
    }

    /**
     * Spreads the rest of [d] (up to 8h) across in-progress sprint tickets in 15-minute steps,
     * favouring tickets with the most time left. Never touches To Do, Done or over-estimate tickets.
     */
    fun fillRest(d: LocalDate) {
        val room = remaining(d)
        if (room < 15 * 60) { messages.tryEmit("${relativeName(d)} is already full"); return }
        val pool = suggestions.filter { i ->
            i.statusCategory == "indeterminate" && i.key != timeOff?.key && i.tracking.let { t ->
                val est = t?.estimate?.takeIf { it > 0 }
                est == null || t.spent + pendingFor(i.key) < est
            }
        }.sortedByDescending { it.tracking?.remaining ?: 0L }.take(4)
        if (pool.isEmpty()) { messages.tryEmit("No in-progress tickets with time left to fill with"); return }
        val step = 15 * 60L
        val slots = room / step
        val each = slots / pool.size
        var extra = slots % pool.size
        pool.forEach { i ->
            val s = (each + if (extra-- > 0) 1 else 0) * step
            if (s > 0) setTicketTime(d, i, ticketSeconds(d, i.key) + s)
        }
        messages.tryEmit("Filled ${formatDuration(room / step * step)} across ${pool.size} ${if (pool.size == 1) "ticket" else "tickets"}. Check before logging.")
    }

    private fun relativeName(d: LocalDate) = when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM"))
    }

    // ---------- meetings that are always logged ----------

    fun isAutoMeeting(m: Meeting) = store.isAutoMeeting(m.title)

    fun setAutoMeeting(m: Meeting, on: Boolean) {
        store.setAutoMeeting(m.title, on)
        if (on) autoAddMeetings(listOf(m))
    }

    /** Adds remembered "always log" meetings as entries, unless already added or already in Jira. */
    private fun autoAddMeetings(list: List<Meeting>) {
        // Wait for Jira's worklogs so a meeting that's already logged isn't added twice.
        if (!loadedOnce) return
        list.forEach { m ->
            if (!store.isAutoMeeting(m.title) || isMeetingAdded(m) || m.date > today || m.key in dismissedMeetings) return@forEach
            val issue = store.meetingIssue(m.title) ?: return@forEach
            val already = logsOn(m.date).any { it.issueKey == issue.key && it.comment.equals(m.title, ignoreCase = true) }
            if (!already) addDrafts(listOf(m.date), issue, { m.minutes * 60L }, m.start, m.title, m.key)
        }
    }

    // ---------- pinned tickets ----------

    fun isPinned(key: String) = pinned.any { it.key == key }

    fun togglePin(issue: Issue) {
        pinned = if (isPinned(issue.key)) pinned.filterNot { it.key == issue.key } else pinned + issue
        store.pinned = pinned
    }

    // ---------- sprint ----------

    /** Remembers the sprint and, if it differs from the last one seen, queues the celebration. */
    private fun noteSprint(name: String?) {
        if (name.isNullOrBlank()) return
        val seen = store.seenSprint
        if (seen != null && seen != name) sprintChange = seen to name
        store.seenSprint = name
    }

    /** Working days (Mon-Fri) left in the sprint, counting today; null without an end date. */
    fun sprintWorkdaysLeft(): Int? {
        val end = sprint?.end?.atZone(zone)?.toLocalDate() ?: return null
        if (end < today) return 0
        var n = 0
        var d = today
        while (d <= end) { if (isWorkday(d)) n++; d = d.plusDays(1) }
        return n
    }

    /** How far through the sprint we are, 0..1; null without dates. */
    fun sprintElapsed(): Float? {
        val s = sprint?.start ?: return null
        val e = sprint?.end ?: return null
        val total = (e.toEpochMilli() - s.toEpochMilli()).toFloat()
        if (total <= 0f) return null
        return ((System.currentTimeMillis() - s.toEpochMilli()) / total).coerceIn(0f, 1f)
    }

    // ---------- snapshot ----------

    private fun applySnapshot(s: io.github.vigneshvicky2.timelog.data.Cache.Snapshot) {
        worklogs = s.worklogs.groupBy { it.date }.mapValues { (_, v) -> v.sortedBy { it.start } }
        suggestions = s.suggestions
        sprintName = s.sprintName
        sprint = s.sprint
        timeOff = s.timeOff
    }

    /** Today's numbers for the home-screen widget. */
    fun todaySummary() = io.github.vigneshvicky2.timelog.widget.TodayWidget.Summary(
        logged = loggedSeconds(today), pending = pendingSeconds(today), target = TARGET_SECONDS,
    )

    private fun friendly(e: Exception): String = when (e) {
        is JiraException -> e.message ?: "Jira error"
        is java.net.UnknownHostException, is java.net.ConnectException ->
            "You're offline. Check your connection and retry."
        is java.net.SocketTimeoutException -> "Jira took too long to answer. Retry in a moment."
        is IOException -> "Couldn't reach Jira. Check your connection and retry."
        else -> e.message ?: e.javaClass.simpleName
    }

    companion object {
        const val UNDO_MILLIS = 5000L
    }
}

/** An action that was done optimistically and can still be taken back. */
data class Undoable(val text: String, val id: String)

fun formatDuration(seconds: Long): String {
    val m = kotlin.math.abs(seconds) / 60
    val h = m / 60
    val mm = m % 60
    return when {
        h == 0L -> "${mm}m"
        mm == 0L -> "${h}h"
        else -> "${h}h ${mm}m"
    }
}
