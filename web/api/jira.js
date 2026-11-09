// Vercel Function: relays Jira Cloud REST v3 calls for the Timelog web app.
// Browsers can't call Jira directly (no CORS), so the page calls this same-origin endpoint.
// Only https://<name>.atlassian.net/rest/api/3/* is reachable. Nothing is stored or logged;
// the user's credentials pass through per request.
// vercel.json rewrites /api/jira/<path>?<query> to /api/jira?path=<path>&<query>.

const SITE_RE = /^[a-z0-9][a-z0-9-]{0,62}\.atlassian\.net$/i

const json = (obj, status) =>
  new Response(JSON.stringify(obj), { status, headers: { 'content-type': 'application/json', 'cache-control': 'no-store' } })

async function relay(request) {
  const url = new URL(request.url)
  const site = (request.headers.get('x-jira-site') || '').trim().toLowerCase()
  if (!SITE_RE.test(site)) return json({ errorMessages: ['Invalid Jira site. Use your-team.atlassian.net'] }, 400)
  const auth = request.headers.get('authorization')
  if (!auth || !auth.startsWith('Basic ')) return json({ errorMessages: ['Missing credentials'] }, 401)

  const path = (url.searchParams.get('path') || '').replace(/^\/+/, '')
  if (!path.startsWith('rest/api/3/') || path.includes('..')) return json({ errorMessages: ['Only Jira REST v3 is allowed'] }, 403)
  url.searchParams.delete('path')
  const query = url.searchParams.toString()

  const upstream = await fetch(`https://${site}/${path}${query ? '?' + query : ''}`, {
    method: request.method,
    headers: {
      authorization: auth,
      accept: 'application/json',
      'content-type': 'application/json',
      'x-atlassian-token': 'no-check',
    },
    body: request.method === 'POST' ? await request.text() : undefined,
    redirect: 'manual',
  })
  return new Response(await upstream.arrayBuffer(), {
    status: upstream.status,
    headers: { 'content-type': upstream.headers.get('content-type') || 'application/json', 'cache-control': 'no-store' },
  })
}

export const GET = relay
export const POST = relay
export const DELETE = relay
