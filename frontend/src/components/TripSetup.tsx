import { useState, type FormEvent } from 'react'
import {
  ArrowRight,
  CalendarDays,
  Clock3,
  Footprints,
  Hotel,
  LocateFixed,
  Route,
  Sparkles,
} from 'lucide-react'
import { api } from '../api/client'
import type { CreateTripInput, TransportMode, Trip, TripPace, User } from '../types'

interface Props {
  user: User | null
  trip?: Trip
  embedded?: boolean
  onReady?: (user: User, trip: Trip) => void
  onUserCreated?: (user: User) => void
  onUpdated?: (trip: Trip) => void
  onError: (error: unknown) => void
}

function tomorrow(): string {
  const date = new Date()
  date.setDate(date.getDate() + 1)
  return date.toISOString().slice(0, 10)
}

function addDays(dateValue: string, days: number): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dateValue)) return ''
  const [year, month, day] = dateValue.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day + days))
  return date.toISOString().slice(0, 10)
}

export function TripSetup({
  user,
  trip,
  embedded = false,
  onReady,
  onUserCreated,
  onUpdated,
  onError,
}: Props) {
  const [nickname, setNickname] = useState(user?.nickname ?? '')
  const [name, setName] = useState(trip?.name ?? '')
  const [startDate, setStartDate] = useState(trip?.startDate ?? tomorrow())
  const [endDate, setEndDate] = useState(trip?.endDate ?? trip?.startDate ?? tomorrow())
  const [dailyStartTime, setDailyStartTime] = useState((trip?.dailyStartTime ?? '09:00').slice(0, 5))
  const [dailyEndTime, setDailyEndTime] = useState((trip?.dailyEndTime ?? '20:00').slice(0, 5))
  const [accommodationName, setAccommodationName] = useState(trip?.accommodationName ?? '')
  const [latitude, setLatitude] = useState(String(trip?.accommodationLatitude ?? '37.5665'))
  const [longitude, setLongitude] = useState(String(trip?.accommodationLongitude ?? '126.9780'))
  const [transportMode, setTransportMode] = useState<TransportMode>(trip?.transportMode ?? 'WALKING')
  const [pace, setPace] = useState<TripPace>(trip?.pace ?? 'STANDARD')
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    try {
      let activeUser = user
      if (!activeUser) {
        activeUser = await api.createUser(nickname)
        onUserCreated?.(activeUser)
      }
      const input: CreateTripInput = {
        userId: activeUser.id,
        name,
        startDate,
        endDate,
        dailyStartTime,
        dailyEndTime,
        accommodationName,
        accommodationLatitude: Number(latitude),
        accommodationLongitude: Number(longitude),
        transportMode,
        pace,
      }
      if (trip) {
        const { userId: _userId, ...updateInput } = input
        void _userId
        onUpdated?.(await api.updateTrip(trip.id, updateInput))
      } else {
        onReady?.(activeUser, await api.createTrip(input))
      }
    } catch (error) {
      onError(error)
    } finally {
      setSubmitting(false)
    }
  }

  const form = (
    <form className="trip-form" onSubmit={handleSubmit}>
      {!user && (
        <label className="field field-wide">
          <span>여행자 이름</span>
          <input value={nickname} onChange={(event) => setNickname(event.target.value)} maxLength={50} required placeholder="어떻게 불러드릴까요?" />
        </label>
      )}
      <label className="field field-wide">
        <span>여행 이름</span>
        <input value={name} onChange={(event) => setName(event.target.value)} maxLength={100} required placeholder="예: 서울의 오래된 골목을 걷는 날" />
      </label>
      <label className="field">
        <span><CalendarDays size={15} /> 여행 시작일</span>
        <input
          type="date"
          value={startDate}
          onChange={(event) => {
            const nextStart = event.target.value
            setStartDate(nextStart)
            if (endDate < nextStart) setEndDate(nextStart)
            else if (endDate > addDays(nextStart, 13)) setEndDate(addDays(nextStart, 13))
          }}
          required
        />
      </label>
      <label className="field">
        <span><CalendarDays size={15} /> 여행 종료일</span>
        <input type="date" min={startDate} max={addDays(startDate, 13)} value={endDate} onChange={(event) => setEndDate(event.target.value)} required />
        <small>최대 14일까지 계획할 수 있어요.</small>
      </label>
      <div className="field time-range-field">
        <span><Clock3 size={15} /> 하루 시간</span>
        <div className="inline-inputs">
          <input aria-label="하루 시작 시간" type="time" value={dailyStartTime} onChange={(event) => setDailyStartTime(event.target.value)} required />
          <span>—</span>
          <input aria-label="하루 종료 시간" type="time" value={dailyEndTime} onChange={(event) => setDailyEndTime(event.target.value)} required />
        </div>
      </div>
      <label className="field field-wide">
        <span><Hotel size={15} /> 숙소 이름</span>
        <input value={accommodationName} onChange={(event) => setAccommodationName(event.target.value)} required maxLength={100} placeholder="출발하고 돌아올 숙소" />
      </label>
      <label className="field">
        <span><LocateFixed size={15} /> 숙소 위도</span>
        <input type="number" step="0.000001" min="-90" max="90" value={latitude} onChange={(event) => setLatitude(event.target.value)} required />
      </label>
      <label className="field">
        <span><LocateFixed size={15} /> 숙소 경도</span>
        <input type="number" step="0.000001" min="-180" max="180" value={longitude} onChange={(event) => setLongitude(event.target.value)} required />
      </label>

      <fieldset className="choice-field field-wide">
        <legend>어떻게 이동할까요?</legend>
        <div className="choice-grid choice-grid-three">
          {([
            ['WALKING', '도보', '천천히 골목까지'],
            ['PUBLIC_TRANSIT', '대중교통', '도시를 넓게'],
            ['DRIVING', '자동차', '멀리 편안하게'],
          ] as const).map(([value, label, description]) => (
            <label key={value} className={transportMode === value ? 'selected' : ''}>
              <input type="radio" name="transport" value={value} checked={transportMode === value} onChange={() => setTransportMode(value)} />
              <Footprints size={18} />
              <strong>{label}</strong>
              <small>{description}</small>
            </label>
          ))}
        </div>
      </fieldset>

      <fieldset className="choice-field field-wide">
        <legend>여행의 호흡은요?</legend>
        <div className="choice-grid choice-grid-three">
          {([
            ['ACTIVE', '알차게', '더 많은 장소'],
            ['STANDARD', '균형 있게', '이동과 휴식 사이'],
            ['RELAXED', '여유롭게', '한 곳을 깊게'],
          ] as const).map(([value, label, description]) => (
            <label key={value} className={pace === value ? 'selected' : ''}>
              <input type="radio" name="pace" value={value} checked={pace === value} onChange={() => setPace(value)} />
              <Sparkles size={18} />
              <strong>{label}</strong>
              <small>{description}</small>
            </label>
          ))}
        </div>
      </fieldset>

      <button className="button button-primary submit-trip" disabled={submitting}>
        {submitting ? '저장하는 중…' : trip ? '여행 설정 저장' : '장소를 담으러 가기'}
        {!submitting && <ArrowRight size={18} />}
      </button>
    </form>
  )

  if (embedded) {
    return (
      <section className="content-section settings-section">
        <div className="section-heading">
          <span className="eyebrow">TRIP SETTINGS</span>
          <h1>여행의 기준을 조정하세요</h1>
          <p>여행 기간, 숙소나 하루 시간이 바뀌면 다음 최적화부터 새 조건이 적용됩니다.</p>
        </div>
        <div className="panel settings-panel">{form}</div>
      </section>
    )
  }

  return (
    <main className="onboarding-shell">
      <section className="onboarding-story">
        <div className="wordmark wordmark-light"><span className="brand-mark"><Route size={21} /></span>RoutePlan</div>
        <div className="story-copy">
          <span className="eyebrow eyebrow-light">CONSTRAINT-AWARE TRAVEL</span>
          <h1>좋은 여행은<br />순서가 아니라<br /><em>호흡</em>에서 시작됩니다.</h1>
          <p>가고 싶은 곳과 여행의 조건을 알려주세요. 이동과 기다림까지 계산해, 매일 숙소로 돌아오는 흐름을 만듭니다.</p>
        </div>
        <div className="route-sketch" aria-hidden="true">
          <span className="route-stop stop-a">숙소</span><i></i><span className="route-stop stop-b">첫 장면</span><i></i><span className="route-stop stop-c">오후의 쉼</span>
        </div>
      </section>
      <section className="onboarding-form-wrap">
        <div className="onboarding-form-head">
          <span>01 / 여행 만들기</span>
          <h2>어떤 여행을 떠나볼까요?</h2>
          <p>하루부터 14일까지, 같은 숙소를 기준으로 일자별 일정을 만들어요.</p>
        </div>
        {form}
      </section>
    </main>
  )
}
