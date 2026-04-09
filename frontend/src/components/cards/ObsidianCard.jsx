import { useObsidianData } from '../../hooks/useMockData'
import styles from './ObsidianCard.module.css'

export default function ObsidianCard() {
  const { data } = useObsidianData()

  return (
    <div className={styles.obsidian}>
      <ul className={styles.notes}>
        {data.recentNotes.map((note) => (
          <li key={note.id} className={styles.note}>
            <div className={styles.noteTitle}>{note.title}</div>
            <div className={styles.excerpt}>{note.excerpt}</div>
            <div className={styles.modified}>{note.modified}</div>
          </li>
        ))}
      </ul>
      <div className={styles.vault}>Vault: {data.vault}</div>
    </div>
  )
}
