import { addDays, fromIso, jiraStarted, parseStarted } from './time.js'
import { TRACKING_FIELDS, trackingOf } from './progress.js'

export class JiraError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

/** Jira Cloud REST v3 client that talks through the same-origin relay (/api/jira). */
export function createJira({ site, email, token }) {
  const auth = 'Basic ' + btoa(unescape(encodeURIComponent(`${email}:${token}`)))
  let sprintField

  async function request(method, path, body) {
    let res
    try {
      res = await fetch(`/api/jira/${path}`, {
        method,
        headers: { authorization: auth, 'x-jira-site': site, 'content-type': 'application/json' },
        body: body ? JSON.stringify(body) : undefined,
      })
    } catch {
      throw new JiraError(0, OFFLINE)
    }
    const text = await res.text()
    if (!res.ok) throw new JiraError(res.status, errorMessage(res.status, text))
    return text ? JSON.parse(text) : null
  }

  async function search(jql, fields = ['summary'], limit = 100) {
    const out = []
    let next
    do {
      const res = await request('POST', 'rest/api/3/search/jql', {
        jql,
        fields,
        maxResults: Math.min(100, limit - out.length),
        ...(next ? { nextPageToken: next } : {}),
      })
      out.push(...(res.issues || []))
      next = !res.isLast && res.nextPageToken ? res.nextPageToken : undefined
    } while (next && out.length < limit)
    return out
  }

  const toIssue = (o, sprint) => ({ key: o.key, summary: o.fields?.summary || '', ...(sprint ? { sprint } : {}) })

  async function findSprintField() {
    if (sprintField !== undefined) return sprintField
    try {
      const fields = await request('GET', 'rest/api/3/field')
      sprintField = fields.find((f) => f.schema?.custom === 'com.pyxis.greenhopper.jira:gh-sprint')?.id || null
    } catch {
      sprintField = null
    }
    return sprintField
  }

  return {
    async myself() {
      const me = await request('GET', 'rest/api/3/myself')
      return { accountId: me.accountId, displayName: me.displayName }
    },

    async sprintIssues() {
      const field = await findSprintField()
      const issues = await search(
        'sprint in openSprints() AND assignee = currentUser() ORDER BY updated DESC',
        ['summary', ...TRACKING_FIELDS, ...(field ? [field] : [])],
      )
      return issues.map((o) => {
        const sprints = field ? o.fields?.[field] : null
        const active = Array.isArray(sprints) ? sprints.filter((s) => s.state === 'active').pop() : null
        return {
          ...toIssue(o, active?.name),
          status: o.fields?.status?.name || '',
          statusCategory: o.fields?.status?.statusCategory?.key || '',
          tracking: trackingOf(o.fields),
        }
      })
    },

    /** The sprint's shared "Time off" ticket; falls back to the newest open one. */
    async timeOff(projects) {
      const scope = projects.length ? `project in (${projects.join(',')}) AND ` : ''
      const match = '(summary ~ "\\"time off\\"" OR summary ~ "timeoff")'
      for (const jql of [
        `${scope}sprint in openSprints() AND ${match} ORDER BY created DESC`,
        `${scope}${match} AND statusCategory != Done ORDER BY created DESC`,
      ]) {
        try {
          const r = await search(jql, ['summary'], 1)
          if (r.length) return toIssue(r[0])
        } catch {
          /* try next */
        }
      }
      return null
    },

    /** The user's worklogs between two dates (inclusive), in local time. */
    async worklogs(accountId, from, to) {
      const issues = await search(
        `worklogAuthor = currentUser() AND worklogDate >= "${from}" AND worklogDate <= "${to}"`,
        ['summary'],
        300,
      )
      const after = fromIso(addDays(from, -1)).getTime()
      const before = fromIso(addDays(to, 2)).getTime()
      const out = []
      const queue = [...issues]
      const worker = async () => {
        while (queue.length) {
          const issue = queue.shift()
          const res = await request(
            'GET',
            `rest/api/3/issue/${issue.key}/worklog?startedAfter=${after}&startedBefore=${before}&maxResults=5000`,
          )
          for (const w of res.worklogs || []) {
            if (w.author?.accountId !== accountId) continue
            const { date, time } = parseStarted(w.started)
            if (date < from || date > to) continue
            out.push({
              id: w.id,
              key: issue.key,
              summary: issue.fields?.summary || '',
              date,
              start: time,
              seconds: w.timeSpentSeconds,
              comment: adfText(w.comment).replace(/\s+/g, ' ').trim(),
            })
          }
        }
      }
      await Promise.all(Array.from({ length: 6 }, worker))
      return out
    },

    /** Details for the ticket preview; description stays as Jira rich text (ADF). */
    async issueDetails(key) {
        const o = await request(
          'GET',
          `rest/api/3/issue/${key}?fields=summary,description,priority,issuetype,assignee,reporter,labels,${TRACKING_FIELDS.join(',')}`,
        )
        const f = o.fields || {}
        return {
          key: o.key,
          summary: f.summary || '',
          type: f.issuetype?.name || '',
          status: f.status?.name || '',
          statusCategory: f.status?.statusCategory?.key || '',
          priority: f.priority?.name || null,
          assignee: f.assignee?.displayName || null,
          reporter: f.reporter?.displayName || null,
          labels: f.labels || [],
          tracking: trackingOf(f),
          description: f.description || null,
          url: `https://${site}/browse/${o.key}`,
        }
    },

    async searchAll(query) {
      const q = query.trim()
      const out = []
      if (/^[A-Za-z][A-Za-z0-9]+-\d+$/.test(q)) {
        try {
          out.push(toIssue(await request('GET', `rest/api/3/issue/${q.toUpperCase()}?fields=summary`)))
        } catch {
          /* not found */
        }
      }
      try {
        const res = await request(
          'GET',
          `rest/api/3/issue/picker?query=${encodeURIComponent(q)}&showSubTasks=true&showSubTaskParent=true`,
        )
        for (const s of res.sections || []) for (const i of s.issues || []) out.push({ key: i.key, summary: i.summaryText })
      } catch {
        /* ignore */
      }
      const seen = new Set()
      return out.filter((i) => !seen.has(i.key) && seen.add(i.key))
    },

    async addWorklog(d) {
      const body = { timeSpentSeconds: d.seconds, started: jiraStarted(d.date, d.start) }
      if (d.comment?.trim()) body.comment = adf(d.comment)
      await request('POST', `rest/api/3/issue/${d.issue.key}/worklog`, body)
    },

    async deleteWorklog(key, id) {
      await request('DELETE', `rest/api/3/issue/${key}/worklog/${id}`)
    },

    /** Moves a ticket to the first transition that lands in an "In Progress" category status. */
    async startProgress(key) {
      const res = await request('GET', `rest/api/3/issue/${key}/transitions`)
      const list = res.transitions || []
      const t =
        list.find((x) => x.to?.statusCategory?.key === 'indeterminate' && /in progress/i.test(x.to?.name || x.name)) ||
        list.find((x) => x.to?.statusCategory?.key === 'indeterminate')
      if (!t) throw new JiraError(400, "This ticket's workflow has no In Progress step you can move it to.")
      await request('POST', `rest/api/3/issue/${key}/transitions`, { transition: { id: t.id } })
      return { status: t.to?.name || 'In Progress', statusCategory: 'indeterminate' }
    },
  }
}

