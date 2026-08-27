import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from './client'

describe('API client', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('gets a CSRF token and returns the authenticated user after signup', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        headerName: 'X-CSRF-TOKEN',
        token: 'csrf-token',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        authenticated: true,
        user: {
          id: 7,
          email: 'traveler@example.com',
          nickname: '여행자',
          createdAt: '2026-08-25T00:00:00Z',
        },
      }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(api.signup({
      email: 'traveler@example.com',
      nickname: '여행자',
      password: 'routeplan12!',
    })).resolves.toMatchObject({ authenticated: true, user: { id: 7 } })
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/v1/auth/signup',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-token' }),
      }),
    )
  })

  it('preserves backend error codes and violations', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation((input: RequestInfo | URL) => {
      if (String(input).includes('/auth/csrf')) {
        return Promise.resolve(new Response(JSON.stringify({
          headerName: 'X-CSRF-TOKEN',
          token: 'csrf-token',
        }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response(JSON.stringify({
        code: 'INFEASIBLE_MUST_VISIT',
        message: '필수 장소를 방문할 수 없습니다.',
        path: '/api/v1/trips/1/optimize',
        timestamp: '2026-08-25T00:00:00Z',
        violations: [{ placeId: 3, placeName: '휴무 장소', reason: 'CLOSED', message: '휴무일입니다.' }],
      }), { status: 422, headers: { 'Content-Type': 'application/json' } }))
    }))

    const error = await api.optimize(1, 'NEAREST_NEIGHBOR').catch((value: unknown) => value)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).body.code).toBe('INFEASIBLE_MUST_VISIT')
    expect((error as ApiError).body.violations[0].placeName).toBe('휴무 장소')
  })

  it('builds community discovery filters without leaking them into the request body', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      content: [],
      page: 1,
      size: 6,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

    await api.discoverRoutes({
      region: ' 오사카 ',
      travelDays: 1,
      sort: 'POPULAR',
      page: 1,
      size: 6,
    })

    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/routes?sort=POPULAR&page=1&size=6&region=%EC%98%A4%EC%82%AC%EC%B9%B4&travelDays=1',
      expect.objectContaining({ headers: {} }),
    )
  })

  it('keeps a safe fallback when a proxy returns a different JSON error shape', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      timestamp: '2026-08-26T00:00:00Z',
      status: 500,
      error: 'Internal Server Error',
      path: '/api/v1/routes',
    }), { status: 500, headers: { 'Content-Type': 'application/json' } })))

    const error = await api.discoverRoutes().catch((value: unknown) => value)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).body.code).toBe('NETWORK_ERROR')
    expect((error as ApiError).message).toBe('요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  })

  it('sends natural language text only to the preview endpoint', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation((input: RequestInfo | URL) => {
      if (String(input).includes('/auth/csrf')) {
        return Promise.resolve(new Response(JSON.stringify({
          headerName: 'X-CSRF-TOKEN',
          token: 'csrf-token',
        }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response(JSON.stringify({
        originalText: '여유롭게 해줘',
        provider: 'RULE_BASED',
        structuredConstraints: {
          dailyStartTime: null,
          dailyEndTime: null,
          pace: 'RELAXED',
          transportMode: null,
          walkingPreference: null,
          placeConstraints: [],
          notes: [],
        },
        trip: {
          before: { dailyStartTime: '09:00:00', dailyEndTime: '20:00:00', pace: 'STANDARD', transportMode: 'WALKING' },
          after: { dailyStartTime: '09:00:00', dailyEndTime: '20:00:00', pace: 'RELAXED', transportMode: 'WALKING' },
          changed: true,
        },
        places: [],
        warnings: [],
        hasChanges: true,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    }))

    await api.previewNaturalLanguageConstraints(11, '여유롭게 해줘')

    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/trips/11/natural-language/preview',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ text: '여유롭게 해줘' }) }),
    )
  })
})
