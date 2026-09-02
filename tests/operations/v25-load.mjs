import { readFile } from 'node:fs/promises'

const REQUIRED_CONFIRMATION = 'RUN_ROUTEPLAN_MUTATING_LOAD'
const configuration = {
  target: process.env.ROUTEPLAN_TARGET_URL ?? '',
  scenariosPath: process.env.ROUTEPLAN_OPERATION_SCENARIOS ?? '',
  concurrency: integer('ROUTEPLAN_LOAD_CONCURRENCY', 2, 1, 50),
  iterationsPerUser: integer('ROUTEPLAN_LOAD_ITERATIONS_PER_USER', 3, 1, 100),
  maxP95Ms: integer('ROUTEPLAN_LOAD_MAX_P95_MS', 5_000, 1, 300_000),
  maxFailurePercent: number('ROUTEPLAN_LOAD_MAX_FAILURE_PERCENT', 0, 0, 100),
  expectedMinInstances: integer('ROUTEPLAN_EXPECTED_MIN_INSTANCES', 2, 1, 50),
  maxGoogleUnitsDelta: optionalInteger('ROUTEPLAN_MAX_GOOGLE_UNITS_DELTA'),
  maxGoogleCallsDelta: optionalInteger('ROUTEPLAN_MAX_GOOGLE_CALLS_DELTA'),
  requireGoogleCostConfigured: boolean('ROUTEPLAN_REQUIRE_GOOGLE_COST_CONFIGURED', true),
  label: process.env.ROUTEPLAN_LOAD_LABEL ?? 'manual',
}

validateSafety(configuration)
const allScenarios = JSON.parse(await readFile(configuration.scenariosPath, 'utf8'))
const scenarios = validateScenarios(allScenarios).slice(0, configuration.concurrency)
const sessions = await Promise.all(scenarios.map(login))
const before = await operations(sessions[0])
const startedAt = Date.now()
let releaseStart
const start = new Promise((resolve) => { releaseStart = resolve })
const tasks = sessions.map((session) => optimizeRepeatedly(session, start))
releaseStart()
const results = (await Promise.all(tasks)).flat()
const elapsedMs = Date.now() - startedAt
const after = await operations(sessions[0])
const report = summarize(results, before, after, elapsedMs)
console.log(JSON.stringify(report, null, 2))
assertReport(report)

function integer(name, fallback, minimum, maximum) {
  const raw = process.env[name]
  const value = raw == null || raw === '' ? fallback : Number(raw)
  if (!Number.isInteger(value) || value < minimum || value > maximum) {
    throw new Error(`${name} must be an integer between ${minimum} and ${maximum}`)
  }
  return value
}

function optionalInteger(name) {
  const raw = process.env[name]
  if (raw == null || raw === '') return null
  const value = Number(raw)
  if (!Number.isInteger(value) || value < 0) throw new Error(`${name} must be a non-negative integer`)
  return value
}

function number(name, fallback, minimum, maximum) {
  const raw = process.env[name]
  const value = raw == null || raw === '' ? fallback : Number(raw)
  if (!Number.isFinite(value) || value < minimum || value > maximum) {
    throw new Error(`${name} must be between ${minimum} and ${maximum}`)
  }
  return value
}

function boolean(name, fallback) {
  const raw = process.env[name]
  if (raw == null || raw === '') return fallback
  if (raw !== 'true' && raw !== 'false') throw new Error(`${name} must be true or false`)
  return raw === 'true'
}

function validateSafety(value) {
  if (process.env.ROUTEPLAN_LOAD_CONFIRM !== REQUIRED_CONFIRMATION) {
    throw new Error(`ROUTEPLAN_LOAD_CONFIRM must equal ${REQUIRED_CONFIRMATION}`)
  }
  if (!value.target || !value.scenariosPath) throw new Error('Target URL and scenario file are required')
  const target = new URL(value.target)
  const internal = target.hostname === 'frontend' || target.hostname === 'localhost' || target.hostname === '127.0.0.1'
  if (target.protocol !== 'https:'
      && !(internal && process.env.ROUTEPLAN_LOAD_ALLOW_INSECURE_INTERNAL === 'true')) {
    throw new Error('Only HTTPS targets are allowed outside the internal Compose network')
  }
}

