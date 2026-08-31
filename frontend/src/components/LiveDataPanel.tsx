import { useEffect, useState } from 'react'
import { advanced } from '../api/advanced'
import type { Trip } from '../types'
import { WeatherAutoRefresh } from './WeatherAutoRefresh'
import { ProviderOperationsPanel } from './ProviderOperationsPanel'

export function LiveDataPanel({ trip, onError, onRefreshed, onBlockedChange }: {
  trip: Trip; onError: (error: unknown) => void; onRefreshed: () => void; onBlockedChange: (value: boolean) => void
}) {
  const [zone, setZone] = useState('')
  const [storedZone, setStoredZone] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [reload, setReload] = useState(0)
  useEffect(() => {
    let active = true
    advanced.zone(trip.id).then(v => { if (active) { setZone(v.timeZoneId); setStoredZone(v.timeZoneId) } }).catch(onError)
    return () => { active = false }
  }, [trip.id, onError, reload])
  useEffect(() => { onBlockedChange(busy || !storedZone || zone !== storedZone) }, [busy, zone, storedZone, onBlockedChange])
  const run = async (action: 'weather' | 'zone') => {
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
      }
    } catch (error) { onError(error) } finally { setBusy(false) }
  }
  return <section className="advanced-panel live-data-panel" aria-label="실제 여행 데이터">
    <h3>실제 여행 데이터</h3>
    <a href="/data-sources.html" target="_blank" rel="noreferrer">데이터 출처·외부 연동 안내</a>
    <p>숙소 좌표로 최대 16일 예보와 현지 시간대를 조회합니다. 기존 수동 예보는 덮어쓰지 않습니다.</p>
    <WeatherAutoRefresh key={trip.id} tripId={trip.id} />
    <div className="advanced-actions"><button type="button" className="button button-ghost button-small" disabled={busy || (!!storedZone && zone !== storedZone)} onClick={() => void run('weather')}>{busy ? '처리 중…' : '날씨 자동 조회'}</button><a href="https://open-meteo.com/" target="_blank" rel="noreferrer">Open-Meteo · CC BY 4.0</a></div>
    <form className="advanced-inline" onSubmit={e => { e.preventDefault(); void run('zone') }}><label className="field"><span>여행지 시간대 (IANA)</span><input aria-label="여행지 시간대" value={zone} maxLength={100} placeholder="Asia/Seoul · Asia/Tokyo · Europe/Paris" onChange={e => setZone(e.target.value)} /></label><button className="button button-ghost button-small" disabled={busy || !zone}>시간대 저장</button></form>
    {!storedZone && <button type="button" onClick={() => setReload(r => r + 1)}>시간대 다시 불러오기</button>}
    {zone !== storedZone && storedZone && <p role="status">시간대 변경을 저장해야 일정을 계산할 수 있습니다.</p>}
    {message && <p role="status">{message}</p>}
    <details><summary>API 품질·비용 대시보드</summary><p>Google 장소는 분할·야간 영업시간과 제공된 현지 7일 특별 영업시간을 반영합니다. 그 밖의 공휴일·임시 휴무는 별도 확인하세요. 대중교통은 방문 종료 시각별 추가 조회가 사용량에 포함됩니다. 무료 Open-Meteo API는 비상업 개발용입니다.</p><ProviderOperationsPanel onError={onError} compact /></details>
  </section>
}
