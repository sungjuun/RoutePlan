import { useEffect, useMemo, useState } from 'react'
import { ArrowDown, ArrowUp, Check, GripVertical, MoveRight, Sparkles } from 'lucide-react'
import { api } from '../api/client'
import { dateLabel, distanceLabel, durationLabel } from '../lib/format'
import type {
  Itinerary,
  ItineraryDayAssignment,
  ManualItineraryEditPreview,
  Trip,
} from '../types'

interface Props {
  trip: Trip
  itinerary: Itinerary
  onItineraryChanged: (itinerary: Itinerary, message: string) => Promise<void>
  onError: (error: unknown) => void
}

function sourceAssignments(trip: Trip, itinerary: Itinerary): ItineraryDayAssignment[] {
  const dates = itinerary.days.length > 0
    ? itinerary.days.map((day) => day.visitDate)
    : [trip.startDate]
  return dates.map((visitDate) => ({
    visitDate,
    itineraryItemIds: itinerary.items
      .filter((item) => item.visitDate === visitDate)
      .map((item) => item.itineraryItemId),
  }))
}

export function ManualScheduleEditor({ trip, itinerary, onItineraryChanged, onError }: Props) {
  const initial = useMemo(() => sourceAssignments(trip, itinerary), [trip, itinerary])
  const [assignments, setAssignments] = useState(initial)
  const [dragging, setDragging] = useState<number | null>(null)
  const [preview, setPreview] = useState<ManualItineraryEditPreview | null>(null)
  const [busy, setBusy] = useState(false)
  const itemById = useMemo(() => new Map(itinerary.items.map((item) => [item.itineraryItemId, item])), [itinerary])
  const dirty = JSON.stringify(assignments) !== JSON.stringify(initial)

  useEffect(() => {
    setAssignments(initial)
    setPreview(null)
  }, [initial])

  const update = (next: ItineraryDayAssignment[]) => {
    setAssignments(next)
    setPreview(null)
  }

  const move = (itemId: number, targetDate: string, targetIndex: number) => {
    const item = itemById.get(itemId)
    if (!item || item.status === 'COMPLETED') return
    const next = assignments.map((day) => ({
      ...day,
      itineraryItemIds: day.itineraryItemIds.filter((id) => id !== itemId),
    }))
    const target = next.find((day) => day.visitDate === targetDate)
    if (!target) return
    const completedCount = target.itineraryItemIds.filter((id) => itemById.get(id)?.status === 'COMPLETED').length
    target.itineraryItemIds.splice(Math.max(completedCount, Math.min(targetIndex, target.itineraryItemIds.length)), 0, itemId)
    update(next)
  }

  const shift = (date: string, itemId: number, direction: -1 | 1) => {
    const day = assignments.find((value) => value.visitDate === date)
    if (!day) return
    const index = day.itineraryItemIds.indexOf(itemId)
    const target = index + direction
    if (target < 0 || target >= day.itineraryItemIds.length
      || itemById.get(day.itineraryItemIds[target])?.status === 'COMPLETED') return
    const ids = [...day.itineraryItemIds]
    ;[ids[index], ids[target]] = [ids[target], ids[index]]
    update(assignments.map((value) => value.visitDate === date
      ? { ...value, itineraryItemIds: ids }
      : value))
  }

  const request = { sourceItineraryId: itinerary.itineraryId, assignments }
  const inspect = async () => {
    setBusy(true)
    try {
      setPreview(await api.previewManualItineraryEdit(trip.id, request))
    } catch (error) {
      onError(error)
    } finally {
      setBusy(false)
    }
  }
  const save = async () => {
    setBusy(true)
    try {
      await onItineraryChanged(
        await api.applyManualItineraryEdit(trip.id, request),
        '직접 옮긴 날짜만 다시 계산해 새 일정 버전으로 저장했습니다.',
      )
    } catch (error) {
      onError(error)
    } finally {
      setBusy(false)
    }
  }

  return (
    <details className="manual-schedule-editor panel">
      <summary><GripVertical size={18} /> 일정 직접 편집 <span>장소를 끌어 순서나 날짜를 바꾸세요</span></summary>
      <div className="manual-editor-help"><MoveRight size={16} /> 변경된 날짜만 영업시간·이동시간을 다시 계산하며, 완료한 방문은 움직이지 않습니다.</div>
      <div className="manual-day-grid">
        {assignments.map((day) => (
          <section
            className="manual-day-column"
            key={day.visitDate}
            onDragOver={(event) => event.preventDefault()}
            onDrop={() => {
              if (dragging != null) move(dragging, day.visitDate, day.itineraryItemIds.length)
              setDragging(null)
            }}
          >
            <header><strong>{dateLabel(day.visitDate)}</strong><small>{day.itineraryItemIds.length}곳</small></header>
            <div className="manual-day-items">
              {day.itineraryItemIds.map((itemId, index) => {
                const item = itemById.get(itemId)!
                return (
                  <article
                    key={itemId}
                    className={item.status === 'COMPLETED' ? 'manual-item completed' : 'manual-item'}
                    draggable={item.status !== 'COMPLETED'}
                    onDragStart={() => setDragging(itemId)}
                    onDragEnd={() => setDragging(null)}
                    onDragOver={(event) => event.preventDefault()}
                    onDrop={(event) => {
                      event.stopPropagation()
                      if (dragging != null && dragging !== itemId) move(dragging, day.visitDate, index)
                      setDragging(null)
                    }}
                  >
                    <GripVertical size={16} aria-hidden="true" />
                    <span><strong>{item.placeName}</strong><small>{item.status === 'COMPLETED' ? '방문 완료 · 고정' : `${durationLabel(item.stayMinutes)} 머무름`}</small></span>
                    {item.status !== 'COMPLETED' && <span className="manual-item-actions">
                      <button type="button" aria-label={`${item.placeName} 위로`} onClick={() => shift(day.visitDate, itemId, -1)}><ArrowUp size={14} /></button>
                      <button type="button" aria-label={`${item.placeName} 아래로`} onClick={() => shift(day.visitDate, itemId, 1)}><ArrowDown size={14} /></button>
                      {assignments.length > 1 && <select
                        aria-label={`${item.placeName} 날짜`}
                        value={day.visitDate}
                        onChange={(event) => move(
                          itemId,
                          event.target.value,
                          assignments.find((value) => value.visitDate === event.target.value)?.itineraryItemIds.length ?? 0,
                        )}
                      >
                        {assignments.map((option, dayIndex) => <option key={option.visitDate} value={option.visitDate}>DAY {dayIndex + 1}</option>)}
                      </select>}
                    </span>}
                  </article>
                )
              })}
              {day.itineraryItemIds.length === 0 && <p>이곳에 장소를 놓으세요.</p>}
            </div>
          </section>
        ))}
      </div>
      <div className="manual-editor-actions">
        <button type="button" className="button button-ghost" disabled={!dirty || busy} onClick={() => { setAssignments(initial); setPreview(null) }}>되돌리기</button>
        <button type="button" className="button button-primary" disabled={!dirty || busy} onClick={() => void inspect()}>{busy ? '계산 중…' : '변경 영향 계산'}</button>
      </div>
      {preview && <div className="manual-preview" role="status">
        <div><span>영향 날짜</span><strong>{preview.affectedDates.map(dateLabel).join(', ')}</strong></div>
        <div><span>총 이동시간</span><strong className={preview.travelMinutesDelta > 0 ? 'worse' : 'better'}>{signedDuration(preview.travelMinutesDelta)}</strong></div>
        <div><span>총 이동거리</span><strong className={preview.distanceMetersDelta > 0 ? 'worse' : 'better'}>{signedDistance(preview.distanceMetersDelta)}</strong></div>
        {preview.recommendation && <section className="manual-recommendation">
          <Sparkles size={18} /><div><strong>최적화 추천</strong><p>{preview.recommendation.message}</p><small>이동 {durationLabel(preview.recommendation.savingMinutes)} · {distanceLabel(preview.recommendation.savingDistanceMeters)} 절약 예상</small></div>
          <button type="button" className="button button-ghost" onClick={() => update(preview.recommendation!.assignments)}>추천 일정 적용</button>
        </section>}
        <button type="button" className="button button-primary" disabled={busy} onClick={() => void save()}><Check size={16} /> 이 순서로 새 버전 저장</button>
      </div>}
    </details>
  )
}

function signedDuration(value: number) {
  if (value === 0) return '변화 없음'
  return `${value > 0 ? '+' : '-'}${durationLabel(Math.abs(value))}`
}

function signedDistance(value: number) {
  if (value === 0) return '변화 없음'
  return `${value > 0 ? '+' : '-'}${distanceLabel(Math.abs(value))}`
}
