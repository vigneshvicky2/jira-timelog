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
