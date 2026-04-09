import { Suspense } from 'react'
import { CARD_REGISTRY } from '../../data/cardRegistry'
import CardShell from '../cards/CardShell'
import styles from './CardSlot.module.css'

export default function CardSlot({ cardConfig }) {
  const entry = CARD_REGISTRY[cardConfig.id]
  if (!entry || !cardConfig.visible) return null

  const CardComponent = entry.component

  return (
    <div className={styles.slot}>
      <CardShell label={entry.label} icon={entry.icon}>
        <Suspense fallback={<div className={styles.loading}>불러오는 중…</div>}>
          <CardComponent settings={cardConfig.settings} />
        </Suspense>
      </CardShell>
    </div>
  )
}
