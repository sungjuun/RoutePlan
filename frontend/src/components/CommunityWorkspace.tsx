import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  ArrowLeft,
  ArrowRight,
  BedDouble,
  CalendarDays,
  Check,
  Clock3,
  Copy,
  Eye,
  Heart,
  LoaderCircle,
  MapPin,
  Navigation,
  Search,
  Share2,
  Sparkles,
  UsersRound,
} from 'lucide-react'
import { api } from '../api/client'
import { dateLabel, distanceLabel, durationLabel, paceLabel, timeLabel, transportLabel } from '../lib/format'
import type {
  Itinerary,
  OptimizationAlgorithm,
  SharedRouteDetail,
  SharedRoutePage,
  SharedRouteSort,
  SharedRouteSummary,
  SharedRouteVisibility,
  TransportMode,
  Trip,
  TripPace,
  User,
} from '../types'
import { SharedRouteMap } from './SharedRouteMap'

interface Props {
  user: User | null
  trip: Trip
  itinerary: Itinerary | null
  onTripCopied: (
    trip: Trip,
    itinerary: Itinerary | null,
    message: string,
  ) => Promise<void>
  onNotify: (kind: 'success' | 'error' | 'info', message: string) => void
  onError: (error: unknown) => void
}

const emptyPage: SharedRoutePage = {
  content: [],
  page: 0,
  size: 12,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
}

