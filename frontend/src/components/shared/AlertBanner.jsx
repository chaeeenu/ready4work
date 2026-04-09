import { useState, useMemo } from 'react'
import { useSubwayAlerts } from '../../hooks/useSubwayAlerts'
import { useTransitData } from '../../hooks/useTransitData'
import { useDashboard } from '../../context/DashboardContext'
import styles from './AlertBanner.module.css'

const DISMISSED_KEY = 'gtw-dismissed-alerts'

function getDismissed() {
  try {
    return JSON.parse(sessionStorage.getItem(DISMISSED_KEY) || '[]')
  } catch { return [] }
}

export default function AlertBanner() {
  const { config } = useDashboard()
  const transitCard = config.cards.find((c) => c.id === 'transit')
  const transitSettings = transitCard?.settings || {}
  const hasTransit = transitSettings.origin && transitSettings.destination

  const { data: transitData } = useTransitData(
    hasTransit ? transitSettings.origin : null,
    hasTransit ? transitSettings.destination : null
  )

  const routeLineNames = useMemo(() => {
    if (!transitData?.routes) return []
    const names = new Set()
    for (const route of transitData.routes) {
      for (const leg of route.legs) {
        if (leg.type !== 'walk' && leg.lineName) {
          names.add(leg.lineName)
        }
      }
    }
    return [...names]
  }, [transitData])

  const { data, loading, error } = useSubwayAlerts()
  const [dismissed, setDismissed] = useState(getDismissed)

  if (loading || error || !data?.alerts?.length || routeLineNames.length === 0) return null

  const active = data.alerts.filter((a) => {
    if (dismissed.includes(a.noftTtl) || !a.noftCn?.trim()) return false
    if (!a.lineNmLst) return false
    return routeLineNames.some((name) => a.lineNmLst.includes(name))
  })
  if (active.length === 0) return null

  function dismiss(id) {
    const next = [...dismissed, id]
    setDismissed(next)
    sessionStorage.setItem(DISMISSED_KEY, JSON.stringify(next))
  }

  return (
    <div className={styles.banners}>
      {active.map((alert) => (
        <div key={alert.noftTtl} className={styles.banner}>
          <span className={styles.icon}>⚠️</span>
          <span className={styles.message}>
            {alert.lineNmLst ? `[${alert.lineNmLst}] ` : ''}{alert.noftCn}
          </span>
          <button
            className={styles.close}
            onClick={() => dismiss(alert.noftTtl)}
            aria-label="닫기"
          >
            ✕
          </button>
        </div>
      ))}
    </div>
  )
}
