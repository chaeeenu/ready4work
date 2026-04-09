import { useState } from 'react'
import styles from './CardShell.module.css'

export default function CardShell({ label, icon, children }) {
  const [collapsed, setCollapsed] = useState(false)

  return (
    <div className={styles.card}>
      <button
        className={styles.header}
        onClick={() => setCollapsed((c) => !c)}
        aria-expanded={!collapsed}
      >
        <span className={styles.title}>
          <span className={styles.icon}>{icon}</span>
          {label}
        </span>
        <span className={styles.chevron} data-collapsed={collapsed}>
          ▾
        </span>
      </button>
      {!collapsed && <div className={styles.body}>{children}</div>}
    </div>
  )
}
