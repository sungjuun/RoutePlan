import { useState } from 'react'
import { advanced, type Usage } from '../api/advanced'

const usageNames: Record<string, string> = {
  GOOGLE_PLACES: 'Google 장소 검색',
  GOOGLE_PLACE_DETAILS: 'Google 장소 상세',
  GOOGLE_ROUTES: 'Google 경로 행렬',
  GOOGLE_GEOMETRY: 'Google 도로 경로선',
  OPENAI_RESPONSES: 'OpenAI 여행 조건 해석',
}
const statusNames: Record<Usage['status'], string> = { NORMAL: '정상', WARNING: '주의', BLOCKED: '차단' }

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
  const [usage, setUsage] = useState<Usage[]>([])
  const [busy, setBusy] = useState(false)
  const load = async () => {
    setBusy(true)
    try { setUsage(await advanced.usage()) } catch (error) { onError(error) } finally { setBusy(false) }
  }
  const content = <>
    {!compact && <div><span className="eyebrow">OPERATIONS</span><h2>외부 API 품질·비용</h2><p>이번 달 Google·OpenAI의 앱 안전 한도와 성공률을 확인합니다.</p></div>}
    <button type="button" disabled={busy} onClick={() => void load()}>{busy ? '조회 중…' : '이번 달 운영 지표 조회'}</button>
    {usage.length > 0 && <div className="usage-grid">{usage.map(row => <UsageCard key={row.operation} row={row} />)}</div>}
    <small>UTC 월·재시도 포함 앱 집계입니다. 공급자 청구서·쿼터가 최종 기준이며 다른 앱과 브라우저 지도 호출은 포함하지 않습니다.</small>
  </>
  return compact ? <div className="provider-operations">{content}</div> : <section className="advanced-panel provider-operations" aria-label="외부 API 품질과 비용">{content}</section>
}
