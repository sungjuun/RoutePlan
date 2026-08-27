import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import type { NaturalLanguagePreview, Trip } from '../types'
import { NaturalLanguageWorkspace } from './NaturalLanguageWorkspace'

vi.mock('../api/client', () => ({
  api: {
    previewNaturalLanguageConstraints: vi.fn(),
    applyNaturalLanguageConstraints: vi.fn(),
  },
}))

const trip: Trip = {
  id: 11,
  userId: 7,
  name: '내 오사카 여행',
  startDate: '2026-09-10',
  endDate: '2026-09-10',
  dailyStartTime: '09:00:00',
  dailyEndTime: '20:00:00',
  accommodationName: '난바 숙소',
  accommodationLatitude: 34.6654,
  accommodationLongitude: 135.5019,
  transportMode: 'WALKING',
  pace: 'STANDARD',
  status: 'OPTIMIZED',
  createdAt: '2026-08-26T00:00:00Z',
  updatedAt: '2026-08-26T00:00:00Z',
  places: [{
    placeId: 21,
    name: '오사카성',
    latitude: 34.6873,
    longitude: 135.5262,
    category: 'ATTRACTION',
    environment: 'OUTDOOR',
    averageStayMinutes: 90,
    priority: 50,
    mustVisit: false,
    preferredStartTime: null,
    preferredEndTime: null,
    minimumStayMinutes: null,
    maximumStayMinutes: null,
  }],
}

const preview: NaturalLanguagePreview = {
  originalText: '오사카성은 꼭 가고 여유롭게 해줘',
  provider: 'RULE_BASED',
  structuredConstraints: {
    dailyStartTime: null,
    dailyEndTime: null,
    pace: 'RELAXED',
    transportMode: null,
    walkingPreference: 'LOW',
    placeConstraints: [{
      placeName: '오사카성',
      preference: 'MUST_VISIT',
      preferredStartTime: null,
      preferredEndTime: null,
      minimumStayMinutes: null,
      maximumStayMinutes: null,
      mealType: null,
    }],
    notes: ['로컬 규칙 기반 해석 결과입니다.'],
  },
  trip: {
    before: { dailyStartTime: '09:00:00', dailyEndTime: '20:00:00', pace: 'STANDARD', transportMode: 'WALKING' },
    after: { dailyStartTime: '09:00:00', dailyEndTime: '20:00:00', pace: 'RELAXED', transportMode: 'WALKING' },
    changed: true,
  },
  places: [{
    before: {
      placeId: 21,
      placeName: '오사카성',
      priority: 50,
      mustVisit: false,
      preferredStartTime: null,
      preferredEndTime: null,
      minimumStayMinutes: null,
      maximumStayMinutes: null,
    },
    after: {
      placeId: 21,
      placeName: '오사카성',
      priority: 100,
      mustVisit: true,
      preferredStartTime: null,
      preferredEndTime: null,
      minimumStayMinutes: null,
      maximumStayMinutes: null,
    },
    changed: true,
  }],
  warnings: ['도보 선호 LOW는 현재 Score에 직접 반영되지 않습니다.'],
  hasChanges: true,
}

describe('NaturalLanguageWorkspace', () => {
  beforeEach(() => {
    vi.mocked(api.previewNaturalLanguageConstraints).mockResolvedValue(preview)
    vi.mocked(api.applyNaturalLanguageConstraints).mockResolvedValue({
      ...trip,
      pace: 'RELAXED',
      status: 'DRAFT',
      places: [{ ...trip.places[0], priority: 100, mustVisit: true }],
    })
  })

  it('previews interpreted changes and applies only the reviewed proposal', async () => {
    const onTripChanged = vi.fn()
    render(
      <NaturalLanguageWorkspace
        trip={trip}
        onTripChanged={onTripChanged}
        onGoToPlaces={vi.fn()}
        onGoToItinerary={vi.fn()}
        onError={vi.fn()}
      />,
    )

    fireEvent.change(screen.getByLabelText('자연어 여행 조건'), {
      target: { value: preview.originalText },
    })
    fireEvent.click(screen.getByRole('button', { name: '조건 미리보기' }))

    expect(await screen.findByText('조건 해석 완료')).toBeInTheDocument()
    expect(screen.getByText('오사카성')).toBeInTheDocument()
    expect(screen.getByText('우선순위 50 → 100')).toBeInTheDocument()
    expect(screen.getByText('확인이 필요한 내용')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '검토한 조건 적용하기' }))

    await waitFor(() => expect(api.applyNaturalLanguageConstraints).toHaveBeenCalledWith(11, {
      trip: preview.trip.after,
      places: [{
        placeId: 21,
        priority: 100,
        mustVisit: true,
        preferredStartTime: null,
        preferredEndTime: null,
        minimumStayMinutes: null,
        maximumStayMinutes: null,
      }],
    }))
    expect(onTripChanged).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'DRAFT', pace: 'RELAXED' }),
      '자연어에서 해석한 여행 조건을 적용했습니다.',
    )
  })
})
