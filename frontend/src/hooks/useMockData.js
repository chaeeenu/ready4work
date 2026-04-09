import { useState } from 'react'
import { mockNews } from '../data/mockNews'
import { mockAssets } from '../data/mockAssets'
import { mockCalendar } from '../data/mockCalendar'
import { mockObsidian } from '../data/mockObsidian'

function useMock(data) {
  const [value] = useState(data)
  return { data: value, loading: false, error: null }
}

export function useNewsData() { return useMock(mockNews) }
export function useAssetData() { return useMock(mockAssets) }
export function useCalendarData() { return useMock(mockCalendar) }
export function useObsidianData() { return useMock(mockObsidian) }
