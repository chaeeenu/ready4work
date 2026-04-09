import { useState, useEffect } from 'react'
import { formatTime, formatDate, getGreeting } from '../../utils/time'
import styles from './Header.module.css'

export default function Header({ onSettingsClick }) {
  const [now, setNow] = useState(new Date())

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 60_000)
    return () => clearInterval(id)
  }, [])

  return (
    <header className={styles.header}>
      <div className={styles.left}>
        <div className={styles.time}>{formatTime(now)}</div>
        <div className={styles.date}>{formatDate(now)}</div>
        <div className={styles.greeting}>{getGreeting(now)}</div>
      </div>
      <button
        className={styles.settingsBtn}
        onClick={onSettingsClick}
        aria-label="설정"
      >
        ⚙️
      </button>
    </header>
  )
}