export function CommunityWorkspace(props: Props) {
  const { user, trip, itinerary, onTripCopied, onNotify, onError } = props
  const [routes, setRoutes] = useState<SharedRoutePage>(emptyPage)
  const [selected, setSelected] = useState<SharedRouteDetail | null>(null)
  const [regionInput, setRegionInput] = useState('')
  const [region, setRegion] = useState('')
  const [sort, setSort] = useState<SharedRouteSort>('LATEST')
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const travelDays = Math.round(
    (Date.parse(`${trip.endDate}T00:00:00Z`) - Date.parse(`${trip.startDate}T00:00:00Z`))
      / 86_400_000,
  ) + 1

  const loadRoutes = useCallback(async () => {
    setLoading(true)
    try {
      setRoutes(await api.discoverRoutes({ region, travelDays, sort, page, size: 12 }))
    } catch (error) {
      onError(error)
    } finally {
      setLoading(false)
    }
  }, [onError, page, region, sort, travelDays])

  useEffect(() => {
    void loadRoutes()
  }, [loadRoutes])

  const openRoute = async (routeId: number) => {
    setDetailLoading(true)
    try {
      const detail = await api.getSharedRoute(routeId, user?.id)
      setSelected(detail)
      setRoutes((current) => ({
        ...current,
        content: current.content.map((route) => route.routeId === routeId
          ? { ...route, viewCount: detail.viewCount }
          : route),
      }))
    } catch (error) {
      onError(error)
    } finally {
      setDetailLoading(false)
    }
  }

  const updateRouteCounts = (routeId: number, values: Partial<SharedRouteSummary>) => {
    setRoutes((current) => ({
      ...current,
      content: current.content.map((route) => route.routeId === routeId
        ? { ...route, ...values }
        : route),
    }))
  }

  const handlePublished = async (route: SharedRouteDetail) => {
    setSelected(route)
    setPage(0)
    setSort('LATEST')
    await loadRoutes()
    onNotify('success', '현재 일정의 Snapshot을 루트 커뮤니티에 공개했습니다.')
  }

  return (
    <section className="content-section community-section">
      <div className="community-hero">
        <div>
          <span className="eyebrow">ROUTE COMMUNITY</span>
          <h1>좋은 동선을 발견하고<br />내 여행으로 다시 계산하세요</h1>
          <p>공개된 시간표는 Snapshot으로 보존됩니다. 가져올 때는 숙소와 날짜, 이동수단을 내 조건으로 바꿔 새 일정을 만듭니다.</p>
        </div>
        <PublishRoutePanel
          user={user}
          trip={trip}
          itinerary={itinerary}
          onPublished={handlePublished}
          onError={onError}
        />
      </div>

      <div className="community-toolbar panel">
        <form
          className="community-search"
          onSubmit={(event) => {
            event.preventDefault()
            setPage(0)
            setRegion(regionInput.trim())
          }}
        >
          <Search size={17} />
          <input
            value={regionInput}
            onChange={(event) => setRegionInput(event.target.value)}
            placeholder="지역으로 찾기 · 예: 오사카"
            maxLength={100}
          />
          <button className="button button-dark button-small" type="submit">검색</button>
        </form>
        <div className="community-sort" aria-label="정렬 방식">
          <button
            className={sort === 'LATEST' ? 'active' : ''}
            onClick={() => { setPage(0); setSort('LATEST') }}
          >최신순</button>
          <button
            className={sort === 'POPULAR' ? 'active' : ''}
            onClick={() => { setPage(0); setSort('POPULAR') }}
          >인기순</button>
        </div>
      </div>

      <div className={`community-layout ${selected ? 'with-detail' : ''}`}>
        <div className="community-results">
          <div className="community-results-head">
            <div>
              <h2>{region ? `${region} 공개 루트` : '새로 공개된 루트'}</h2>
              <span>{routes.totalElements.toLocaleString()}개</span>
            </div>
            {region && (
              <button
                className="button button-ghost button-small"
                onClick={() => { setRegionInput(''); setRegion(''); setPage(0) }}
              >전체 지역 보기</button>
            )}
          </div>

          {loading ? (
            <div className="community-state panel"><LoaderCircle className="spin" /><p>공개 루트를 불러오는 중입니다</p></div>
          ) : routes.content.length === 0 ? (
            <div className="community-state panel"><UsersRound size={28} /><h3>조건에 맞는 루트가 아직 없습니다</h3><p>첫 번째 일정 Snapshot을 공개해 보세요.</p></div>
          ) : (
            <div className="route-card-grid">
              {routes.content.map((route) => (
                <RouteCard
                  key={route.routeId}
                  route={route}
                  selected={selected?.routeId === route.routeId}
                  onOpen={() => void openRoute(route.routeId)}
                />
              ))}
            </div>
          )}

          {routes.totalPages > 1 && (
            <div className="community-pagination">
              <button className="button button-ghost button-small" disabled={routes.first} onClick={() => setPage((value) => value - 1)}><ArrowLeft size={15} /> 이전</button>
              <span>{routes.page + 1} / {routes.totalPages}</span>
              <button className="button button-ghost button-small" disabled={routes.last} onClick={() => setPage((value) => value + 1)}>다음 <ArrowRight size={15} /></button>
            </div>
          )}
        </div>

        {(selected || detailLoading) && (
          <aside className="community-detail-wrap">
            {detailLoading && !selected ? (
              <div className="community-state panel"><LoaderCircle className="spin" /><p>루트 상세를 불러오는 중입니다</p></div>
            ) : selected && (
              <RouteDetail
                key={selected.routeId}
                route={selected}
                user={user}
                currentTrip={trip}
                onClose={() => setSelected(null)}
                onChanged={(next) => {
                  setSelected(next)
                  updateRouteCounts(next.routeId, {
                    likeCount: next.likeCount,
                    copyCount: next.copyCount,
                    viewCount: next.viewCount,
                  })
                }}
                onTripCopied={onTripCopied}
                onError={onError}
              />
            )}
          </aside>
        )}
      </div>
    </section>
  )
}

function RouteCard({
  route,
  selected,
  onOpen,
}: {
  route: SharedRouteSummary
  selected: boolean
  onOpen: () => void
}) {
  return (
    <article className={`community-route-card panel ${selected ? 'selected' : ''}`}>
      <button className="route-card-open" onClick={onOpen}>
        <div className="route-card-cover">
          <span>{route.region}</span>
          <i>{route.travelDays}일</i>
          <div className="route-card-line" aria-hidden="true">
            {[...Array(Math.min(route.placeCount, 4))].map((_, index) => (
              <span key={index}>{index + 1}</span>
            ))}
          </div>
        </div>
        <div className="route-card-body">
          <div className="route-card-author"><span>{route.ownerNickname.slice(0, 1)}</span><strong>{route.ownerNickname}</strong><small>{dateLabel(route.sourceStartDate)}</small></div>
          <h3>{route.title}</h3>
          <p>{route.placePreview}</p>
          <div className="route-card-meta">
            <span><Navigation size={13} /> {distanceLabel(route.totalDistanceMeters)}</span>
            <span><Clock3 size={13} /> {durationLabel(route.estimatedTravelMinutes)}</span>
          </div>
        </div>
      </button>
      <footer>
        <span><Heart size={14} /> {route.likeCount.toLocaleString()}</span>
        <span><Copy size={14} /> {route.copyCount.toLocaleString()}</span>
        <span><Eye size={14} /> {route.viewCount.toLocaleString()}</span>
        <button onClick={onOpen}>루트 보기 <ArrowRight size={14} /></button>
      </footer>
    </article>
  )
}

