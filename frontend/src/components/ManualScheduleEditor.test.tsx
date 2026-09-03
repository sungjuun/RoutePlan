import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import type { Itinerary, Trip } from '../types'
import { ManualScheduleEditor } from './ManualScheduleEditor'

const trip = {
  id: 7,
  startDate: '2026-09-10',
  endDate: '2026-09-10',
} as Trip

const item = (id: number, name: string, sequence: number) => ({
  itineraryItemId: id,
  sequence,
  placeId: id,
  placeName: name,
  visitDate: '2026-09-10',
  stayMinutes: 60,
  status: 'PLANNED' as const,
})

const itinerary = {
  itineraryId: 44,
  days: [{ visitDate: '2026-09-10', dayNumber: 1 }],
  items: [item(101, '첫 장소', 1), item(102, '둘째 장소', 2)],
} as Itinerary

describe('manual schedule editor', () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks() })

  it('previews the changed order before saving it', async () => {
    const preview = vi.spyOn(api, 'previewManualItineraryEdit').mockResolvedValue({
      sourceItineraryId: 44,
      sourceVersion: 1,
      affectedDates: ['2026-09-10'],
      travelMinutesDelta: 12,
      distanceMetersDelta: 800,
      totalTravelMinutes: 52,
      totalDistanceMeters: 3800,
      recommendation: null,
    })
    render(<ManualScheduleEditor
      trip={trip}
      itinerary={itinerary}
      onItineraryChanged={vi.fn()}
      onError={vi.fn()}
    />)

    fireEvent.click(screen.getByText('일정 직접 편집'))
    fireEvent.click(screen.getByRole('button', { name: '둘째 장소 위로' }))
    fireEvent.click(screen.getByRole('button', { name: '변경 영향 계산' }))

    await waitFor(() => expect(preview).toHaveBeenCalledWith(7, {
      sourceItineraryId: 44,
      assignments: [{ visitDate: '2026-09-10', itineraryItemIds: [102, 101] }],
    }))
    expect(await screen.findByText('+12분')).toBeVisible()
    expect(screen.getByRole('button', { name: /이 순서로 새 버전 저장/ })).toBeVisible()
  })
})
