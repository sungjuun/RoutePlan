import { useMemo, useState, type FormEvent } from 'react'
import {
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  Clock3,
  Footprints,
  MapPin,
  RefreshCcw,
  ShieldCheck,
  Sparkles,
} from 'lucide-react'
import { api } from '../api/client'
import type {
  NaturalLanguagePlaceSettings,
  NaturalLanguagePreview,
  TransportMode,
  Trip,
  TripPace,
} from '../types'

interface Props {
  trip: Trip
  onTripChanged: (trip: Trip, message?: string) => void
  onGoToPlaces: () => void
  onGoToItinerary: () => void
  onError: (error: unknown) => void
}

const paceLabels: Record<TripPace, string> = {
  ACTIVE: '알차게',
  STANDARD: '균형 있게',
  RELAXED: '여유롭게',
}

const transportLabels: Record<TransportMode, string> = {
  WALKING: '도보',
  DRIVING: '자동차',
  PUBLIC_TRANSIT: '대중교통',
}

function time(value: string | null): string {
  return value ? value.slice(0, 5) : '지정 안 함'
}

function sampleFor(trip: Trip): string {
  const [first, second] = trip.places
  const placeRequest = first
    ? `${first.name}은 꼭 가고 싶어.${second ? ` 점심에는 ${second.name}에 가고 싶어.` : ''}`
    : '일정은 여유롭게 보내고 싶어.'
  return `오전 10시에 출발해서 저녁 7시까지 여행할래. ${placeRequest} 대중교통을 이용하고 너무 많이 걷고 싶지 않아.`
}

export function NaturalLanguageWorkspace({
  trip,
  onTripChanged,
  onGoToPlaces,
  onGoToItinerary,
  onError,
}: Props) {
  const sample = useMemo(() => sampleFor(trip), [trip])
  const [text, setText] = useState('')
  const [preview, setPreview] = useState<NaturalLanguagePreview | null>(null)
  const [interpreting, setInterpreting] = useState(false)
  const [applying, setApplying] = useState(false)

  const interpret = async (event: FormEvent) => {
    event.preventDefault()
    if (!text.trim()) return
    setInterpreting(true)
    try {
      setPreview(await api.previewNaturalLanguageConstraints(trip.id, text.trim()))
    } catch (error) {
      onError(error)
    } finally {
      setInterpreting(false)
    }
  }

  const apply = async () => {
    if (!preview?.hasChanges) return
    setApplying(true)
    try {
      const places = preview.places.map(({ after }) => {
        const { placeName: _placeName, ...settings } = after
        void _placeName
        return settings
      })
      const nextTrip = await api.applyNaturalLanguageConstraints(trip.id, {
        trip: preview.trip.after,
        places,
      })
      onTripChanged(nextTrip, '자연어에서 해석한 여행 조건을 적용했습니다.')
      setPreview(null)
    } catch (error) {
      onError(error)
    } finally {
      setApplying(false)
    }
  }

  return (
    <section className="content-section natural-language-section">
      <div className="natural-language-hero">
        <div className="section-heading">
          <span className="eyebrow">STRUCTURED TRAVEL INPUT</span>
          <h1>여행의 취향을<br /><em>말로 알려주세요</em></h1>
          <p>자연어는 계산 결과가 아니라 검증 가능한 조건으로만 바뀝니다. 적용 전 변경 내용을 직접 확인할 수 있어요.</p>
        </div>
        <div className="panel ai-boundary-card">
          <span><ShieldCheck size={21} /></span>
          <div>
            <strong>AI는 입력만 해석합니다</strong>
            <p>거리·이동시간·방문 순서·영업시간 검증과 Score 계산은 기존 최적화 엔진이 담당합니다.</p>
          </div>
        </div>
      </div>

      <div className="natural-language-layout">
        <div className="natural-language-input-column">
          <form className="panel natural-language-form" onSubmit={interpret}>
            <div className="natural-language-form-head">
              <div><Sparkles size={18} /><strong>어떤 하루를 원하시나요?</strong></div>
              <button type="button" onClick={() => setText(sample)}>예시 불러오기</button>
            </div>
            <textarea
              value={text}
              onChange={(event) => {
                setText(event.target.value)
                setPreview(null)
              }}
              maxLength={2000}
              rows={8}
              placeholder="예: 오전 10시에 시작하고, 오사카성은 꼭 가고 싶어. 점심에는 이치란 라멘에 가고 너무 많이 걷지 않는 여유로운 일정이면 좋겠어."
              aria-label="자연어 여행 조건"
              required
            />
            <div className="natural-language-form-footer">
              <small>{text.length} / 2,000</small>
              <button className="button button-primary" disabled={interpreting || !text.trim()}>
                {interpreting ? <><RefreshCcw className="spin" size={17} /> 해석하는 중…</> : <><Sparkles size={17} /> 조건 미리보기</>}
              </button>
            </div>
          </form>

          <div className="natural-language-guide">
            <div><Clock3 size={17} /><strong>시간</strong><span>“10시에 시작해서 7시까지”</span></div>
            <div><MapPin size={17} /><strong>장소</strong><span>“이곳은 꼭, 저곳은 점심에”</span></div>
            <div><Footprints size={17} /><strong>호흡</strong><span>“대중교통으로 여유롭게”</span></div>
          </div>

          {trip.places.length === 0 && (
            <div className="inline-notice natural-language-empty-places">
              <MapPin size={19} />
              <div><strong>먼저 장소를 담아주세요.</strong><span>V8은 현재 Trip에 담긴 장소의 이름만 안전하게 연결합니다.</span></div>
              <button onClick={onGoToPlaces}>장소 담기</button>
            </div>
          )}
        </div>

        <div className="natural-language-preview-column" aria-live="polite">
          {!preview ? (
            <div className="panel natural-language-empty">
              <span><Sparkles size={25} /></span>
              <h2>적용 전에 한 번 더 확인해요</h2>
              <p>해석된 시간, 여행 강도, 이동수단과 장소별 우선순위가 이곳에 표시됩니다.</p>
            </div>
          ) : (
            <PreviewPanel
              preview={preview}
              applying={applying}
              onApply={() => void apply()}
              onGoToItinerary={onGoToItinerary}
            />
          )}
        </div>
      </div>
    </section>
  )
}

