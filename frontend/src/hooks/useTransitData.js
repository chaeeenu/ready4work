import { useState, useEffect } from 'react'

export function useTransitData(origin, destination) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    if (!origin || !destination) return

    let cancelled = false
    setLoading(true)
    setError(null)

    async function fetchRoutes() {
      try {
        const params = new URLSearchParams({ origin, destination })
        const now = new Date()
        const departureTime = now.getFullYear().toString()
          + String(now.getMonth() + 1).padStart(2, '0')
          + String(now.getDate()).padStart(2, '0')
          + String(now.getHours()).padStart(2, '0')
          + String(now.getMinutes()).padStart(2, '0')
        params.set('departureTime', departureTime)
        if (refreshKey > 0) params.set('refresh', 'true')
        const res = await fetch(`/api/transit/routes?${params}`)
        if (!res.ok) throw new Error(`경로를 불러올 수 없습니다 (${res.status})`)
        const json = await res.json()
        if (!cancelled) setData(json)
      } catch (err) {
        if (!cancelled) setError(err.message)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    fetchRoutes()
    return () => { cancelled = true }
  }, [origin, destination, refreshKey])

  const refresh = () => setRefreshKey(k => k + 1)

  return { data, loading, error, refresh }
}
