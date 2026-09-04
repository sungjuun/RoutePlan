import type {
  ApiErrorBody,
  ApplyNaturalLanguageConstraintsInput,
  AuthSession,
  BudgetCurrency,
  CopyRouteInput,
  CreateTripInput,
  Itinerary,
  ManualItineraryEditInput,
  ManualItineraryEditPreview,
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
  SharedExpense,
  SignupInput,
  Trip,
  TripSummary,
  TripPlaceConstraints,
  TripWeatherForecast,
  TripWeatherForecastInput,
  TripBudget,
  TripBudgetInput,
  User,
  Wishlist,
  WishlistSummary,
  WishlistPriority,
  ContentImport,
  ExchangeRateQuote,
  NearbyPlaceRecommendation,
  TripCollaboration,
  TripMemberRole,
  TripSettlement,
  TripVoteValue,
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
    public readonly retryAfterSeconds?: number,
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
      ...(init?.body && !(init.body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}),
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
    if (response.status === 401 || response.status === 403) csrfRequest = null
    const retryAfter = Number(response.headers.get('Retry-After'))
    throw new ApiError(response.status, body, retryAfter > 0 ? retryAfter : undefined)
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
  getAuthOptions: () => request<{ mailMode: 'DISABLED' | 'LOCAL' | 'SMTP' }>('/auth/options'),
  requestEmailVerification: () => request<void>('/auth/email/verification-request', { method: 'POST' }),
  verifyEmail: (token: string) => request<void>('/auth/email/verify', json('POST', { token })),
  requestPasswordReset: (email: string) => request<{ message: string }>('/auth/password/reset-request', json('POST', { email })),
  resetPassword: async (token: string, newPassword: string) => {
    await request<void>('/auth/password/reset', json('POST', { token, newPassword }))
    csrfRequest = null
  },
  changePassword: async (currentPassword: string, newPassword: string) => {
    await request<void>('/auth/password/change', json('POST', { currentPassword, newPassword }))
    csrfRequest = null
  },
  changeNickname: (nickname: string) => request<User>('/auth/profile', json('PATCH', { nickname })),
  changeEmail: async (currentPassword: string, newEmail: string) => {
    await request<void>('/auth/email/change', json('POST', { currentPassword, newEmail }))
    csrfRequest = null
  },
  deleteAccount: async (currentPassword: string, confirmation: string) => {
    await request<void>('/auth/account', json('DELETE', { currentPassword, confirmation }))
    csrfRequest = null
  },

  uploadProfileImage: (file: File) => {
    const body = new FormData()
    body.append('file', file)
    return request<{ profileImageUrl: string }>('/profile/avatar', { method: 'PUT', body })
  },
  removeProfileImage: () => request<{ profileImageUrl: null }>('/profile/avatar', { method: 'DELETE' }),

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

  getWishlists: () => request<WishlistSummary[]>('/wishlists'),

  getWishlist: (wishlistId: number) => request<Wishlist>(`/wishlists/${wishlistId}`),

  createWishlist: (input: { name: string; country?: string; city?: string }) =>
    request<Wishlist>('/wishlists', json('POST', input)),

  updateWishlist: (wishlistId: number, input: { name: string; country?: string; city?: string }) =>
    request<Wishlist>(`/wishlists/${wishlistId}`, json('PATCH', input)),

  deleteWishlist: (wishlistId: number) => request<void>(`/wishlists/${wishlistId}`, { method: 'DELETE' }),

  addWishlistPlace: (wishlistId: number, input: {
    placeId: number
    priority?: WishlistPriority
    sourceType?: string
    sourceUrl?: string
    memo?: string
    estimatedCostMinor?: number
  }) => request<Wishlist>(`/wishlists/${wishlistId}/places`, json('POST', input)),

  updateWishlistPlace: (wishlistId: number, wishlistPlaceId: number, input: {
    priority?: WishlistPriority
    sourceType?: string
    sourceUrl?: string
    memo?: string
    estimatedCostMinor?: number
  }) => request<Wishlist>(`/wishlists/${wishlistId}/places/${wishlistPlaceId}`, json('PATCH', input)),

  removeWishlistPlace: (wishlistId: number, wishlistPlaceId: number) =>
    request<void>(`/wishlists/${wishlistId}/places/${wishlistPlaceId}`, { method: 'DELETE' }),

  createTripFromWishlist: (wishlistId: number, input: CreateTripInput & { wishlistPlaceIds: number[] }) =>
    request<Trip>(`/wishlists/${wishlistId}/trips`, json('POST', input)),

  startContentImport: (input: { url: string; inputText?: string; wishlistId?: number }) =>
    request<ContentImport>('/imports/url', json('POST', input)),

  getContentImport: (importId: number) => request<ContentImport>(`/imports/${importId}`),

  retryContentImport: (importId: number, inputText: string) =>
    request<ContentImport>(`/imports/${importId}/retry`, json('POST', { inputText })),

  saveContentImport: (importId: number, wishlistId: number, candidateIds: number[]) =>
    request<Wishlist>(`/imports/${importId}/save`, json('POST', { wishlistId, candidateIds })),

  getTrips: () => request<TripSummary[]>('/trips'),

  getTrip: (tripId: number) => request<Trip>(`/trips/${tripId}`),

  getTripCollaboration: (tripId: number) =>
    request<TripCollaboration>(`/trips/${tripId}/collaboration`),

  addTripMember: (tripId: number, email: string, role: Exclude<TripMemberRole, 'OWNER'>) =>
    request<TripCollaboration>(`/trips/${tripId}/members`, json('POST', { email, role })),

  updateTripMember: (tripId: number, memberId: number, role: Exclude<TripMemberRole, 'OWNER'>) =>
    request<TripCollaboration>(`/trips/${tripId}/members/${memberId}`, json('PATCH', { role })),

  removeTripMember: (tripId: number, memberId: number) =>
    request<TripCollaboration>(`/trips/${tripId}/members/${memberId}`, { method: 'DELETE' }),

  voteTripPlace: (tripId: number, placeId: number, value: TripVoteValue) =>
    request<TripCollaboration>(`/trips/${tripId}/places/${placeId}/vote`, json('PUT', { value })),

  removeTripPlaceVote: (tripId: number, placeId: number) =>
    request<TripCollaboration>(`/trips/${tripId}/places/${placeId}/vote`, { method: 'DELETE' }),

  getTripSettlement: (tripId: number) =>
    request<TripSettlement>(`/trips/${tripId}/settlement`),

  addSharedExpense: (tripId: number, input: {
    requestId: string
    date: string
    category: SharedExpense['category']
    description: string
    amountMinor: number
    placeId: number | null
    currency: BudgetCurrency
    payerUserId: number
    participantUserIds: number[]
  }) => request<TripSettlement>(`/trips/${tripId}/settlement/expenses`, json('POST', input)),

  removeSharedExpense: (tripId: number, expenseId: number) =>
    request<TripSettlement>(`/trips/${tripId}/settlement/expenses/${expenseId}`, { method: 'DELETE' }),

  getNearbyRecommendations: (tripId: number, input: {
    date: string
    currentTime: string
    currentLatitude: number
    currentLongitude: number
    nextPlaceId?: number
    availableMinutes: number
    maxResults?: number
  }) => {
    const params = new URLSearchParams({
      date: input.date,
      currentTime: input.currentTime,
      currentLatitude: String(input.currentLatitude),
      currentLongitude: String(input.currentLongitude),
      availableMinutes: String(input.availableMinutes),
      maxResults: String(input.maxResults ?? 5),
    })
    if (input.nextPlaceId != null) params.set('nextPlaceId', String(input.nextPlaceId))
    return request<NearbyPlaceRecommendation[]>(`/trips/${tripId}/nearby-recommendations?${params}`)
  },

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

  getTripExchangeRate: (tripId: number, quote = 'KRW') =>
    request<ExchangeRateQuote>(`/trips/${tripId}/exchange-rate?quote=${encodeURIComponent(quote)}`),

  replaceTripBudget: (tripId: number, input: TripBudgetInput) =>
    request<TripBudget>(`/trips/${tripId}/budget`, json('PUT', input)),

  replaceTripWeather: (tripId: number, forecasts: TripWeatherForecastInput[]) =>
    request<TripWeatherForecast[]>(
      `/trips/${tripId}/weather`,
      json('PUT', { forecasts }),
    ),

  optimize: (tripId: number, algorithm: OptimizationAlgorithm) =>
    request<Itinerary>(`/trips/${tripId}/optimize?algorithm=${algorithm}`, { method: 'POST' }),

  previewManualItineraryEdit: (tripId: number, input: ManualItineraryEditInput) =>
    request<ManualItineraryEditPreview>(`/trips/${tripId}/itineraries/manual-edit/preview`, json('POST', input)),

  applyManualItineraryEdit: (tripId: number, input: ManualItineraryEditInput) =>
    request<Itinerary>(`/trips/${tripId}/itineraries/manual-edit`, json('POST', input)),

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
