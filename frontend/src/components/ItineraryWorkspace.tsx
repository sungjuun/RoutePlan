import { useEffect, useMemo, useState } from 'react'
import {
  AlertTriangle,
  ArrowRight,
  BedDouble,
  Check,
  CircleDot,
  Clock3,
  Gauge,
  GitCompareArrows,
  LocateFixed,
  MapPin,
  Navigation,
  Plus,
  RefreshCw,
  Route as RouteIcon,
  Sparkles,
  TimerReset,
  TriangleAlert,
  WandSparkles,
} from 'lucide-react'
import { api } from '../api/client'
import { dateLabel, distanceLabel, durationLabel, reasonLabel, timeLabel } from '../lib/format'
import { compareItineraries, minimumCompletedCount } from '../lib/itinerary'
import type {
  Itinerary,
  ItineraryChangeReason,
  OptimizationAlgorithm,
  Place,
  Trip,
} from '../types'
import { MapPanel } from './MapPanel'

interface Props {
  trip: Trip
  itinerary: Itinerary | null
  previousItinerary: Itinerary | null
  itineraryPlaces: Record<number, Place>
  onItineraryChanged: (itinerary: Itinerary, message: string) => Promise<void>
  onError: (error: unknown) => void
  onGoToPlaces: () => void
}

type ItineraryTab = 'route' | 'reoptimize' | 'compare'

const algorithms: Array<{ value: OptimizationAlgorithm; label: string; note: string }> = [
  { value: 'NEAREST_NEIGHBOR', label: '빠른 계산', note: '가까운 장소부터' },
  { value: 'NEAREST_NEIGHBOR_2_OPT', label: '균형 추천', note: '빠른 경로를 한 번 더 개선' },
  { value: 'EXACT_SEARCH', label: '최적 경로', note: '10곳 이하 전수 비교' },
]

export function ItineraryWorkspace(props: Props) {
  const { trip, itinerary, previousItinerary, itineraryPlaces, onItineraryChanged, onError, onGoToPlaces } = props
  const [algorithm, setAlgorithm] = useState<OptimizationAlgorithm>(itinerary?.algorithm ?? 'NEAREST_NEIGHBOR_2_OPT')
  const [tab, setTab] = useState<ItineraryTab>('route')
  const [calculating, setCalculating] = useState(false)

  useEffect(() => {
    if (itinerary) setAlgorithm(itinerary.algorithm)
  }, [itinerary])

  const optimize = async () => {
    setCalculating(true)
    try {
      await onItineraryChanged(
        await api.optimize(trip.id, algorithm),
        itinerary ? '전체 조건으로 새 일정 버전을 만들었습니다.' : '첫 일정을 만들었습니다.',
      )
      setTab('route')
    } catch (error) {
      onError(error)
    } finally {
      setCalculating(false)
    }
  }

  if (!itinerary) {
    return (
      <section className="content-section empty-itinerary-section">
        <div className="empty-itinerary-copy">
          <span className="eyebrow">READY TO PLAN</span>
          <h1>{trip.places.length > 0 ? '선택한 장면을 하나의 하루로' : '먼저 가고 싶은 곳을 담아주세요'}</h1>
          <p>{trip.places.length > 0
            ? `${trip.places.length}곳의 영업시간과 머무는 시간, 숙소 복귀까지 함께 계산합니다.`
            : '장소가 한 곳 이상 있어야 실행 가능한 일정을 만들 수 있습니다.'}</p>
        </div>
        <div className="planner-launch-card">
          <div className="launch-visual" aria-hidden="true"><span>H</span><i></i><span>1</span><i></i><span>2</span><i></i><span>H</span></div>
          <AlgorithmPicker algorithm={algorithm} onChange={setAlgorithm} placeCount={trip.places.length} />
          {trip.places.length === 0 ? (
            <button className="button button-primary" onClick={onGoToPlaces}><Plus size={18} /> 장소 담으러 가기</button>
          ) : (
            <button className="button button-primary button-large" onClick={() => void optimize()} disabled={calculating}>
              <WandSparkles size={19} /> {calculating ? '실행 가능한 일정을 계산하는 중…' : '내 일정 계산하기'}
            </button>
          )}
          <small><Sparkles size={14} /> 계산 결과는 새 버전으로 안전하게 저장됩니다.</small>
        </div>
      </section>
    )
  }

  return (
    <section className="content-section itinerary-section">
      <div className="itinerary-titlebar">
        <div>
          <div className="title-badges">
            <span className="version-badge">VERSION {itinerary.version}</span>
            <span className={`generation-badge ${itinerary.generationType === 'REOPTIMIZATION' ? 'changed' : ''}`}>
              {itinerary.generationType === 'REOPTIMIZATION' ? reasonLabel(itinerary.changeReason) : '첫 일정'}
            </span>
          </div>
          <h1>{trip.name}</h1>
          <p>{dateLabel(trip.startDate)} · {trip.accommodationName}에서 출발</p>
        </div>
        <div className="title-actions">
          <button className="button button-ghost" onClick={onGoToPlaces}><Plus size={16} /> 장소 변경</button>
          <button className="button button-primary" onClick={() => setTab('reoptimize')}><TimerReset size={17} /> 남은 일정 다시 짜기</button>
        </div>
      </div>

      <div className="itinerary-tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'route'} className={tab === 'route' ? 'active' : ''} onClick={() => setTab('route')}><RouteIcon size={17} /> 일정과 지도</button>
        <button role="tab" aria-selected={tab === 'reoptimize'} className={tab === 'reoptimize' ? 'active' : ''} onClick={() => setTab('reoptimize')}><RefreshCw size={17} /> 재최적화</button>
        <button role="tab" aria-selected={tab === 'compare'} className={tab === 'compare' ? 'active' : ''} onClick={() => setTab('compare')} disabled={!previousItinerary}><GitCompareArrows size={17} /> 버전 비교</button>
      </div>

      {tab === 'route' && (
        <RouteView
          trip={trip}
          itinerary={itinerary}
          itineraryPlaces={itineraryPlaces}
          algorithm={algorithm}
          setAlgorithm={setAlgorithm}
          calculating={calculating}
          optimize={optimize}
        />
      )}
      {tab === 'reoptimize' && (
        <ReoptimizationView
          {...props}
          algorithm={algorithm}
          onAlgorithmChange={setAlgorithm}
          onDone={() => setTab('route')}
        />
      )}
      {tab === 'compare' && previousItinerary && (
        <VersionCompare before={previousItinerary} after={itinerary} />
      )}
    </section>
  )
}

