import { expect, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { fileURLToPath } from 'node:url'
import { signUp, testAccount } from './helpers'

const stubURL = process.env.E2E_GOOGLE_STUB_URL
const backendURL = process.env.E2E_BACKEND_URL
const composeProject = process.env.E2E_RESTART_PROJECT
const backendReplicas = Number(process.env.E2E_BACKEND_REPLICAS ?? '1')
const execFileAsync = promisify(execFile)

interface StubState {
  requestCount: number
  elementCount: number
  maxConcurrentRequests: number
  requests: Array<{
    travelMode: string
    routingPreference: string | null
    departureTime: string | null
    elements: number
  }>
}

interface ItineraryResult {
  itineraryId: number
  version: number
  routeDataType: string
  routeProviderCallCount: number
  routeCacheHitCount: number
  routeCacheMissCount: number
  dataWarnings: string
}

function futureDate(days: number): string {
  const value = new Date()
  value.setUTCDate(value.getUTCDate() + days)
  return value.toISOString().slice(0, 10)
}

interface MutationResponse<T> {
  body: T
  instance: string | null
}

async function mutateResponse<T>(request: APIRequestContext, method: 'POST' | 'PUT', path: string,
  data: unknown): Promise<MutationResponse<T>> {
  const csrfResponse = await request.get('/api/v1/auth/csrf')
  expect(csrfResponse.ok()).toBe(true)
  const csrf = await csrfResponse.json() as { headerName: string; token: string }
  const response = await request.fetch(`/api/v1${path}`, {
    method,
    headers: { [csrf.headerName]: csrf.token },
    data,
  })
  const text = await response.text()
  expect(response.ok(), `${method} ${path}: ${response.status()} ${text}`).toBe(true)
  return {
    body: text ? JSON.parse(text) as T : undefined as T,
    instance: response.headers()['x-routeplan-instance'] ?? null,
  }
}

async function mutate<T>(request: APIRequestContext, method: 'POST' | 'PUT', path: string, data: unknown): Promise<T> {
  return (await mutateResponse<T>(request, method, path, data)).body
}

async function resetStub(request: APIRequestContext, delayMs = 120): Promise<void> {
  const reset = await request.post(`${stubURL}/__admin/reset`)
  expect(reset.ok()).toBe(true)
  const configured = await request.post(`${stubURL}/__admin/config`, { data: { delayMs } })
  expect(configured.ok()).toBe(true)
}

async function stubState(request: APIRequestContext): Promise<StubState> {
  const response = await request.get(`${stubURL}/__admin/state`)
  expect(response.ok()).toBe(true)
  return response.json() as Promise<StubState>
}

async function createTrafficTrip(page: Page, seed: string, placeCount: number, dateOffset: number): Promise<number> {
  const date = futureDate(dateOffset)
  const trip = await mutate<{ id: number }>(page.request, 'POST', '/trips', {
    name: `V25 교통량 E2E ${seed}`,
    startDate: date,
    endDate: date,
    dailyStartTime: '09:00',
    dailyEndTime: placeCount > 3 ? '18:00' : '12:00',
    accommodationName: 'V25 E2E 숙소',
    accommodationLatitude: 35.6800 + dateOffset * 0.0001,
    accommodationLongitude: 139.7600 + dateOffset * 0.0001,
    transportMode: 'DRIVING',
    pace: 'STANDARD',
  })
  for (let index = 0; index < placeCount; index += 1) {
    const place = await mutate<{ id: number }>(page.request, 'POST', '/places', {
      name: `V25 E2E 장소 ${seed}-${index + 1}`,
      latitude: 35.6815 + dateOffset * 0.0001 + index * 0.004,
      longitude: 139.7620 + dateOffset * 0.0001 + index * 0.003,
      category: 'E2E_TRAFFIC',
      averageStayMinutes: 30,
      environment: 'MIXED',
    })
    await mutate(page.request, 'POST', `/trips/${trip.id}/places`, {
      placeId: place.id,
      priority: 90 - index,
      mustVisit: true,
      minimumStayMinutes: 30,
      maximumStayMinutes: 30,
    })
  }
  await mutate(page.request, 'PUT', `/trips/${trip.id}/time-zone`, { timeZoneId: 'Asia/Tokyo' })
  return trip.id
}

async function openItineraryPlanner(page: Page, tripId: number): Promise<void> {
  await page.goto(`/?tripId=${tripId}`)
  await expect(page.getByRole('heading', { name: '오늘의 장면을 골라보세요' })).toBeVisible()
  await page.getByRole('button', { name: '일정 만들기', exact: true }).click()
  await expect(page.getByRole('heading', { name: '선택한 장면을 여행 날짜마다' })).toBeVisible()
  await expect(page.getByRole('button', { name: /내 일정 계산하기/ })).toBeEnabled()
}

async function clearIsolatedRedisRouteCache(): Promise<number> {
  const { stdout } = await runCompose([
    'exec', '-T', 'redis', 'redis-cli', 'EVAL',
    "local keys=redis.call('keys',ARGV[1]); if #keys==0 then return 0 end; return redis.call('del',unpack(keys))",
    '0', 'routeplan:e2e:v25:google-routes:*',
  ])
  return Number(stdout.trim())
}

async function runCompose(arguments_: string[]): Promise<{ stdout: string; stderr: string }> {
  if (!composeProject || !/^routeplan-e2e-v25(?:-[a-z0-9-]+)?$/.test(composeProject)) {
    throw new Error('Unsafe V25 Compose target')
  }
  const docker = process.platform === 'win32' ? 'docker.exe' : 'docker'
  const baseCompose = fileURLToPath(new URL('../../compose.yaml', import.meta.url))
  const v25Compose = fileURLToPath(new URL('../../compose.e2e-v25.yaml', import.meta.url))
  return execFileAsync(docker, [
    'compose', '-p', composeProject, '-f', baseCompose, '-f', v25Compose,
    ...arguments_,
  ], { timeout: 30_000, windowsHide: true })
}

async function waitForRedis(): Promise<void> {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    try {
      const { stdout } = await runCompose(['exec', '-T', 'redis', 'redis-cli', 'ping'])
      if (stdout.trim() === 'PONG') return
    } catch {
      // The real Redis process may still be restoring its in-memory state.
    }
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  throw new Error('Redis did not recover after the V25 fault injection')
}

async function interruptPostgisRouteCache(): Promise<void> {
  const { stdout } = await runCompose([
    'exec', '-T', 'postgres', 'psql', '-At', '-v', 'ON_ERROR_STOP=1',
    '-U', 'routeplan', '-d', 'routeplan', '-c',
    "SELECT (to_regclass('public.route_leg_cache') IS NOT NULL)::int || ':' || (to_regclass('public.route_leg_cache_fault_e2e') IS NOT NULL)::int;",
  ])
  expect(stdout.trim()).toBe('1:0')
  await runCompose([
    'exec', '-T', 'postgres', 'psql', '-v', 'ON_ERROR_STOP=1',
    '-U', 'routeplan', '-d', 'routeplan', '-c',
    'ALTER TABLE route_leg_cache RENAME TO route_leg_cache_fault_e2e;',
  ])
}

async function restorePostgisRouteCache(): Promise<void> {
  await runCompose([
    'exec', '-T', 'postgres', 'psql', '-v', 'ON_ERROR_STOP=1',
    '-U', 'routeplan', '-d', 'routeplan', '-c',
    'ALTER TABLE IF EXISTS route_leg_cache_fault_e2e RENAME TO route_leg_cache;',
  ])
}

async function metrics(request: APIRequestContext): Promise<string> {
  const snapshots: string[] = []
  for (let index = 0; index < Math.max(1, backendReplicas * 2); index += 1) {
    const response = await request.get(`${backendURL}/actuator/prometheus`)
    expect(response.ok()).toBe(true)
    snapshots.push(await response.text())
  }
  return snapshots.join('\n')
}

test.describe.serial('V25 교통량 전역 최적화와 계층 캐시', () => {
  test.beforeEach(() => {
    test.skip(process.env.E2E_V25 !== 'true' || !stubURL || !backendURL,
      'Requires the isolated V25 Docker E2E stack')
  })

  test('성공 화면·Redis 재사용·PostGIS 재가열과 사용자 문구', async ({ page, request }) => {
    test.setTimeout(120_000)
    await resetStub(request)
    const seed = `success-${Date.now()}`
    await signUp(page, testAccount(seed, 'owner'))
    const tripId = await createTrafficTrip(page, seed, 2, 7)
    await openItineraryPlanner(page, tripId)

    await page.getByRole('button', { name: /내 일정 계산하기/ }).click()
    await expect(page.getByText('VERSION 1', { exact: true })).toBeVisible()
    await expect(page.locator('.data-warning')).toContainText('예측 교통량을 반영한')
    await expect(page.locator('.data-warning')).toContainText('시간 Matrix로 날짜와 방문 순서를 전역 재탐색')
    await expect(page.locator('.route-detail-card')).toContainText('실제 도로 경로')
    const cold = await stubState(request)
    expect(cold.requestCount).toBeGreaterThan(1)
    expect(cold.requests).toHaveLength(cold.requestCount)
    expect(cold.requests.every((value) => value.travelMode === 'DRIVE'
      && value.routingPreference === 'TRAFFIC_AWARE'
      && value.departureTime != null)).toBe(true)

    await page.getByRole('button', { name: '전체 새 버전 계산', exact: true }).click()
    await expect(page.getByText('VERSION 2', { exact: true })).toBeVisible()
    await expect(page.locator('.data-warning')).toContainText('외부 호출 0회')
    expect((await stubState(request)).requestCount).toBe(cold.requestCount)

    expect(await clearIsolatedRedisRouteCache()).toBeGreaterThan(0)
    await page.getByRole('button', { name: '전체 새 버전 계산', exact: true }).click()
    await expect(page.getByText('VERSION 3', { exact: true })).toBeVisible()
    await expect(page.locator('.data-warning')).toContainText('외부 호출 0회')
    expect((await stubState(request)).requestCount).toBe(cold.requestCount)

    const values = await metrics(request)
    expect(values).toMatch(/routeplan_route_cache_tier_reads_total\{[^}]*outcome="hit"[^}]*tier="database"[^}]*\} [1-9]/)
    expect(values).toMatch(/routeplan_route_cache_tier_writes_total\{[^}]*outcome="success"[^}]*tier="redis_warm"[^}]*\} [1-9]/)
  })

  test('후보 안전 상한을 넘으면 기존 일정으로 전환하고 이유를 표시', async ({ page, request }) => {
    test.setTimeout(120_000)
    await resetStub(request)
    const seed = `fallback-${Date.now()}`
    await signUp(page, testAccount(seed, 'owner'))
    const tripId = await createTrafficTrip(page, seed, 4, 8)
    await openItineraryPlanner(page, tripId)

    await page.getByRole('button', { name: /내 일정 계산하기/ }).click()
    await expect(page.getByText('VERSION 1', { exact: true })).toBeVisible()
    await expect(page.locator('.data-warning')).toContainText('안전 상한(candidate_limit)')
    await expect(page.locator('.data-warning')).toContainText('기존 일정 검증으로 전환')
    await expect(page.locator('.route-detail-card')).toContainText('실제 도로 경로')
    const state = await stubState(request)
    expect(state.requestCount).toBe(1)
    expect(state.requests[0].routingPreference).toBe('TRAFFIC_AWARE')
  })

  test('Redis 중지와 PostGIS Route Cache 격리에도 실제 fallback 후 복구한다', async ({ page, request }) => {
    test.setTimeout(120_000)
    await resetStub(request)
    const seed = `fault-${Date.now()}`
    await signUp(page, testAccount(seed, 'owner'))
    const tripId = await createTrafficTrip(page, seed, 2, 9)
    const warm = await mutate<ItineraryResult>(page.request, 'POST',
      `/trips/${tripId}/optimize?algorithm=EXACT_SEARCH`, null)
    expect(warm.routeProviderCallCount).toBeGreaterThan(0)
    const warmRequests = (await stubState(request)).requestCount
    let redisStopped = false
    let postgisInterrupted = false
    try {
      redisStopped = true
      await runCompose(['stop', 'redis'])
      const redisDown = await mutate<ItineraryResult>(page.request, 'POST',
        `/trips/${tripId}/optimize?algorithm=EXACT_SEARCH`, null)
      expect(redisDown.routeProviderCallCount).toBe(0)
      expect(redisDown.routeCacheHitCount).toBeGreaterThan(0)
      expect(redisDown.routeCacheFailureCount).toBeGreaterThan(0)
      expect((await stubState(request)).requestCount).toBe(warmRequests)

      await runCompose(['start', 'redis'])
      await waitForRedis()
      redisStopped = false
      await clearIsolatedRedisRouteCache()
      await interruptPostgisRouteCache()
      postgisInterrupted = true
      const postgisDown = await mutate<ItineraryResult>(page.request, 'POST',
        `/trips/${tripId}/optimize?algorithm=EXACT_SEARCH`, null)
      expect(postgisDown.routeProviderCallCount).toBeGreaterThan(0)
      expect(postgisDown.routeCacheFailureCount).toBeGreaterThan(0)
      expect((await stubState(request)).requestCount).toBeGreaterThan(warmRequests)
    } finally {
      if (postgisInterrupted) await restorePostgisRouteCache()
      if (redisStopped) {
        await runCompose(['start', 'redis'])
        await waitForRedis()
      }
    }
  })

  test('서로 다른 두 사용자의 동일 최적화가 Google 요청을 중복 생성하지 않는다', async ({ browser, request, baseURL }) => {
    test.setTimeout(120_000)
    await resetStub(request, 220)
    const contexts: BrowserContext[] = []
    try {
      const firstContext = await browser.newContext({ baseURL })
      const secondContext = await browser.newContext({ baseURL })
      contexts.push(firstContext, secondContext)
      const firstPage = await firstContext.newPage()
      const secondPage = await secondContext.newPage()
      const seed = `parallel-${Date.now()}`
      await signUp(firstPage, testAccount(`${seed}-a`, 'owner'))
      await signUp(secondPage, testAccount(`${seed}-b`, 'guest'))
      const firstTrip = await createTrafficTrip(firstPage, `${seed}-a`, 2, 10)
      const secondTrip = await createTrafficTrip(secondPage, `${seed}-b`, 2, 10)

      const started = Date.now()
      const [firstResponse, secondResponse] = await Promise.all([
        mutateResponse<ItineraryResult>(firstPage.request, 'POST', `/trips/${firstTrip}/optimize?algorithm=EXACT_SEARCH`, null),
        mutateResponse<ItineraryResult>(secondPage.request, 'POST', `/trips/${secondTrip}/optimize?algorithm=EXACT_SEARCH`, null),
      ])
      const first = firstResponse.body
      const second = secondResponse.body
      const elapsedMs = Date.now() - started
      expect(first.routeDataType).toBe('GOOGLE_ROUTES')
      expect(second.routeDataType).toBe('GOOGLE_ROUTES')
      expect(first.dataWarnings).toContain('전역 재탐색')
      expect(second.dataWarnings).toContain('전역 재탐색')

      const state = await stubState(request)
      const departures = state.requests.map((value) => value.departureTime)
      expect(state.requestCount).toBe(4)
      expect(new Set(departures).size).toBe(state.requestCount)
      expect(first.routeProviderCallCount + second.routeProviderCallCount).toBe(state.requestCount)
      expect(first.routeCacheMissCount + second.routeCacheMissCount).toBeGreaterThan(0)
      expect(new Set([firstResponse.instance, secondResponse.instance]).size).toBe(backendReplicas)
      expect(firstResponse.instance).not.toBeNull()
      expect(secondResponse.instance).not.toBeNull()
      console.log(`V25_CONCURRENCY_RESULT,users=2,instances=${firstResponse.instance}|${secondResponse.instance},google_requests=${state.requestCount},elements=${state.elementCount},elapsed_ms=${elapsedMs}`)
    } finally {
      await Promise.all(contexts.map((context) => context.close()))
    }
  })
})
