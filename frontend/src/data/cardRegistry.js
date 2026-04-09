import { lazy } from 'react'

const WeatherCard = lazy(() => import('../components/cards/WeatherCard'))
const TransitCard = lazy(() => import('../components/cards/TransitCard'))
const NewsCard = lazy(() => import('../components/cards/NewsCard'))
const AssetCard = lazy(() => import('../components/cards/AssetCard'))
const CalendarCard = lazy(() => import('../components/cards/CalendarCard'))
const ObsidianCard = lazy(() => import('../components/cards/ObsidianCard'))

export const CARD_REGISTRY = {
  weather:  { component: WeatherCard,  label: '날씨',         icon: '🌤',  defaultSection: 'prep' },
  transit:  { component: TransitCard,  label: '교통시간표',    icon: '🚇',  defaultSection: 'prep' },
  news:     { component: NewsCard,     label: '주요뉴스',      icon: '📰',  defaultSection: 'prep' },
  assets:   { component: AssetCard,    label: '관심자산현황',   icon: '📈',  defaultSection: 'prep' },
  calendar: { component: CalendarCard, label: '캘린더',        icon: '📅',  defaultSection: 'schedule' },
  obsidian: { component: ObsidianCard, label: '옵시디언 메모',  icon: '📝',  defaultSection: 'schedule' },
}

export const DEFAULT_CARD_CONFIG = {
  version: 1,
  cards: [
    { id: 'weather',  section: 'prep',     visible: true,  order: 0, settings: { city: 'Seoul' } },
    { id: 'transit',  section: 'prep',     visible: true,  order: 1, settings: { origin: '', destination: '' } },
    { id: 'news',     section: 'prep',     visible: true,  order: 2, settings: {} },
    { id: 'assets',   section: 'prep',     visible: false, order: 3, settings: { tickers: ['AAPL', '005930.KS'] } },
    { id: 'calendar', section: 'schedule', visible: true,  order: 0, settings: {} },
    { id: 'obsidian', section: 'schedule', visible: true,  order: 1, settings: {} },
  ],
}

export const SECTIONS = [
  { id: 'prep', label: '출근 준비' },
  { id: 'schedule', label: '일정·메모' },
]
