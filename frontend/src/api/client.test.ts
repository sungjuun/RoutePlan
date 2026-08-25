import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from './client'

describe('API client', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('returns typed JSON for successful requests', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: 7,
      nickname: '여행자',
      createdAt: '2026-08-25T00:00:00Z',
    }), { status: 201, headers: { 'Content-Type': 'application/json' } })))

    await expect(api.createUser('여행자')).resolves.toMatchObject({ id: 7, nickname: '여행자' })
    expect(fetch).toHaveBeenCalledWith('/api/v1/users', expect.objectContaining({ method: 'POST' }))
  })

  it('preserves backend error codes and violations', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 'INFEASIBLE_MUST_VISIT',
      message: '필수 장소를 방문할 수 없습니다.',
      path: '/api/v1/trips/1/optimize',
      timestamp: '2026-08-25T00:00:00Z',
      violations: [{ placeId: 3, placeName: '휴무 장소', reason: 'CLOSED', message: '휴무일입니다.' }],
    }), { status: 422, headers: { 'Content-Type': 'application/json' } })))

    const error = await api.optimize(1, 'NEAREST_NEIGHBOR').catch((value: unknown) => value)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).body.code).toBe('INFEASIBLE_MUST_VISIT')
    expect((error as ApiError).body.violations[0].placeName).toBe('휴무 장소')
  })
})
