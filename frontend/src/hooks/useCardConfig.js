import { useDashboard } from '../context/DashboardContext'

export function useCardConfig() {
  const { config, dispatch } = useDashboard()

  return {
    cards: config.cards,
    toggleCard: (id) => dispatch({ type: 'TOGGLE_CARD', id }),
    reorderCard: (id, direction) => dispatch({ type: 'REORDER_CARD', id, direction }),
    updateSettings: (id, settings) => dispatch({ type: 'UPDATE_SETTINGS', id, settings }),
    resetToDefaults: () => dispatch({ type: 'RESET' }),
  }
}
