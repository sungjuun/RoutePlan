import { useEffect, useState } from 'react'
import { advanced, type WeatherRefreshSettings } from '../api/advanced'

export function WeatherAutoRefresh({ tripId }: { tripId: number }) {
  const [settings, setSettings] = useState<WeatherRefreshSettings | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    let active = true
    setSettings(null); setError('')
    advanced.weatherRefreshSettings(tripId).then(value => { if (active) setSettings(value) })
      .catch(() => { if (active) setError('자동 갱신 설정을 불러오지 못했습니다.') })
    return () => { active = false }
  }, [tripId, retry])
  const toggle = async (enabled: boolean) => {
    if (!settings || busy) return
    const previous = settings
    setSettings({ ...settings, enabled })
    setBusy(true); setError('')
    try { setSettings(await advanced.saveWeatherRefreshSettings(tripId, enabled)) }
    catch { setSettings(previous); setError('자동 갱신 설정 저장에 실패했습니다. 다시 시도하세요.') }
    finally { setBusy(false) }
  }
  return <div className="weather-auto-refresh">
    <label><input type="checkbox" checked={settings?.enabled ?? false} disabled={!settings || busy} onChange={e => void toggle(e.target.checked)} /> 날씨를 3시간마다 자동 갱신</label>
    <p>서버 실행 중, 예보 가능 기간의 여행만 갱신합니다. 직접 입력한 날씨·시간대와 저장된 일정은 바꾸지 않습니다.</p>
    {settings?.enabled && <p role="status">자동 갱신 켜짐 · {settings.lastSuccessAt ? `마지막 성공: ${new Date(settings.lastSuccessAt).toLocaleString('ko-KR')}` : '첫 조회 대기 중 (예보 범위 안이면 약 1분 이내)'}</p>}
    {settings?.lastError && <p className="inline-error">{settings.lastError}</p>}
    {error && <p role="alert">{error}</p>}
    <button type="button" className="button button-ghost button-small" disabled={busy} onClick={() => setRetry(value => value + 1)}>갱신 상태 확인</button>
  </div>
}
