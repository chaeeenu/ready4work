import { useState } from 'react'
import { useTransitData } from '../../hooks/useTransitData'
import { useCardConfig } from '../../hooks/useCardConfig'
import { cn } from '../../utils/cn'
import styles from './TransitCard.module.css'

function SetupForm({ settings, onSave }) {
  const [origin, setOrigin] = useState(settings.origin || '')
  const [destination, setDestination] = useState(settings.destination || '')

  const handleSubmit = (e) => {
    e.preventDefault()
    if (origin.trim() && destination.trim()) {
      onSave({ origin: origin.trim(), destination: destination.trim() })
    }
  }

  return (
    <form className={styles.setupForm} onSubmit={handleSubmit}>
      <p className={styles.setupHint}>출발지와 도착지를 설정하세요</p>
      <input
        className={styles.input}
        type="text"
        placeholder="출발지 (예: 서울시 강남구 테헤란로 123)"
        value={origin}
        onChange={(e) => setOrigin(e.target.value)}
      />
      <input
        className={styles.input}
        type="text"
        placeholder="도착지 (예: 서울시 중구 세종대로 110)"
        value={destination}
        onChange={(e) => setDestination(e.target.value)}
      />
      <button className={styles.saveBtn} type="submit" disabled={!origin.trim() || !destination.trim()}>
        경로 검색
      </button>
    </form>
  )
}

/** "수도권2호선" → "2호선", "수도권9호선(급행)" → "9호선(급행)", "345" → "345" */
function displayLineName(leg) {
  if (leg.type === 'subway' && leg.lineName) {
    return leg.lineName.replace(/^수도권/, '')
  }
  return leg.lineName ?? ''
}

