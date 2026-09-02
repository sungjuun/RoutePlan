const prometheus = new URL(process.env.ROUTEPLAN_PROMETHEUS_URL ?? 'http://prometheus:9090')
const lookback = process.env.ROUTEPLAN_REPORT_LOOKBACK ?? '7d'
if (!/^\d+[smhdw]$/.test(lookback)) throw new Error('ROUTEPLAN_REPORT_LOOKBACK must be a Prometheus duration such as 24h or 7d')

const queries = {
  healthyBackendInstances: 'routeplan:backend_instances:healthy',
  beamWidth: 'max(routeplan_optimization_time_dependent_config_beam_width{application="routeplan"})',
  maxStates: 'max(routeplan_optimization_time_dependent_config_max_states{application="routeplan"})',
  maxDurationSeconds: 'max(routeplan_optimization_time_dependent_config_max_duration_seconds{application="routeplan"})',
  p95States: `histogram_quantile(0.95, sum by (le) (rate(routeplan_optimization_time_dependent_states_bucket{application="routeplan",outcome="applied"}[${lookback}])))`,
  p95DurationSeconds: `histogram_quantile(0.95, sum by (le) (rate(routeplan_optimization_time_dependent_duration_seconds_bucket{application="routeplan",outcome="applied"}[${lookback}])))`,
  fallbackRatio: `sum(increase(routeplan_optimization_time_dependent_total{application="routeplan",outcome="fallback"}[${lookback}])) / clamp_min(sum(increase(routeplan_optimization_time_dependent_total{application="routeplan"}[${lookback}])), 1)`,
  searchLimitFallbacks: `sum(increase(routeplan_optimization_time_dependent_total{application="routeplan",outcome="fallback",reason="search_limit"}[${lookback}])) or vector(0)`,
  googleRoutesUnitsMonth: 'routeplan:google_routes_units:month',
  googleRoutesCallsMonth: 'max(routeplan_external_usage_calls{application="routeplan",provider="google",operation="GOOGLE_ROUTES",outcome="attempt"}) or vector(0)',
  googleCostUsdMonth: 'routeplan:google_cost_usd:month',
  googleBudgetUsdMonth: 'max(routeplan_external_budget_usd{application="routeplan",provider="google"}) or vector(0)',
  googlePriceSeries: 'count(routeplan_external_usage_cost_usd{application="routeplan",provider="google",configured="true"}) or vector(0)',
}

const values = {}
for (const [name, expression] of Object.entries(queries)) values[name] = await instant(expression)
const report = {
  generatedAt: new Date().toISOString(),
  lookback,
  ...values,
  stateLimitUtilization: ratio(values.p95States, values.maxStates),
  durationLimitUtilization: ratio(values.p95DurationSeconds, values.maxDurationSeconds),
  googleBudgetUtilization: ratio(values.googleCostUsdMonth, values.googleBudgetUsdMonth),
}
report.recommendation = recommendation(report)
report.billingNote = values.googlePriceSeries > 0
  ? 'Configured prices produce an estimate; Google Cloud Billing remains authoritative.'
  : 'Google prices are not configured, so app-side USD cost is unavailable; configure contract prices and verify Google Cloud Billing.'
console.log(JSON.stringify(report, null, 2))

async function instant(query) {
  const url = new URL('/api/v1/query', prometheus)
  url.searchParams.set('query', query)
  const response = await fetch(url)
  if (!response.ok) throw new Error(`Prometheus query failed: HTTP ${response.status}`)
  const payload = await response.json()
  if (payload.status !== 'success') throw new Error(`Prometheus query failed: ${payload.error ?? 'unknown'}`)
  const raw = payload.data?.result?.[0]?.value?.[1]
  if (raw == null || raw === 'NaN') return null
  const value = Number(raw)
  return Number.isFinite(value) ? value : null
}

function ratio(value, limit) {
  return value == null || limit == null || limit <= 0 ? null : value / limit
}

function recommendation(report) {
  if (report.p95States == null || report.p95DurationSeconds == null) {
    return 'Insufficient applied-search samples: keep Beam 128 and current safety limits until at least one normal traffic window is recorded.'
  }
  if ((report.stateLimitUtilization ?? 0) >= 0.8
      || (report.durationLimitUtilization ?? 0) >= 0.8
      || (report.searchLimitFallbacks ?? 0) > 0) {
    return 'Keep Beam 128. Profile candidate counts and bucket cost first; raise state/time limits only after a canary proves stable latency and Google usage.'
  }
  if ((report.fallbackRatio ?? 0) <= 0.01
      && (report.stateLimitUtilization ?? 1) < 0.5
      && (report.durationLimitUtilization ?? 1) < 0.5) {
    return 'Beam 128 and current limits have adequate headroom; do not increase them without a measured schedule-quality gain.'
  }
  return 'Keep Beam 128 and collect another traffic window before changing limits; investigate fallback reasons separately.'
}
