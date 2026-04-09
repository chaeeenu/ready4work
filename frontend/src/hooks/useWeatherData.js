import { useState, useEffect } from 'react'

export function useWeatherData(city = 'Seoul') {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [coords, setCoords] = useState(null);

  // 1. 컴포넌트 마운트 시 브라우저 좌표 가져오기
  useEffect(() => {
    if (!navigator.geolocation) {
      setError("이 브라우저는 위치 정보를 지원하지 않습니다.")
      setLoading(false)
      return
    }

    navigator.geolocation.getCurrentPosition(
        (position) => {
          setCoords({
            lat: position.coords.latitude,
            lon: position.coords.longitude
          })
        },
        (err) => {
          console.warn("위치 권한 거부 또는 획득 실패, 기본값(Seoul) 사용", err)
          // 사용자가 권한을 거부하면 기본 city 값으로 진행하기 위해 null 유지
          setCoords('default')
        },
        { enableHighAccuracy: true, timeout: 5000 }
    )
  }, [])

  // 2. 좌표가 준비되었을 때 API 호출
  useEffect(() => {
    if (!coords) return // 아직 좌표 확인 중

    let cancelled = false

    async function fetchWeather() {
      setLoading(true)
      setError(null)
      try {
        // 좌표가 있으면 lat/lon을, 없으면 city 이름을 쿼리 파라미터로 전송
        const query = coords !== 'default'
            ? `lat=${coords.lat}&lon=${coords.lon}`
            : `city=${encodeURIComponent(city)}`

        const res = await fetch(`/api/weather?${query}`)
        // const res = await fetch(`/api/weather?city=${encodeURIComponent(city)}`)
        if (!res.ok) throw new Error(`날씨 정보를 불러올 수 없습니다 (${res.status})`)
        const json = await res.json()
        if (!cancelled) setData(json)
      } catch (err) {
        if (!cancelled) setError(err.message)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    fetchWeather()
    return () => { cancelled = true }
  }, [coords, city])

  return { data, loading, error }
}
