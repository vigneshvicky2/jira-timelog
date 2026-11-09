package io.github.vigneshvicky2.timelog.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Instances
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reads meetings from the phone's calendar (Google Calendar syncs here), so no Google sign-in is needed. */
class CalendarRepo(private val context: Context) {

    fun status(): CalendarStatus {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
            return CalendarStatus.NoPermission
        return if (calendarIds(null).isEmpty()) CalendarStatus.NoCalendars else CalendarStatus.Ok
    }

    /** Visible calendars; when [workDomain] matches some accounts, only those (keeps personal events out). */
    private fun calendarIds(workDomain: String?): Set<Long> {
        val all = mutableListOf<Pair<Long, String>>()
        context.contentResolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID, Calendars.ACCOUNT_NAME),
            "${Calendars.VISIBLE}=1", null, null
        )?.use { c ->
            while (c.moveToNext()) all += c.getLong(0) to (c.getString(1) ?: "")
        }
        val work = workDomain?.let { d -> all.filter { it.second.endsWith("@$d", ignoreCase = true) } }.orEmpty()
        return (work.ifEmpty { all }).map { it.first }.toSet()
    }

    fun meetings(date: LocalDate, zone: ZoneId, workEmail: String): List<Meeting> {
        if (status() != CalendarStatus.Ok) return emptyList()
        val ids = calendarIds(workEmail.substringAfter('@', "").ifBlank { null })
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, dayStart)
            ContentUris.appendId(it, dayEnd)
        }.build()
        val out = mutableListOf<Meeting>()
        context.contentResolver.query(
            uri,
            arrayOf(Instances.TITLE, Instances.BEGIN, Instances.END, Instances.CALENDAR_ID, Instances.SELF_ATTENDEE_STATUS),
            "${Instances.ALL_DAY}=0", null, "${Instances.BEGIN} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getLong(3) !in ids) continue
                if (c.getInt(4) == Attendees.ATTENDEE_STATUS_DECLINED) continue
                val begin = maxOf(c.getLong(1), dayStart)
                val end = minOf(c.getLong(2), dayEnd)
                val minutes = ((end - begin) / 60_000).toInt()
                if (minutes < 5 || minutes > 10 * 60) continue
                out += Meeting(
                    title = c.getString(0)?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotBlank() } ?: "Meeting",
                    date = date,
                    start = Instant.ofEpochMilli(begin).atZone(zone).toLocalTime().withSecond(0).withNano(0),
                    end = Instant.ofEpochMilli(end).atZone(zone).toLocalTime().withSecond(0).withNano(0),
                    minutes = minutes,
                )
            }
        }
        return out.distinctBy { Triple(it.start, it.end, it.title.lowercase()) }
    }
}
