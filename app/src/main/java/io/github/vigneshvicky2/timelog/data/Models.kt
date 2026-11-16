package io.github.vigneshvicky2.timelog.data

import java.time.LocalDate
import java.time.LocalTime

data class Issue(
    val key: String,
    val summary: String,
    val sprint: String? = null,
    val status: String = "",
    /** Jira status category key: "new", "indeterminate" or "done". */
    val statusCategory: String = "",
    /** Only filled for sprint tickets (fetched together with the sprint). */
    val tracking: TimeTracking? = null,
)

data class Worklog(
    val id: String,
    val issueKey: String,
    val issueSummary: String,
    val date: LocalDate,
    val start: LocalTime,
    val seconds: Long,
    val comment: String,
)

data class Meeting(
    val title: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val minutes: Int,
) {
    /** Stable identity used to tell whether a meeting has already been turned into an entry. */
    val key: String get() = "$date|$start|$title"
}

data class Draft(
    val id: Long,
    val date: LocalDate,
    val issue: Issue,
    val seconds: Long,
    val start: LocalTime,
    val comment: String,
    val meetingKey: String? = null,
    val error: String? = null,
)

enum class CalendarStatus { Unknown, NoPermission, NoCalendars, Ok }

/** A run of description text with its inline formatting. */
data class Span(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val link: String? = null,
)

/** One block of a ticket's description, flattened from Jira's rich-text (ADF) document. */
sealed interface DescBlock {
    data class Heading(val spans: List<Span>, val level: Int) : DescBlock
    data class Paragraph(val spans: List<Span>) : DescBlock
    data class Item(val spans: List<Span>, val marker: String, val depth: Int) : DescBlock
    data class Code(val text: String) : DescBlock
    data class Quote(val spans: List<Span>) : DescBlock
    data object Rule : DescBlock
}

data class IssueDetails(
    val key: String,
    val summary: String,
    val type: String,
    val status: String,
    /** Jira status category key: "new", "indeterminate" or "done". */
    val statusCategory: String,
    val priority: String?,
    val assignee: String?,
    val reporter: String?,
    val labels: List<String>,
    val description: List<DescBlock>,
    val url: String,
    val tracking: TimeTracking = TimeTracking(),
)

/** Jira time tracking in seconds; null when the ticket has no value. */
data class TimeTracking(val estimate: Long? = null, val remaining: Long? = null, val spent: Long = 0)

/**
 * Progress against the estimate plus warnings for the easy mistakes: logging past the estimate
 * and logging on a ticket that is still To Do. [pending] is queued time not yet sent to Jira.
 */
data class TicketProgress(
    val spent: Long,
    val estimate: Long?,
    val remaining: Long?,
    val total: Long?,
    val over: Long,
    val pending: Long,
    val afterRemaining: Long?,
    val afterOver: Long,
    val fraction: Float,
    val pendingFraction: Float,
    val warnings: List<Pair<WarnKind, String>>,
) {
    enum class WarnKind { Over, Status, Info }

    companion object {
        fun of(t: TimeTracking, statusCategory: String, pending: Long, fmt: (Long) -> String): TicketProgress {
            val spent = t.spent
            val estimate = t.estimate?.takeIf { it > 0 }
            val remaining = t.remaining ?: estimate?.let { maxOf(0L, it - spent) }
            val total = estimate ?: remaining?.let { spent + it }
            val over = estimate?.let { maxOf(0L, spent - it) } ?: 0L
            val afterRemaining = remaining?.let { maxOf(0L, it - pending) }
            val afterOver = estimate?.let { maxOf(0L, spent + pending - it) } ?: 0L
            val fraction = if (total != null && total > 0) (spent.toFloat() / total).coerceIn(0f, 1f) else 0f
            val pendingFraction = if (total != null && total > 0) (pending.toFloat() / total).coerceIn(0f, 1f - fraction) else 0f
            val todo = statusCategory == "new"
            val w = mutableListOf<Pair<WarnKind, String>>()
            if (todo && spent > 0) w += WarnKind.Status to "${fmt(spent)} is logged but the ticket is still To Do. Move it to In Progress or check those entries."
            if (todo && pending > 0) w += WarnKind.Status to "You're about to log ${fmt(pending)} on a ticket that is still To Do."
            if (statusCategory == "done" && pending > 0) w += WarnKind.Status to "This ticket is already Done; you're about to log ${fmt(pending)} more."
            if (over > 0) w += WarnKind.Over to "Over-logged by ${fmt(over)}: ${fmt(spent)} logged against a ${fmt(estimate!!)} estimate."
            if (afterOver > over) w += WarnKind.Over to "Your pending ${fmt(pending)} would take it ${fmt(afterOver)} over the estimate."
            if (estimate == null && remaining == null) w += WarnKind.Info to "No estimate on this ticket, so remaining time is unknown."
            return TicketProgress(spent, estimate, remaining, total, over, pending, afterRemaining, afterOver, fraction, pendingFraction, w)
        }
    }
}
