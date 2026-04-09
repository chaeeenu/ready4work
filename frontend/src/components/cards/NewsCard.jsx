import { useNewsData } from '../../hooks/useMockData'
import styles from './NewsCard.module.css'

export default function NewsCard() {
  const { data } = useNewsData()

  return (
    <ul className={styles.list}>
      {data.articles.map((article) => (
        <li key={article.id} className={styles.item}>
          <span className={styles.title}>{article.title}</span>
          <span className={styles.meta}>
            {article.source} · {article.time}
          </span>
        </li>
      ))}
    </ul>
  )
}
