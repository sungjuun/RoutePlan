import { useMemo, useState, type FormEvent } from 'react'
import {
  ArrowRight,
  Clock3,
  ExternalLink,
  LocateFixed,
  MapPin,
  PencilLine,
  Plus,
  Search,
  SlidersHorizontal,
  Sparkles,
  Trash2,
} from 'lucide-react'
import { ApiError, api } from '../api/client'
import { categoryLabel, durationLabel, environmentLabel } from '../lib/format'
import type { PlaceEnvironment, PlaceSearchResult, Trip, TripPlace, TripPlaceConstraints } from '../types'
import { MapPanel } from './MapPanel'
import { advanced } from '../api/advanced'

interface Props {
  trip: Trip
  hasItinerary: boolean
  onTripChanged: (trip: Trip, message?: string) => void
  onGoToItinerary: () => void
  onError: (error: unknown) => void
}

const defaultConstraints: TripPlaceConstraints = {
  priority: 70,
  mustVisit: false,
  preferredStartTime: null,
  preferredEndTime: null,
  minimumStayMinutes: null,
  maximumStayMinutes: null,
}

export function PlaceWorkspace({ trip, hasItinerary, onTripChanged, onGoToItinerary, onError }: Props) {
  const [mode, setMode] = useState<'search' | 'manual'>('search')
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<PlaceSearchResult[]>([])
  const [searching, setSearching] = useState(false)
  const [providerDisabled, setProviderDisabled] = useState(false)
  const [addingId, setAddingId] = useState<string | null>(null)

  const selectedIds = useMemo(() => new Set(trip.places.map((place) => place.placeId)), [trip.places])

  const handleSearch = async (event: FormEvent) => {
    event.preventDefault()
    if (!query.trim()) return
    setSearching(true)
    setProviderDisabled(false)
    try {
      setResults(await api.searchPlaces({
        query: query.trim(),
        latitude: Number(trip.accommodationLatitude),
        longitude: Number(trip.accommodationLongitude),
      }))
    } catch (error) {
      if (error instanceof ApiError && error.body.code === 'EXTERNAL_PROVIDER_NOT_CONFIGURED') {
        setProviderDisabled(true)
      } else {
        onError(error)
      }
    } finally {
      setSearching(false)
    }
  }

  const handleExternalAdd = async (result: PlaceSearchResult) => {
    setAddingId(result.externalPlaceId)
    try {
      const place = await api.importPlace(result)
      const nextTrip = await api.addTripPlace(trip.id, place.id, defaultConstraints)
      onTripChanged(nextTrip, `${place.name}을 여행에 담았습니다.`)
    } catch (error) {
      onError(error)
    } finally {
      setAddingId(null)
    }
  }

  return (
    <section className="content-section place-section">
      <div className="section-heading section-heading-row">
        <div>
          <span className="eyebrow">BUILD YOUR DAY</span>
          <h1>오늘의 장면을 골라보세요</h1>
          <p>중요도와 머무를 시간을 함께 정하면, 실제 가능한 하루를 계산합니다.</p>
        </div>
        <button className="button button-primary" disabled={trip.places.length === 0} onClick={onGoToItinerary}>
          {hasItinerary ? '일정 다시 보기' : '일정 만들기'} <ArrowRight size={18} />
        </button>
      </div>

      <div className="place-layout">
        <div className="place-column">
          <div className="panel add-place-panel">
            <div className="segmented-control" role="tablist" aria-label="장소 추가 방식">
              <button role="tab" aria-selected={mode === 'search'} className={mode === 'search' ? 'active' : ''} onClick={() => setMode('search')}><Search size={16} /> 장소 검색</button>
              <button role="tab" aria-selected={mode === 'manual'} className={mode === 'manual' ? 'active' : ''} onClick={() => setMode('manual')}><LocateFixed size={16} /> 좌표로 추가</button>
            </div>
            {mode === 'search' ? (
              <>
                <form className="place-search" onSubmit={handleSearch}>
                  <Search size={19} />
                  <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="카페, 미술관, 장소 이름을 검색하세요" aria-label="장소 검색어" />
                  <button className="button button-dark" disabled={searching}>{searching ? '검색 중…' : '검색'}</button>
                </form>
                {providerDisabled && (
                  <div className="inline-notice">
                    <Sparkles size={19} />
                    <div><strong>외부 장소 검색이 꺼져 있습니다.</strong><span>기본 로컬 환경에서는 ‘좌표로 추가’를 이용하거나 Google Place Provider를 설정하세요.</span></div>
                    <button onClick={() => setMode('manual')}>좌표로 추가</button>
                  </div>
                )}
                {results.length > 0 && (
                  <div className="search-results">
                    <small className="google-attribution">Google Maps 제공 · <a href="/data-sources.html" target="_blank" rel="noreferrer">이용 안내</a></small>
                    {results.map((result) => (
                      <article key={result.externalPlaceId}>
                        <span className="place-result-icon"><MapPin size={18} /></span>
                        <div><strong>{result.name}</strong><span>{result.formattedAddress}</span><small>{categoryLabel(result.primaryType)}</small></div>
                        <button className="icon-button icon-button-add" onClick={() => void handleExternalAdd(result)} disabled={addingId === result.externalPlaceId} aria-label={`${result.name} 추가`}><Plus size={19} /></button>
                      </article>
                    ))}
                  </div>
                )}
              </>
            ) : (
              <ManualPlaceForm trip={trip} onTripChanged={onTripChanged} onError={onError} />
            )}
          </div>

          <div className="selected-heading">
            <div><h2>담은 장소</h2><span>{trip.places.length} / 50</span></div>
            <p>카드를 눌러 방문 조건을 세밀하게 바꿀 수 있어요.</p>
          </div>
          {trip.places.length === 0 ? (
            <div className="empty-card">
              <span><MapPin size={25} /></span>
              <h3>아직 담은 장소가 없어요</h3>
              <p>가장 가고 싶은 곳부터 하나씩 추가해 보세요.</p>
            </div>
          ) : (
            <div className="place-card-list">
              {trip.places.map((place, index) => (
                <SelectedPlaceCard
                  key={place.placeId}
                  place={place}
                  index={index}
                  tripId={trip.id}
                  onTripChanged={onTripChanged}
                  onError={onError}
                />
              ))}
            </div>
          )}
          {selectedIds.size > 0 && <small className="selection-footnote"><ExternalLink size={13} /> 장소 순서는 최적화 후 결정됩니다.</small>}
        </div>
        <aside className="place-map-column">
          <MapPanel trip={trip} compact />
          <div className="map-side-note">
            <span><SlidersHorizontal size={17} /></span>
            <div><strong>조건이 곧 여행의 취향입니다</strong><p>꼭 가야 할 장소는 먼저 지키고, 남는 시간에는 우선순위가 높은 장소를 배치합니다.</p></div>
          </div>
        </aside>
      </div>
    </section>
  )
}