function validateScenarios(value) {
  if (!Array.isArray(value) || value.length < configuration.concurrency) {
    throw new Error('The scenario file must contain at least one dedicated account per concurrent user')
  }
  const seenEmails = new Set()
  const seenTrips = new Set()
  value.forEach((scenario, index) => {
    if (typeof scenario?.email !== 'string' || !scenario.email.includes('@')
        || /replace-with|\.invalid$/i.test(scenario.email)) {
      throw new Error(`Scenario ${index + 1} must use a real dedicated test email`)
    }
    if (typeof scenario.password !== 'string' || scenario.password.length < 12
        || /replace-with/i.test(scenario.password)) {
      throw new Error(`Scenario ${index + 1} must contain its dedicated account password`)
    }
    if (!Number.isSafeInteger(scenario.tripId) || scenario.tripId < 1) {
      throw new Error(`Scenario ${index + 1} must contain a positive tripId`)
    }
    if (seenEmails.has(scenario.email) || seenTrips.has(scenario.tripId)) {
      throw new Error('Concurrent scenarios must use distinct accounts and trips')
    }
    seenEmails.add(scenario.email)
    seenTrips.add(scenario.tripId)
  })
  return value
}

class Session {
  constructor(scenario) {
    this.scenario = scenario
    this.cookies = new Map()
    this.csrf = null
  }

  remember(response) {
    const values = response.headers.getSetCookie?.()
      ?? (response.headers.get('set-cookie') ? [response.headers.get('set-cookie')] : [])
    values.filter(Boolean).forEach((header) => {
      const [pair] = header.split(';', 1)
      const separator = pair.indexOf('=')
      if (separator > 0) this.cookies.set(pair.slice(0, separator), pair.slice(separator + 1))
    })
  }

  cookieHeader() {
    return [...this.cookies].map(([name, value]) => `${name}=${value}`).join('; ')
  }
}

async function login(scenario) {
  const session = new Session(scenario)
  await refreshCsrf(session)
  await request(session, '/api/v1/auth/login', {
    method: 'POST',
    json: { email: scenario.email, password: scenario.password },
    csrf: true,
  })
  await refreshCsrf(session)
  return session
}

async function refreshCsrf(session) {
  const response = await request(session, '/api/v1/auth/csrf')
  session.csrf = { headerName: response.body.headerName, token: response.body.token }
}

async function request(session, path, options = {}) {
  const headers = { Accept: 'application/json' }
  const cookie = session.cookieHeader()
  if (cookie) headers.Cookie = cookie
  if (options.csrf) {
    if (!session.csrf) throw new Error('CSRF token is unavailable')
    headers[session.csrf.headerName] = session.csrf.token
  }
  let body
  if (options.json !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.json)
  }
  const response = await fetch(new URL(path, configuration.target), {
    method: options.method ?? 'GET', headers, body, redirect: 'manual',
  })
  session.remember(response)
  const text = await response.text()
  let parsed = null
  if (text) {
    try { parsed = JSON.parse(text) } catch { parsed = { message: text.slice(0, 200) } }
  }
  if (!response.ok) {
    const error = new Error(`${options.method ?? 'GET'} ${path} returned ${response.status}`)
    error.status = response.status
    error.code = parsed?.code ?? 'UNKNOWN'
    throw error
  }
  return {
    status: response.status,
    body: parsed,
    instance: response.headers.get('x-routeplan-instance') ?? 'unknown',
  }
}

async function optimizeRepeatedly(session, start) {
  await start
  const results = []
  for (let iteration = 0; iteration < configuration.iterationsPerUser; iteration += 1) {
    const started = performance.now()
    try {
      const response = await request(session,
        `/api/v1/trips/${session.scenario.tripId}/optimize?algorithm=NEAREST_NEIGHBOR_2_OPT`,
        { method: 'POST', csrf: true })
      results.push({
        ok: true,
        status: response.status,
        latencyMs: performance.now() - started,
        instance: response.instance,
        providerCalls: response.body?.routeProviderCallCount ?? 0,
        cacheHits: response.body?.routeCacheHitCount ?? 0,
        cacheMisses: response.body?.routeCacheMissCount ?? 0,
        cacheFailures: response.body?.routeCacheFailureCount ?? 0,
      })
    } catch (error) {
      results.push({
        ok: false,
        status: error.status ?? 0,
        code: error.code ?? error.name,
        latencyMs: performance.now() - started,
        instance: 'unknown',
        providerCalls: 0,
        cacheHits: 0,
        cacheMisses: 0,
        cacheFailures: 0,
      })
    }
  }
  return results
}

