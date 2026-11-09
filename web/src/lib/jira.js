import { addDays, fromIso, jiraStarted, parseStarted } from './time.js'

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
      throw new JiraError(0, 'Network problem. Check your connection.')
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
        ['summary', ...(field ? [field] : [])],
      )
      return issues.map((o) => {
        const sprints = field ? o.fields?.[field] : null
        const active = Array.isArray(sprints) ? sprints.filter((s) => s.state === 'active').pop() : null
        return toIssue(o, active?.name)
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
  }
}

function errorMessage(status, text) {
  if (status === 401) return "That API key didn't work. Check your email and key."
  if (status === 403) return "Jira says you don't have permission for this."
  try {
    const o = JSON.parse(text)
    const msgs = [...(o.errorMessages || []), ...Object.values(o.errors || {})]
    if (msgs.length) return msgs.join(' ')
  } catch {
    /* not json */
  }
  return `Jira returned an error (${status}).`
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
