import { motion } from 'motion/react'
import { useState } from 'react'
import { useStore } from '../store.js'
import { Icon, Spinner } from './ui.jsx'

const item = (i) => ({
  initial: { opacity: 0, y: 18 },
  animate: { opacity: 1, y: 0 },
  transition: { delay: 0.08 + i * 0.06, duration: 0.5, ease: [0.2, 0.8, 0.2, 1] },
})

export default function Setup() {
  const connect = useStore((s) => s.connect)
  const [site, setSite] = useState('')
  const [email, setEmail] = useState('')
  const [token, setToken] = useState('')
  const [show, setShow] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [shake, setShake] = useState(0)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await connect(site, email, token)
    } catch (err) {
      setError(err.message)
      setShake((n) => n + 1)
    } finally {
      setBusy(false)
    }
  }

  return (
    <motion.div className="setup" exit={{ opacity: 0, scale: 1.02 }} transition={{ duration: 0.3 }}>
      <div className="blobs" aria-hidden="true">
        <span /><span /><span />
      </div>
      <motion.form className="setup-card" onSubmit={submit}
        initial={{ opacity: 0, y: 24, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.55, ease: [0.2, 0.8, 0.2, 1] }}>
        <motion.div className="logo-wrap" {...item(0)}>
          <div className="logo-spin" />
          <img src="/favicon.svg" alt="" className="logo" />
        </motion.div>
        <motion.h1 {...item(1)}>Timelog</motion.h1>
        <motion.p className="muted center" {...item(2)}>
          Log Jira time for several days in one go, with your meetings ready as suggestions.
        </motion.p>

        <motion.div {...item(3)} className="field-stack">
          <label className="field">
            <span>Jira site</span>
            <input value={site} onChange={(e) => setSite(e.target.value)} placeholder="your-team.atlassian.net" autoComplete="url" />
          </label>
          <label className="field">
            <span>Atlassian email</span>
            <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@company.com" type="email" autoComplete="email" />
          </label>
          <motion.label className={'field' + (error ? ' invalid' : '')} key={shake}
            animate={shake ? { x: [0, -10, 10, -7, 7, -3, 0] } : {}} transition={{ duration: 0.45 }}>
            <span>API key</span>
            <div className="input-row">
              <input value={token} onChange={(e) => setToken(e.target.value)} type={show ? 'text' : 'password'}
                placeholder="Paste your Jira API token" autoComplete="off" />
              <button type="button" className="ghost-btn small" onClick={() => setShow((v) => !v)}>{show ? 'Hide' : 'Show'}</button>
            </div>
          </motion.label>
          {error && (
            <motion.div className="error-text" initial={{ opacity: 0, y: -4 }} animate={{ opacity: 1, y: 0 }}>
              {error}
            </motion.div>
          )}
        </motion.div>

        <motion.button {...item(4)} className="btn primary big" disabled={busy} whileTap={{ scale: 0.97 }}>
          {busy ? <Spinner size={20} /> : 'Connect to Jira'}
        </motion.button>
        <motion.div {...item(5)} className="setup-links">
          <a href="https://id.atlassian.com/manage-profile/security/api-tokens" target="_blank" rel="noreferrer">
            <Icon name="link" size={15} /> Create an API key
          </a>
          <a href="/?demo">
            <Icon name="sparkle" size={15} /> Try the demo
          </a>
        </motion.div>
        <motion.p {...item(6)} className="fine">
          Your key is stored only in this browser and sent straight to your Jira site through Timelog's relay. Nothing is saved on a server.
        </motion.p>
      </motion.form>
    </motion.div>
  )
}
