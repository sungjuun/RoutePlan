import type {
  ApiErrorBody,
  CreateTripInput,
  Itinerary,
  OptimizationAlgorithm,
  Place,
  PlaceSearchResult,
  ReoptimizeInput,
  Trip,
  TripPlaceConstraints,
  User,
} from '../types'

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: ApiErrorBody,
  ) {
    super(body.message)
    this.name = 'ApiError'
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/v1${path}`, {
    ...init,
    headers: {
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  })

  if (!response.ok) {
    const fallback: ApiErrorBody = {
      code: 'NETWORK_ERROR',
      message: '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      path,
      timestamp: new Date().toISOString(),
      violations: [],
    }
    let body = fallback
    try {
      body = (await response.json()) as ApiErrorBody
    } catch {
      // Keep a user-safe fallback for non-JSON proxy or server errors.
    }
    throw new ApiError(response.status, body)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

function json(method: string, body: unknown): RequestInit {
  return { method, body: JSON.stringify(body) }
}

export const api = {
  createUser: (nickname: string) =>
    request<User>('/users', json('POST', { nickname })),

  createTrip: (input: CreateTripInput) =>
    request<Trip>('/trips', json('POST', input)),

  getTrip: (tripId: number) => request<Trip>(`/trips/${tripId}`),

  updateTrip: (tripId: number, input: Omit<CreateTripInput, 'userId'>) =>
    request<Trip>(`/trips/${tripId}`, json('PATCH', input)),

  createPlace: (input: {
    name: string
    latitude: number
    longitude: number
    category: string | null
    averageStayMinutes: number
  }) => request<Place>('/places', json('POST', input)),

  getPlace: (placeId: number) => request<Place>(`/places/${placeId}`),

  searchPlaces: (input: {
    query: string
    latitude?: number
    longitude?: number
  }) => {
    const params = new URLSearchParams({ query: input.query, limit: '10', languageCode: 'ko' })
    if (input.latitude != null && input.longitude != null) {
      params.set('latitude', String(input.latitude))
      params.set('longitude', String(input.longitude))
      params.set('radiusMeters', '10000')
    }
    return request<PlaceSearchResult[]>(`/places/search?${params}`)
  },

  importPlace: (result: PlaceSearchResult, averageStayMinutes = 60) =>
    request<Place>(
      '/places/import',
      json('POST', {
        externalPlaceId: result.externalPlaceId,
        name: result.name,
        latitude: result.latitude,
        longitude: result.longitude,
        category: result.primaryType,
        averageStayMinutes,
      }),
    ),

  addTripPlace: (tripId: number, placeId: number, constraints: TripPlaceConstraints) =>
    request<Trip>(`/trips/${tripId}/places`, json('POST', { placeId, ...constraints })),

  updateTripPlace: (
    tripId: number,
    placeId: number,
    constraints: TripPlaceConstraints,
  ) => request<Trip>(`/trips/${tripId}/places/${placeId}`, json('PATCH', constraints)),

  removeTripPlace: (tripId: number, placeId: number) =>
    request<void>(`/trips/${tripId}/places/${placeId}`, { method: 'DELETE' }),

  optimize: (tripId: number, algorithm: OptimizationAlgorithm) =>
    request<Itinerary>(`/trips/${tripId}/optimize?algorithm=${algorithm}`, { method: 'POST' }),

  getLatestItinerary: (tripId: number) =>
    request<Itinerary>(`/trips/${tripId}/itineraries/latest`),

  getItinerary: (itineraryId: number) =>
    request<Itinerary>(`/itineraries/${itineraryId}`),

  reoptimize: (
    tripId: number,
    algorithm: OptimizationAlgorithm,
    input: ReoptimizeInput,
  ) => request<Itinerary>(`/trips/${tripId}/reoptimize?algorithm=${algorithm}`, json('POST', input)),
}
