export type TransportMode = 'WALKING' | 'DRIVING' | 'PUBLIC_TRANSIT'
export type TripPace = 'ACTIVE' | 'STANDARD' | 'RELAXED'
export type OptimizationAlgorithm =
  | 'NEAREST_NEIGHBOR'
  | 'EXACT_SEARCH'
  | 'NEAREST_NEIGHBOR_2_OPT'
export type ItineraryChangeReason =
  | 'DELAY'
  | 'PLACE_ADDED'
  | 'PLACE_REMOVED'
  | 'USER_REQUEST'
  | 'OTHER'

export interface User {
  id: number
  nickname: string
  createdAt: string
}

export interface TripPlace {
  placeId: number
  name: string
  latitude: number
  longitude: number
  category: string | null
  averageStayMinutes: number
  priority: number
  mustVisit: boolean
  preferredStartTime: string | null
  preferredEndTime: string | null
  minimumStayMinutes: number | null
  maximumStayMinutes: number | null
}

export interface Trip {
  id: number
  userId: number
  name: string
  startDate: string
  endDate: string
  dailyStartTime: string
  dailyEndTime: string
  accommodationName: string
  accommodationLatitude: number
  accommodationLongitude: number
  transportMode: TransportMode
  pace: TripPace
  status: 'DRAFT' | 'OPTIMIZED'
  createdAt: string
  updatedAt: string
  places: TripPlace[]
}

export interface Place {
  id: number
  externalPlaceId: string | null
  name: string
  latitude: number
  longitude: number
  category: string | null
  averageStayMinutes: number
  createdAt: string
  updatedAt: string
}

export interface PlaceSearchResult {
  externalPlaceId: string
  name: string
  formattedAddress: string
  latitude: number
  longitude: number
  primaryType: string | null
  provider: string
}

export interface ItineraryItem {
  itineraryItemId: number
  sequence: number
  placeId: number
  placeName: string
  travelDistanceMeters: number
  estimatedTravelMinutes: number
  visitDate: string
  arrivalTime: string
  startTime: string
  endTime: string
  waitingMinutes: number
  stayMinutes: number
  priority: number
  mustVisit: boolean
  status: 'PLANNED' | 'COMPLETED'
}

export interface ItineraryExclusion {
  placeId: number
  placeName: string
  priority: number
  reason: 'CLOSED' | 'TIME_WINDOW' | 'DAILY_LIMIT'
}

export interface Itinerary {
  itineraryId: number
  tripId: number
  version: number
  generationType: 'INITIAL_OPTIMIZATION' | 'REOPTIMIZATION'
  parentItineraryId: number | null
  changeReason: ItineraryChangeReason | null
  changeReasonDetail: string | null
  reoptimizationStartTime: string | null
  reoptimizationStartLatitude: number | null
  reoptimizationStartLongitude: number | null
  algorithm: OptimizationAlgorithm
  totalDistanceMeters: number
  estimatedTravelMinutes: number
  optimizationScore: number
  visitedPriorityScore: number
  totalStayMinutes: number
  totalWaitingMinutes: number
  closedTour: boolean
  returnTravelDistanceMeters: number
  returnTravelMinutes: number
  returnArrivalTime: string
  routeDataType: string
  routeProviderCallCount: number
  routeMatrixElementCount: number
  routeMatrixBuildMillis: number
  routeCacheEnabled: boolean
  routeCacheHitCount: number
  routeCacheMissCount: number
  routeCacheFailureCount: number
  routeCacheHitRatio: number
  createdAt: string
  items: ItineraryItem[]
  exclusions: ItineraryExclusion[]
}

export interface ApiViolation {
  placeId: number | null
  placeName: string | null
  reason: string
  message: string
}

export interface ApiErrorBody {
  code: string
  message: string
  path: string
  timestamp: string
  violations: ApiViolation[]
}

export interface CreateTripInput {
  userId: number
  name: string
  startDate: string
  endDate: string
  dailyStartTime: string
  dailyEndTime: string
  accommodationName: string
  accommodationLatitude: number
  accommodationLongitude: number
  transportMode: TransportMode
  pace: TripPace
}

export interface TripPlaceConstraints {
  priority: number
  mustVisit: boolean
  preferredStartTime: string | null
  preferredEndTime: string | null
  minimumStayMinutes: number | null
  maximumStayMinutes: number | null
}

export interface ReoptimizeInput {
  sourceItineraryId: number
  currentTime: string
  currentLatitude: number
  currentLongitude: number
  completedItemIds: number[]
  reason: ItineraryChangeReason
  reasonDetail: string | null
}

export interface WorkspaceReference {
  user: User | null
  tripId: number | null
}
