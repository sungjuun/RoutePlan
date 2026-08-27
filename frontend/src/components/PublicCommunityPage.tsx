import { useEffect, useState, type FormEvent } from 'react'
import { ArrowLeft, ArrowRight, CalendarDays, Copy, Eye, Heart, Search } from 'lucide-react'
import { api } from '../api/client'
import { dateLabel, durationLabel, paceLabel, transportLabel } from '../lib/format'
import type { SharedRouteDetail, SharedRoutePage, SharedRouteSort, User } from '../types'
import { AsyncState } from './AsyncState'
import { SharedRouteMap } from './SharedRouteMap'

interface Props {
  user: User | null
  initialRegion: string
  onRequireAuth: () => void
  onCreateTrip: () => void
  onError: (error: unknown) => void
}

const emptyPage: SharedRoutePage = { content: [], page: 0, size: 12, totalElements: 0, totalPages: 0, first: true, last: true }

export function PublicCommunityPage({ user, initialRegion, onRequireAuth, onCreateTrip, onError }: Props) {
  const [input, setInput] = useState(initialRegion)
  const [region, setRegion] = useState(initialRegion)
  const [sort, setSort] = useState<SharedRouteSort>('POPULAR')
  const [page, setPage] = useState(0)
  const [routes, setRoutes] = useState<SharedRoutePage>(emptyPage)
  const [selected, setSelected] = useState<SharedRouteDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    api.discoverRoutes({ region, sort, page, size: 12 })
      .then((result) => { if (!cancelled) setRoutes(result) })
      .catch((error) => {
        if (!cancelled) {
          setLoadFailed(true)
          onError(error)
        }
      })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [onError, page, region, reloadKey, sort])

  const search = (event: FormEvent) => {
    event.preventDefault()
    setLoading(true)
    setLoadFailed(false)
    setPage(0)
    setRegion(input.trim())
    setSelected(null)
    setReloadKey((key) => key + 1)
  }

  return (
    <main className="public-community-page">
      <section className="public-community-hero"><span className="eyebrow eyebrow-light">ROUTE COMMUNITY</span><h1>다른 여행자의 좋은 동선을<br />내 여행의 시작점으로</h1><p>일정 Snapshot을 살펴보고 숙소와 날짜를 바꿔 나만의 실행 가능한 루트를 만드세요.</p></section>
      <section className="public-community-content">
        <div className="community-toolbar panel">
          <form className="community-search" onSubmit={search}><Search size={17} /><input value={input} onChange={(event) => setInput(event.target.value)} placeholder="도시 또는 지역 · 예: 오사카" /><button className="button button-dark button-small">검색</button></form>
          <div className="community-sort"><button className={sort === 'POPULAR' ? 'active' : ''} onClick={() => { setLoading(true); setLoadFailed(false); setSelected(null); setSort('POPULAR'); setPage(0); setReloadKey((key) => key + 1) }}>인기순</button><button className={sort === 'LATEST' ? 'active' : ''} onClick={() => { setLoading(true); setLoadFailed(false); setSelected(null); setSort('LATEST'); setPage(0); setReloadKey((key) => key + 1) }}>최신순</button></div>
        </div>
        <div className={`public-community-layout ${selected ? 'with-detail' : ''}`}>
          <div>
            <div className="community-results-head"><div><h2>{region ? `${region} 추천 루트` : '모든 공개 루트'}</h2><span>{loading || loadFailed ? '—' : `${routes.totalElements.toLocaleString()}개`}</span></div></div>
            {loading ? <AsyncState kind="loading" title="루트를 불러오는 중입니다" className="community-state" /> : loadFailed ? <AsyncState kind="error" title="공개 루트를 불러오지 못했습니다" message="잠시 후 다시 시도해 주세요." actionLabel="다시 시도" onAction={() => { setLoading(true); setLoadFailed(false); setReloadKey((key) => key + 1) }} className="community-state" /> : routes.content.length === 0 ? <AsyncState kind="empty" title="아직 공개된 루트가 없습니다" message="다른 지역을 검색해 보세요." className="community-state" /> : <div className="route-card-grid">{routes.content.map((route, index) => <button key={route.routeId} className={`public-route-card panel ${selected?.routeId === route.routeId ? 'selected' : ''}`} onClick={async () => { try { setSelected(await api.getSharedRoute(route.routeId)) } catch (error) { onError(error) } }}><div className={`public-route-cover cover-${(index % 4) + 1}`}><span>{route.region}</span><b>{route.travelDays}일</b></div><div><small>{route.ownerNickname}</small><h3>{route.title}</h3><p>{route.placePreview}</p><div className="public-route-meta"><span>{transportLabel(route.transportMode)}</span><span>{paceLabel(route.pace)}</span></div><footer><span><Heart size={13} /> {route.likeCount}</span><span><Copy size={13} /> {route.copyCount}</span><span><Eye size={13} /> {route.viewCount}</span></footer></div></button>)}</div>}
            {routes.totalPages > 1 && <div className="community-pagination"><button className="button button-ghost button-small" disabled={routes.first} onClick={() => { setLoading(true); setLoadFailed(false); setSelected(null); setPage((value) => value - 1) }}><ArrowLeft size={15} /> 이전</button><span>{routes.page + 1} / {routes.totalPages}</span><button className="button button-ghost button-small" disabled={routes.last} onClick={() => { setLoading(true); setLoadFailed(false); setSelected(null); setPage((value) => value + 1) }}>다음 <ArrowRight size={15} /></button></div>}
          </div>
          {selected && <aside className="public-route-detail panel"><button className="detail-close" onClick={() => setSelected(null)} aria-label="상세 닫기">×</button><span className="eyebrow">{selected.region} · {selected.travelDays} DAYS</span><h2>{selected.title}</h2><p>{selected.description}</p><SharedRouteMap route={selected} /><div className="public-detail-facts"><span><CalendarDays size={14} /> {dateLabel(selected.sourceStartDate)} 출발</span><span>{transportLabel(selected.transportMode)}</span><span>{durationLabel(selected.estimatedTravelMinutes)} 이동</span></div><ol>{selected.items.map((item) => <li key={item.itemId}><span>{item.dayNumber}일차 · {item.sequence}</span><strong>{item.placeName}</strong><small>{item.startTime.slice(0, 5)}–{item.endTime.slice(0, 5)}</small></li>)}</ol><button className="button button-primary button-large" onClick={user ? onCreateTrip : onRequireAuth}>{user ? '이 루트를 참고해 내 여행 만들기' : '로그인하고 내 여행으로 가져오기'} <ArrowRight size={17} /></button></aside>}
        </div>
      </section>
    </main>
  )
}
