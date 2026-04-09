export function formatTime(date = new Date()) {
  return date.toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

export function formatDate(date = new Date()) {
  return date.toLocaleDateString('ko-KR', {
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  })
}

export function getGreeting(date = new Date()) {
  const hour = date.getHours()
  if (hour < 6) return '좋은 새벽이에요'
  if (hour < 12) return '좋은 아침이에요'
  if (hour < 18) return '좋은 오후에요'
  return '좋은 저녁이에요'
}

export function getRelativeTime(targetDate) {
  const now = new Date()
  const diffMs = targetDate.getTime() - now.getTime()
  const diffMin = Math.round(diffMs / 60000)

  if (diffMin < 0) return `${Math.abs(diffMin)}분 전`
  if (diffMin === 0) return '지금'
  if (diffMin < 60) return `${diffMin}분 후`
  const hours = Math.floor(diffMin / 60)
  const mins = diffMin % 60
  return mins > 0 ? `${hours}시간 ${mins}분 후` : `${hours}시간 후`
}
