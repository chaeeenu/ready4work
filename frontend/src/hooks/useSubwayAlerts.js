import { useState, useEffect, useCallback } from 'react'

export function useSubwayAlerts() {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const fetchAlerts = useCallback(async (refresh = false) => {
    try {
      const url = refresh ? '/api/alerts/subway?refresh=true' : '/api/alerts/subway'
      const res = await fetch(url)
      if (!res.ok) throw new Error(`지하철 알림을 불러올 수 없습니다 (${res.status})`)
      const json = await res.json()
      setData(json)
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const res = await fetch('/api/alerts/subway')
        if (!res.ok) throw new Error(`지하철 알림을 불러올 수 없습니다 (${res.status})`)
        const json = await res.json()
        console.log(json);
        if (!cancelled) {
          setData(json)
          setError(null)
        }
      } catch (err) {
        if (!cancelled) setError(err.message)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()

    const interval = setInterval(() => {
      if (!cancelled) load()
    }, 60000)

    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [])

  return { data, loading, error, refresh: () => fetchAlerts(true) }
}
