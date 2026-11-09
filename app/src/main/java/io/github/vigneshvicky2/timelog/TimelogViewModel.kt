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
    /** Only tickets assigned to the user in the active sprint. */
    var suggestions by mutableStateOf<List<Issue>>(emptyList()); private set
    var sprintName by mutableStateOf<String?>(null); private set
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

    val site get() = store.site
    val email get() = store.email

    init {
        store.token?.let { token ->
            api = JiraApi(store.site, store.email, token)
            displayName = store.displayName
            configured = true
            refresh()
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
                    }
                    .onFailure { messages.tryEmit("Couldn't load sprint tickets: ${it.message}") }
                val projects = suggestions.map { it.key.substringBefore('-') }.distinct()
                timeOff = a.timeOffIssue(projects)
            } catch (e: Exception) {
                messages.tryEmit(friendly(e))
            } finally {
                loading = false
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

    fun loggedSeconds(d: LocalDate) = worklogs[d].orEmpty().sumOf { it.seconds }
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

    fun removeDraft(id: Long) { drafts.removeAll { it.id == id } }

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
            var ok = 0
            var total = 0L
            batch.forEachIndexed { i, d ->
                try {
                    a.addWorklog(d, zone)
                    drafts.removeAll { it.id == d.id }
                    ok++
                    total += d.seconds
                } catch (e: Exception) {
                    val idx = drafts.indexOfFirst { it.id == d.id }
                    if (idx >= 0) drafts[idx] = d.copy(error = friendly(e))
                }
                submitProgress = (i + 1f) / batch.size
            }
            submitting = false
            if (ok == batch.size) {
                celebrate++
                messages.tryEmit("Logged $ok ${if (ok == 1) "entry" else "entries"} · ${formatDuration(total)}")
            } else {
                messages.tryEmit("$ok of ${batch.size} logged. Check the ones marked in red.")
            }
            refresh()
        }
    }

    fun deleteWorklog(w: Worklog) {
        val a = api ?: return
        viewModelScope.launch {
            try {
                a.deleteWorklog(w.issueKey, w.id)
                worklogs = worklogs.mapValues { (_, v) -> v.filterNot { it.id == w.id } }
                messages.tryEmit("Removed ${formatDuration(w.seconds)} from ${w.issueKey}")
            } catch (e: Exception) {
                messages.tryEmit(friendly(e))
            }
        }
    }

    private fun friendly(e: Exception): String = when (e) {
        is JiraException -> e.message ?: "Jira error"
        is java.net.UnknownHostException -> "No internet connection."
        is IOException -> "Network problem: ${e.message}"
        else -> e.message ?: e.javaClass.simpleName
    }
}

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
