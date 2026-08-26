import { describe, expect, it } from 'vitest'
import type { Itinerary, ItineraryItem } from '../types'
import { compareItineraries, minimumCompletedCount } from './itinerary'

function item(
  itineraryItemId: number,
  placeId: number,
  sequence: number,
  startTime: string,
  status: ItineraryItem['status'] = 'PLANNED',
): ItineraryItem {
  return {
    itineraryItemId,
    placeId,
    placeName: `장소 ${placeId}`,
    sequence,
    travelDistanceMeters: 0,
    estimatedTravelMinutes: 0,
    visitDate: '2026-09-10',
    arrivalTime: startTime,
    startTime,
    endTime: startTime === '09:00:00' ? '10:00:00' : '12:00:00',
    waitingMinutes: 0,
    stayMinutes: 60,
    priority: 70,
    mustVisit: false,
    status,
  }
}

function itinerary(version: number, items: ItineraryItem[]): Itinerary {
  return {
    itineraryId: version,
    tripId: 1,
    version,
    generationType: version === 1 ? 'INITIAL_OPTIMIZATION' : 'REOPTIMIZATION',
    parentItineraryId: version === 1 ? null : version - 1,
    changeReason: version === 1 ? null : 'DELAY',
    changeReasonDetail: null,
    reoptimizationStartTime: version === 1 ? null : '10:30:00',
    reoptimizationStartLatitude: null,
    reoptimizationStartLongitude: null,
    algorithm: 'NEAREST_NEIGHBOR',
    totalDistanceMeters: 0,
    estimatedTravelMinutes: 0,
    optimizationScore: 0,
    visitedPriorityScore: 0,
    totalStayMinutes: 0,
    totalWaitingMinutes: 0,
    closedTour: true,
    returnTravelDistanceMeters: 0,
    returnTravelMinutes: 0,
    returnArrivalTime: '18:00:00',
    routeDataType: 'STRAIGHT_LINE_ESTIMATE',
    routeProviderCallCount: 0,
    routeMatrixElementCount: 0,
    routeMatrixBuildMillis: 0,
    routeCacheEnabled: false,
    routeCacheHitCount: 0,
    routeCacheMissCount: 0,
    routeCacheFailureCount: 0,
    routeCacheHitRatio: 0,
    createdAt: '2026-09-10T00:00:00Z',
    days: [],
    items,
    exclusions: [],
  }
}

describe('itinerary comparison', () => {
  it('separates additions, removals, rescheduling, and unchanged visits', () => {
    const before = itinerary(1, [item(1, 10, 1, '09:00:00'), item(2, 20, 2, '11:00:00'), item(3, 30, 3, '13:00:00')])
    const after = itinerary(2, [item(4, 10, 1, '09:00:00', 'COMPLETED'), item(5, 40, 2, '11:00:00'), item(6, 20, 3, '14:00:00')])

    const diff = compareItineraries(before, after)

    expect(diff.added.map((value) => value.placeId)).toEqual([40])
    expect(diff.removed.map((value) => value.placeId)).toEqual([30])
    expect(diff.rescheduled.map((value) => value.after.placeId)).toEqual([20])
    expect(diff.unchanged).toBe(1)
  })

  it('counts only the contiguous completed prefix', () => {
    const source = itinerary(2, [
      item(4, 10, 1, '09:00:00', 'COMPLETED'),
      item(5, 20, 2, '11:00:00', 'COMPLETED'),
      item(6, 30, 3, '13:00:00'),
    ])

    expect(minimumCompletedCount(source)).toBe(2)
  })
})