function AlgorithmPicker({ algorithm, onChange, placeCount }: { algorithm: OptimizationAlgorithm; onChange: (value: OptimizationAlgorithm) => void; placeCount: number }) {
  return (
    <fieldset className="algorithm-picker">
      <legend>계산 방식</legend>
      {algorithms.map((item) => (
        <label key={item.value} className={algorithm === item.value ? 'selected' : ''}>
          <input type="radio" name="algorithm" value={item.value} checked={algorithm === item.value} onChange={() => onChange(item.value)} disabled={item.value === 'EXACT_SEARCH' && placeCount > 10} />
          <span><strong>{item.label}</strong><small>{item.note}{item.value === 'EXACT_SEARCH' && placeCount > 10 ? ' · 현재 사용 불가' : ''}</small></span>
          <i>{algorithm === item.value && <Check size={14} />}</i>
        </label>
      ))}
    </fieldset>
  )
}

function RouteView({
  trip,
  itinerary,
  itineraryPlaces,
  algorithm,
  setAlgorithm,
  calculating,
  optimize,
}: {
  trip: Trip
  itinerary: Itinerary
  itineraryPlaces: Record<number, Place>
  algorithm: OptimizationAlgorithm
  setAlgorithm: (value: OptimizationAlgorithm) => void
  calculating: boolean
  optimize: () => Promise<void>
}) {
  const completedCount = minimumCompletedCount(itinerary)
  const visit = (item: Itinerary['items'][number]) => (
    <div className={`timeline-item ${item.status === 'COMPLETED' ? 'timeline-completed' : ''}`} key={item.itineraryItemId}>
      <div className="timeline-time"><strong>{timeLabel(item.startTime)}</strong><span>{timeLabel(item.endTime)}</span></div>
      <div className="timeline-line"><span>{item.status === 'COMPLETED' ? <Check size={15} /> : item.sequence}</span><i></i></div>
      <div className="timeline-copy">
        <div><strong>{item.placeName}</strong>{item.mustVisit && <em>꼭 가기</em>}</div>
        <small>{durationLabel(item.stayMinutes)} 머무름 · 이동 {durationLabel(item.estimatedTravelMinutes)}{item.waitingMinutes > 0 ? ` · ${durationLabel(item.waitingMinutes)} 대기` : ''}</small>
      </div>
    </div>
  )

  return (
    <>
      <div className="metric-strip">
        <Metric icon={<Navigation size={18} />} label="총 이동" value={distanceLabel(itinerary.totalDistanceMeters)} note={durationLabel(itinerary.estimatedTravelMinutes)} />
        <Metric icon={<MapPin size={18} />} label="방문 장소" value={`${itinerary.items.length}곳`} note={itinerary.exclusions.length ? `${itinerary.exclusions.length}곳 제외` : '모두 포함'} />
        <Metric icon={<Clock3 size={18} />} label="머무는 시간" value={durationLabel(itinerary.totalStayMinutes)} note={itinerary.totalWaitingMinutes ? `대기 ${durationLabel(itinerary.totalWaitingMinutes)}` : '대기 없음'} />
        <Metric icon={<Gauge size={18} />} label="숙소 도착" value={timeLabel(itinerary.returnArrivalTime)} note={`복귀 ${durationLabel(itinerary.returnTravelMinutes)}`} />
      </div>

      <div className="route-layout">
        <div className="timeline-panel panel">
          <div className="panel-title"><div><span className="eyebrow">YOUR DAY</span><h2>시간표</h2></div><span>{itinerary.items.length + (itinerary.generationType === 'REOPTIMIZATION' ? 3 : 2)}개의 장면</span></div>
          <div className="timeline">
            <div className="timeline-item timeline-hotel">
              <div className="timeline-time">{timeLabel(trip.dailyStartTime)}</div>
              <div className="timeline-line"><span><BedDouble size={16} /></span><i></i></div>
              <div className="timeline-copy"><strong>{trip.accommodationName}</strong><small>오늘의 출발점</small></div>
            </div>
            {itinerary.items.slice(0, completedCount).map(visit)}
            {itinerary.generationType === 'REOPTIMIZATION' && (
              <div className="timeline-item timeline-current">
                <div className="timeline-time"><strong>{timeLabel(itinerary.reoptimizationStartTime)}</strong></div>
                <div className="timeline-line"><span><LocateFixed size={15} /></span><i></i></div>
                <div className="timeline-copy">
                  <strong>현재 위치에서 다시 출발</strong>
                  <small>여기부터 남은 일정을 새로 계산했습니다</small>
                </div>
              </div>
            )}
            {itinerary.items.slice(completedCount).map(visit)}
            <div className="timeline-item timeline-hotel timeline-return">
              <div className="timeline-time"><strong>{timeLabel(itinerary.returnArrivalTime)}</strong></div>
              <div className="timeline-line"><span><BedDouble size={16} /></span></div>
              <div className="timeline-copy"><strong>{trip.accommodationName}</strong><small>하루 종료 전 숙소 복귀 완료</small></div>
            </div>
          </div>
          {itinerary.exclusions.length > 0 && (
            <div className="exclusion-box">
              <TriangleAlert size={18} />
              <div><strong>이번 일정에 들어가지 못한 장소</strong>{itinerary.exclusions.map((item) => <span key={item.placeId}>{item.placeName} · {exclusionLabel(item.reason)}</span>)}</div>
            </div>
          )}
        </div>
        <div className="route-map-stack">
          <MapPanel trip={trip} itinerary={itinerary} places={itineraryPlaces} />
          <div className="route-detail-card panel">
            <div><span className="route-source-dot"></span><span><strong>{itinerary.routeDataType === 'EXTERNAL_PROVIDER' ? '실제 도로 경로' : '좌표 기반 예상 경로'}</strong><small>Matrix {itinerary.routeMatrixElementCount.toLocaleString()}요소 · {itinerary.routeMatrixBuildMillis}ms</small></span></div>
            <button className="button button-ghost button-small" onClick={() => void optimize()} disabled={calculating}>{calculating ? '계산 중…' : '전체 새 버전 계산'}</button>
          </div>
          <details className="algorithm-details panel">
            <summary>계산 방식 바꾸기 <ArrowRight size={15} /></summary>
            <AlgorithmPicker algorithm={algorithm} onChange={setAlgorithm} placeCount={trip.places.length} />
          </details>
        </div>
      </div>
    </>
  )
}