function PreviewPanel({
  preview,
  applying,
  onApply,
  onGoToItinerary,
}: {
  preview: NaturalLanguagePreview
  applying: boolean
  onApply: () => void
  onGoToItinerary: () => void
}) {
  const providerLabel = preview.provider.startsWith('OPENAI:')
    ? `OpenAI · ${preview.provider.split(':')[1]}`
    : '로컬 규칙 기반'
  return (
    <div className="natural-language-preview">
      <div className="panel preview-status-card">
        <div><span><CheckCircle2 size={18} /></span><div><strong>조건 해석 완료</strong><small>{providerLabel}</small></div></div>
        <span className={preview.hasChanges ? 'change-ready' : 'no-change'}>{preview.hasChanges ? '변경 가능' : '변경 없음'}</span>
      </div>

      <div className="panel interpreted-settings">
        <h2>여행 전체 설정</h2>
        <TripSettingRow label="하루 시간" before={`${time(preview.trip.before.dailyStartTime)}–${time(preview.trip.before.dailyEndTime)}`} after={`${time(preview.trip.after.dailyStartTime)}–${time(preview.trip.after.dailyEndTime)}`} />
        <TripSettingRow label="여행 호흡" before={paceLabels[preview.trip.before.pace]} after={paceLabels[preview.trip.after.pace]} />
        <TripSettingRow label="이동수단" before={transportLabels[preview.trip.before.transportMode]} after={transportLabels[preview.trip.after.transportMode]} />
      </div>

      {preview.places.length > 0 && (
        <div className="panel interpreted-places">
          <h2>장소별 조건 <span>{preview.places.length}</span></h2>
          {preview.places.map(({ before, after, changed }) => (
            <PlaceChangeRow key={after.placeId} before={before} after={after} changed={changed} />
          ))}
        </div>
      )}

      {preview.structuredConstraints.notes.length > 0 && (
        <div className="interpretation-notes">
          {preview.structuredConstraints.notes.map((note) => <p key={note}>{note}</p>)}
        </div>
      )}

      {preview.warnings.length > 0 && (
        <div className="panel interpretation-warnings">
          <div><AlertTriangle size={18} /><strong>확인이 필요한 내용</strong></div>
          <ul>{preview.warnings.map((warning) => <li key={warning}>{warning}</li>)}</ul>
        </div>
      )}

      <button className="button button-primary natural-language-apply" onClick={onApply} disabled={!preview.hasChanges || applying}>
        {applying ? '적용하는 중…' : '검토한 조건 적용하기'} {!applying && <ArrowRight size={17} />}
      </button>
      {!preview.hasChanges && <button className="button button-ghost natural-language-next" onClick={onGoToItinerary}>현재 조건으로 일정 보기</button>}
      <small className="natural-language-safe-note"><ShieldCheck size={13} /> 적용 후 일정 최적화를 실행해야 방문 순서가 다시 계산됩니다.</small>
    </div>
  )
}

function TripSettingRow({ label, before, after }: { label: string; before: string; after: string }) {
  const changed = before !== after
  return (
    <div className={`interpreted-setting-row ${changed ? 'changed' : ''}`}>
      <strong>{label}</strong><span>{before}</span><ArrowRight size={14} /><em>{after}</em>
    </div>
  )
}

function PlaceChangeRow({
  before,
  after,
  changed,
}: {
  before: NaturalLanguagePlaceSettings
  after: NaturalLanguagePlaceSettings
  changed: boolean
}) {
  const visitLabel = after.mustVisit ? '꼭 가기' : after.priority >= 60 ? '가급적 가기' : '시간 남으면'
  const timeLabel = after.preferredStartTime || after.preferredEndTime
    ? `${time(after.preferredStartTime)}–${time(after.preferredEndTime)}`
    : '시간 자유'
  return (
    <div className={`interpreted-place-row ${changed ? 'changed' : ''}`}>
      <span><MapPin size={15} /></span>
      <div><strong>{after.placeName}</strong><small>우선순위 {before.priority} → {after.priority}</small></div>
      <div><em>{visitLabel}</em><small>{timeLabel}</small></div>
    </div>
  )
}
