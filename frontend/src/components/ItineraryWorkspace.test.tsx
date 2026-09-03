import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Itinerary, Trip } from '../types'
import { ItineraryWorkspace } from './ItineraryWorkspace'

vi.mock('./MapPanel', () => ({ MapPanel: () => null }))
vi.mock('./LiveDataPanel', () => ({ LiveDataPanel: () => null }))
vi.mock('./WeatherPlanner', () => ({ WeatherPlanner: () => null }))
vi.mock('./BudgetPlanner', () => ({ BudgetPlanner: () => null }))
vi.mock('./SpendingPanel', () => ({ SpendingPanel: () => null }))
vi.mock('./ManualScheduleEditor', () => ({ ManualScheduleEditor: () => null }))

const trip: Trip = {
  id: 7,
  userId: 1,
  name: '경로 출처 검증',
  startDate: '2026-09-10',
  endDate: '2026-09-10',
  dailyStartTime: '09:00:00',
  dailyEndTime: '18:00:00',
  accommodationName: '출발점',
  accommodationLatitude: 37.5547,
  accommodationLongitude: 126.9707,
  transportMode: 'PUBLIC_TRANSIT',
  pace: 'STANDARD',
  status: 'OPTIMIZED',
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
  places: [],
}

const itinerary: Itinerary = {
  itineraryId: 10,
  tripId: trip.id,
  version: 1,
  generationType: 'INITIAL_OPTIMIZATION',
  parentItineraryId: null,
  changeReason: null,
  changeReasonDetail: null,
  reoptimizationStartDate: null,
  reoptimizationStartTime: null,
  reoptimizationStartLatitude: null,
  reoptimizationStartLongitude: null,
  algorithm: 'NEAREST_NEIGHBOR_2_OPT',
  totalDistanceMeters: 0,
  estimatedTravelMinutes: 0,
  optimizationScore: 0,
  visitedPriorityScore: 0,
  totalStayMinutes: 0,
  totalWaitingMinutes: 0,
  closedTour: true,
  returnTravelDistanceMeters: 0,
  returnTravelMinutes: 0,
  returnArrivalTime: '09:00:00',
  routeDataType: 'GOOGLE_ROUTES',
  routeProviderCallCount: 1,
  routeMatrixElementCount: 4,
  routeMatrixBuildMillis: 484,
  routeCacheEnabled: false,
  routeCacheHitCount: 0,
  routeCacheMissCount: 0,
  routeCacheFailureCount: 0,
  routeCacheHitRatio: 0,
  costSummary: {
    currency: 'KRW',
    limitMinor: null,
    fixedCostMinor: 0,
    knownVisitCostMinor: 0,
    estimatedTotalMinor: 0,
    unpricedPlaceCount: 0,
    remainingMinor: null,
  },
  timeZoneId: 'Asia/Seoul',
  createdAt: '2026-09-01T00:00:00Z',
  days: [],
  items: [],
  exclusions: [],
}

describe('itinerary route source', () => {
  afterEach(cleanup)

  it.each([
    { routeDataType: 'GOOGLE_ROUTES', label: '실제 도로 경로', otherLabel: '좌표 기반 예상 경로' },
    { routeDataType: 'STRAIGHT_LINE_ESTIMATE', label: '좌표 기반 예상 경로', otherLabel: '실제 도로 경로' },
  ] as const)('labels $routeDataType as $label', ({ routeDataType, label, otherLabel }) => {
    render(<ItineraryWorkspace
      trip={trip}
      itinerary={{ ...itinerary, routeDataType }}
      previousItinerary={null}
      itineraryPlaces={{}}
      onItineraryChanged={vi.fn()}
      onError={vi.fn()}
      onGoToPlaces={vi.fn()}
    />)

    expect(screen.getByText(label, { exact: true })).toBeVisible()
    expect(screen.queryByText(otherLabel, { exact: true })).not.toBeInTheDocument()
    expect(screen.getByText('Matrix 4요소 · 484ms')).toBeVisible()
  })
})