function Metric({ icon, label, value, note }: { icon: React.ReactNode; label: string; value: string; note: string }) {
  return <div className="metric-card"><span>{icon}</span><div><small>{label}</small><strong>{value}</strong><em>{note}</em></div></div>
}

function ReoptimizationView({
  trip,
  itinerary,
  algorithm,
  onAlgorithmChange,
  onItineraryChanged,
  onError,
  onDone,
}: Props & { algorithm: OptimizationAlgorithm; onAlgorithmChange: (value: OptimizationAlgorithm) => void; onDone: () => void }) {
  const minimum = minimumCompletedCount(itinerary!)
  const [completedCount, setCompletedCount] = useState(minimum)
  const [currentTime, setCurrentTime] = useState((itinerary!.items[minimum - 1]?.endTime ?? itinerary!.reoptimizationStartTime ?? trip.dailyStartTime).slice(0, 5))
  const [latitude, setLatitude] = useState(String(itinerary!.reoptimizationStartLatitude ?? trip.accommodationLatitude))
  const [longitude, setLongitude] = useState(String(itinerary!.reoptimizationStartLongitude ?? trip.accommodationLongitude))
  const [reason, setReason] = useState<ItineraryChangeReason>('DELAY')
  const [detail, setDetail] = useState('')
  const [locating, setLocating] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const toggleCompleted = (index: number) => {
    const next = completedCount === index + 1 ? Math.max(minimum, index) : index + 1
    setCompletedCount(next)
    const lastEnd = itinerary!.items[next - 1]?.endTime?.slice(0, 5)
    if (lastEnd && currentTime < lastEnd) setCurrentTime(lastEnd)
  }

  const locate = () => {
    if (!navigator.geolocation) {
      onError(new Error('이 브라우저에서는 현재 위치를 사용할 수 없습니다.'))
      return
    }
    setLocating(true)
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLatitude(position.coords.latitude.toFixed(6))
        setLongitude(position.coords.longitude.toFixed(6))
        setLocating(false)
      },
      () => {
        setLocating(false)
        onError(new Error('현재 위치 권한을 확인해 주세요.'))
      },
      { enableHighAccuracy: true, timeout: 10_000 },
    )
  }

  const submit = async () => {
    setSubmitting(true)
    try {
      const next = await api.reoptimize(trip.id, algorithm, {
        sourceItineraryId: itinerary!.itineraryId,
        currentTime,
        currentLatitude: Number(latitude),
        currentLongitude: Number(longitude),
        completedItemIds: itinerary!.items.slice(0, completedCount).map((item) => item.itineraryItemId),
        reason,
        reasonDetail: detail.trim() || null,
      })
      await onItineraryChanged(next, `남은 일정을 버전 ${next.version}으로 다시 만들었습니다.`)
      onDone()
    } catch (error) {
      onError(error)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="reoptimize-layout">
      <div className="reoptimize-main panel">
        <div className="panel-title"><div><span className="eyebrow">REPLAN FROM HERE</span><h2>어디까지 다녀오셨나요?</h2></div><span>{completedCount}곳 완료</span></div>
        <p className="panel-description">완료한 앞부분은 그대로 잠그고, 현재 위치부터 남은 장소만 다시 계산합니다.</p>
        <div className="completion-list">
          {itinerary!.items.map((item, index) => {
            const checked = index < completedCount
            const locked = index < minimum
            return (
              <button key={item.itineraryItemId} className={checked ? 'completed' : ''} onClick={() => toggleCompleted(index)} disabled={locked}>
                <span>{checked ? <Check size={16} /> : index + 1}</span>
                <div><strong>{item.placeName}</strong><small>{timeLabel(item.startTime)}–{timeLabel(item.endTime)}</small></div>
                {locked && <em>이전 버전에서 완료</em>}
              </button>
            )
          })}
        </div>

        <div className="reopt-form-grid">
          <label className="field"><span><Clock3 size={15} /> 현재 시각</span><input type="time" min={trip.dailyStartTime.slice(0, 5)} max={trip.dailyEndTime.slice(0, 5)} value={currentTime} onChange={(event) => setCurrentTime(event.target.value)} /></label>
          <div className="field location-field"><span><LocateFixed size={15} /> 현재 위치</span><button className="button button-ghost" onClick={locate}><LocateFixed size={16} /> {locating ? '확인 중…' : '내 위치 사용'}</button></div>
          <label className="field"><span>위도</span><input type="number" step="0.000001" min="-90" max="90" value={latitude} onChange={(event) => setLatitude(event.target.value)} /></label>
          <label className="field"><span>경도</span><input type="number" step="0.000001" min="-180" max="180" value={longitude} onChange={(event) => setLongitude(event.target.value)} /></label>
          <label className="field"><span>변경 이유</span><select value={reason} onChange={(event) => setReason(event.target.value as ItineraryChangeReason)}><option value="DELAY">일정이 지연됐어요</option><option value="PLACE_ADDED">장소를 추가했어요</option><option value="PLACE_REMOVED">장소를 삭제했어요</option><option value="USER_REQUEST">순서를 바꾸고 싶어요</option><option value="OTHER">기타</option></select></label>
          <label className="field"><span>한 줄 메모</span><input value={detail} maxLength={500} onChange={(event) => setDetail(event.target.value)} placeholder="예: 점심 대기가 길었어요" /></label>
        </div>
      </div>
      <aside className="reoptimize-side">
        <div className="panel reopt-summary">
          <span className="summary-icon"><RefreshCw size={22} /></span>
          <h3>새 버전에서 달라지는 것</h3>
          <ul>
            <li><Check size={15} /> 완료한 {completedCount}곳의 시간표 보존</li>
            <li><CircleDot size={15} /> 남은 {itinerary!.items.length - completedCount}곳과 새 장소 재배치</li>
            <li><BedDouble size={15} /> {timeLabel(trip.dailyEndTime)} 전 숙소 복귀 검증</li>
          </ul>
        </div>
        <AlgorithmPicker algorithm={algorithm} onChange={onAlgorithmChange} placeCount={trip.places.length - completedCount} />
        <button className="button button-primary button-large reopt-submit" disabled={submitting} onClick={() => void submit()}><RefreshCw size={18} /> {submitting ? '남은 일정을 계산하는 중…' : '이 지점부터 다시 계산'}</button>
        <small className="safe-note"><Check size={14} /> 현재 버전 {itinerary!.version}를 수정하지 않습니다.</small>
      </aside>
    </div>
  )
}

