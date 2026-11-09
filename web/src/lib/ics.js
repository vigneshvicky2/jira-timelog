import ICAL from 'ical.js'
import { addDays, fromIso, hhmm, iso } from './time.js'

/** Fetches iCal feeds through the relay and parses them once. */
export async function loadCalendars(urls) {
  const parsed = []
  for (const url of urls) {
    const res = await fetch(`/api/ics?url=${encodeURIComponent(url.trim())}`)
    const text = await res.text()
    if (!res.ok) {
      let msg = `Calendar error (${res.status})`
      try {
        msg = JSON.parse(text).error || msg
      } catch {
        /* keep default */
      }
      throw new Error(msg)
    }
    parsed.push(parse(text))
  }
  return parsed
}

function parse(text) {
  const root = new ICAL.Component(ICAL.parse(text))
  for (const tz of root.getAllSubcomponents('vtimezone')) {
    try {
      ICAL.TimezoneService.register(tz)
    } catch {
      /* already registered */
    }
  }
  const masters = new Map()
  const exceptions = []
  for (const v of root.getAllSubcomponents('vevent')) {
    const e = new ICAL.Event(v)
    if (e.isRecurrenceException()) exceptions.push(e)
    else masters.set(e.uid, e)
  }
  for (const ex of exceptions) {
    const m = masters.get(ex.uid)
    if (m) m.relateException(ex)
    else masters.set(ex.uid + ex.recurrenceId, ex)
  }
  return [...masters.values()]
}

/**
 * Meetings between two dates (inclusive), grouped by local date. Skips all-day, cancelled
 * and declined events, clips to the day and drops anything under 5 minutes or over 10 hours.
 */
export function meetingsBetween(calendars, from, to, email) {
  const start = ICAL.Time.fromJSDate(fromIso(from), false)
  const end = ICAL.Time.fromJSDate(fromIso(addDays(to, 1)), false)
  const me = (email || '').toLowerCase()
  const out = {}
  const add = (summary, comp, s, e) => {
    if (comp.getFirstPropertyValue('status') === 'CANCELLED') return
    if (me) {
      const mine = comp
        .getAllProperties('attendee')
        .find((p) => String(p.getFirstValue()).toLowerCase().replace('mailto:', '') === me)
      if (mine && String(mine.getParameter('partstat')).toUpperCase() === 'DECLINED') return
    }
    const day = iso(s)
    const dayStart = fromIso(day)
    const dayEnd = fromIso(addDays(day, 1))
    const b = new Date(Math.max(s, dayStart))
    const f = new Date(Math.min(e, dayEnd))
    const minutes = Math.round((f - b) / 60000)
    if (minutes < 5 || minutes > 600) return
    const title = (summary || 'Meeting').replace(/\s+/g, ' ').trim()
    const m = { title, date: day, start: hhmm(b), end: hhmm(f), minutes }
    m.key = `${m.date}|${m.start}|${m.title}`
    ;(out[day] ||= []).push(m)
  }

  for (const events of calendars) {
    for (const ev of events) {
      if (ev.startDate?.isDate) continue
      if (ev.isRecurring()) {
        const it = ev.iterator()
        let next
        let guard = 0
        while ((next = it.next()) && guard++ < 5000) {
          if (next.compare(end) >= 0) break
          const det = ev.getOccurrenceDetails(next)
          if (det.endDate.compare(start) <= 0) continue
          add(det.item.summary, det.item.component, det.startDate.toJSDate(), det.endDate.toJSDate())
        }
      } else if (ev.startDate && ev.endDate) {
        if (ev.endDate.compare(start) <= 0 || ev.startDate.compare(end) >= 0) continue
        add(ev.summary, ev.component, ev.startDate.toJSDate(), ev.endDate.toJSDate())
      }
    }
  }
  for (const day of Object.keys(out)) {
    const seen = new Set()
    out[day] = out[day]
      .sort((a, b) => a.start.localeCompare(b.start))
      .filter((m) => {
        const k = `${m.start}|${m.end}|${m.title.toLowerCase()}`
        return !seen.has(k) && seen.add(k)
      })
  }
  return out
}