function ManualPlaceForm({ trip, onTripChanged, onError }: Pick<Props, 'trip' | 'onTripChanged' | 'onError'>) {
  const [name, setName] = useState('')
  const [category, setCategory] = useState('')
  const [latitude, setLatitude] = useState(String(trip.accommodationLatitude))
  const [longitude, setLongitude] = useState(String(trip.accommodationLongitude))
  const [stayMinutes, setStayMinutes] = useState('60')
  const [environment, setEnvironment] = useState<PlaceEnvironment>('MIXED')
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    try {
      const place = await api.createPlace({
        name,
        latitude: Number(latitude),
        longitude: Number(longitude),
        category: category.trim() || null,
        averageStayMinutes: Number(stayMinutes),
        environment,
      })
      const nextTrip = await api.addTripPlace(trip.id, place.id, defaultConstraints)
      onTripChanged(nextTrip, `${place.name}을 여행에 담았습니다.`)
      setName('')
    } catch (error) {
      onError(error)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="manual-place-form" onSubmit={handleSubmit}>
      <label className="field field-wide"><span>장소 이름</span><input value={name} onChange={(event) => setName(event.target.value)} required maxLength={150} placeholder="예: 북촌 작은 책방" /></label>
      <label className="field"><span>카테고리</span><input value={category} onChange={(event) => setCategory(event.target.value)} maxLength={50} placeholder="카페, 미술관…" /></label>
      <label className="field"><span><Clock3 size={14} /> 평균 체류시간</span><div className="input-suffix"><input type="number" min="1" max="1440" value={stayMinutes} onChange={(event) => setStayMinutes(event.target.value)} required /><span>분</span></div></label>
      <label className="field"><span>공간 유형</span><select aria-label="공간 유형" value={environment} onChange={(event) => setEnvironment(event.target.value as PlaceEnvironment)}><option value="INDOOR">실내</option><option value="OUTDOOR">실외</option><option value="MIXED">실내·실외 혼합</option></select></label>
      <label className="field"><span>위도</span><input type="number" step="0.000001" min="-90" max="90" value={latitude} onChange={(event) => setLatitude(event.target.value)} required /></label>
      <label className="field"><span>경도</span><input type="number" step="0.000001" min="-180" max="180" value={longitude} onChange={(event) => setLongitude(event.target.value)} required /></label>
      <button className="button button-primary field-wide" disabled={submitting}><Plus size={17} /> {submitting ? '추가하는 중…' : '이 장소 담기'}</button>
    </form>
  )
}

