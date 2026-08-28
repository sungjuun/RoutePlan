import { useEffect, useState } from 'react'
import { advanced, interests, interestNames, type Preferences } from '../api/advanced'
import type { TransportMode, TripPace } from '../types'

export function PreferencesPanel({ onError }: { onError: (e: unknown) => void }) {
  const [value, setValue] = useState<Preferences | null>(null)
  const [regions, setRegions] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [reload, setReload] = useState(0)
  useEffect(() => {
    let active = true
    advanced.preferences().then(p => { if (active) { setValue(p); setRegions(p.regions.join(', ')) } }).catch(onError)
    return () => { active = false }
  }, [onError, reload])
  const save = async () => {
    if (!value) return
    setBusy(true); setMessage('')
    try {
      const saved = await advanced.savePreferences({ ...value, regions: regions.split(',').map(s => s.trim()).filter(Boolean) })
      setValue(saved); setRegions(saved.regions.join(', ')); setMessage('취향을 저장했습니다. 메인 페이지에서 맞춤 추천을 확인하세요.')
    } catch (e) { onError(e) } finally { setBusy(false) }
  }
  return <section className="advanced-panel panel" aria-label="여행 취향">
    <h2>나의 여행 취향</h2><p>선택한 취향으로 공개 루트를 추천합니다. 여행의 실제 조건은 바꾸지 않으며, 전부 해제해 저장하면 취향을 지울 수 있습니다.</p>
    {!value ? <button onClick={() => setReload(r => r + 1)}>취향 불러오기</button> : <form onSubmit={e => { e.preventDefault(); void save() }}>
      <fieldset disabled={busy} className="advanced-grid"><legend>관심사와 여행 방식</legend>
        <div className="preference-chips field-wide">{interests.map(interest => <label key={interest}><input type="checkbox" checked={value.interests.includes(interest)} onChange={e => setValue({ ...value, interests: e.target.checked ? [...value.interests, interest] : value.interests.filter(i => i !== interest) })} />{interestNames[interest]}</label>)}</div>
        <label className="field field-wide"><span>관심 지역 · 쉼표로 구분, 최대 10곳</span><input value={regions} maxLength={1000} placeholder="서울, 오사카, 파리" onChange={e => setRegions(e.target.value)} /></label>
        <label className="field"><span>선호 여행 속도</span><select value={value.pace ?? ''} onChange={e => setValue({ ...value, pace: (e.target.value || null) as TripPace | null })}><option value="">상관없음</option><option value="RELAXED">여유롭게</option><option value="STANDARD">보통</option><option value="ACTIVE">알차게</option></select></label>
        <label className="field"><span>선호 이동수단</span><select value={value.transportMode ?? ''} onChange={e => setValue({ ...value, transportMode: (e.target.value || null) as TransportMode | null })}><option value="">상관없음</option><option value="WALKING">도보</option><option value="PUBLIC_TRANSIT">대중교통</option><option value="DRIVING">자동차</option></select></label>
        <button className="button button-primary">{busy ? '저장 중…' : '여행 취향 저장'}</button>
      </fieldset>
    </form>}
    {message && <p role="status">{message}</p>}
  </section>
}
