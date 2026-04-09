import { useState, useMemo } from 'react'
import { useTransitData } from '../../hooks/useTransitData'
import { useSubwayArrivals } from '../../hooks/useSubwayArrivals'
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

  return (
    <div className={styles.leg}>
      <span className={styles.marker}>
        <span className={styles.markerDot} style={{ background: leg.lineColor }} />
      </span>
      <span className={styles.lineBadge} style={{ background: leg.lineColor }}>
        {leg.lineName}
      </span>
      <div className={styles.legDetail}>
        <span className={styles.legStations}>{leg.startName} → {leg.endName}</span>
        <span className={styles.legMeta}>{leg.stationCount}개 {leg.type === 'bus' ? '정류장' : '역'} · {leg.sectionTime}분</span>
      </div>
    </div>
  )
}

function ArrivalInfo({ arrivals }) {
  if (!arrivals || arrivals.length === 0) return null
  const display = arrivals.slice(0, 3)
  return (
    <div className={styles.arrivalInfo}>
      {display.map((a, i) => (
        <div key={i} className={styles.arrivalRow}>
          <span className={styles.arrivalLine}>{a.trainLineNm}</span>
          <span className={styles.arrivalDir}>{a.bstatnNm}행</span>
          <span className={styles.arrivalTime}>{a.arvlMsg2}</span>
        </div>
      ))}
    </div>
  )
}

function RouteCard({ route, label, arrivalData }) {
  return (
    <div className={styles.routeCard}>
      {label && <span className={styles.routeLabel}>{label}</span>}
      <div className={styles.routeSummary}>
        <span className={styles.totalTime}>
          {route.totalTime >= 60 && <>{Math.floor(route.totalTime / 60)}<small>시간</small></>}
          {route.totalTime % 60 > 0 && <>{route.totalTime % 60}<small>분</small></>}
          {route.totalTime === 0 && <>0<small>분</small></>}
        </span>
        <div className={styles.routeMeta}>
          <span>환승 {route.transferCount}회</span>
          <span>{route.totalCost.toLocaleString()}원</span>
          {route.walkTime > 0 && <span>도보 {route.walkTime}분</span>}
        </div>
      </div>
      <div className={styles.timeline}>
        {route.legs.map((leg, i) => {
          const isFirstSubway = leg.type === 'subway' && !route.legs.slice(0, i).some(l => l.type === 'subway')
          return (
            <div key={i}>
              <LegBadge leg={leg} />
              {isFirstSubway && arrivalData && <ArrivalInfo arrivals={arrivalData.arrivals} />}
            </div>
          )
        })}
        <div className={styles.arrivalLabel}>
          <span className={styles.marker}>
            <span className={styles.markerEnd} />
          </span>
          <span>{route.legs.findLast(l => l.type !== 'walk')?.endName} 도착</span>
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

  const firstSubwayStation = useMemo(() => {
    if (!data || !data.routes || data.routes.length === 0) return null
    const mainRoute = data.routes[0]
    const subwayLeg = mainRoute.legs.find(l => l.type === 'subway')
    return subwayLeg ? subwayLeg.startName : null
  }, [data])

  const { data: arrivalData } = useSubwayArrivals(firstSubwayStation)

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

  if (loading) return <div className={styles.transit}>경로를 검색하는 중…</div>
  if (error) return <div className={styles.transit}><div className={styles.error}>⚠️ {error}</div></div>
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
          ? ['최단시간']
          : ['최단시간', '최소환승']
        return (
          <RouteCard key={i} route={route} label={labels[i]} arrivalData={i === 0 ? arrivalData : null} />
        )
      })}
    </div>
  )
}
