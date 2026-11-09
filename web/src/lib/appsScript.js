// Google Apps Script the user pastes into script.google.com when their Workspace admin has
// disabled the calendar's "Secret address". It runs as the user, reads only their own
// calendar, and returns an iCal feed of meeting titles and times for Timelog.
export const CALENDAR_SCRIPT = `// Timelog calendar feed: shares your meeting titles and times with Timelog.
// Deploy > New deployment > Web app > Execute as: Me, Who has access: Anyone.
// Anyone with the deployment URL can read these titles/times, so keep it private.
const DAYS_BACK = 45
const DAYS_AHEAD = 30

function doGet() {
  const now = new Date()
  const start = new Date(now.getTime() - DAYS_BACK * 864e5)
  const end = new Date(now.getTime() + DAYS_AHEAD * 864e5)
  const events = CalendarApp.getDefaultCalendar().getEvents(start, end)
  const stamp = (d) => Utilities.formatDate(d, 'UTC', "yyyyMMdd'T'HHmmss'Z'")
  const esc = (s) => String(s || '').replace(/\\\\/g, '\\\\\\\\').replace(/;/g, '\\\\;').replace(/,/g, '\\\\,').replace(/\\r?\\n/g, ' ')
  const out = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Timelog//Calendar feed//EN']
  events.forEach((ev) => {
    if (ev.isAllDayEvent()) return
    if (ev.getMyStatus() === CalendarApp.GuestStatus.NO) return
    out.push(
      'BEGIN:VEVENT',
      'UID:' + esc(ev.getId()) + '-' + ev.getStartTime().getTime(),
      'DTSTAMP:' + stamp(now),
      'DTSTART:' + stamp(ev.getStartTime()),
      'DTEND:' + stamp(ev.getEndTime()),
      'SUMMARY:' + esc(ev.getTitle()),
      'END:VEVENT',
    )
  })
  out.push('END:VCALENDAR')
  return ContentService.createTextOutput(out.join('\\r\\n')).setMimeType(ContentService.MimeType.ICAL)
}
`
