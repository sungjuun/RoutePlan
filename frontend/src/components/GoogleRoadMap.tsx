import { useEffect, useRef, useState } from 'react'
import { advanced } from '../api/advanced'
import type { Itinerary } from '../types'

let mapsLoading: Promise<void> | undefined
function loadMaps(key: string): Promise<void> {
  if (typeof google !== 'undefined' && google.maps?.Map) return Promise.resolve()
  if (mapsLoading) return mapsLoading
  mapsLoading = new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    const host = window as Window & { routePlanMapsReady?: () => void; gm_authFailure?: () => void }
    const fail = (message: string) => { window.clearTimeout(timer); script.remove(); reject(new Error(message)) }
    const timer = window.setTimeout(() => fail('Google 지도를 불러오지 못했습니다.'), 20000)
    host.routePlanMapsReady = () => { window.clearTimeout(timer); resolve() }
    host.gm_authFailure = () => {
      window.dispatchEvent(new Event('routeplan:google-auth-failure'))
      fail('브라우저 지도 키의 API 활성화와 웹사이트 제한을 확인해 주세요.')
    }
    script.src = `https://maps.googleapis.com/maps/api/js?${new URLSearchParams({ key, language: 'ko', v: 'weekly', loading: 'async', libraries: 'geometry', callback: 'routePlanMapsReady' })}`
    script.async = true
    script.onerror = () => fail('Google 지도에 연결하지 못했습니다.')
    document.head.append(script)
  }).catch(error => { mapsLoading = undefined; throw error })
  return mapsLoading
}

export function GoogleRoadMap({ itinerary, date }: { itinerary: Itinerary; date: string }) {
  const container = useRef<HTMLDivElement>(null)
  const [message, setMessage] = useState('Google 지도 설정을 확인하는 중…')
  const [ready, setReady] = useState(false)
  const [loading, setLoading] = useState(true)
  const [reload, setReload] = useState(0)
  useEffect(() => {
    let active = true
    let map: google.maps.Map | undefined
    const lines: google.maps.Polyline[] = []
    const authFailure = () => {
      if (active) { setReady(false); setMessage('브라우저 지도 키의 API 활성화와 웹사이트 제한을 확인해 주세요.') }
    }
    window.addEventListener('routeplan:google-auth-failure', authFailure)
    const load = async () => {
      setReady(false)
      setLoading(true)
      setMessage('Google 지도와 실제 경로를 불러오는 중…')
      const config = await advanced.maps()
      if (!config.browserKey) throw new Error('실제 도로 지도에는 GOOGLE_MAPS_BROWSER_KEY 설정과 Maps JavaScript API 활성화가 필요합니다. 서버 API 키를 재사용하지 마세요.')
      await loadMaps(config.browserKey)
      if (!active) return
      const geometry = await advanced.geometry(itinerary.itineraryId, date)
      if (!active || !container.current) return
      map = new google.maps.Map(container.current, { center: { lat: 37.57, lng: 126.98 }, zoom: 13, mapTypeControl: false, streetViewControl: false })
      const bounds = new google.maps.LatLngBounds()
      geometry.encodedPolylines.forEach(encoded => {
        const path = google.maps.geometry.encoding.decodePath(encoded)
        path.forEach(point => bounds.extend(point))
        lines.push(new google.maps.Polyline({ map, path, strokeColor: '#db694a', strokeOpacity: 0.95, strokeWeight: 5 }))
      })
      if (bounds.isEmpty()) throw new Error('표시할 실제 경로가 없습니다.')
      map.fitBounds(bounds, 30)
      setReady(true); setMessage('Google Maps · 선택한 날짜의 미완료 구간 · 조회 시점 실제 경로')
    }
    void load().catch(e => { if (active) setMessage(e instanceof Error ? e.message : '실제 경로 조회에 실패했습니다.') }).finally(() => { if (active) setLoading(false) })
    return () => { active = false; window.removeEventListener('routeplan:google-auth-failure', authFailure); lines.forEach(line => line.setMap(null)); if (map) google.maps.event.clearInstanceListeners(map) }
  }, [itinerary.itineraryId, date, reload])
  return <div className="road-map"><div ref={container} className="google-road-canvas" aria-label="Google 실제 도로 경로 지도" /><p role="status">{message}</p>{!ready && <button disabled={loading} onClick={() => setReload(r => r + 1)}>{loading ? '조회 중…' : '지도 다시 시도'}</button>}<small>방문 순서는 유지하며 경로만 조회합니다. 기존 일정의 도착 시각을 다시 계산하지 않습니다.</small></div>
}
