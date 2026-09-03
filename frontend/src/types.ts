export type TransportMode = 'WALKING' | 'DRIVING' | 'PUBLIC_TRANSIT'
export type RouteDataType = 'STRAIGHT_LINE_ESTIMATE' | 'GOOGLE_ROUTES'
export type TripPace = 'ACTIVE' | 'STANDARD' | 'RELAXED'
export type OptimizationAlgorithm =
  | 'NEAREST_NEIGHBOR'
  | 'EXACT_SEARCH'
  | 'NEAREST_NEIGHBOR_2_OPT'
export type ItineraryChangeReason =
  | 'DELAY'
  | 'PLACE_ADDED'
  | 'PLACE_REMOVED'
  | 'WEATHER'
  | 'BUDGET'
  | 'USER_REQUEST'
  | 'OTHER'
export type SharedRouteVisibility = 'PUBLIC' | 'UNLISTED'
export type SharedRouteSort = 'LATEST' | 'POPULAR'
export type WalkingPreference = 'LOW' | 'STANDARD' | 'HIGH'
export type PlacePreference = 'MUST_VISIT' | 'PREFERRED' | 'OPTIONAL'
export type MealType = 'BREAKFAST' | 'LUNCH' | 'DINNER'
export type PlaceEnvironment = 'INDOOR' | 'OUTDOOR' | 'MIXED'
export type WeatherCondition = 'UNKNOWN' | 'CLEAR' | 'CLOUDY' | 'RAIN' | 'SNOW' | 'EXTREME'
export type BudgetCurrency = 'KRW' | 'JPY' | 'USD' | 'EUR' | 'GBP' | 'CNY'
export type WishlistPriority = 'MUST' | 'HIGH' | 'NORMAL' | 'LOW'
export type ContentSourceType = 'MANUAL' | 'INSTAGRAM' | 'YOUTUBE' | 'TIKTOK' | 'BLOG' | 'COMMUNITY' | 'GENERIC_WEB'
export type ContentImportStatus = 'RECEIVED' | 'PROCESSING' | 'PLACE_MATCHING' | 'COMPLETED' | 'AWAITING_INPUT' | 'FAILED'

export interface TripBudgetInput {
  currency: BudgetCurrency
  limitMinor: number | null
  fixedCostMinor: number
  placeCosts: Array<{ placeId: number; estimatedCostMinor: number | null }>
}

export interface TripBudget extends TripBudgetInput {
  placeCosts: Array<{
    placeId: number
    placeName: string
    mustVisit: boolean
    estimatedCostMinor: number | null
  }>
}

export interface CostSummary {
  currency: BudgetCurrency
  limitMinor: number | null
  fixedCostMinor: number
  knownVisitCostMinor: number
  estimatedTotalMinor: number
  unpricedPlaceCount: number
  remainingMinor: number | null
}

export interface ExchangeRateQuote {
  base: BudgetCurrency
  quote: BudgetCurrency
  rate: number
  rateDate: string
  fetchedAt: string
  provider: string
}

export interface User {
  emailVerified?: boolean
  profileImageUrl?: string | null
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
  externalPlaceId?: string | null
  placeId: number
  name: string
  latitude: number
  longitude: number
  category: string | null
  averageStayMinutes: number
  environment: PlaceEnvironment
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

export interface TripSummary {
  id: number
  name: string
  startDate: string
  endDate: string
  accommodationName: string
  transportMode: TransportMode
  pace: TripPace
  status: 'DRAFT' | 'OPTIMIZED'
  placeCount: number
  createdAt: string
  updatedAt: string
}

export interface Place {
  id: number
  externalPlaceId: string | null
  name: string
  latitude: number
  longitude: number
  category: string | null
  averageStayMinutes: number
  environment: PlaceEnvironment
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

export interface WishlistPlace {
  id: number
  placeId: number
  externalPlaceId: string | null
  name: string
  latitude: number
  longitude: number
  category: string | null
  priority: WishlistPriority
  sourceType: ContentSourceType
  sourceUrl: string | null
  memo: string | null
  estimatedCostMinor: number | null
  createdAt: string
  updatedAt: string
}

export interface WishlistSummary {
  id: number
  name: string
  country: string | null
  city: string | null
  placeCount: number
  createdAt: string
  updatedAt: string
}

export interface Wishlist extends Omit<WishlistSummary, 'placeCount'> {
  places: WishlistPlace[]
}

export interface ContentImportCandidate {
  id: number
  mentionOrder: number
  matchRank: number
  extractedName: string
  matched: boolean
  externalPlaceId: string | null
  matchedName: string | null
  formattedAddress: string | null
  latitude: number | null
  longitude: number | null
  primaryType: string | null
  provider: string | null
}

export interface ContentImport {
  id: number
  wishlistId: number | null
  sourceType: ContentSourceType
  status: ContentImportStatus
  sourceUrl: string
  detectedTitle: string | null
  warning: string | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
  startedAt: string | null
  completedAt: string | null
  candidates: ContentImportCandidate[]
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
  environment: PlaceEnvironment
  weatherScoreAdjustment: number
  estimatedCostMinor: number | null
  status: 'PLANNED' | 'COMPLETED'
}

export interface ItineraryExclusion {
  placeId: number
  placeName: string
  priority: number
  reason: 'CLOSED' | 'TIME_WINDOW' | 'DAILY_LIMIT' | 'BUDGET'
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
  weatherCondition: WeatherCondition
  precipitationProbability: number
}

export interface TripWeatherForecast {
  forecastDate: string
  condition: Exclude<WeatherCondition, 'UNKNOWN'>
  precipitationProbability: number
  updatedAt: string
}

export interface TripWeatherForecastInput {
  forecastDate: string
  condition: WeatherCondition
  precipitationProbability: number
}

export interface Itinerary {
  timeZoneId?: string
  dataWarnings?: string
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
  routeDataType: RouteDataType
  routeProviderCallCount: number
  routeMatrixElementCount: number
  routeMatrixBuildMillis: number
  routeCacheEnabled: boolean
  routeCacheHitCount: number
  routeCacheMissCount: number
  routeCacheFailureCount: number
  routeCacheHitRatio: number
  costSummary: CostSummary
  createdAt: string
  days: ItineraryDay[]
  items: ItineraryItem[]
  exclusions: ItineraryExclusion[]
}

export interface ItineraryDayAssignment {
  visitDate: string
  itineraryItemIds: number[]
}

export interface ManualItineraryEditInput {
  sourceItineraryId: number
  assignments: ItineraryDayAssignment[]
}

export interface ManualItineraryEditPreview {
  sourceItineraryId: number
  sourceVersion: number
  affectedDates: string[]
  travelMinutesDelta: number
  distanceMetersDelta: number
  totalTravelMinutes: number
  totalDistanceMeters: number
  recommendation: null | {
    assignments: ItineraryDayAssignment[]
    savingMinutes: number
    savingDistanceMeters: number
    message: string
  }
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
