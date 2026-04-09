import { useCardConfig } from '../../hooks/useCardConfig'
import { CARD_REGISTRY, SECTIONS } from '../../data/cardRegistry'

const reorderStyles = {
  section: {
    display: 'flex',
    flexDirection: 'column',
    gap: 'var(--space-sm)',
    marginBottom: 'var(--space-md)',
  },
  sectionLabel: {
    fontSize: 'var(--text-xs)',
    color: 'var(--color-text-tertiary)',
    fontWeight: 600,
  },
  item: {
    display: 'flex',
    alignItems: 'center',
    gap: 'var(--space-sm)',
    padding: 'var(--space-sm) var(--space-md)',
    borderRadius: 'var(--radius-sm)',
    background: 'var(--color-bg)',
    fontSize: 'var(--text-sm)',
  },
  label: {
    flex: 1,
    display: 'flex',
    alignItems: 'center',
    gap: 'var(--space-sm)',
  },
  btn: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: 28,
    height: 28,
    border: '1px solid var(--color-border)',
    borderRadius: 'var(--radius-sm)',
    background: 'var(--color-surface)',
    cursor: 'pointer',
    fontSize: 'var(--text-xs)',
    color: 'var(--color-text-secondary)',
  },
  btnDisabled: {
    opacity: 0.3,
    cursor: 'default',
  },
}

export default function CardReorderList() {
  const { cards, reorderCard } = useCardConfig()

  return (
    <div>
      {SECTIONS.map((section) => {
        const sectionCards = cards
          .filter((c) => c.section === section.id)
          .sort((a, b) => a.order - b.order)

        return (
          <div key={section.id} style={reorderStyles.section}>
            <span style={reorderStyles.sectionLabel}>{section.label}</span>
            {sectionCards.map((card, idx) => {
              const entry = CARD_REGISTRY[card.id]
              if (!entry) return null
              const isFirst = idx === 0
              const isLast = idx === sectionCards.length - 1

              return (
                <div key={card.id} style={reorderStyles.item}>
                  <span style={reorderStyles.label}>
                    <span>{entry.icon}</span>
                    {entry.label}
                  </span>
                  <button
                    style={{
                      ...reorderStyles.btn,
                      ...(isFirst ? reorderStyles.btnDisabled : {}),
                    }}
                    onClick={() => !isFirst && reorderCard(card.id, -1)}
                    disabled={isFirst}
                    aria-label={`${entry.label} 위로`}
                  >
                    ▲
                  </button>
                  <button
                    style={{
                      ...reorderStyles.btn,
                      ...(isLast ? reorderStyles.btnDisabled : {}),
                    }}
                    onClick={() => !isLast && reorderCard(card.id, 1)}
                    disabled={isLast}
                    aria-label={`${entry.label} 아래로`}
                  >
                    ▼
                  </button>
                </div>
              )
            })}
          </div>
        )
      })}
    </div>
  )
}
