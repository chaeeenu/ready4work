import { useCardConfig } from '../../hooks/useCardConfig'
import CardToggleList from './CardToggleList'
import CardReorderList from './CardReorderList'
import styles from './SettingsPanel.module.css'

export default function SettingsPanel({ onClose }) {
  const { resetToDefaults } = useCardConfig()

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.sheet} onClick={(e) => e.stopPropagation()}>
        <div className={styles.handle} />
        <div className={styles.header}>
          <h2 className={styles.title}>설정</h2>
          <button className={styles.closeBtn} onClick={onClose} aria-label="닫기">
            ✕
          </button>
        </div>

        <div className={styles.content}>
          <section className={styles.section}>
            <h3 className={styles.sectionTitle}>카드 표시</h3>
            <CardToggleList />
          </section>

          <section className={styles.section}>
            <h3 className={styles.sectionTitle}>카드 순서</h3>
            <CardReorderList />
          </section>

          <button className={styles.resetBtn} onClick={resetToDefaults}>
            기본값으로 초기화
          </button>
        </div>
      </div>
    </div>
  )
}