function SelectedPlaceCard({
  place,
  index,
  tripId,
  onTripChanged,
  onError,
}: {
  place: TripPlace
  index: number
  tripId: number
  onTripChanged: Props['onTripChanged']
  onError: Props['onError']
}) {
  const [expanded, setExpanded] = useState(false)
  const [priority, setPriority] = useState(place.priority)
  const [mustVisit, setMustVisit] = useState(place.mustVisit)
  const [preferredStartTime, setPreferredStartTime] = useState(place.preferredStartTime?.slice(0, 5) ?? '')
  const [preferredEndTime, setPreferredEndTime] = useState(place.preferredEndTime?.slice(0, 5) ?? '')
  const [minimumStayMinutes, setMinimumStayMinutes] = useState(place.minimumStayMinutes?.toString() ?? '')
  const [maximumStayMinutes, setMaximumStayMinutes] = useState(place.maximumStayMinutes?.toString() ?? '')
  const [saving, setSaving] = useState(false)
  const [hoursBusy, setHoursBusy] = useState(false)
  const [hours, setHours] = useState<{ weekdayDescriptions: string[]; warning: string } | null>(null)
  const loadHours = async () => {
    setHoursBusy(true)
    try { setHours(await advanced.hours(tripId, place.placeId)) }
    catch (error) { onError(error) } finally { setHoursBusy(false) }
  }

  const preset = mustVisit ? 'must' : priority >= 60 ? 'prefer' : 'optional'
  const applyPreset = (value: 'must' | 'prefer' | 'optional') => {
    setMustVisit(value === 'must')
    setPriority(value === 'must' ? 100 : value === 'prefer' ? 70 : 30)
  }

  const constraints = (): TripPlaceConstraints => ({
    priority,
    mustVisit,
    preferredStartTime: preferredStartTime || null,
    preferredEndTime: preferredEndTime || null,
    minimumStayMinutes: minimumStayMinutes ? Number(minimumStayMinutes) : null,
    maximumStayMinutes: maximumStayMinutes ? Number(maximumStayMinutes) : null,
  })

  const save = async () => {
    setSaving(true)
    try {
      onTripChanged(await api.updateTripPlace(tripId, place.placeId, constraints()), `${place.name}의 방문 조건을 저장했습니다.`)
      setExpanded(false)
    } catch (error) {
      onError(error)
    } finally {
      setSaving(false)
    }
  }

  const remove = async () => {
    if (!window.confirm(`${place.name}을 여행에서 삭제할까요? 이전 일정 버전은 그대로 보존됩니다.`)) return
    try {
      await api.removeTripPlace(tripId, place.placeId)
      onTripChanged(await api.getTrip(tripId), `${place.name}을 여행에서 뺐습니다.`)
    } catch (error) {
      onError(error)
    }
  }

  return (
    <article className={`selected-place-card ${expanded ? 'expanded' : ''}`}>
      <button className="place-card-summary" onClick={() => setExpanded((value) => !value)} aria-expanded={expanded}>
        <span className="place-index">{String(index + 1).padStart(2, '0')}</span>
        <div className="place-card-copy"><strong>{place.name}</strong><span>{categoryLabel(place.category)} · {environmentLabel(place.environment)} · 평균 {durationLabel(place.averageStayMinutes)}</span></div>
        <span className={`priority-badge priority-${preset}`}>{preset === 'must' ? '꼭 가기' : preset === 'prefer' ? '가급적 가기' : '시간 남으면'}</span>
        <PencilLine size={17} />
      </button>
      {expanded && (
        <div className="place-constraints">
          {place.externalPlaceId && <section className="place-hours field-wide"><button className="button button-ghost button-small" disabled={hoursBusy} onClick={() => void loadHours()}>{hoursBusy ? '조회 중…' : 'Google 영업시간 가져오기'}</button><p>일정 계산 시 자동 반영됩니다. 수동 설정된 영업시간이 있으면 우선 사용합니다.</p>{hours && <><ul>{hours.weekdayDescriptions.map(line => <li key={line}>{line}</li>)}</ul><p role="status">{hours.warning}</p><small className="google-attribution">Google Maps 제공 · 정규 주간 영업시간</small></>}</section>}
          <div className="visit-priority-select">
            {([
              ['must', '꼭 가기', '일정에 반드시 포함'],
              ['prefer', '가급적 가기', '높은 우선순위'],
              ['optional', '시간 남으면', '여유가 있을 때'],
            ] as const).map(([value, label, description]) => (
              <button key={value} className={preset === value ? 'selected' : ''} onClick={() => applyPreset(value)}><strong>{label}</strong><span>{description}</span></button>
            ))}
          </div>
          <label className="field field-wide priority-slider"><span>세부 우선순위 <strong>{priority}</strong></span><input type="range" min="1" max="100" value={priority} onChange={(event) => setPriority(Number(event.target.value))} /></label>
          <label className="field"><span>선호 시작</span><input type="time" value={preferredStartTime} onChange={(event) => setPreferredStartTime(event.target.value)} /></label>
          <label className="field"><span>선호 종료</span><input type="time" value={preferredEndTime} onChange={(event) => setPreferredEndTime(event.target.value)} /></label>
          <label className="field"><span>최소 체류</span><div className="input-suffix"><input type="number" min="1" max="1440" value={minimumStayMinutes} onChange={(event) => setMinimumStayMinutes(event.target.value)} /><span>분</span></div></label>
          <label className="field"><span>최대 체류</span><div className="input-suffix"><input type="number" min="1" max="1440" value={maximumStayMinutes} onChange={(event) => setMaximumStayMinutes(event.target.value)} /><span>분</span></div></label>
          <div className="constraint-actions field-wide">
            <button className="button button-danger-ghost" onClick={() => void remove()}><Trash2 size={16} /> 장소 삭제</button>
            <button className="button button-primary" onClick={() => void save()} disabled={saving}>{saving ? '저장 중…' : '조건 저장'}</button>
          </div>
        </div>
      )}
    </article>
  )
}
