// Local preview: serves dist/ and mounts the Vercel Functions in api/ the way vercel.json does.
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { extname, join } from 'node:path'
import * as jira from '../api/jira.js'
import * as ics from '../api/ics.js'

const PORT = Number(process.env.PORT || 4173)
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.json': 'application/json' }

createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`)
  try {
    let handler
    if (url.pathname.startsWith('/api/jira/')) {
      url.searchParams.set('path', url.pathname.slice('/api/jira/'.length))
      url.pathname = '/api/jira'
      handler = jira[req.method]
    } else if (url.pathname === '/api/ics') handler = ics[req.method]
    if (url.pathname.startsWith('/api/')) {
      if (!handler) return res.writeHead(405).end()
      const chunks = []
      for await (const c of req) chunks.push(c)
      const request = new Request(url, { method: req.method, headers: req.headers, body: chunks.length ? Buffer.concat(chunks) : undefined })
      const r = await handler(request)
      res.writeHead(r.status, Object.fromEntries(r.headers))
      return res.end(Buffer.from(await r.arrayBuffer()))
    }
    const file = join('dist', url.pathname === '/' ? 'index.html' : url.pathname)
    const body = await readFile(file).catch(() => readFile('dist/index.html'))
    res.writeHead(200, { 'content-type': types[extname(file)] || 'text/html' })
    res.end(body)
  } catch (e) {
    res.writeHead(500).end(String(e))
  }
}).listen(PORT, () => console.log(`Timelog preview on http://localhost:${PORT}`))
