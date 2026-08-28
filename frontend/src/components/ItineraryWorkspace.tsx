import { Fragment, useEffect, useMemo, useState } from 'react'
import {
  AlertTriangle,
  ArrowRight,
  BedDouble,
  CalendarDays,
  Check,
  CircleDot,
  CloudSun,
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
  Wallet,
} from 'lucide-react'
import { api } from '../api/client'
import { dateLabel, distanceLabel, durationLabel, reasonLabel, timeLabel, weatherLabel } from '../lib/format'
import { compareItineraries, minimumCompletedCount } from '../lib/itinerary'
import type {
  Itinerary,
  ItineraryChangeReason,
  OptimizationAlgorithm,
  Place,
  Trip,
} from '../types'
import { MapPanel } from './MapPanel'
import { WeatherPlanner } from './WeatherPlanner'
import { BudgetPlanner } from './BudgetPlanner'
import { BudgetSummary } from './BudgetSummary'
import { moneyLabel } from '../lib/money'
import { LiveDataPanel } from './LiveDataPanel'
import { SpendingPanel } from './SpendingPanel'

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
  const [budgetDirty, setBudgetDirty] = useState(true)
  const [liveDataBlocked, setLiveDataBlocked] = useState(true)
  const [weatherRevision, setWeatherRevision] = useState(0)
  const budgetBlocked = budgetDirty || liveDataBlocked
  const multiDay = trip.startDate !== trip.endDate

  useEffect(() => {
    if (itinerary) setAlgorithm(itinerary.algorithm)
  }, [itinerary])

  const optimize = async () => {
    if (budgetBlocked) return
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
          <h1>{trip.places.length > 0 ? '선택한 장면을 여행 날짜마다' : '먼저 가고 싶은 곳을 담아주세요'}</h1>
          <p>{trip.places.length > 0
            ? `${trip.places.length}곳의 날짜별 영업시간과 머무는 시간, 매일 숙소 복귀까지 함께 계산합니다.`
            : '장소가 한 곳 이상 있어야 실행 가능한 일정을 만들 수 있습니다.'}</p>
        </div>
        <div className="planner-launch-card">
          <div className="launch-visual" aria-hidden="true"><span>H</span><i></i><span>1</span><i></i><span>2</span><i></i><span>H</span></div>
          <LiveDataPanel trip={trip} onError={onError} onRefreshed={() => setWeatherRevision(r => r + 1)} onBlockedChange={setLiveDataBlocked} />
          <WeatherPlanner key={weatherRevision} trip={trip} onError={onError} />
          <BudgetPlanner trip={trip} onError={onError} onBlockedChange={setBudgetDirty} />
          <SpendingPanel trip={trip} onError={onError} />
          <AlgorithmPicker algorithm={algorithm} onChange={setAlgorithm} placeCount={trip.places.length} />
          {trip.places.length === 0 ? (
            <button className="button button-primary" onClick={onGoToPlaces}><Plus size={18} /> 장소 담으러 가기</button>
          ) : (
            <button className="button button-primary button-large" onClick={() => void optimize()} disabled={calculating || budgetBlocked}>
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
          <p>{dateLabel(trip.startDate)}{multiDay ? ` – ${dateLabel(trip.endDate)}` : ''} · {trip.accommodationName}에서 출발</p>
        </div>
        <div className="title-actions">
          <button className="button button-ghost" onClick={onGoToPlaces}><Plus size={16} /> 장소 변경</button>
          <button className="button button-primary" onClick={() => setTab('reoptimize')}><TimerReset size={17} /> {multiDay ? '이 날짜부터 다시 짜기' : '남은 일정 다시 짜기'}</button>
        </div>
      </div>

      <div className="itinerary-tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'route'} className={tab === 'route' ? 'active' : ''} onClick={() => setTab('route')}><RouteIcon size={17} /> 일정과 지도</button>
        <button role="tab" aria-selected={tab === 'reoptimize'} className={tab === 'reoptimize' ? 'active' : ''} onClick={() => setTab('reoptimize')}><RefreshCw size={17} /> 재최적화</button>
        <button role="tab" aria-selected={tab === 'compare'} className={tab === 'compare' ? 'active' : ''} onClick={() => setTab('compare')} disabled={!previousItinerary}><GitCompareArrows size={17} /> 버전 비교</button>
      </div>

      <details className="weather-config panel">
        <summary><CloudSun size={18} /> 날씨·현지 시간대 <span>설정 변경 후 새 버전을 계산하세요</span></summary>
        <LiveDataPanel trip={trip} onError={onError} onRefreshed={() => setWeatherRevision(r => r + 1)} onBlockedChange={setLiveDataBlocked} />
        <WeatherPlanner key={weatherRevision} trip={trip} onError={onError} compact />
      </details>
      <details className="weather-config budget-config panel">
        <summary><Wallet size={18} /> 여행 비용과 예산 설정 <span>{budgetBlocked ? '예산 설정을 확인하고 저장하세요' : '저장한 비용으로 새 버전을 계산하세요'}</span></summary>
        <BudgetPlanner trip={trip} onError={onError} onBlockedChange={setBudgetDirty} />
      </details>
      <details className="weather-config panel">
        <summary><Wallet size={18} /> 날짜별·항목별 예산과 지출 <span>예상 비용과 별도로 관리합니다</span></summary>
        <SpendingPanel trip={trip} onError={onError} />
      </details>
      {budgetBlocked && <p className="budget-dirty">예산·시간대 설정을 불러오는 중이거나 저장하지 않은 변경이 있습니다. 설정을 확인해 주세요.</p>}
      {itinerary.dataWarnings && <p className="data-warning" role="status">{itinerary.dataWarnings}</p>}
      <p className="data-caption">일정 시간대: {itinerary.timeZoneId ?? 'Asia/Seoul'} · 대중교통 소요시간은 각 날짜의 출발 시각 기준 추정이며, 방문 후 출발 시각별 배차를 재탐색하지 않습니다.</p>

      {tab === 'route' && (
        <RouteView
          trip={trip}
          itinerary={itinerary}
          itineraryPlaces={itineraryPlaces}
          algorithm={algorithm}
          setAlgorithm={setAlgorithm}
          calculating={calculating}
          budgetBlocked={budgetBlocked}
          optimize={optimize}
        />
      )}
      {tab === 'reoptimize' && (
        <ReoptimizationView
          {...props}
          algorithm={algorithm}
          onAlgorithmChange={setAlgorithm}
          onDone={() => setTab('route')}
          budgetBlocked={budgetBlocked}
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
  budgetBlocked,
  optimize,
}: {
  trip: Trip
  itinerary: Itinerary
  itineraryPlaces: Record<number, Place>
  algorithm: OptimizationAlgorithm
  setAlgorithm: (value: OptimizationAlgorithm) => void
  calculating: boolean
  budgetBlocked: boolean
  optimize: () => Promise<void>
}) {
  const completedCount = minimumCompletedCount(itinerary)
  const days = itinerary.days.length > 0 ? itinerary.days : [{
    dayNumber: 1,
    visitDate: trip.startDate,
    totalDistanceMeters: itinerary.totalDistanceMeters,
    estimatedTravelMinutes: itinerary.estimatedTravelMinutes,
    totalStayMinutes: itinerary.totalStayMinutes,
    totalWaitingMinutes: itinerary.totalWaitingMinutes,
    returnTravelDistanceMeters: itinerary.returnTravelDistanceMeters,
    returnTravelMinutes: itinerary.returnTravelMinutes,
    returnArrivalTime: itinerary.returnArrivalTime,
    returnedToAccommodation: itinerary.closedTour,
    weatherCondition: 'UNKNOWN' as const,
    precipitationProbability: 0,
  }]
  const multiDay = days.length > 1
  const visit = (item: Itinerary['items'][number]) => (
    <div className={`timeline-item ${item.status === 'COMPLETED' ? 'timeline-completed' : ''}`} key={item.itineraryItemId}>
      <div className="timeline-time"><strong>{timeLabel(item.startTime)}</strong><span>{timeLabel(item.endTime)}</span></div>
      <div className="timeline-line"><span>{item.status === 'COMPLETED' ? <Check size={15} /> : item.sequence}</span><i></i></div>
      <div className="timeline-copy">
        <div><strong>{item.placeName}</strong>{item.mustVisit && <em>꼭 가기</em>}{item.weatherScoreAdjustment !== 0 && <em className={item.weatherScoreAdjustment > 0 ? 'weather-up' : 'weather-down'}>날씨 {item.weatherScoreAdjustment > 0 ? '+' : ''}{item.weatherScoreAdjustment}</em>}</div>
        <small>{durationLabel(item.stayMinutes)} 머무름 · 이동 {durationLabel(item.estimatedTravelMinutes)}{item.waitingMinutes > 0 ? ` · ${durationLabel(item.waitingMinutes)} 대기` : ''}</small>
        <small className="visit-cost">{item.estimatedCostMinor == null ? '비용 미입력' : `예상 ${moneyLabel(item.estimatedCostMinor, itinerary.costSummary.currency)}`}</small>
      </div>
    </div>
  )

  return (
    <>
      <BudgetSummary summary={itinerary.costSummary} />
      <div className="metric-strip">
        <Metric icon={<Navigation size={18} />} label="총 이동" value={distanceLabel(itinerary.totalDistanceMeters)} note={durationLabel(itinerary.estimatedTravelMinutes)} />
        <Metric icon={<MapPin size={18} />} label="방문 장소" value={`${itinerary.items.length}곳`} note={itinerary.exclusions.length ? `${itinerary.exclusions.length}곳 제외` : '모두 포함'} />
        <Metric icon={<Clock3 size={18} />} label="머무는 시간" value={durationLabel(itinerary.totalStayMinutes)} note={itinerary.totalWaitingMinutes ? `대기 ${durationLabel(itinerary.totalWaitingMinutes)}` : '대기 없음'} />
        <Metric icon={<Gauge size={18} />} label={multiDay ? '숙소 복귀' : '숙소 도착'} value={multiDay ? `${days.length}일 완료` : timeLabel(itinerary.returnArrivalTime)} note={`복귀 이동 ${durationLabel(itinerary.returnTravelMinutes)}`} />
      </div>

      <div className="route-layout">
        <div className="timeline-panel panel">
          <div className="panel-title"><div><span className="eyebrow">YOUR TRIP</span><h2>{multiDay ? '일자별 시간표' : '시간표'}</h2></div><span>{days.length}일 · {itinerary.items.length}곳</span></div>
          <div className="timeline">
            {days.map((day) => {
              const dayItems = itinerary.items.filter((item) => item.visitDate === day.visitDate)
              return (
                <section className="timeline-day" key={day.visitDate}>
                  <div className="timeline-day-head">
                    <span><CalendarDays size={15} /> DAY {day.dayNumber}</span>
                    <strong>{dateLabel(day.visitDate)}</strong>
                    <small>{dayItems.length}곳 · 이동 {durationLabel(day.estimatedTravelMinutes)}</small>
                    <em className={`day-weather weather-${day.weatherCondition.toLowerCase()}`}><CloudSun size={13} /> {weatherLabel(day.weatherCondition)}{day.weatherCondition !== 'UNKNOWN' ? ` · 강수 ${day.precipitationProbability}%` : ''}</em>
                  </div>
                  <div className="timeline-item timeline-hotel">
                    <div className="timeline-time">{timeLabel(trip.dailyStartTime)}</div>
                    <div className="timeline-line"><span><BedDouble size={16} /></span><i></i></div>
                    <div className="timeline-copy"><strong>{trip.accommodationName}</strong><small>DAY {day.dayNumber} 출발점</small></div>
                  </div>
                  {dayItems.map((item) => {
                    const itemIndex = itinerary.items.findIndex((candidate) => candidate.itineraryItemId === item.itineraryItemId)
                    return (
                      <Fragment key={item.itineraryItemId}>
                        {itinerary.generationType === 'REOPTIMIZATION' && itemIndex === completedCount && (
                          <div className="timeline-item timeline-current">
                            <div className="timeline-time"><strong>{timeLabel(itinerary.reoptimizationStartTime)}</strong></div>
                            <div className="timeline-line"><span><LocateFixed size={15} /></span><i></i></div>
                            <div className="timeline-copy"><strong>현재 위치에서 다시 출발</strong><small>여기부터 남은 일정을 새로 계산했습니다</small></div>
                          </div>
                        )}
                        {visit(item)}
                      </Fragment>
                    )
                  })}
                  {dayItems.length === 0 && <div className="timeline-day-empty">배정된 장소 없이 숙소에서 쉬는 날입니다.</div>}
                  <div className="timeline-item timeline-hotel timeline-return">
                    <div className="timeline-time"><strong>{timeLabel(day.returnArrivalTime)}</strong></div>
                    <div className="timeline-line"><span><BedDouble size={16} /></span></div>
                    <div className="timeline-copy"><strong>{trip.accommodationName}</strong><small>숙소 복귀 · {durationLabel(day.returnTravelMinutes)}</small></div>
                  </div>
                </section>
              )
            })}
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
            <div><span className="route-source-dot"></span><span><strong>{itinerary.routeDataType === 'GOOGLE_ROUTES' ? '실제 도로 경로' : '좌표 기반 예상 경로'}</strong><small>Matrix {itinerary.routeMatrixElementCount.toLocaleString()}요소 · {itinerary.routeMatrixBuildMillis}ms</small></span></div>
            <button className="button button-ghost button-small" onClick={() => void optimize()} disabled={calculating || budgetBlocked}>{calculating ? '계산 중…' : '전체 새 버전 계산'}</button>
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
  budgetBlocked,
}: Props & { algorithm: OptimizationAlgorithm; onAlgorithmChange: (value: OptimizationAlgorithm) => void; onDone: () => void; budgetBlocked: boolean }) {
  const statusMinimum = minimumCompletedCount(itinerary!)
  const travelDates = itinerary!.days.length > 0
    ? itinerary!.days.map((day) => day.visitDate)
    : [trip.startDate]
  const lastCompletedDate = itinerary!.items[statusMinimum - 1]?.visitDate ?? trip.startDate
  const selectableDates = travelDates.filter((date) => date >= lastCompletedDate)
  const initialCurrentDate = itinerary!.reoptimizationStartDate
    ?? selectableDates[0]
    ?? trip.startDate
  const minimumForDate = (date: string) => Math.max(
    statusMinimum,
    itinerary!.items.filter((item) => item.visitDate < date).length,
  )
  const initialMinimum = minimumForDate(initialCurrentDate)
  const [currentDate, setCurrentDate] = useState(initialCurrentDate)
  const [completedCount, setCompletedCount] = useState(initialMinimum)
  const initialLastItem = itinerary!.items[initialMinimum - 1]
  const [currentTime, setCurrentTime] = useState((
    initialLastItem?.visitDate === initialCurrentDate
      ? initialLastItem.endTime
      : itinerary!.reoptimizationStartDate === initialCurrentDate
        ? itinerary!.reoptimizationStartTime ?? trip.dailyStartTime
        : trip.dailyStartTime
  ).slice(0, 5))
  const [latitude, setLatitude] = useState(String(itinerary!.reoptimizationStartLatitude ?? trip.accommodationLatitude))
  const [longitude, setLongitude] = useState(String(itinerary!.reoptimizationStartLongitude ?? trip.accommodationLongitude))
  const [reason, setReason] = useState<ItineraryChangeReason>('DELAY')
  const [detail, setDetail] = useState('')
  const [locating, setLocating] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const minimum = minimumForDate(currentDate)
  const pastItemCount = itinerary!.items.filter((item) => item.visitDate < currentDate).length
  const currentDayItems = itinerary!.items.filter((item) => item.visitDate === currentDate)
  const completedToday = itinerary!.items
    .slice(0, completedCount)
    .filter((item) => item.visitDate === currentDate).length

  const changeCurrentDate = (nextDate: string) => {
    const nextMinimum = minimumForDate(nextDate)
    const nextLast = itinerary!.items[nextMinimum - 1]
    setCurrentDate(nextDate)
    setCompletedCount(nextMinimum)
    setCurrentTime((nextLast?.visitDate === nextDate ? nextLast.endTime : trip.dailyStartTime).slice(0, 5))
  }

  const toggleCompleted = (index: number) => {
    if (itinerary!.items[index].visitDate !== currentDate) return
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
    if (budgetBlocked) return
    setSubmitting(true)
    try {
      const next = await api.reoptimize(trip.id, algorithm, {
        sourceItineraryId: itinerary!.itineraryId,
        currentDate,
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
        <div className="panel-title"><div><span className="eyebrow">REPLAN FROM HERE</span><h2>어느 날, 어디까지 다녀오셨나요?</h2></div><span>오늘 {completedToday}곳 완료</span></div>
        <p className="panel-description">지난 날짜는 그대로 잠그고, 선택한 날짜의 현재 위치부터 남은 여행만 다시 계산합니다.</p>
        <label className="field reopt-date-field">
          <span><CalendarDays size={15} /> 재계산 시작 날짜</span>
          <select value={currentDate} onChange={(event) => changeCurrentDate(event.target.value)}>
            {selectableDates.map((date) => <option key={date} value={date}>{dateLabel(date)}</option>)}
          </select>
        </label>
        <div className="completion-list">
          {itinerary!.items.map((item, index) => {
            const checked = index < completedCount
            const previousDate = item.visitDate < currentDate
            const futureDate = item.visitDate > currentDate
            const locked = index < statusMinimum || previousDate
            return (
              <button key={item.itineraryItemId} className={checked ? 'completed' : ''} onClick={() => toggleCompleted(index)} disabled={locked || futureDate}>
                <span>{checked ? <Check size={16} /> : index + 1}</span>
                <div><strong>{item.placeName}</strong><small>{dateLabel(item.visitDate)} · {timeLabel(item.startTime)}–{timeLabel(item.endTime)}</small></div>
                {previousDate && <em>지난 날짜 고정</em>}
                {!previousDate && index < statusMinimum && <em>이전 버전에서 완료</em>}
                {futureDate && <em>이후 날짜 재배치</em>}
              </button>
            )
          })}
        </div>

        <div className="reopt-form-grid">
          <label className="field"><span><Clock3 size={15} /> 현재 시각</span><input type="time" min={trip.dailyStartTime.slice(0, 5)} max={trip.dailyEndTime.slice(0, 5)} value={currentTime} onChange={(event) => setCurrentTime(event.target.value)} /></label>
          <div className="field location-field"><span><LocateFixed size={15} /> 현재 위치</span><button className="button button-ghost" onClick={locate}><LocateFixed size={16} /> {locating ? '확인 중…' : '내 위치 사용'}</button></div>
          <label className="field"><span>위도</span><input type="number" step="0.000001" min="-90" max="90" value={latitude} onChange={(event) => setLatitude(event.target.value)} /></label>
          <label className="field"><span>경도</span><input type="number" step="0.000001" min="-180" max="180" value={longitude} onChange={(event) => setLongitude(event.target.value)} /></label>
          <label className="field"><span>변경 이유</span><select value={reason} onChange={(event) => setReason(event.target.value as ItineraryChangeReason)}><option value="DELAY">일정이 지연됐어요</option><option value="PLACE_ADDED">장소를 추가했어요</option><option value="PLACE_REMOVED">장소를 삭제했어요</option><option value="WEATHER">날씨가 바뀌었어요</option><option value="BUDGET">예산이 바뀌었어요</option><option value="USER_REQUEST">순서를 바꾸고 싶어요</option><option value="OTHER">기타</option></select></label>
          <label className="field"><span>한 줄 메모</span><input value={detail} maxLength={500} onChange={(event) => setDetail(event.target.value)} placeholder="예: 점심 대기가 길었어요" /></label>
        </div>
      </div>
      <aside className="reoptimize-side">
        <div className="panel reopt-summary">
          <span className="summary-icon"><RefreshCw size={22} /></span>
          <h3>새 버전에서 달라지는 것</h3>
          <ul>
            <li><Check size={15} /> 지난 일정 {pastItemCount}곳과 오늘 완료 {completedToday}곳 보존</li>
            <li><CircleDot size={15} /> 오늘 남은 {Math.max(0, currentDayItems.length - completedToday)}곳과 이후 날짜 재배치</li>
            <li><BedDouble size={15} /> 남은 모든 날짜의 {timeLabel(trip.dailyEndTime)} 전 숙소 복귀 검증</li>
          </ul>
        </div>
        <AlgorithmPicker algorithm={algorithm} onChange={onAlgorithmChange} placeCount={trip.places.length - completedCount} />
        <button className="button button-primary button-large reopt-submit" disabled={submitting || budgetBlocked} onClick={() => void submit()}><RefreshCw size={18} /> {submitting ? '남은 일정을 계산하는 중…' : '이 지점부터 다시 계산'}</button>
        <small className="safe-note"><Check size={14} /> 현재 버전 {itinerary!.version}와 지난 날짜는 수정하지 않습니다.</small>
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
        <div className="compare-reason"><strong>{after.changeReasonDetail ?? '남은 일정 조건을 반영했습니다.'}</strong><span>{after.reoptimizationStartTime ? `${after.reoptimizationStartDate ? `${dateLabel(after.reoptimizationStartDate)} ` : ''}${timeLabel(after.reoptimizationStartTime)}부터 재계산` : '전체 일정 계산'}</span></div>
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
        <div className="version-row" key={item.itineraryItemId}><span>{item.sequence}</span><div><strong>{item.placeName}</strong><small>{dateLabel(item.visitDate)} · {timeLabel(item.startTime)}–{timeLabel(item.endTime)}</small></div>{item.status === 'COMPLETED' && <Check size={15} />}</div>
      ))}
      <div className="version-return"><BedDouble size={16} /> 숙소 {timeLabel(itinerary.returnArrivalTime)} 도착</div>
    </div>
  )
}

function exclusionLabel(reason: string): string {
  return { CLOSED: '휴무일', TIME_WINDOW: '방문 시간 충돌', DAILY_LIMIT: '하루 시간 부족', BUDGET: '예산 부족' }[reason] ?? reason
}
