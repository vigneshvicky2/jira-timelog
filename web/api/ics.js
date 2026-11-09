// Vercel Function: fetches a calendar's iCal feed for the Timelog web app (no CORS on feeds).
// Only Google Calendar and Outlook hosts are allowed, so this can't be used as an open proxy.

const ICS_HOSTS = new Set(['calendar.google.com', 'outlook.office365.com', 'outlook.office.com', 'outlook.live.com'])

const json = (obj, status) =>
  new Response(JSON.stringify(obj), { status, headers: { 'content-type': 'application/json', 'cache-control': 'no-store' } })

export async function GET(request) {
  let target
  try {
    target = new URL(new URL(request.url).searchParams.get('url') || '')
  } catch {
    return json({ error: 'Invalid calendar link' }, 400)
  }
  if (target.protocol === 'webcal:') target = new URL('https:' + target.href.slice('webcal:'.length))
  if (target.protocol !== 'https:' || !ICS_HOSTS.has(target.hostname)) {
    return json({ error: 'Use a Google Calendar or Outlook iCal link' }, 400)
  }
  const upstream = await fetch(target.toString(), { redirect: 'follow' })
  if (!upstream.ok) return json({ error: `Calendar returned ${upstream.status}. Check the link.` }, 502)
  return new Response(await upstream.text(), {
    headers: { 'content-type': 'text/calendar; charset=utf-8', 'cache-control': 'no-store' },
  })
}
