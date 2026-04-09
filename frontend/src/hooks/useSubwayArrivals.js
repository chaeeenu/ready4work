import { useState, useEffect } from 'react'

export function useSubwayArrivals(stationName) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!stationName) return

    let cancelled = false
    let intervalId = null

    async function fetchArrivals() {
      try {
        if (!data) setLoading(true)
        const params = new URLSearchParams({ stationName })
        const res = await fetch(`/api/subway/arrivals?${params}`)
        if (!res.ok) throw new Error(`도착정보를 불러올 수 없습니다 (${res.status})`)
        const json = await res.json()
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

    fetchArrivals()
    intervalId = setInterval(fetchArrivals, 30000)

    return () => {
      cancelled = true
      if (intervalId) clearInterval(intervalId)
    }
  }, [stationName])

  return { data, loading, error }
}
