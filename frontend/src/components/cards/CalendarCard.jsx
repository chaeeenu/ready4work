import { useCalendarData } from '../../hooks/useMockData'
import styles from './CalendarCard.module.css'

export default function CalendarCard() {
  const { data } = useCalendarData()

  return (
    <ul className={styles.events}>
      {data.events.map((event) => (
        <li key={event.id} className={styles.event}>
          <div
            className={styles.indicator}
            style={{ backgroundColor: event.color }}
          />
          <div className={styles.time}>
            {event.start} – {event.end}
          </div>
          <div className={styles.title}>{event.title}</div>
        </li>
      ))}
    </ul>
  )
}
