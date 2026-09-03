import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ArrowRight, Check, Heart, Link2, LoaderCircle, MapPin, Plus, Search, Sparkles, Trash2 } from 'lucide-react'
import { api } from '../api/client'
import type { ContentImport, PlaceSearchResult, Wishlist, WishlistSummary } from '../types'
import { AsyncState } from './AsyncState'

interface Props {
  initialUrl?: string
  onCreateTrip: (wishlistId: number, wishlistPlaceIds: number[]) => void
  onError: (error: unknown) => void
}

const terminal = new Set(['COMPLETED', 'AWAITING_INPUT', 'FAILED'])

export function DiscoveryPage({ initialUrl = '', onCreateTrip, onError }: Props) {
  const [wishlists, setWishlists] = useState<WishlistSummary[]>([])
  const [wishlist, setWishlist] = useState<Wishlist | null>(null)
  const [wishlistName, setWishlistName] = useState('')
  const [url, setUrl] = useState(initialUrl)
  const [inputText, setInputText] = useState('')
  const [job, setJob] = useState<ContentImport | null>(null)
  const [selectedCandidates, setSelectedCandidates] = useState<Record<number, number>>({})
  const [selectedPlaces, setSelectedPlaces] = useState<number[]>([])
  const [placeQuery, setPlaceQuery] = useState('')
  const [placeResults, setPlaceResults] = useState<PlaceSearchResult[]>([])
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)

  const loadWishlists = async (preferredId?: number) => {
    const summaries = await api.getWishlists()
    setWishlists(summaries)
    const id = preferredId ?? wishlist?.id ?? summaries[0]?.id
    if (id) {
      const detail = await api.getWishlist(id)
      setWishlist(detail)
      setSelectedPlaces(detail.places.map(place => place.id))
    } else {
      setWishlist(null)
      setSelectedPlaces([])
    }
  }

  useEffect(() => {
    loadWishlists().catch(onError).finally(() => setLoading(false))
    // Initial load is intentionally tied only to the authenticated page mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => { if (initialUrl) setUrl(initialUrl) }, [initialUrl])

  useEffect(() => {
    if (!job || terminal.has(job.status)) {
      if (job) setBusy(false)
      return
    }
    const timer = window.setTimeout(() => {
      api.getContentImport(job.id).then(setJob).catch((error) => { setBusy(false); onError(error) })
    }, 700)
    return () => window.clearTimeout(timer)
  }, [job, onError])

  useEffect(() => {
    if (job?.status !== 'COMPLETED') return
    const firstMatches: Record<number, number> = {}
    for (const candidate of job.candidates) {
      if (candidate.matched && firstMatches[candidate.mentionOrder] == null) {
        firstMatches[candidate.mentionOrder] = candidate.id
      }
    }
    setSelectedCandidates(firstMatches)
  }, [job])

  const candidateGroups = useMemo(() => {
    const groups = new Map<number, ContentImport['candidates']>()
    for (const candidate of job?.candidates ?? []) {
      groups.set(candidate.mentionOrder, [...(groups.get(candidate.mentionOrder) ?? []), candidate])
    }
    return [...groups.entries()]
  }, [job])

  const createWishlist = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    try {
      const created = await api.createWishlist({ name: wishlistName })
      setWishlistName('')
      await loadWishlists(created.id)
    } catch (error) { onError(error) } finally { setBusy(false) }
  }

  const chooseWishlist = async (id: number) => {
    setBusy(true)
    try {
      const detail = await api.getWishlist(id)
      setWishlist(detail)
      setSelectedPlaces(detail.places.map(place => place.id))
    } catch (error) { onError(error) } finally { setBusy(false) }
  }

  const startImport = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    try {
      setJob(await api.startContentImport({
        url,
        inputText: inputText.trim() || undefined,
        wishlistId: wishlist?.id,
      }))
    } catch (error) { setBusy(false); onError(error) }
  }

  const retryImport = async () => {
    if (!job) return
    setBusy(true)
    try { setJob(await api.retryContentImport(job.id, inputText)) }
    catch (error) { setBusy(false); onError(error) }
  }

  const saveCandidates = async () => {
    if (!job || !wishlist) return
    const ids = Object.values(selectedCandidates)
    if (ids.length === 0) return
    setBusy(true)
    try {
      const updated = await api.saveContentImport(job.id, wishlist.id, ids)
      setWishlist(updated)
      setSelectedPlaces(updated.places.map(place => place.id))
      await loadWishlists(updated.id)
    } catch (error) { onError(error) } finally { setBusy(false) }
  }

  const searchPlace = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    try { setPlaceResults(await api.searchPlaces({ query: placeQuery })) }
    catch (error) { onError(error) } finally { setBusy(false) }
  }

  const savePlace = async (result: PlaceSearchResult) => {
    if (!wishlist) return
    setBusy(true)
    try {
      const place = await api.importPlace(result)
      const updated = await api.addWishlistPlace(wishlist.id, { placeId: place.id, sourceType: 'MANUAL' })
      setWishlist(updated)
      setSelectedPlaces(updated.places.map(item => item.id))
      await loadWishlists(updated.id)
    } catch (error) { onError(error) } finally { setBusy(false) }
  }

  const removePlace = async (wishlistPlaceId: number) => {
    if (!wishlist) return
    setBusy(true)
    try {
      await api.removeWishlistPlace(wishlist.id, wishlistPlaceId)
      await loadWishlists(wishlist.id)
    } catch (error) { onError(error) } finally { setBusy(false) }
  }

  if (loading) return <main className="discovery-page"><AsyncState kind="loading" title="가고 싶은 곳을 불러오는 중입니다" /></main>

  return (
    <main className="discovery-page">
      <section className="discovery-heading">
        <span className="eyebrow">SAVE FIRST, PLAN LATER</span>
        <h1>마음에 든 여행 콘텐츠를<br /><em>실행 가능한 일정</em>으로 바꾸세요</h1>
        <p>게시물 링크나 장소 목록을 가져와 후보를 확인한 뒤, 원하는 곳만 담아 날짜별 동선을 계산합니다.</p>
      </section>

      <div className="discovery-layout">
        <aside className="wishlist-sidebar panel">
          <div className="panel-title"><Heart size={18} /><div><strong>내 위시리스트</strong><small>{wishlists.length}개 목록</small></div></div>
          <form className="wishlist-create" onSubmit={createWishlist}>
            <input value={wishlistName} onChange={event => setWishlistName(event.target.value)} maxLength={100} placeholder="예: 교토 가을 여행" required />
            <button className="icon-button" aria-label="위시리스트 만들기" disabled={busy}><Plus size={17} /></button>
          </form>
          <div className="wishlist-tabs">
            {wishlists.map(item => (
              <button key={item.id} className={wishlist?.id === item.id ? 'active' : ''} onClick={() => void chooseWishlist(item.id)}>
                <span>{item.name}</span><small>{item.placeCount}곳</small>
              </button>
            ))}
          </div>
          {wishlists.length === 0 && <p className="muted-copy">먼저 여행별 위시리스트를 하나 만들어 주세요.</p>}
        </aside>

        <div className="discovery-main">
          <section className="panel import-panel">
            <div className="section-heading compact"><span className="eyebrow">CONTENT IMPORT</span><h2>SNS·웹에서 장소 가져오기</h2><p>Instagram은 캡션을 함께 붙여 넣어야 하며 게시물을 자동 크롤링하지 않습니다.</p></div>
            <form className="import-form" onSubmit={startImport}>
              <label className="field field-wide"><span><Link2 size={15} /> 게시물 또는 웹 페이지 URL</span><input type="url" value={url} onChange={event => setUrl(event.target.value)} maxLength={2048} placeholder="https://www.instagram.com/p/..." required /></label>
              <label className="field field-wide"><span>캡션 또는 장소 목록 <small>(선택, 한 줄에 하나 권장)</small></span><textarea value={inputText} onChange={event => setInputText(event.target.value)} maxLength={10000} rows={5} placeholder={'경복궁\n북촌한옥마을\n국립현대미술관 서울'} /></label>
              <button className="button button-primary" disabled={busy || !wishlist}>{busy && !job?.status ? <LoaderCircle className="spin" size={17} /> : <Sparkles size={17} />} 장소 후보 찾기</button>
              {!wishlist && <small className="form-hint">저장할 위시리스트를 먼저 선택해 주세요.</small>}
            </form>

            {job && (
              <div className="import-result" aria-live="polite">
                <div className="import-status"><strong>{job.detectedTitle ?? job.sourceType}</strong><span className={`status-pill status-${job.status.toLowerCase()}`}>{job.status}</span></div>
                {!terminal.has(job.status) && <p><LoaderCircle className="spin" size={16} /> 콘텐츠를 분석하고 장소 후보를 확인하는 중입니다.</p>}
                {job.warning && <p className="import-warning">{job.warning}</p>}
                {job.errorMessage && <p className="import-error">{job.errorMessage}</p>}
                {job.status === 'AWAITING_INPUT' && <button className="button button-secondary" onClick={() => void retryImport()} disabled={!inputText.trim() || busy}>붙여 넣은 내용으로 다시 분석</button>}
                {candidateGroups.map(([order, candidates]) => (
                  <fieldset key={order} className="candidate-group">
                    <legend>{candidates[0].extractedName}</legend>
                    {candidates.some(candidate => candidate.matched) ? candidates.filter(candidate => candidate.matched).map(candidate => (
                      <label key={candidate.id} className={selectedCandidates[order] === candidate.id ? 'selected' : ''}>
                        <input type="radio" name={`mention-${order}`} checked={selectedCandidates[order] === candidate.id} onChange={() => setSelectedCandidates(current => ({ ...current, [order]: candidate.id }))} />
                        <MapPin size={16} /><span><strong>{candidate.matchedName}</strong><small>{candidate.formattedAddress ?? candidate.primaryType}</small></span>{selectedCandidates[order] === candidate.id && <Check size={16} />}
                      </label>
                    )) : <p className="unmatched-place">장소 검색 결과가 없습니다. 추출 이름을 수정해 다시 시도해 주세요.</p>}
                  </fieldset>
                ))}
                {job.status === 'COMPLETED' && candidateGroups.length > 0 && <button className="button button-dark" onClick={() => void saveCandidates()} disabled={!wishlist || Object.keys(selectedCandidates).length === 0 || busy}>선택한 장소를 위시리스트에 저장 <ArrowRight size={16} /></button>}
              </div>
            )}
          </section>

          <section className="panel direct-place-panel">
            <div className="section-heading compact"><span className="eyebrow">DIRECT SEARCH</span><h2>장소 이름으로 직접 담기</h2></div>
            <form className="direct-place-search" onSubmit={searchPlace}><Search size={18} /><input value={placeQuery} onChange={event => setPlaceQuery(event.target.value)} placeholder="장소 이름 또는 주소" required /><button className="button button-secondary" disabled={busy || !wishlist}>검색</button></form>
            <div className="direct-place-results">{placeResults.map(result => <div key={result.externalPlaceId}><MapPin size={16} /><span><strong>{result.name}</strong><small>{result.formattedAddress}</small></span><button className="button button-ghost button-small" onClick={() => void savePlace(result)} disabled={!wishlist || busy}><Plus size={14} /> 담기</button></div>)}</div>
          </section>

          <section className="panel saved-places-panel">
            <div className="section-heading compact"><span className="eyebrow">READY TO PLAN</span><h2>{wishlist?.name ?? '위시리스트'}의 장소</h2><p>여행에 포함할 장소만 체크한 뒤 기간과 숙소를 설정하세요.</p></div>
            {wishlist && wishlist.places.length > 0 ? (
              <>
                <div className="saved-place-list">{wishlist.places.map(place => <label key={place.id}><input type="checkbox" checked={selectedPlaces.includes(place.id)} onChange={() => setSelectedPlaces(current => current.includes(place.id) ? current.filter(id => id !== place.id) : [...current, place.id])} /><MapPin size={16} /><span><strong>{place.name}</strong><small>{place.category ?? '장소'} · {place.priority}</small></span><button type="button" className="icon-button" aria-label={`${place.name} 삭제`} onClick={event => { event.preventDefault(); void removePlace(place.id) }}><Trash2 size={15} /></button></label>)}</div>
                <button className="button button-primary plan-wishlist" disabled={selectedPlaces.length === 0} onClick={() => onCreateTrip(wishlist.id, selectedPlaces)}>선택한 {selectedPlaces.length}곳으로 여행 만들기 <ArrowRight size={17} /></button>
              </>
            ) : <AsyncState kind="empty" title="아직 저장한 장소가 없습니다" message="콘텐츠 URL을 분석하거나 장소 이름으로 직접 검색해 담아 보세요." />}
          </section>
        </div>
      </div>
    </main>
  )
}
