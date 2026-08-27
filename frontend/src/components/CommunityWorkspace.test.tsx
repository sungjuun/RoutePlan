import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import type { SharedRoutePage, Trip, User } from '../types'
import { CommunityWorkspace } from './CommunityWorkspace'

vi.mock('../api/client', () => ({
  api: {
    discoverRoutes: vi.fn(),
  },
}))

const user: User = {
  id: 7,
  email: 'traveler@example.com',
  nickname: '여행자',
  createdAt: '2026-08-26T00:00:00Z',
}

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
  places: [],
}

const routePage: SharedRoutePage = {
  content: [{
    routeId: 31,
    ownerId: 2,
    ownerNickname: '오사카러버',
    title: '오사카 핵심 하루',
    description: '걷기 좋은 동선',
    region: '오사카',
    travelDays: 1,
    visibility: 'PUBLIC',
    sourceTripName: '원본 여행',
    sourceStartDate: '2026-09-10',
    transportMode: 'WALKING',
    pace: 'RELAXED',
    placeCount: 2,
    placePreview: '오사카성 · 도톤보리',
    totalDistanceMeters: 3200,
    estimatedTravelMinutes: 48,
    optimizationScore: 170,
    viewCount: 12,
    copyCount: 4,
    likeCount: 8,
    publishedAt: '2026-08-26T00:00:00Z',
  }],
  page: 0,
  size: 12,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
}

describe('CommunityWorkspace', () => {
  beforeEach(() => {
    vi.mocked(api.discoverRoutes).mockResolvedValue(routePage)
  })

  it('shows published route summaries and reloads them using popular sort', async () => {
    render(
      <CommunityWorkspace
        user={user}
        trip={trip}
        itinerary={null}
        onTripCopied={vi.fn()}
        onNotify={vi.fn()}
        onError={vi.fn()}
      />,
    )

    expect(await screen.findByText('오사카 핵심 하루')).toBeInTheDocument()
    expect(screen.getByText('오사카성 · 도톤보리')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '인기순' }))

    await waitFor(() => expect(api.discoverRoutes).toHaveBeenLastCalledWith({
      region: '',
      travelDays: 1,
      sort: 'POPULAR',
      page: 0,
      size: 12,
    }))
  })
})
