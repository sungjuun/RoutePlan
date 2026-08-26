import type { Itinerary, ItineraryItem } from '../types'

export interface ItineraryDifference {
  added: ItineraryItem[]
  removed: ItineraryItem[]
  rescheduled: Array<{ before: ItineraryItem; after: ItineraryItem }>
  unchanged: number
}

export function compareItineraries(
  before: Itinerary,
  after: Itinerary,
): ItineraryDifference {
  const beforeByPlace = new Map(before.items.map((item) => [item.placeId, item]))
  const afterByPlace = new Map(after.items.map((item) => [item.placeId, item]))

  const added = after.items.filter((item) => !beforeByPlace.has(item.placeId))
  const removed = before.items.filter((item) => !afterByPlace.has(item.placeId))
  const shared = after.items.filter((item) => beforeByPlace.has(item.placeId))
  const rescheduled = shared
    .map((afterItem) => ({ before: beforeByPlace.get(afterItem.placeId)!, after: afterItem }))
    .filter(({ before: beforeItem, after: afterItem }) =>
      beforeItem.sequence !== afterItem.sequence ||
      beforeItem.visitDate !== afterItem.visitDate ||
      beforeItem.startTime !== afterItem.startTime ||
      beforeItem.endTime !== afterItem.endTime,
    )

  return {
    added,
    removed,
    rescheduled,
    unchanged: shared.length - rescheduled.length,
  }
}

export function minimumCompletedCount(itinerary: Itinerary): number {
  let count = 0
  for (const item of itinerary.items) {
    if (item.status !== 'COMPLETED') break
    count += 1
  }
  return count
}
