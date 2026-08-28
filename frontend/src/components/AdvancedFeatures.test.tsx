import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { advanced, type Discussion, type Spending } from '../api/advanced'
import type { Itinerary, Trip } from '../types'
import { LiveDataPanel } from './LiveDataPanel'
import { SpendingPanel } from './SpendingPanel'
import { PreferencesPanel } from './PreferencesPanel'
import { DiscussionPanel } from './DiscussionPanel'
import { GoogleRoadMap } from './GoogleRoadMap'

const trip = { id: 7, startDate: '2026-09-10', endDate: '2026-09-11', places: [] } as unknown as Trip
const spending: Spending = { currency: 'USD', totalLimitMinor: 10000, spentMinor: 0, scopes: [], expenses: [] }
const discussion: Discussion = { comments: [], reviews: [], averageRating: 0, reviewCount: 0, commentCount: 0, page: 0 }
const user = { id: 7, nickname: '여행자', email: 'test@example.com', createdAt: '2026-01-01T00:00:00Z' }

describe('live data and community', () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks() })

  it('loads and saves the local time zone and refreshes automatic weather', async () => {
    vi.spyOn(advanced, 'weatherRefreshSettings').mockResolvedValue({ enabled: false, nextRefreshAt: null, lastSuccessAt: null, lastError: null })
    vi.spyOn(advanced, 'zone').mockResolvedValue({ timeZoneId: 'Asia/Seoul' })
    const save = vi.spyOn(advanced, 'saveZone').mockResolvedValue({ timeZoneId: 'Europe/Paris' })
    const weather = vi.spyOn(advanced, 'weather').mockResolvedValue({ updatedDates: 1, preservedManualDates: 1, timeZoneId: 'Europe/Paris', message: '수동 보존' })
    const blocked = vi.fn(), refreshed = vi.fn()
    render(<LiveDataPanel trip={trip} onError={vi.fn()} onRefreshed={refreshed} onBlockedChange={blocked} />)
    await waitFor(() => expect(blocked).toHaveBeenLastCalledWith(false))
    fireEvent.change(screen.getByLabelText('여행지 시간대'), { target: { value: 'Europe/Paris' } })
    expect(blocked).toHaveBeenLastCalledWith(true)
    expect(screen.getByRole('button', { name: '날씨 자동 조회' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: '시간대 저장' }))
    await waitFor(() => expect(save).toHaveBeenCalledWith(7, 'Europe/Paris'))
    await waitFor(() => expect(blocked).toHaveBeenLastCalledWith(false))
    fireEvent.click(screen.getByRole('button', { name: '날씨 자동 조회' }))
    expect(await screen.findByText(/1일 예보 갱신.*직접 입력 1일 보존/)).toBeVisible()
    expect(weather).toHaveBeenCalledOnce(); expect(refreshed).toHaveBeenCalledOnce()
  })

  it('records decimal money exactly and reuses the request id after a network failure', async () => {
    vi.spyOn(advanced, 'spending').mockResolvedValue(spending)
    const save = vi.spyOn(advanced, 'expense').mockRejectedValueOnce(new Error('network')).mockResolvedValue(spending)
    const onError = vi.fn()
    render(<SpendingPanel trip={trip} onError={onError} />)
    await screen.findByLabelText('실제 지출 금액')
    fireEvent.change(screen.getByLabelText('지출 내용'), { target: { value: '점심' } })
    fireEvent.change(screen.getByLabelText('실제 지출 금액'), { target: { value: '12.34' } })
    fireEvent.click(screen.getByRole('button', { name: '지출 기록' }))
    await waitFor(() => expect(onError).toHaveBeenCalledOnce())
    fireEvent.click(screen.getByRole('button', { name: '지출 기록' }))
    await waitFor(() => expect(save).toHaveBeenCalledTimes(2))
    expect(save.mock.calls[0][2].requestId).toEqual(save.mock.calls[1][2].requestId)
    expect(save.mock.calls[1][2].amountMinor).toBe(1234)
    expect(save.mock.calls[1][1]).toBe('USD')
  })

  it('saves explicit interests and lets the user clear them', async () => {
    const empty = { interests: [], regions: [], pace: null, transportMode: null }
    vi.spyOn(advanced, 'preferences').mockResolvedValue(empty)
    const save = vi.spyOn(advanced, 'savePreferences').mockResolvedValue(empty)
    render(<PreferencesPanel onError={vi.fn()} />)
    fireEvent.click(await screen.findByLabelText('미식'))
    fireEvent.change(screen.getByLabelText(/관심 지역/), { target: { value: '서울, 오사카' } })
    fireEvent.click(screen.getByRole('button', { name: '여행 취향 저장' }))
    await waitFor(() => expect(save).toHaveBeenCalledWith({ interests: ['FOOD'], regions: ['서울', '오사카'], pace: null, transportMode: null }))
    expect(await screen.findByText(/취향을 저장했습니다/)).toBeVisible()
  })

  it('renders comment text safely, saves reviews and submits a report', async () => {
    vi.spyOn(advanced, 'discussion').mockResolvedValue({ ...discussion, comments: [{ id: 9, userId: 2, nickname: '다른 여행자', body: '<script>alert(1)</script>', rating: null, createdAt: user.createdAt }], commentCount: 1 })
    const review = vi.spyOn(advanced, 'review').mockResolvedValue(discussion)
    const report = vi.spyOn(advanced, 'report').mockResolvedValue({ id: 1, status: 'OPEN' })
    render(<DiscussionPanel routeId={3} ownerId={2} user={user} onError={vi.fn()} />)
    expect(await screen.findByText('<script>alert(1)</script>')).toBeVisible()
    expect(document.querySelector('.discussion-panel script')).toBeNull()
    fireEvent.change(screen.getByLabelText('후기 내용'), { target: { value: '좋은 동선이었어요' } })
    fireEvent.click(screen.getByRole('button', { name: '후기 저장' }))
    await waitFor(() => expect(review).toHaveBeenCalledWith(3, 5, '좋은 동선이었어요'))
    fireEvent.click(screen.getByRole('button', { name: '이 루트 신고' }))
    fireEvent.change(screen.getByLabelText('신고 사유'), { target: { value: 'MISLEADING' } })
    fireEvent.click(screen.getByRole('button', { name: '신고 접수' }))
    await waitFor(() => expect(report).toHaveBeenCalledWith(3, { targetType: 'ROUTE', targetId: 3, reason: 'MISLEADING', detail: '' }))
    expect(await screen.findByText(/신고가 접수되었습니다/)).toBeVisible()
  })

  it('does not request paid route geometry when a browser map key is missing', async () => {
    vi.spyOn(advanced, 'maps').mockResolvedValue({ browserKey: '' })
    const geometry = vi.spyOn(advanced, 'geometry')
    render(<GoogleRoadMap itinerary={{ itineraryId: 10 } as Itinerary} date="2026-09-10" />)
    expect(await screen.findByText(/GOOGLE_MAPS_BROWSER_KEY 설정/)).toBeVisible()
    expect(geometry).not.toHaveBeenCalled()
  })
})
