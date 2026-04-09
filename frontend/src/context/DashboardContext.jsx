import { createContext, useContext, useReducer, useEffect } from 'react'
import { DEFAULT_CARD_CONFIG } from '../data/cardRegistry'

const STORAGE_KEY = 'r4w-card-config'

function loadConfig() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (parsed.version === 1 && Array.isArray(parsed.cards)) return parsed
    }
  } catch { /* ignore */ }
  return DEFAULT_CARD_CONFIG
}

function saveConfig(config) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(config))
  } catch { /* ignore */ }
}

function reducer(state, action) {
  switch (action.type) {
    case 'TOGGLE_CARD': {
      const cards = state.cards.map((c) =>
        c.id === action.id ? { ...c, visible: !c.visible } : c
      )
      return { ...state, cards }
    }
    case 'REORDER_CARD': {
      const { id, direction } = action
      const cards = [...state.cards]
      const idx = cards.findIndex((c) => c.id === id)
      if (idx === -1) return state
      const card = cards[idx]
      const sectionCards = cards
        .map((c, i) => ({ ...c, _idx: i }))
        .filter((c) => c.section === card.section)
        .sort((a, b) => a.order - b.order)
      const posInSection = sectionCards.findIndex((c) => c.id === id)
      const swapPos = posInSection + direction
      if (swapPos < 0 || swapPos >= sectionCards.length) return state
      const targetOrder = sectionCards[swapPos].order
      cards[idx] = { ...cards[idx], order: targetOrder }
      cards[sectionCards[swapPos]._idx] = { ...cards[sectionCards[swapPos]._idx], order: card.order }
      return { ...state, cards }
    }
    case 'UPDATE_SETTINGS': {
      const cards = state.cards.map((c) =>
        c.id === action.id ? { ...c, settings: { ...c.settings, ...action.settings } } : c
      )
      return { ...state, cards }
    }
    case 'RESET':
      return DEFAULT_CARD_CONFIG
    default:
      return state
  }
}

const DashboardContext = createContext(null)

export function DashboardProvider({ children }) {
  const [config, dispatch] = useReducer(reducer, null, loadConfig)

  useEffect(() => {
    saveConfig(config)
  }, [config])

  return (
    <DashboardContext.Provider value={{ config, dispatch }}>
      {children}
    </DashboardContext.Provider>
  )
}

export function useDashboard() {
  const ctx = useContext(DashboardContext)
  if (!ctx) throw new Error('useDashboard must be used within DashboardProvider')
  return ctx
}
