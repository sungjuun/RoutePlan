export function timeLabel(value: string | null | undefined): string {
  if (!value) return '—'
  return value.slice(0, 5)
}

export function distanceLabel(meters: number): string {
  if (meters < 1000) return `${Math.round(meters)}m`
  return `${(meters / 1000).toFixed(meters >= 10_000 ? 0 : 1)}km`
}

export function durationLabel(minutes: number): string {
  if (minutes < 60) return `${minutes}분`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours}시간` : `${hours}시간 ${rest}분`
}

export function dateLabel(date: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  }).format(new Date(`${date}T00:00:00`))
}

export function categoryLabel(value: string | null): string {
  if (!value) return '장소'
  return value.replaceAll('_', ' ').toLowerCase()
}

export function transportLabel(value: string): string {
  return {
    WALKING: '도보',
    DRIVING: '자동차',
    PUBLIC_TRANSIT: '대중교통',
  }[value] ?? value
}

export function paceLabel(value: string): string {
  return {
    ACTIVE: '알차게',
    STANDARD: '균형 있게',
    RELAXED: '여유롭게',
  }[value] ?? value
}

export function reasonLabel(value: string | null): string {
  if (!value) return '최초 생성'
  return {
    DELAY: '일정 지연',
    PLACE_ADDED: '장소 추가',
    PLACE_REMOVED: '장소 삭제',
    WEATHER: '날씨 변경',
    BUDGET: '예산 변경',
    USER_REQUEST: '사용자 요청',
    OTHER: '기타 변경',
  }[value] ?? value
}

export function environmentLabel(value: string): string {
  return {
    INDOOR: '실내',
    OUTDOOR: '실외',
    MIXED: '실내·실외',
  }[value] ?? value
}

export function weatherLabel(value: string): string {
  return {
    UNKNOWN: '예보 없음',
    CLEAR: '맑음',
    CLOUDY: '흐림',
    RAIN: '비',
    SNOW: '눈',
    EXTREME: '위험 기상',
  }[value] ?? value
}
