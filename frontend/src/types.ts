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
export type SharedRouteVisibility = 'PUBLIC' | 'UNLISTED'
export type SharedRouteSort = 'LATEST' | 'POPULAR'
export type WalkingPreference = 'LOW' | 'STANDARD' | 'HIGH'
export type PlacePreference = 'MUST_VISIT' | 'PREFERRED' | 'OPTIONAL'
export type MealType = 'BREAKFAST' | 'LUNCH' | 'DINNER'

export interface User {
  id: number
  email: string
  nickname: string
  createdAt: string
}

export interface AuthSession {
  authenticated: boolean
  user: User | null
}

export interface SignupInput {
  email: string
  nickname: string
  password: string
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

export interface ItineraryDay {
  dayNumber: number
  visitDate: string
  totalDistanceMeters: number
  estimatedTravelMinutes: number
  totalStayMinutes: number
  totalWaitingMinutes: number
  returnTravelDistanceMeters: number
  returnTravelMinutes: number
  returnArrivalTime: string
  returnedToAccommodation: boolean
}

export interface Itinerary {
  itineraryId: number
  tripId: number
  version: number
  generationType: 'INITIAL_OPTIMIZATION' | 'REOPTIMIZATION'
  parentItineraryId: number | null
  changeReason: ItineraryChangeReason | null
  changeReasonDetail: string | null
  reoptimizationStartDate: string | null
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
  days: ItineraryDay[]
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
  currentDate: string
  currentTime: string
  currentLatitude: number
  currentLongitude: number
  completedItemIds: number[]
  reason: ItineraryChangeReason
  reasonDetail: string | null
}

export interface WorkspaceReference {
  tripId: number | null
}

export interface SharedRouteSummary {
  routeId: number
  ownerId: number
  ownerNickname: string
  title: string
  description: string | null
  region: string
  travelDays: number
  visibility: SharedRouteVisibility
  sourceTripName: string
  sourceStartDate: string
  transportMode: TransportMode
  pace: TripPace
  placeCount: number
  placePreview: string
  totalDistanceMeters: number
  estimatedTravelMinutes: number
  optimizationScore: number
  viewCount: number
  copyCount: number
  likeCount: number
  publishedAt: string
}

export interface SharedRouteItem {
  itemId: number
  placeId: number
  dayNumber: number
  sequence: number
  visitDate: string
  placeName: string
  latitude: number
  longitude: number
  category: string | null
  arrivalTime: string
  startTime: string
  endTime: string
  travelDistanceMeters: number
  estimatedTravelMinutes: number
  waitingMinutes: number
  stayMinutes: number
  priority: number
  mustVisit: boolean
}

export interface SharedRouteDetail extends Omit<SharedRouteSummary, 'placePreview'> {
  sourceTripId: number | null
  sourceItineraryId: number | null
  sourceItineraryVersion: number
  dailyStartTime: string
  dailyEndTime: string
  accommodationName: string
  accommodationLatitude: number
  accommodationLongitude: number
  algorithm: OptimizationAlgorithm
  likedByViewer: boolean
  items: SharedRouteItem[]
}

export interface SharedRoutePage {
  content: SharedRouteSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface RouteLikeResult {
  routeId: number
  likeCount: number
  liked: boolean
}

export interface PublishRouteInput {
  title: string
  description: string | null
  region: string
  visibility: SharedRouteVisibility
}

export interface CopyRouteInput {
  name: string
  startDate: string
  dailyStartTime: string
  dailyEndTime: string
  accommodationName: string
  accommodationLatitude: number
  accommodationLongitude: number
  transportMode: TransportMode
  pace: TripPace
}

export interface NaturalLanguagePlaceConstraint {
  placeName: string | null
  preference: PlacePreference | null
  preferredStartTime: string | null
  preferredEndTime: string | null
  minimumStayMinutes: number | null
  maximumStayMinutes: number | null
  mealType: MealType | null
}

export interface StructuredTravelConstraints {
  dailyStartTime: string | null
  dailyEndTime: string | null
  pace: TripPace | null
  transportMode: TransportMode | null
  walkingPreference: WalkingPreference | null
  placeConstraints: NaturalLanguagePlaceConstraint[]
  notes: string[]
}

export interface NaturalLanguageTripSettings {
  dailyStartTime: string
  dailyEndTime: string
  pace: TripPace
  transportMode: TransportMode
}

export interface NaturalLanguagePlaceSettings {
  placeId: number
  placeName: string
  priority: number
  mustVisit: boolean
  preferredStartTime: string | null
  preferredEndTime: string | null
  minimumStayMinutes: number | null
  maximumStayMinutes: number | null
}

export interface NaturalLanguagePreview {
  originalText: string
  provider: string
  structuredConstraints: StructuredTravelConstraints
  trip: {
    before: NaturalLanguageTripSettings
    after: NaturalLanguageTripSettings
    changed: boolean
  }
  places: Array<{
    before: NaturalLanguagePlaceSettings
    after: NaturalLanguagePlaceSettings
    changed: boolean
  }>
  warnings: string[]
  hasChanges: boolean
}

export interface ApplyNaturalLanguageConstraintsInput {
  trip: NaturalLanguageTripSettings
  places: Array<Omit<NaturalLanguagePlaceSettings, 'placeName'>>
}