function PublishRoutePanel({
  user,
  trip,
  itinerary,
  onPublished,
  onError,
}: {
  user: User | null
  trip: Trip
  itinerary: Itinerary | null
  onPublished: (route: SharedRouteDetail) => Promise<void>
  onError: (error: unknown) => void
}) {
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState(`${trip.name} 루트`)
  const [description, setDescription] = useState('')
  const [region, setRegion] = useState('')
  const [visibility, setVisibility] = useState<SharedRouteVisibility>('PUBLIC')
  const [submitting, setSubmitting] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!user || !itinerary) return
    setSubmitting(true)
    try {
      const route = await api.publishRoute(itinerary.itineraryId, {
        userId: user.id,
        title,
        description: description.trim() || null,
        region,
        visibility,
      })
      setOpen(false)
      await onPublished(route)
    } catch (error) {
      onError(error)
    } finally {
      setSubmitting(false)
    }
  }

  if (!open) {
    return (
      <div className="publish-callout panel">
        <span><Share2 size={22} /></span>
        <div><strong>내 일정도 공유할까요?</strong><p>{itinerary ? `현재 버전 ${itinerary.version}을 수정 불가능한 Snapshot으로 보존합니다.` : '일정을 먼저 계산하면 공개할 수 있습니다.'}</p></div>
        <button className="button button-primary" disabled={!user || !itinerary} onClick={() => setOpen(true)}><Share2 size={16} /> 현재 일정 공개</button>
      </div>
    )
  }

  return (
    <form className="publish-form panel" onSubmit={(event) => void submit(event)}>
      <div className="publish-form-head"><div><span className="eyebrow">PUBLISH SNAPSHOT</span><h2>현재 일정 공개</h2></div><button type="button" onClick={() => setOpen(false)}>취소</button></div>
      <label className="field"><span>루트 제목</span><input required maxLength={150} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
      <label className="field"><span>지역</span><input required maxLength={100} value={region} onChange={(event) => setRegion(event.target.value)} placeholder="예: 오사카" /></label>
      <label className="field field-wide"><span>한 줄 설명</span><input maxLength={1000} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="이 루트의 특징을 알려주세요" /></label>
      <label className="field"><span>공개 범위</span><select value={visibility} onChange={(event) => setVisibility(event.target.value as SharedRouteVisibility)}><option value="PUBLIC">커뮤니티 공개</option><option value="UNLISTED">링크로만 공개</option></select></label>
      <button className="button button-primary" disabled={submitting}>{submitting ? 'Snapshot 저장 중…' : '루트 공개하기'}</button>
    </form>
  )
}

