import { useStore } from '../store.js'
import { Icon, Modal } from './ui.jsx'

const GROUPS = [
  ['Days', [
    [['Click'], 'Add or remove a day'],
    [['Shift', 'Click'], 'Select a range of days'],
    [['Double-click'], 'Show just that day'],
  ]],
  ['Grid', [
    [['↑ ↓ ← →'], 'Move between cells'],
    [['1.5'], 'Type a time to open the cell (also 45m, 1h 30m, 1:30)'],
    [['Enter'], 'Edit the focused cell'],
    [['Del'], 'Clear the focused cell'],
    [['/'], 'Search any Jira ticket'],
  ]],
  ['Everywhere', [
    [['Ctrl', 'Enter'], 'Log all pending entries'],
    [['R'], 'Refresh from Jira'],
    [['?'], 'Show this sheet'],
    [['Esc'], 'Close a popup'],
  ]],
]

export default function Shortcuts() {
  const setShortcuts = useStore((s) => s.setShortcuts)
  const close = () => setShortcuts(false)
  return (
    <Modal onClose={close} width={480} label="Keyboard shortcuts">
      <div className="modal-head">
        <h2><Icon name="keyboard" size={20} /> Shortcuts</h2>
        <button className="icon-btn small" onClick={close} aria-label="Close"><Icon name="close" /></button>
      </div>
      {GROUPS.map(([title, rows]) => (
        <section key={title} className="set-sec">
          <div className="set-title">{title}</div>
          <dl className="keys">
            {rows.map(([keys, text]) => (
              <div key={text} className="keys-row">
                <dt>{keys.map((k, i) => <span key={k}>{i > 0 && <span className="muted"> + </span>}<kbd>{k}</kbd></span>)}</dt>
                <dd>{text}</dd>
              </div>
            ))}
          </dl>
        </section>
      ))}
    </Modal>
  )
}
