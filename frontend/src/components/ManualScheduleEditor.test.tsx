import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import type { Itinerary, ItineraryItem, Trip } from '../types'
import { ManualScheduleEditor } from './ManualScheduleEditor'

const trip = {
  id: 7,
  startDate: '2026-09-10',
  endDate: '2026-09-10',
} as Trip

const item = (id: number, name: string, sequence: number): ItineraryItem => ({
  itineraryItemId: id,
  sequence,
  placeId: id,
  placeName: name,
  visitDate: '2026-09-10',
  travelDistanceMeters: 100,
  estimatedTravelMinutes: 2,
  arrivalTime: '09:00:00',
  startTime: '09:00:00',
  endTime: '10:00:00',
  waitingMinutes: 0,
  stayMinutes: 60,
  priority: 50,
  mustVisit: false,
  environment: 'MIXED',
  weatherScoreAdjustment: 0,
  estimatedCostMinor: null,
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

  it('resets the draft and preview before rendering a new itinerary version', async () => {
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
    const props = { trip, onItineraryChanged: vi.fn(), onError: vi.fn() }
    const { rerender } = render(<ManualScheduleEditor {...props} itinerary={itinerary} />)
    fireEvent.click(screen.getByText('일정 직접 편집'))
    fireEvent.click(screen.getByRole('button', { name: '둘째 장소 위로' }))
    fireEvent.click(screen.getByRole('button', { name: '변경 영향 계산' }))
    expect(await screen.findByText('+12분')).toBeVisible()

    const next = {
      ...itinerary,
      itineraryId: 45,
      version: 2,
      items: [item(201, '새 첫 장소', 1), item(202, '새 둘째 장소', 2)],
    }
    rerender(<ManualScheduleEditor {...props} itinerary={next} />)
    const details = screen.getByText('일정 직접 편집').closest('details')!
    if (!details.open) fireEvent.click(screen.getByText('일정 직접 편집'))
    expect(screen.getByText('새 첫 장소')).toBeVisible()
    expect(screen.queryByText('첫 장소')).not.toBeInTheDocument()
    expect(screen.queryByText('+12분')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /이 순서로 새 버전 저장/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '변경 영향 계산' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: '새 둘째 장소 위로' }))
    fireEvent.click(screen.getByRole('button', { name: '변경 영향 계산' }))
    await waitFor(() => expect(preview).toHaveBeenLastCalledWith(7, {
      sourceItineraryId: 45,
      assignments: [{ visitDate: '2026-09-10', itineraryItemIds: [202, 201] }],
    }))
  })
})