function RouteDetail({
  route,
  user,
  currentTrip,
  onClose,
  onChanged,
  onTripCopied,
  onError,
}: {
  route: SharedRouteDetail
  user: User | null
  currentTrip: Trip
  onClose: () => void
  onChanged: (route: SharedRouteDetail) => void
  onTripCopied: Props['onTripCopied']
  onError: Props['onError']
}) {
  const [liking, setLiking] = useState(false)
  const [copyOpen, setCopyOpen] = useState(false)

  const toggleLike = async () => {
    if (!user) return
    setLiking(true)
    try {
      const result = route.likedByViewer
        ? await api.unlikeSharedRoute(route.routeId, user.id)
        : await api.likeSharedRoute(route.routeId, user.id)
      onChanged({ ...route, likeCount: result.likeCount, likedByViewer: result.liked })
    } catch (error) {
      onError(error)
    } finally {
      setLiking(false)
    }
  }

  return (
    <div className="community-detail panel">
      <div className="community-detail-head">
        <button className="detail-back" onClick={onClose}><ArrowLeft size={16} /> 목록으로</button>
        <span>{route.visibility === 'PUBLIC' ? '커뮤니티 공개' : '링크 공개'}</span>
      </div>
      <div className="community-detail-title">
        <span className="eyebrow">{route.region} · {route.travelDays} DAY</span>
        <h2>{route.title}</h2>
        <p>{route.description ?? '작성자가 남긴 설명이 없습니다.'}</p>
        <div><strong>{route.ownerNickname}</strong><span>원본 일정 v{route.sourceItineraryVersion}</span><span>{dateLabel(route.sourceStartDate)}</span></div>
      </div>

      <SharedRouteMap route={route} />

      <div className="community-detail-metrics">
        <span><Navigation size={15} /><strong>{distanceLabel(route.totalDistanceMeters)}</strong><small>총 이동</small></span>
        <span><Clock3 size={15} /><strong>{durationLabel(route.estimatedTravelMinutes)}</strong><small>이동시간</small></span>
        <span><MapPin size={15} /><strong>{route.placeCount}곳</strong><small>방문 장소</small></span>
        <span><Sparkles size={15} /><strong>{route.optimizationScore}</strong><small>일정 점수</small></span>
      </div>

      <div className="shared-route-timeline">
        {[...Array(route.travelDays)].map((_, index) => {
          const dayNumber = index + 1
          const dayItems = route.items.filter((item) => item.dayNumber === dayNumber)
          return (
            <section className="shared-route-day" key={dayNumber}>
              <div className="shared-route-day-head"><strong>DAY {dayNumber}</strong><span>{dayItems[0] ? dateLabel(dayItems[0].visitDate) : `${dayNumber}일차`}</span><small>{dayItems.length}곳</small></div>
              <div className="shared-route-stop hotel"><span><BedDouble size={14} /></span><div><strong>{route.accommodationName}</strong><small>{timeLabel(route.dailyStartTime)} 출발</small></div></div>
              {dayItems.map((item) => (
                <div className="shared-route-stop" key={item.itemId}>
                  <span>{item.sequence}</span>
                  <div><strong>{item.placeName}{item.mustVisit && <em>꼭 가기</em>}</strong><small>{timeLabel(item.startTime)}–{timeLabel(item.endTime)} · {durationLabel(item.stayMinutes)} 머무름</small></div>
                </div>
              ))}
              <div className="shared-route-stop hotel"><span><BedDouble size={14} /></span><div><strong>{route.accommodationName}</strong><small>{timeLabel(route.dailyEndTime)} 이전 복귀</small></div></div>
            </section>
          )
        })}
      </div>

      <div className="community-social-bar">
        <button className={route.likedByViewer ? 'liked' : ''} disabled={!user || liking} onClick={() => void toggleLike()}><Heart size={17} fill={route.likedByViewer ? 'currentColor' : 'none'} /> {route.likeCount.toLocaleString()}</button>
        <span><Copy size={15} /> {route.copyCount.toLocaleString()}회 복사</span>
        <span><Eye size={15} /> {route.viewCount.toLocaleString()}회 조회</span>
      </div>

      {!copyOpen ? (
        <button className="button button-primary button-large community-copy-cta" disabled={!user} onClick={() => setCopyOpen(true)}><Copy size={18} /> 내 여행으로 가져오기</button>
      ) : (
        <CopyRouteForm
          route={route}
          user={user!}
          currentTrip={currentTrip}
          onCancel={() => setCopyOpen(false)}
          onTripCopied={onTripCopied}
          onError={onError}
        />
      )}
    </div>
  )
}

