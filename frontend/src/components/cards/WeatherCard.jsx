import { useWeatherData } from '../../hooks/useWeatherData'
import styles from './WeatherCard.module.css'

export default function WeatherCard({ settings = {} }) {
  const { data, loading, error } = useWeatherData(settings.city)

  if (loading) return <div className={styles.weather}>날씨 정보를 불러오는 중…</div>
  if (error) return <div className={styles.weather}>⚠️ {error}</div>
  if (!data) return null

  return (
    <div className={styles.weather}>
      <div className={styles.current}>
        <div className={styles.tempBlock}>
          <span className={styles.tempIcon}>{data.current.icon}</span>
          <span className={styles.temp}>{data.current.temp}°</span>
        </div>
        <div className={styles.details}>
          <span className={styles.condition}>{data.current.condition}</span>
          <span className={styles.range}>
            최고 {data.high}° / 최저 {data.low}°
          </span>
          <span className={styles.meta}>
            습도 {data.current.humidity}% · 바람 {data.current.wind}
          </span>
        </div>
      </div>

      <div className={styles.hourly}>
        {data.hourly.map((h) => (
          <div key={h.time} className={styles.hour}>
            <span className={styles.hourTime}>{h.time}</span>
            <span>{h.icon}</span>
            <span className={styles.hourTemp}>{h.temp}°</span>
          </div>
        ))}
      </div>

      <div className={styles.clothing}>
        <span className={styles.clothingIcon}>👔</span>
        {data.clothing}
      </div>
    </div>
  )
}
