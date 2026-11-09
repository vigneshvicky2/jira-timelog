package io.github.vigneshvicky2.timelog.data

import java.time.LocalDate
import java.time.LocalTime

data class Issue(val key: String, val summary: String, val sprint: String? = null)

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
)
