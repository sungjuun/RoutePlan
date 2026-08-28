import { useEffect, useState, type FormEvent } from 'react'
import { ArrowRight, CalendarDays, Copy, Heart, MapPin, Search, Sparkles } from 'lucide-react'
import { api } from '../api/client'
import { durationLabel, paceLabel, transportLabel } from '../lib/format'
import type { SharedRouteSummary, User } from '../types'
import { AsyncState } from './AsyncState'
import { advanced } from '../api/advanced'

const destinations = [
  { country: '일본', region: '오사카', code: 'JP', copy: '골목과 미식, 오래된 성' },
  { country: '대한민국', region: '서울', code: 'KR', copy: '궁궐과 동네를 잇는 하루' },
  { country: '프랑스', region: '파리', code: 'FR', copy: '미술관과 센강의 저녁' },
  { country: '이탈리아', region: '로마', code: 'IT', copy: '고대 도시를 걷는 시간' },
  { country: '태국', region: '방콕', code: 'TH', copy: '사원과 야시장의 리듬' },
]

interface Props {
  user: User | null
  onExplore: (region?: string) => void
  onCreateTrip: () => void
  onError: (error: unknown) => void
}

export function LandingPage({ user, onExplore, onCreateTrip, onError }: Props) {
  const [query, setQuery] = useState('')
  const [routes, setRoutes] = useState<SharedRouteSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const [reasons, setReasons] = useState<Record<number, string[]>>({})

  useEffect(() => {
    let cancelled = false
    const load = user
      ? advanced.recommendations().then(items => ({ content: items.map(i => i.route), reasons: Object.fromEntries(items.map(i => [i.route.routeId, i.reasons])) }))
      : api.discoverRoutes({ sort: 'POPULAR', size: 6 }).then(page => ({ content: page.content, reasons: {} }))
    load.then((page) => {
        if (!cancelled) { setRoutes(page.content); setReasons(page.reasons) }
      })
      .catch((error) => {
        if (!cancelled) {
          setLoadFailed(true)
          onError(error)
        }
      })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [onError, reloadKey, user])

  const search = (event: FormEvent) => {
    event.preventDefault()
    onExplore(query.trim() || undefined)
  }

  return (
    <main className="landing-page">
      <section className="landing-hero">
        <div className="landing-hero-copy">
          <span className="eyebrow eyebrow-light">ROUTES MADE PERSONAL</span>
          <h1>좋은 여행 동선을 발견하고<br /><em>내 여행으로 다시 계산하세요</em></h1>
          <p>검증된 공개 루트를 찾아보고, 내 숙소와 날짜·이동수단에 맞춰 실행 가능한 일정으로 바꿔보세요.</p>
          <form className="destination-search" onSubmit={search}>
            <Search size={20} />
            <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="어디로 떠나시나요?  예: 오사카" maxLength={100} />
            <button className="button button-primary">루트 찾기 <ArrowRight size={17} /></button>
          </form>
          <div className="landing-hero-actions">
            <button className="button button-light" onClick={onCreateTrip}>
              <Sparkles size={17} /> {user ? '새 여행 만들기' : '로그인하고 여행 만들기'}
            </button>
            <button className="landing-text-button" onClick={() => onExplore()}>커뮤니티 둘러보기</button>
          </div>
        </div>
        <div className="landing-route-art" aria-hidden="true">
          <span className="art-card art-card-a"><b>DAY 1</b><strong>오사카성</strong><small>09:30 · 120분</small></span>
          <span className="art-line art-line-a"></span>
          <span className="art-pin art-pin-a">1</span>
          <span className="art-pin art-pin-b">2</span>
          <span className="art-pin art-pin-c">3</span>
          <span className="art-card art-card-b"><b>REOPTIMIZED</b><strong>우메다의 저녁</strong><small>내 숙소 기준 18분</small></span>
        </div>
      </section>

      <section className="landing-section destination-section">
        <div className="landing-section-head">
          <div><span className="eyebrow">DESTINATIONS</span><h2>나라별 추천 루트</h2></div>
          <button onClick={() => onExplore()}>전체 여행지 보기 <ArrowRight size={16} /></button>
        </div>
        <div className="destination-grid">
          {destinations.map((destination, index) => (
            <button key={destination.code} className={`destination-card destination-card-${index + 1}`} onClick={() => onExplore(destination.region)}>
              <span>{destination.code}</span>
              <div><small>{destination.country}</small><strong>{destination.region}</strong><p>{destination.copy}</p></div>
              <ArrowRight size={18} />
            </button>
          ))}
        </div>
      </section>

      <section className="landing-section popular-section">
        <div className="landing-section-head">
          <div><span className="eyebrow">{user ? 'FOR YOU' : 'POPULAR ROUTES'}</span><h2>{user ? '내 취향에 맞는 여행' : '지금 많이 가져가는 여행'}</h2><p>{user ? '마이페이지에서 선택한 관심 지역·장소 유형·여행 방식으로 추천합니다.' : '실제로 다른 여행자가 자신의 일정으로 복사한 횟수를 우선합니다.'}</p></div>
          <button onClick={() => onExplore()}>루트 커뮤니티 <ArrowRight size={16} /></button>
        </div>
        {loading ? (
          <AsyncState kind="loading" title="추천 루트를 불러오는 중입니다" className="landing-empty" />
        ) : loadFailed ? (
          <AsyncState kind="error" title="추천 루트를 불러오지 못했습니다" message="잠시 후 다시 시도해 주세요." actionLabel="다시 시도" onAction={() => { setLoading(true); setLoadFailed(false); setReloadKey((key) => key + 1) }} className="landing-empty" />
        ) : routes.length === 0 ? (
          <AsyncState kind="empty" title="아직 공개된 추천 루트가 없습니다" message="첫 번째 일정을 공개해 커뮤니티를 시작해 보세요." className="landing-empty" />
        ) : (
          <div className="landing-route-grid">
            {routes.map((route, index) => (
              <button key={route.routeId} className="landing-route-card panel" onClick={() => onExplore(route.region)}>
                <div className={`landing-route-cover cover-${(index % 4) + 1}`}>
                  <span><MapPin size={14} /> {route.region}</span><b>{route.travelDays}일</b>
                </div>
                <div className="landing-route-body">
                  <small>{route.ownerNickname}의 Route</small>
                  <h3>{route.title}</h3>
                  <p>{route.placePreview}</p>
                  {reasons[route.routeId] && <p className="recommendation-reasons">{reasons[route.routeId].join(' · ')}</p>}
                  <div><span><CalendarDays size={13} /> {route.travelDays}일</span><span>{transportLabel(route.transportMode)}</span><span>{paceLabel(route.pace)}</span></div>
                  <footer><span><Heart size={14} /> {route.likeCount}</span><span><Copy size={14} /> {route.copyCount}</span><strong>{durationLabel(route.estimatedTravelMinutes)}</strong></footer>
                </div>
              </button>
            ))}
          </div>
        )}
      </section>

      <section className="landing-how">
        <div><span>01</span><strong>좋은 루트를 발견</strong><p>나라와 도시, 여행 기간으로 공개 루트를 찾습니다.</p></div>
        <i></i>
        <div><span>02</span><strong>내 여행으로 복사</strong><p>방문 장소와 우선순위를 내 여행으로 가져옵니다.</p></div>
        <i></i>
        <div><span>03</span><strong>내 조건으로 재계산</strong><p>숙소·날짜·이동수단을 반영해 실행 가능한 일정을 만듭니다.</p></div>
      </section>
    </main>
  )
}
