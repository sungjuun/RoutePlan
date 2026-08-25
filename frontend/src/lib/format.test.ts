import { describe, expect, it } from 'vitest'
import { distanceLabel, durationLabel, reasonLabel, timeLabel } from './format'

describe('format helpers', () => {
  it('formats schedule time without seconds', () => {
    expect(timeLabel('09:30:00')).toBe('09:30')
    expect(timeLabel(null)).toBe('—')
  })

  it('formats distance and duration for a readable itinerary summary', () => {
    expect(distanceLabel(840)).toBe('840m')
    expect(distanceLabel(2_350)).toBe('2.4km')
    expect(durationLabel(45)).toBe('45분')
    expect(durationLabel(125)).toBe('2시간 5분')
  })

  it('translates reoptimization reasons', () => {
    expect(reasonLabel('PLACE_ADDED')).toBe('장소 추가')
    expect(reasonLabel(null)).toBe('최초 생성')
  })
})
