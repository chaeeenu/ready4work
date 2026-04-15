import { useState, useEffect, useRef, useCallback } from 'react'

const POLL_INTERVAL = 30_000 // 30초

export function useTransitData(origin, destination) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const intervalRef = useRef(null)

  const fetchRoutes = useCallback(async (isRefresh, signal) => {
    const params = new URLSearchParams({ origin, destination, enrich: 'true' })
    if (isRefresh) params.set('refresh', 'true')
    const res = await fetch(`/api/transit/routes?${params}`, { signal })
    if (!res.ok) throw new Error(`경로를 불러올 수 없습니다 (${res.status})`)
    return res.json()
  }, [origin, destination])

  useEffect(() => {
    if (!origin || !destination) return

    const controller = new AbortController()
    let cancelled = false

    async function initialFetch() {
      setLoading(true)
      setError(null)
      try {
        const json = await fetchRoutes(refreshKey > 0, controller.signal)
        if (!cancelled) setData(json)
      } catch (err) {
        if (!cancelled && err.name !== 'AbortError') setError(err.message)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    initialFetch()

    // 30초 폴링 (실시간 도착정보 갱신)
    intervalRef.current = setInterval(async () => {
      if (cancelled) return
      try {
        const json = await fetchRoutes(false, controller.signal)
        if (!cancelled) setData(json)
      } catch {
        // 폴링 실패는 무시 (기존 데이터 유지)
      }
    }, POLL_INTERVAL)

    return () => {
      cancelled = true
      controller.abort()
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
  }, [origin, destination, refreshKey, fetchRoutes])

  const refresh = () => setRefreshKey(k => k + 1)

  return { data, loading, error, refresh }
}