function CopyRouteForm({
  route,
  user,
  currentTrip,
  onCancel,
  onTripCopied,
  onError,
}: {
  route: SharedRouteDetail
  user: User
  currentTrip: Trip
  onCancel: () => void
  onTripCopied: Props['onTripCopied']
  onError: Props['onError']
}) {
  const [name, setName] = useState(`${route.title} - 내 여행`)
  const [startDate, setStartDate] = useState(currentTrip.startDate)
  const [dailyStartTime, setDailyStartTime] = useState(currentTrip.dailyStartTime.slice(0, 5))
  const [dailyEndTime, setDailyEndTime] = useState(currentTrip.dailyEndTime.slice(0, 5))
  const [accommodationName, setAccommodationName] = useState(currentTrip.accommodationName)
  const [latitude, setLatitude] = useState(String(currentTrip.accommodationLatitude))
  const [longitude, setLongitude] = useState(String(currentTrip.accommodationLongitude))
  const [transportMode, setTransportMode] = useState<TransportMode>(currentTrip.transportMode)
  const [pace, setPace] = useState<TripPace>(currentTrip.pace)
  const [algorithm, setAlgorithm] = useState<OptimizationAlgorithm>('NEAREST_NEIGHBOR_2_OPT')
  const [submitting, setSubmitting] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    try {
      const copied = await api.copySharedRoute(route.routeId, {
        userId: user.id,
        name,
        startDate,
        dailyStartTime,
        dailyEndTime,
        accommodationName,
        accommodationLatitude: Number(latitude),
        accommodationLongitude: Number(longitude),
        transportMode,
        pace,
      })
      try {
        const optimized = await api.optimize(copied.id, algorithm)
        const optimizedTrip = await api.getTrip(copied.id)
        await onTripCopied(optimizedTrip, optimized, `공개 루트를 복사하고 내 조건으로 일정 버전 ${optimized.version}을 만들었습니다.`)
      } catch (optimizationError) {
        await onTripCopied(copied, null, '루트 장소를 새 여행에 복사했습니다. 조건을 확인한 뒤 다시 계산해 주세요.')
        onError(optimizationError)
      }
    } catch (error) {
      onError(error)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="copy-route-form" onSubmit={(event) => void submit(event)}>
      <div className="copy-route-head"><div><span className="eyebrow">PERSONALIZE</span><h3>내 조건으로 다시 계산</h3></div><button type="button" onClick={onCancel}>취소</button></div>
      <p>장소와 우선순위만 가져옵니다. 공개된 시간표를 그대로 복사하지 않습니다.</p>
      <div className="copy-route-grid">
        <label className="field field-wide"><span>새 여행 이름</span><input required maxLength={100} value={name} onChange={(event) => setName(event.target.value)} /></label>
        <label className="field"><span><CalendarDays size={14} /> 여행 시작일</span><input required type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} /><small>원본과 같은 {route.travelDays}일 여행으로 복사됩니다.</small></label>
        <label className="field"><span><Clock3 size={14} /> 하루 시간</span><div className="inline-inputs"><input required type="time" value={dailyStartTime} onChange={(event) => setDailyStartTime(event.target.value)} /><span>–</span><input required type="time" value={dailyEndTime} onChange={(event) => setDailyEndTime(event.target.value)} /></div></label>
        <label className="field field-wide"><span><BedDouble size={14} /> 숙소 이름</span><input required maxLength={100} value={accommodationName} onChange={(event) => setAccommodationName(event.target.value)} /></label>
        <label className="field"><span>숙소 위도</span><input required type="number" step="0.000001" min="-90" max="90" value={latitude} onChange={(event) => setLatitude(event.target.value)} /></label>
        <label className="field"><span>숙소 경도</span><input required type="number" step="0.000001" min="-180" max="180" value={longitude} onChange={(event) => setLongitude(event.target.value)} /></label>
        <label className="field"><span>이동수단</span><select value={transportMode} onChange={(event) => setTransportMode(event.target.value as TransportMode)}><option value="WALKING">도보</option><option value="PUBLIC_TRANSIT">대중교통</option><option value="DRIVING">자동차</option></select></label>
        <label className="field"><span>여행 강도</span><select value={pace} onChange={(event) => setPace(event.target.value as TripPace)}><option value="RELAXED">여유롭게</option><option value="STANDARD">보통</option><option value="ACTIVE">알차게</option></select></label>
        <label className="field field-wide"><span>계산 방식</span><select value={algorithm} onChange={(event) => setAlgorithm(event.target.value as OptimizationAlgorithm)}><option value="NEAREST_NEIGHBOR_2_OPT">균형 추천</option><option value="NEAREST_NEIGHBOR">빠른 계산</option>{route.placeCount <= 10 && <option value="EXACT_SEARCH">최적 경로</option>}</select></label>
      </div>
      <div className="copy-route-summary"><Check size={16} /><span>{route.travelDays}일 · {route.placeCount}곳 · {transportLabel(transportMode)} · {paceLabel(pace)}</span></div>
      <button className="button button-primary button-large" disabled={submitting}>{submitting ? '복사하고 새 일정을 계산하는 중…' : '복사 후 재최적화'}</button>
      <small><Sparkles size={13} /> 새 Trip과 Itinerary로 저장되며 원본 루트는 바뀌지 않습니다.</small>
    </form>
  )
}
