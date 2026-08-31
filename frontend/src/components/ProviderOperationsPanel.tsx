import { useState } from 'react'
import { AlertTriangle, ShieldCheck } from 'lucide-react'
import { advanced, type OperationsSnapshot, type Usage } from '../api/advanced'

const usageNames: Record<string, string> = {
  GOOGLE_PLACES: 'Google 장소 검색',
  GOOGLE_PLACE_DETAILS: 'Google 장소 상세',
  GOOGLE_ROUTES: 'Google 경로 행렬',
  GOOGLE_GEOMETRY: 'Google 도로 경로선',
  OPENAI_RESPONSES: 'OpenAI 여행 조건 해석',
}
const statusNames: Record<Usage['status'], string> = { NORMAL: '정상', WARNING: '주의', BLOCKED: '차단' }
const circuitNames: Record<string, string> = { CLOSED: '정상', HALF_OPEN: '복구 확인 중', OPEN: '호출 차단' }

function UsageCard({ row }: { row: Usage }) {
  const unit = row.operation === 'GOOGLE_ROUTES' ? '요소' : '회'
  return <article className={`usage-card usage-${row.status.toLowerCase()}`}>
    <header><strong>{usageNames[row.operation] ?? row.operation}</strong><span>{statusNames[row.status]}</span></header>
    <div className="usage-meter" role="progressbar" aria-label={`${usageNames[row.operation] ?? row.operation} 월 사용량`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.min(100, row.usagePercent)}><i style={{ width: `${Math.min(100, row.usagePercent)}%` }} /></div>
    <p><b>{row.attemptedUnits.toLocaleString()}</b> / {row.limit.toLocaleString()} {unit} · {row.remainingUnits.toLocaleString()} {unit} 남음</p>
    <dl><div><dt>성공/실패</dt><dd>{row.successCount.toLocaleString()} / {row.failureCount.toLocaleString()}</dd></div><div><dt>성공률</dt><dd>{row.successRatePercent == null ? '기록 없음' : `${row.successRatePercent.toFixed(1)}%`}</dd></div><div><dt>평균/최대 지연</dt><dd>{row.averageLatencyMs == null ? '기록 없음' : `${row.averageLatencyMs.toLocaleString()} / ${row.maxLatencyMs.toLocaleString()}ms`}</dd></div></dl>
    {row.unclassifiedUnits > 0 && <small>V21 적용 전 호출 {row.unclassifiedUnits.toLocaleString()} {unit}은 결과 분류가 없습니다.</small>}
    {row.tokenLimit != null && <p>토큰: {(row.inputTokens + row.outputTokens).toLocaleString()} / {row.tokenLimit.toLocaleString()} · 입력 {row.inputTokens.toLocaleString()} · 출력 {row.outputTokens.toLocaleString()}</p>}
    <small>{row.costConfigured ? `설정 단가 기준 추정 $${row.estimatedCostUsd.toFixed(6)}` : '비용 단가 미설정 · 공급자 청구 화면에서 확인'}</small>
  </article>
}

export function ProviderOperationsPanel({ onError, compact = false }: { onError: (error: unknown) => void; compact?: boolean }) {
  const [snapshot, setSnapshot] = useState<OperationsSnapshot | null>(null)
  const [busy, setBusy] = useState(false)
  const load = async () => {
    setBusy(true)
    try { setSnapshot(await advanced.operations()) } catch (error) { onError(error) } finally { setBusy(false) }
  }
  const content = <>
    {!compact && <div><span className="eyebrow">OPERATIONS</span><h2>외부 API 안정성·품질·비용</h2><p>공급자 장애 격리 상태와 이번 달 앱 안전 한도·비용을 확인합니다.</p></div>}
    <button type="button" disabled={busy} onClick={() => void load()}>{busy ? '조회 중…' : '이번 달 운영 지표 조회'}</button>
    {snapshot && <>
      {snapshot.alerts.length > 0
        ? <section className="operations-alerts" aria-label="활성 운영 경고"><h3><AlertTriangle size={17} /> 활성 경고 {snapshot.alerts.length}건</h3>{snapshot.alerts.map(alert => <p key={alert.code} className={`operations-alert operations-alert-${alert.severity.toLowerCase()}`}><b>{alert.provider.toUpperCase()}</b> {alert.message}</p>)}</section>
        : <p className="operations-healthy"><ShieldCheck size={17} /> 활성 운영 경고가 없습니다.</p>}
      <div className="provider-health-grid">{snapshot.providers.map(provider => <article key={provider.provider} className={`provider-health provider-health-${provider.state.toLowerCase().replace('_', '-')}`}><strong>{provider.provider.toUpperCase()}</strong><span>{circuitNames[provider.state] ?? provider.state}</span><small>연속 장애 {provider.consecutiveFailures}회 · 동시 호출 {provider.activeCalls}/{provider.maxConcurrentCalls}</small></article>)}</div>
      {snapshot.costs.some(cost => cost.monthlyBudgetUsd > 0) && <div className="provider-costs">{snapshot.costs.filter(cost => cost.monthlyBudgetUsd > 0).map(cost => <p key={cost.provider}><b>{cost.provider.toUpperCase()}</b> 예상 ${cost.estimatedCostUsd.toFixed(6)} / 월 예산 ${cost.monthlyBudgetUsd.toFixed(2)}{cost.budgetUsagePercent == null ? '' : ` · ${cost.budgetUsagePercent.toFixed(1)}%`}</p>)}</div>}
      {snapshot.usage.length > 0 && <div className="usage-grid">{snapshot.usage.map(row => <UsageCard key={row.operation} row={row} />)}</div>}
    </>}
    <small>UTC 월·재시도 포함 앱 집계입니다. 공급자 청구서·쿼터가 최종 기준이며 다른 앱과 브라우저 지도 호출은 포함하지 않습니다.</small>
  </>
  return compact ? <div className="provider-operations">{content}</div> : <section className="advanced-panel provider-operations" aria-label="외부 API 품질과 비용">{content}</section>
}