async function operations(session) {
  return (await request(session, '/api/v1/integrations/operations')).body
}

function googleRoutes(snapshot) {
  return snapshot?.usage?.find((value) => value.operation === 'GOOGLE_ROUTES') ?? {}
}

function googleCost(snapshot) {
  return snapshot?.costs?.find((value) => value.provider === 'google') ?? {}
}

function delta(after, before, name) {
  return Number(after?.[name] ?? 0) - Number(before?.[name] ?? 0)
}

function percentile(values, quantile) {
  if (values.length === 0) return null
  const sorted = [...values].sort((left, right) => left - right)
  return sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * quantile) - 1)]
}

function summarize(results, before, after, elapsedMs) {
  const beforeRoutes = googleRoutes(before)
  const afterRoutes = googleRoutes(after)
  const latencies = results.map((value) => value.latencyMs)
  const failures = results.filter((value) => !value.ok)
  const instances = [...new Set(results.filter((value) => value.ok).map((value) => value.instance))].sort()
  return {
    label: configuration.label,
    targetHost: new URL(configuration.target).host,
    users: scenarios.length,
    iterationsPerUser: configuration.iterationsPerUser,
    requests: results.length,
    successes: results.length - failures.length,
    failures: failures.length,
    failurePercent: results.length === 0 ? 100 : failures.length * 100 / results.length,
    elapsedMs,
    throughputPerSecond: results.length / Math.max(0.001, elapsedMs / 1_000),
    latencyMs: {
      p50: percentile(latencies, 0.50),
      p95: percentile(latencies, 0.95),
      p99: percentile(latencies, 0.99),
      max: latencies.length ? Math.max(...latencies) : null,
    },
    backendInstances: instances,
    itineraryMetrics: {
      providerCalls: results.reduce((sum, value) => sum + value.providerCalls, 0),
      cacheHits: results.reduce((sum, value) => sum + value.cacheHits, 0),
      cacheMisses: results.reduce((sum, value) => sum + value.cacheMisses, 0),
      cacheFailures: results.reduce((sum, value) => sum + value.cacheFailures, 0),
    },
    googleRoutesDelta: {
      units: delta(afterRoutes, beforeRoutes, 'attemptedUnits'),
      calls: delta(afterRoutes, beforeRoutes, 'attemptCount'),
      successes: delta(afterRoutes, beforeRoutes, 'successCount'),
      failures: delta(afterRoutes, beforeRoutes, 'failureCount'),
      estimatedCostUsd: Number(googleCost(after).estimatedCostUsd ?? 0)
        - Number(googleCost(before).estimatedCostUsd ?? 0),
      costConfigured: googleCost(after).costConfigured === true,
    },
    failureCodes: Object.groupBy(failures, (value) => `${value.status}:${value.code}`),
  }
}

function assertReport(report) {
  const violations = []
  if (report.failurePercent > configuration.maxFailurePercent) {
    violations.push(`failure percent ${report.failurePercent.toFixed(2)} exceeds ${configuration.maxFailurePercent}`)
  }
  if ((report.latencyMs.p95 ?? Infinity) > configuration.maxP95Ms) {
    violations.push(`p95 ${report.latencyMs.p95?.toFixed(1)}ms exceeds ${configuration.maxP95Ms}ms`)
  }
  if (report.backendInstances.length < configuration.expectedMinInstances) {
    violations.push(`only ${report.backendInstances.length} backend instance(s) handled requests`)
  }
  if (configuration.maxGoogleUnitsDelta != null
      && report.googleRoutesDelta.units > configuration.maxGoogleUnitsDelta) {
    violations.push(`Google units delta ${report.googleRoutesDelta.units} exceeds ${configuration.maxGoogleUnitsDelta}`)
  }
  if (configuration.maxGoogleCallsDelta != null
      && report.googleRoutesDelta.calls > configuration.maxGoogleCallsDelta) {
    violations.push(`Google calls delta ${report.googleRoutesDelta.calls} exceeds ${configuration.maxGoogleCallsDelta}`)
  }
  if (configuration.requireGoogleCostConfigured && !report.googleRoutesDelta.costConfigured) {
    violations.push('Google contract pricing is not configured, so cost validation is unavailable')
  }
  if (violations.length) throw new Error(`Operational load validation failed: ${violations.join('; ')}`)
}
