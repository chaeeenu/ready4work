import { useCardConfig } from '../../hooks/useCardConfig'
import { CARD_REGISTRY } from '../../data/cardRegistry'
import styles from './SettingsPanel.module.css'

const toggleStyles = {
  item: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 'var(--space-md)',
    borderRadius: 'var(--radius-sm)',
    background: 'var(--color-bg)',
  },
  label: {
    display: 'flex',
    alignItems: 'center',
    gap: 'var(--space-sm)',
    fontSize: 'var(--text-sm)',
    fontWeight: 500,
  },
  toggle: {
    position: 'relative',
    width: 44,
    height: 24,
    borderRadius: 12,
    border: 'none',
    cursor: 'pointer',
    transition: 'background 150ms ease',
    padding: 0,
  },
  knob: {
    position: 'absolute',
    top: 2,
    width: 20,
    height: 20,
    borderRadius: '50%',
    background: 'white',
    transition: 'left 150ms ease',
    boxShadow: '0 1px 3px rgba(0,0,0,0.2)',
  },
}

export default function CardToggleList() {
  const { cards, toggleCard } = useCardConfig()

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-sm)' }}>
      {cards.map((card) => {
        const entry = CARD_REGISTRY[card.id]
        if (!entry) return null
        return (
          <div key={card.id} style={toggleStyles.item}>
            <span style={toggleStyles.label}>
              <span>{entry.icon}</span>
              {entry.label}
            </span>
            <button
              style={{
                ...toggleStyles.toggle,
                background: card.visible ? 'var(--color-accent)' : 'var(--color-border)',
              }}
              onClick={() => toggleCard(card.id)}
              role="switch"
              aria-checked={card.visible}
              aria-label={`${entry.label} ${card.visible ? '끄기' : '켜기'}`}
            >
              <div
                style={{
                  ...toggleStyles.knob,
                  left: card.visible ? 22 : 2,
                }}
              />
            </button>
          </div>
        )
      })}
    </div>
  )
}
