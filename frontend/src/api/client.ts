import type {
  ApiErrorBody,
  ApplyNaturalLanguageConstraintsInput,
  AuthSession,
  CopyRouteInput,
  CreateTripInput,
  Itinerary,
  NaturalLanguagePreview,
  OptimizationAlgorithm,
  Place,
  PlaceEnvironment,
  PlaceSearchResult,
  ReoptimizeInput,
  PublishRouteInput,
  RouteLikeResult,
  SharedRouteDetail,
  SharedRoutePage,
  SharedRouteSort,
  SignupInput,
  Trip,
  TripSummary,
  TripPlaceConstraints,
  TripWeatherForecast,
  TripWeatherForecastInput,
  TripBudget,
  TripBudgetInput,
} from '../types'

interface CsrfView {
  headerName: string
  token: string
}

let csrfRequest: Promise<CsrfView> | null = null

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: ApiErrorBody,
  ) {
    super(body.message)
    this.name = 'ApiError'
  }
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const method = (init?.method ?? 'GET').toUpperCase()
  const csrf = ['GET', 'HEAD', 'OPTIONS'].includes(method) ? null : await csrfToken()
  const response = await fetch(`/api/v1${path}`, {
    ...init,
    credentials: 'same-origin',
    headers: {
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...(csrf ? { [csrf.headerName]: csrf.token } : {}),
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
      const parsed = (await response.json()) as Partial<ApiErrorBody>
      if (typeof parsed.code === 'string' && typeof parsed.message === 'string') {
        body = {
          code: parsed.code,
          message: parsed.message,
          path: typeof parsed.path === 'string' ? parsed.path : path,
          timestamp: typeof parsed.timestamp === 'string'
            ? parsed.timestamp
            : fallback.timestamp,
          violations: Array.isArray(parsed.violations) ? parsed.violations : [],
        }
      }
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

async function csrfToken(): Promise<CsrfView> {
  if (!csrfRequest) {
    csrfRequest = fetch('/api/v1/auth/csrf', { credentials: 'same-origin' })
      .then(async (response) => {
        if (!response.ok) throw new Error('보안 토큰을 준비하지 못했습니다.')
        return response.json() as Promise<CsrfView>
      })
      .catch((error) => {
        csrfRequest = null
        throw error
      })
  }
  return csrfRequest
}

function json(method: string, body: unknown): RequestInit {
  return { method, body: JSON.stringify(body) }
}

export const api = {
  getAuthSession: () => request<AuthSession>('/auth/me'),

  signup: async (input: SignupInput) => {
    const session = await request<AuthSession>('/auth/signup', json('POST', input))
    csrfRequest = null
    return session
  },

  login: async (email: string, password: string) => {
    const session = await request<AuthSession>(
      '/auth/login',
      json('POST', { email, password }),
    )
    csrfRequest = null
    return session
  },

  logout: async () => {
    await request<void>('/auth/logout', { method: 'POST' })
    csrfRequest = null
  },

  createTrip: (input: CreateTripInput) =>
    request<Trip>('/trips', json('POST', input)),

  getTrips: () => request<TripSummary[]>('/trips'),

  getTrip: (tripId: number) => request<Trip>(`/trips/${tripId}`),

  updateTrip: (tripId: number, input: CreateTripInput) =>
    request<Trip>(`/trips/${tripId}`, json('PATCH', input)),

  createPlace: (input: {
    name: string
    latitude: number
    longitude: number
    category: string | null
    averageStayMinutes: number
    environment: PlaceEnvironment
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

  getTripWeather: (tripId: number) =>
    request<TripWeatherForecast[]>(`/trips/${tripId}/weather`),

  getTripBudget: (tripId: number) => request<TripBudget>(`/trips/${tripId}/budget`),

  replaceTripBudget: (tripId: number, input: TripBudgetInput) =>
    request<TripBudget>(`/trips/${tripId}/budget`, json('PUT', input)),

  replaceTripWeather: (tripId: number, forecasts: TripWeatherForecastInput[]) =>
    request<TripWeatherForecast[]>(
      `/trips/${tripId}/weather`,
      json('PUT', { forecasts }),
    ),

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

  publishRoute: (itineraryId: number, input: PublishRouteInput) =>
    request<SharedRouteDetail>(`/itineraries/${itineraryId}/share`, json('POST', input)),

  discoverRoutes: (input: {
    region?: string
    travelDays?: number
    sort?: SharedRouteSort
    page?: number
    size?: number
  } = {}) => {
    const params = new URLSearchParams({
      sort: input.sort ?? 'LATEST',
      page: String(input.page ?? 0),
      size: String(input.size ?? 12),
    })
    if (input.region?.trim()) params.set('region', input.region.trim())
    if (input.travelDays != null) params.set('travelDays', String(input.travelDays))
    return request<SharedRoutePage>(`/routes?${params}`)
  },

  getSharedRoute: (routeId: number) =>
    request<SharedRouteDetail>(`/routes/${routeId}`),

  likeSharedRoute: (routeId: number) =>
    request<RouteLikeResult>(`/routes/${routeId}/likes`, { method: 'POST' }),

  unlikeSharedRoute: (routeId: number) =>
    request<RouteLikeResult>(`/routes/${routeId}/likes`, { method: 'DELETE' }),

  copySharedRoute: (routeId: number, input: CopyRouteInput) =>
    request<Trip>(`/routes/${routeId}/copy`, json('POST', input)),

  previewNaturalLanguageConstraints: (tripId: number, text: string) =>
    request<NaturalLanguagePreview>(
      `/trips/${tripId}/natural-language/preview`,
      json('POST', { text }),
    ),

  applyNaturalLanguageConstraints: (
    tripId: number,
    input: ApplyNaturalLanguageConstraintsInput,
  ) => request<Trip>(
    `/trips/${tripId}/natural-language/apply`,
    json('POST', input),
  ),
}
