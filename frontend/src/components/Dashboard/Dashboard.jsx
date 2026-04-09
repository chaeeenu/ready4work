import { useState } from 'react'
import { useDashboard } from '../../context/DashboardContext'
import { SECTIONS } from '../../data/cardRegistry'
import Header from '../shared/Header'
import AlertBanner from '../shared/AlertBanner'
import CardSlot from './CardSlot'
import SettingsPanel from '../settings/SettingsPanel'
import styles from './Dashboard.module.css'

export default function Dashboard() {
  const { config } = useDashboard()
  const [settingsOpen, setSettingsOpen] = useState(false)

  return (
    <div className={styles.dashboard}>
      <Header onSettingsClick={() => setSettingsOpen(true)} />
      <AlertBanner />

      {SECTIONS.map((section) => {
        const sectionCards = config.cards
          .filter((c) => c.section === section.id)
          .sort((a, b) => a.order - b.order)

        const visibleCards = sectionCards.filter((c) => c.visible)
        if (visibleCards.length === 0) return null

        return (
          <section key={section.id} className={styles.section}>
            <h2 className={styles.sectionTitle}>{section.label}</h2>
            <div className={styles.cards}>
              {sectionCards.map((card) => (
                <CardSlot key={card.id} cardConfig={card} />
              ))}
            </div>
          </section>
        )
      })}

      {settingsOpen && (
        <SettingsPanel onClose={() => setSettingsOpen(false)} />
      )}
    </div>
  )
}
