import { useEffect, useState } from 'react'
import { advanced, type Usage } from '../api/advanced'
import type { Trip } from '../types'

export function LiveDataPanel({ trip, onError, onRefreshed, onBlockedChange }: {
  trip: Trip; onError: (error: unknown) => void; onRefreshed: () => void; onBlockedChange: (value: boolean) => void
}) {
  const [zone, setZone] = useState('')
  const [storedZone, setStoredZone] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [usage, setUsage] = useState<Usage[]>([])
  const [reload, setReload] = useState(0)
  useEffect(() => {
    let active = true
    advanced.zone(trip.id).then(v => { if (active) { setZone(v.timeZoneId); setStoredZone(v.timeZoneId) } }).catch(onError)
    return () => { active = false }
  }, [trip.id, onError, reload])
  useEffect(() => { onBlockedChange(busy || !storedZone || zone !== storedZone) }, [busy, zone, storedZone, onBlockedChange])
  const run = async (action: 'weather' | 'zone' | 'usage') => {
    setBusy(true); setMessage('')
    try {
      if (action === 'weather') {
        const value = await advanced.weather(trip.id)
        setZone(value.timeZoneId); setStoredZone(value.timeZoneId)
        setMessage(`${value.updatedDates}일 예보 갱신 · 직접 입력 ${value.preservedManualDates}일 보존. ${value.message}`)
        onRefreshed()
      } else if (action === 'zone') {
        const value = await advanced.saveZone(trip.id, zone)
        setStoredZone(value.timeZoneId); setMessage('시간대를 저장했습니다. 새 일정 계산에 적용됩니다.')
      } else setUsage(await advanced.usage())
    } catch (error) { onError(error) } finally { setBusy(false) }
  }
  return <section className="advanced-panel live-data-panel" aria-label="실제 여행 데이터">
    <h3>실제 여행 데이터</h3>
    <a href="/data-sources.html" target="_blank" rel="noreferrer">데이터 출처·외부 연동 안내</a>
    <p>숙소 좌표로 최대 16일 예보와 현지 시간대를 조회합니다. 기존 수동 예보는 덮어쓰지 않습니다.</p>
    <div className="advanced-actions"><button type="button" className="button button-ghost button-small" disabled={busy || (!!storedZone && zone !== storedZone)} onClick={() => void run('weather')}>{busy ? '처리 중…' : '날씨 자동 조회'}</button><a href="https://open-meteo.com/" target="_blank" rel="noreferrer">Open-Meteo · CC BY 4.0</a></div>
    <form className="advanced-inline" onSubmit={e => { e.preventDefault(); void run('zone') }}><label className="field"><span>여행지 시간대 (IANA)</span><input aria-label="여행지 시간대" value={zone} maxLength={100} placeholder="Asia/Seoul · Asia/Tokyo · Europe/Paris" onChange={e => setZone(e.target.value)} /></label><button className="button button-ghost button-small" disabled={busy || !zone}>시간대 저장</button></form>
    {!storedZone && <button type="button" onClick={() => setReload(r => r + 1)}>시간대 다시 불러오기</button>}
    {zone !== storedZone && storedZone && <p role="status">시간대 변경을 저장해야 일정을 계산할 수 있습니다.</p>}
    {message && <p role="status">{message}</p>}
    <details><summary>API 사용량과 주의사항</summary><p>Google 장소는 계산할 때 정규 영업시간을 조회합니다. 현지 공휴일·임시 휴무는 확인이 필요합니다. 무료 Open-Meteo API는 비상업 개발용입니다.</p><button type="button" disabled={busy} onClick={() => void run('usage')}>이번 달 호출량 조회</button>{usage.map(row => <p key={row.operation}>{row.operation}: {row.attemptedUnits.toLocaleString()} / {row.limit.toLocaleString()} {row.operation === 'GOOGLE_ROUTES' ? '행렬 요소' : '요청'}</p>)}<small>재시도 포함 앱 집계이며 Google 청구서가 아닙니다. 다른 앱 사용량과 브라우저 지도 호출은 포함하지 않습니다.</small></details>
  </section>
}
