import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { useStore } from '../store.js'
import { Icon, KeyChip } from './ui.jsx'

/**
 * Ticket preview: the backdrop fades in while the card springs up from slightly below;
 * closing shrinks it away. Click outside, press Esc or Close to dismiss.
 */
export default function IssuePeek({ issue }) {
  const closePeek = useStore((s) => s.closePeek)
  const issueDetails = useStore((s) => s.issueDetails)
  const [state, setState] = useState({ status: 'loading' })

  useEffect(() => {
    let live = true
    issueDetails(issue.key).then(
      (d) => live && setState({ status: 'ready', d }),
      (e) => live && setState({ status: 'error', message: e.message }),
    )
    return () => {
      live = false
    }
  }, [issue.key, issueDetails])

  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && closePeek()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [closePeek])

  const d = state.status === 'ready' ? state.d : null

  return createPortal(
    <motion.div className="peek-scrim" onMouseDown={closePeek}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0, transition: { duration: 0.2, delay: 0.04 } }}
      transition={{ duration: 0.22 }}>
      <motion.div className="peek" onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-label={`${issue.key} details`}
        initial={{ opacity: 0, scale: 0.88, y: 36 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.93, y: 18, transition: { duration: 0.18, ease: [0.4, 0, 0.2, 1] } }}
        transition={{ type: 'spring', stiffness: 380, damping: 30 }}
        style={{ transformOrigin: '50% 65%' }}>
        <div className="peek-body">
          <div className="peek-top">
            <KeyChip k={issue.key} />
            <AnimatePresence>
              {d && (
                <motion.span className="peek-top" initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.25 }}>
                  {d.type && <span className="peek-type">{d.type}</span>}
                  {d.status && <span className={'status-pill ' + d.statusCategory}>{d.status}</span>}
                </motion.span>
              )}
            </AnimatePresence>
          </div>
          <h2>{d?.summary || issue.summary}</h2>

          <AnimatePresence mode="wait" initial={false}>
            {state.status === 'loading' && (
              <motion.div key="load" className="shimmer" exit={{ opacity: 0, transition: { duration: 0.12 } }}>
                <span style={{ width: '42%', height: 26, borderRadius: 13 }} />
                <span style={{ width: '100%' }} /><span style={{ width: '92%' }} /><span style={{ width: '97%' }} /><span style={{ width: '58%' }} />
              </motion.div>
            )}
            {state.status === 'error' && (
              <motion.div key="err" className="peek-error" initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }}>{state.message}</motion.div>
            )}
            {d && (
              <motion.div key="ready" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3, ease: [0.2, 0.8, 0.2, 1] }}>
                {(d.assignee || d.priority || d.labels.length > 0) && (
                  <div className="peek-meta">
                    {d.assignee && <span className="meta-chip"><Icon name="person" size={14} />{d.assignee}</span>}
                    {d.priority && <span className="meta-chip"><Icon name="flag" size={14} />{d.priority}</span>}
                    {d.labels.slice(0, 5).map((l) => <span key={l} className="meta-chip">{l}</span>)}
                  </div>
                )}
                <div className="peek-label">Description</div>
                {d.description?.content?.length ? (
                  <div className="adf"><Adf node={d.description} /></div>
                ) : (
                  <div className="peek-empty">No description on this ticket.</div>
                )}
                {d.reporter && <div className="muted small" style={{ marginTop: 18 }}>Reported by {d.reporter}</div>}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
        <div className="peek-foot">
          <a className="ghost-btn" href={d?.url || '#'} target="_blank" rel="noreferrer"
            onClick={(e) => { if (!d) e.preventDefault() }}>
            <Icon name="external" size={16} /> Open in Jira
          </a>
          <motion.button className="btn small" whileTap={{ scale: 0.95 }} onClick={closePeek} autoFocus>Close</motion.button>
        </div>
      </motion.div>
    </motion.div>,
    document.body,
  )
}

// ---------- Atlassian Document Format -> React ----------
function Adf({ node }) {
  return <>{(node.content || []).map((n, i) => <AdfNode key={i} n={n} />)}</>
}

function AdfNode({ n }) {
  const kids = <Adf node={n} />
  switch (n.type) {
    case 'paragraph':
      return <p>{kids}</p>
    case 'heading': {
      const H = `h${Math.min(6, Math.max(1, n.attrs?.level || 3))}`
      return <H>{kids}</H>
    }
    case 'bulletList':
      return <ul>{kids}</ul>
    case 'orderedList':
      return <ol start={n.attrs?.order || 1}>{kids}</ol>
    case 'listItem':
      return <li>{kids}</li>
    case 'taskList':
      return <ul>{kids}</ul>
    case 'taskItem':
      return <li className="task">{n.attrs?.state === 'DONE' ? '☑ ' : '☐ '}{kids}</li>
    case 'codeBlock':
      return <pre><code>{(n.content || []).map((t) => t.text).join('')}</code></pre>
    case 'blockquote':
      return <blockquote>{kids}</blockquote>
    case 'panel':
      return <div className="panel">{kids}</div>
    case 'rule':
      return <hr />
    case 'hardBreak':
      return <br />
    case 'mention':
      return <span className="mention">{n.attrs?.text || '@someone'}</span>
    case 'emoji':
      return <>{n.attrs?.text || n.attrs?.shortName || ''}</>
    case 'inlineCard':
    case 'blockCard':
      return n.attrs?.url ? <a href={n.attrs.url} target="_blank" rel="noreferrer">{n.attrs.url}</a> : null
    case 'status':
      return <span className="status-pill">{n.attrs?.text}</span>
    case 'mediaSingle':
    case 'mediaGroup':
      return <p className="peek-empty">📎 Attachment: open in Jira to view</p>
    case 'table':
      return <table><tbody>{kids}</tbody></table>
    case 'tableRow':
      return <tr>{kids}</tr>
    case 'tableHeader':
      return <th>{kids}</th>
    case 'tableCell':
      return <td>{kids}</td>
    case 'text':
      return <Text n={n} />
    default:
      return n.content ? kids : null
  }
}

function Text({ n }) {
  let el = n.text
  for (const m of n.marks || []) {
    if (m.type === 'strong') el = <strong>{el}</strong>
    else if (m.type === 'em') el = <em>{el}</em>
    else if (m.type === 'code') el = <code>{el}</code>
    else if (m.type === 'strike') el = <s>{el}</s>
    else if (m.type === 'underline') el = <u>{el}</u>
    else if (m.type === 'link' && /^https?:/i.test(m.attrs?.href || '')) el = <a href={m.attrs.href} target="_blank" rel="noreferrer">{el}</a>
  }
  return <>{el}</>
}