function VersionCompare({ before, after }: { before: Itinerary; after: Itinerary }) {
  const diff = useMemo(() => compareItineraries(before, after), [before, after])
  return (
    <div className="compare-layout">
      <div className="compare-hero panel">
        <div className="compare-version"><span>V{before.version}</span><small>{reasonLabel(before.changeReason)}</small></div>
        <div className="compare-arrow"><GitCompareArrows size={24} /><i></i></div>
        <div className="compare-version current"><span>V{after.version}</span><small>{reasonLabel(after.changeReason)}</small></div>
        <div className="compare-reason"><strong>{after.changeReasonDetail ?? '남은 일정 조건을 반영했습니다.'}</strong><span>{after.reoptimizationStartTime ? `${timeLabel(after.reoptimizationStartTime)}부터 재계산` : '전체 일정 계산'}</span></div>
      </div>
      <div className="compare-stats">
        <div className="panel added"><Plus size={18} /><strong>{diff.added.length}</strong><span>새로 추가</span></div>
        <div className="panel removed"><AlertTriangle size={18} /><strong>{diff.removed.length}</strong><span>일정에서 제외</span></div>
        <div className="panel changed"><RefreshCw size={18} /><strong>{diff.rescheduled.length}</strong><span>시간·순서 변경</span></div>
        <div className="panel stable"><Check size={18} /><strong>{diff.unchanged}</strong><span>그대로 유지</span></div>
      </div>
      <div className="version-columns">
        <VersionColumn title={`이전 버전 ${before.version}`} itinerary={before} muted />
        <VersionColumn title={`현재 버전 ${after.version}`} itinerary={after} />
      </div>
    </div>
  )
}

function VersionColumn({ title, itinerary, muted = false }: { title: string; itinerary: Itinerary; muted?: boolean }) {
  return (
    <div className={`version-column panel ${muted ? 'muted' : ''}`}>
      <div className="version-column-head"><h3>{title}</h3><span>{distanceLabel(itinerary.totalDistanceMeters)} · {durationLabel(itinerary.estimatedTravelMinutes)}</span></div>
      {itinerary.items.map((item) => (
        <div className="version-row" key={item.itineraryItemId}><span>{item.sequence}</span><div><strong>{item.placeName}</strong><small>{timeLabel(item.startTime)}–{timeLabel(item.endTime)}</small></div>{item.status === 'COMPLETED' && <Check size={15} />}</div>
      ))}
      <div className="version-return"><BedDouble size={16} /> 숙소 {timeLabel(itinerary.returnArrivalTime)} 도착</div>
    </div>
  )
}

function exclusionLabel(reason: string): string {
  return { CLOSED: '휴무일', TIME_WINDOW: '방문 시간 충돌', DAILY_LIMIT: '하루 시간 부족' }[reason] ?? reason
}