function LegBadge({ leg }) {
  if (leg.type === 'walk') {
    return (
      <div className={styles.walkLeg}>
        <span className={styles.marker}>
          <span className={styles.markerRing} />
        </span>
        <span className={styles.walkIcon}>🚶</span>
        <span>도보 {leg.sectionTime}분</span>
      </div>
    )
  }

  const hasArrival = leg.arrivalMessage != null
  const isAdjusted = hasArrival && leg.waitTime != null && leg.adjustedTime !== leg.sectionTime

  return (
    <div className={styles.leg}>
      <span className={styles.marker}>
        <span className={styles.markerDot} style={{ background: leg.lineColor }} />
      </span>
      <span className={styles.lineBadge} style={{ background: leg.lineColor }}>
        {displayLineName(leg)}
      </span>
      <div className={styles.legDetail}>
        <span className={styles.legStations}>{leg.startName} → {leg.endName}</span>
        <span className={styles.legMeta}>
          {leg.stationCount}개 {leg.type === 'bus' ? '정류장' : '역'}
          {' · '}
          {isAdjusted
            ? <>{leg.adjustedTime}분 <span className={styles.estimateInline}>(예상 {leg.sectionTime}분)</span></>
            : <>{leg.sectionTime}분</>
          }
        </span>
        {hasArrival && (
          <div className={styles.legArrivalInfo}>
            <span className={styles.arrivalBadge}>{leg.arrivalMessage}</span>
            {leg.arrivalMessage2 && leg.arrivalMessage2.trim() && (
              <span className={styles.nextArrival}>다음: {leg.arrivalMessage2}</span>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function TimeDisplay({ route }) {
  const isEnriched = route.enrichedAt != null && route.adjustedTotalTime !== route.totalTime

  const displayTime = isEnriched ? route.adjustedTotalTime : route.totalTime

  const formatTime = (minutes) => {
    const parts = []
    if (minutes >= 60) parts.push(<>{Math.floor(minutes / 60)}<small>시간</small></>)
    if (minutes % 60 > 0 || minutes === 0) parts.push(<>{minutes % 60}<small>분</small></>)
    return parts
  }

  return (
    <div className={styles.timeDisplay}>
      <span className={styles.totalTime}>{formatTime(displayTime)}</span>
      {isEnriched && (
        <span className={styles.referenceTime}>경로 예상 {route.totalTime}분</span>
      )}
    </div>
  )
}

function EtaDisplay({ route }) {
  if (!route.enrichedAt) return null

  const displayTime = route.adjustedTotalTime !== route.totalTime
    ? route.adjustedTotalTime
    : route.totalTime
  const eta = new Date(Date.now() + displayTime * 60_000)
  const etaStr = `${String(eta.getHours()).padStart(2, '0')}:${String(eta.getMinutes()).padStart(2, '0')}`

  return (
    <div className={styles.etaDisplay}>
      지금 출발 시 약 <strong>{etaStr}</strong> 도착 예상
    </div>
  )
}

function Freshness({ enrichedAt }) {
  if (!enrichedAt) return null

  const updatedDate = new Date(enrichedAt.replace(' ', 'T'))
  const diffSec = Math.round((Date.now() - updatedDate.getTime()) / 1000)
  const label = diffSec < 5 ? '방금' : `${diffSec}초 전`

  return <span className={styles.freshness}>{label} 업데이트</span>
}

function RouteCard({ route, label, destination }) {
  return (
    <div className={styles.routeCard}>
      <div className={styles.routeCardHeader}>
        {label && <span className={styles.routeLabel}>{label}</span>}
        <Freshness enrichedAt={route.enrichedAt} />
      </div>
      <div className={styles.routeSummary}>
        <TimeDisplay route={route} />
        <div className={styles.routeMeta}>
          <span>환승 {route.transferCount}회</span>
          <span>{route.totalCost.toLocaleString()}원</span>
          {route.walkTime > 0 && <span>도보 {route.walkTime}분</span>}
        </div>
      </div>
      <EtaDisplay route={route} />
      <div className={styles.timeline}>
        {route.legs.map((leg, i) => (
          <LegBadge key={i} leg={leg} />
        ))}
        <div className={styles.arrivalLabel}>
          <span className={styles.marker}>
            <span className={styles.markerEnd} />
          </span>
          <span>{destination} 도착</span>
        </div>
      </div>
    </div>
  )
}

export default function TransitCard({ settings = {} }) {
  const { updateSettings } = useCardConfig()
  const { origin, destination } = settings
  const hasSettings = origin && destination
  const { data, loading, error, refresh } = useTransitData(
    hasSettings ? origin : null,
    hasSettings ? destination : null
  )
  const [editing, setEditing] = useState(false)

  const handleSave = (newSettings) => {
    updateSettings('transit', newSettings)
    setEditing(false)
  }

  if (!hasSettings || editing) {
    return (
      <div className={styles.transit}>
        <SetupForm settings={settings} onSave={handleSave} />
      </div>
    )
  }

  if (loading && !data) return <div className={styles.transit}>경로를 검색하는 중…</div>
  if (error && !data) return <div className={styles.transit}><div className={styles.error}>⚠️ {error}</div></div>
  if (!data || data.routes.length === 0) {
    return (
      <div className={styles.transit}>
        <div className={styles.error}>경로를 찾을 수 없습니다</div>
        <button className={styles.editBtn} onClick={() => setEditing(true)}>다시 설정</button>
      </div>
    )
  }

  return (
    <div className={styles.transit}>
      <div className={styles.routeHeader}>
        <span className={styles.headerText}>{data.origin} → {data.destination}</span>
        <div className={styles.headerActions}>
          <button className={cn(styles.refreshBtn, loading && styles.spinning)} onClick={refresh} disabled={loading} aria-label="새로고침">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 2v6h-6" />
              <path d="M3 12a9 9 0 0 1 15-6.7L21 8" />
              <path d="M3 22v-6h6" />
              <path d="M21 12a9 9 0 0 1-15 6.7L3 16" />
            </svg>
          </button>
          <button className={styles.editBtn} onClick={() => setEditing(true)}>수정</button>
        </div>
      </div>

      {data.routes.map((route, i) => {
        const labels = data.routes.length === 1
          ? ['최적 경로']
          : ['최적 경로', '환승 적음']
        return (
          <RouteCard key={i} route={route} label={labels[i]} destination={data.destination} />
        )
      })}
    </div>
  )
}
