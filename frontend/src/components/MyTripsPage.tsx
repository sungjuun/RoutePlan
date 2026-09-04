import { useEffect, useMemo, useState } from 'react'
import { ArrowRight, BedDouble, CalendarDays, MapPinned, Plus } from 'lucide-react'
import { api } from '../api/client'
import { dateLabel, paceLabel, transportLabel } from '../lib/format'
import type { TripSummary } from '../types'
import { AsyncState } from './AsyncState'

interface Props {
  onOpenTrip: (tripId: number) => void
  onNewTrip: () => void
  onError: (error: unknown) => void
}

function localToday(): string {
  const date = new Date()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

export function MyTripsPage({ onOpenTrip, onNewTrip, onError }: Props) {
  const [trips, setTrips] = useState<TripSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const today = localToday()
  const optimizedCount = useMemo(
    () => trips.filter((trip) => trip.status === 'OPTIMIZED').length,
    [trips],
  )
  const upcomingCount = useMemo(
    () => trips.filter((trip) => trip.endDate >= today).length,
    [today, trips],
  )

  useEffect(() => {
    let cancelled = false
    api.getTrips()
      .then((result) => { if (!cancelled) setTrips(result) })
      .catch((error) => {
        if (!cancelled) {
          setLoadFailed(true)
          onError(error)
        }
      })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [onError, reloadKey])

  return (
    <main className="account-page my-trips-page">
      <section className="account-hero">
        <div><span className="eyebrow eyebrow-light">MY JOURNEYS</span><h1>내 여행</h1><p>직접 만든 여행과 커뮤니티에서 가져온 루트를 한곳에서 이어가세요.</p></div>
        <button className="button button-light" onClick={onNewTrip}><Plus size={17} /> 새 여행 만들기</button>
      </section>

      <section className="account-content">
        <div className="journey-summary-row">
          <div><span>전체 여행</span><strong>{loading || loadFailed ? '—' : trips.length}</strong></div>
          <div><span>일정 계산 완료</span><strong>{loading || loadFailed ? '—' : optimizedCount}</strong></div>
          <div><span>예정·진행 여행</span><strong>{loading || loadFailed ? '—' : upcomingCount}</strong></div>
        </div>

        <div className="account-section-head"><div><span className="eyebrow">SAVED TRIPS</span><h2>저장된 여행</h2></div><span>최근 수정순</span></div>
        {loading ? (
          <AsyncState kind="loading" title="여행 목록을 불러오는 중입니다" className="account-empty" />
        ) : loadFailed ? (
          <AsyncState kind="error" title="여행 목록을 불러오지 못했습니다" message="연결 상태를 확인한 뒤 다시 시도해 주세요." actionLabel="다시 시도" onAction={() => { setLoading(true); setLoadFailed(false); setReloadKey((key) => key + 1) }} className="account-empty" />
        ) : trips.length === 0 ? (
          <AsyncState kind="empty" title="아직 저장된 여행이 없습니다" message="새 여행을 만들거나 커뮤니티 루트를 내 일정으로 가져와 보세요." actionLabel="첫 여행 만들기" onAction={onNewTrip} className="account-empty" />
        ) : (
          <div className="my-trip-grid">
            {trips.map((trip, index) => (
              <button key={trip.id} className="my-trip-card panel" onClick={() => onOpenTrip(trip.id)}>
                <div className={`my-trip-cover trip-cover-${(index % 4) + 1}`}><span>{!trip.accessRole || trip.accessRole === 'OWNER' ? (trip.status === 'OPTIMIZED' ? '일정 완성' : '계획 중') : `동행 · ${trip.accessRole === 'EDITOR' ? '편집자' : '조회자'}`}</span><MapPinned size={28} /></div>
                <div className="my-trip-card-body">
                  <small>TRIP #{trip.id}</small><h3>{trip.name}</h3>
                  <p><CalendarDays size={14} /> {dateLabel(trip.startDate)}{trip.startDate !== trip.endDate ? ` – ${dateLabel(trip.endDate)}` : ''}</p>
                  <p><BedDouble size={14} /> {trip.accommodationName}</p>
                  <div><span>{trip.placeCount}곳</span><span>{transportLabel(trip.transportMode)}</span><span>{paceLabel(trip.pace)}</span></div>
                  <footer><span>수정 {new Date(trip.updatedAt).toLocaleDateString('ko-KR')}</span><strong>여행 열기 <ArrowRight size={15} /></strong></footer>
                </div>
              </button>
            ))}
          </div>
        )}
      </section>
    </main>
  )
}