export const OFFLINE = "You're offline. Check your connection and retry."
export const BAD_KEY = 'Jira rejected your API key. Reconnect in Settings.'
export const NOT_FOUND = "That ticket no longer exists or you can't see it."

/** Turns a Jira HTTP failure into a sentence a person can act on. */
function errorMessage(status, text) {
  if (status === 401 || status === 403) return BAD_KEY
  if (status === 404) return NOT_FOUND
  try {
    const o = JSON.parse(text)
    const msgs = [...(o.errorMessages || []), ...Object.values(o.errors || {})].filter(Boolean)
    if (msgs.length) return msgs.join(' ')
  } catch {
    /* not json */
  }
  if (status === 429) return 'Jira is busy right now. Wait a moment and retry.'
  if (status >= 500) return `Jira had a problem on its side (${status}). Retry in a moment.`
  return `Jira returned an error (${status}).`
}

/** Friendly text for any error thrown while talking to Jira. */
export function friendlyError(e) {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) return OFFLINE
  if (e instanceof JiraError) return e.message
  if (e instanceof TypeError || /network|failed to fetch|load failed/i.test(e?.message || '')) return OFFLINE
  return e?.message || 'Something went wrong. Retry in a moment.'
}

const adf = (text) => ({
  type: 'doc',
  version: 1,
  content: text
    .trim()
    .split('\n')
    .map((line) => ({ type: 'paragraph', content: line.trim() ? [{ type: 'text', text: line }] : [] })),
})

function adfText(node) {
  if (!node) return ''
  if (typeof node === 'string') return node
  if (Array.isArray(node)) return node.map(adfText).join(' ')
  if (node.type === 'text') return node.text || ''
  return adfText(node.content)
}
