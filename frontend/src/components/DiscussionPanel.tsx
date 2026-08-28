import { useEffect, useState } from 'react'
import { advanced, type Discussion, type Entry, type Target } from '../api/advanced'
import type { User } from '../types'

const reasons = { SPAM: '스팸·광고', ABUSE: '욕설·괴롭힘', MISLEADING: '잘못된 정보', PRIVACY: '개인정보 노출', OTHER: '기타' }

export function DiscussionPanel({ routeId, ownerId, user, onError, onRequireAuth }: {
  routeId: number; ownerId: number; user: User | null; onError: (e: unknown) => void; onRequireAuth?: () => void
}) {
  const [value, setValue] = useState<Discussion | null>(null)
  const [page, setPage] = useState(0)
  const [reload, setReload] = useState(0)
  const [busy, setBusy] = useState(false)
  const [comment, setComment] = useState('')
  const [editing, setEditing] = useState<number>()
  const [review, setReview] = useState('')
  const [rating, setRating] = useState(5)
  const [target, setTarget] = useState<{ type: Target; id: number } | null>(null)
  const [reason, setReason] = useState('SPAM')
  const [detail, setDetail] = useState('')
  const [message, setMessage] = useState('')
  useEffect(() => {
    let active = true
    advanced.discussion(routeId, page).then(result => { if (active) setValue(result) }).catch(onError)
    return () => { active = false }
  }, [routeId, page, reload, onError])
  const mutate = async (action: () => Promise<Discussion>) => {
    setBusy(true); setMessage('')
    try { const next = await action(); setPage(0); setValue(next) }
    catch (e) { onError(e) } finally { setBusy(false) }
  }
  const startReport = (type: Target, id: number) => { setTarget({ type, id }); setDetail(''); setMessage('') }
  const entry = (item: Entry, type: 'COMMENT' | 'REVIEW') => <article key={item.id} className="discussion-entry">
    <header><strong>{item.nickname}</strong><span>{item.rating != null && `★ ${item.rating} · `}{new Date(item.createdAt).toLocaleDateString('ko-KR')}</span></header>
    <p>{item.body}</p>
    {user && <div className="advanced-actions">
      {user.id === item.userId && <><button disabled={busy} onClick={() => { if (type === 'COMMENT') { setEditing(item.id); setComment(item.body) } else { setReview(item.body); setRating(item.rating ?? 5) } }}>수정</button><button disabled={busy} onClick={() => { if (window.confirm('작성한 내용을 삭제할까요?')) void mutate(() => advanced.removeEntry(routeId, item.id, type)) }}>삭제</button></>}
      <button disabled={busy} onClick={() => startReport(type, item.id)}>신고</button>
    </div>}
  </article>
  return <section className="advanced-panel discussion-panel" aria-label="댓글과 후기">
    <h3>여행자의 댓글과 후기</h3>
    {!value ? <button onClick={() => setReload(r => r + 1)}>댓글·후기 불러오기</button> : <>
      <p>후기 {value.reviewCount}개 · 평균 {value.averageRating.toFixed(1)} / 5 · 댓글 {value.commentCount}개</p>
      <p className="data-caption">후기는 이용자 의견이며 실제 방문 인증을 거치지 않습니다. 개인정보와 예약번호를 적지 마세요.</p>
      <h4>댓글</h4>{value.comments.map(item => entry(item, 'COMMENT'))}{!value.comments.length && <p>이 페이지에 댓글이 없습니다.</p>}
      {user && <form onSubmit={e => { e.preventDefault(); void mutate(async () => { const next = await advanced.comment(routeId, comment, editing); setComment(''); setEditing(undefined); return next }) }}>
        <label className="field"><span>{editing ? '댓글 수정' : '댓글 작성'}</span><textarea value={comment} required maxLength={2000} onChange={e => setComment(e.target.value)} /></label>
        <div className="advanced-actions"><button className="button button-ghost button-small" disabled={busy || !comment.trim()}>{editing ? '댓글 수정 저장' : '댓글 등록'}</button>{editing && <button type="button" onClick={() => { setEditing(undefined); setComment('') }}>수정 취소</button>}</div>
      </form>}
      <h4>후기</h4>{value.reviews.map(item => entry(item, 'REVIEW'))}{!value.reviews.length && <p>이 페이지에 후기가 없습니다.</p>}
      {user && user.id !== ownerId && <form onSubmit={e => { e.preventDefault(); void mutate(async () => { const next = await advanced.review(routeId, rating, review); setReview(''); return next }) }}>
        <label className="field"><span>별점</span><select aria-label="별점" value={rating} onChange={e => setRating(Number(e.target.value))}>{[5, 4, 3, 2, 1].map(n => <option key={n} value={n}>{n}점</option>)}</select></label>
        <label className="field"><span>후기 내용</span><textarea value={review} required maxLength={2000} onChange={e => setReview(e.target.value)} /></label>
        <button className="button button-ghost button-small" disabled={busy || !review.trim()}>후기 저장</button><small>루트당 1개의 후기만 유지하며 다시 저장하면 내 후기를 수정합니다.</small>
      </form>}
      {Math.max(value.commentCount, value.reviewCount) > 20 && <div className="advanced-actions"><button disabled={busy || page === 0} onClick={() => setPage(p => p - 1)}>이전 댓글·후기</button><span>{page + 1}페이지</span><button disabled={busy || (page + 1) * 20 >= Math.max(value.commentCount, value.reviewCount)} onClick={() => setPage(p => p + 1)}>다음 댓글·후기</button></div>}
    </>}
    {!user && <p>로그인하면 댓글·후기·신고를 작성할 수 있습니다. {onRequireAuth && <button onClick={onRequireAuth}>로그인</button>}</p>}
    {user && <button className="text-button" onClick={() => startReport('ROUTE', routeId)}>이 루트 신고</button>}
    {target && <form className="report-form" onSubmit={e => { e.preventDefault(); setBusy(true); void advanced.report(routeId, { targetType: target.type, targetId: target.id, reason, detail }).then(() => { setMessage('신고가 접수되었습니다. 운영자가 확인합니다.'); setTarget(null) }).catch(onError).finally(() => setBusy(false)) }}>
      <h4>{target.type === 'ROUTE' ? '루트' : target.type === 'COMMENT' ? '댓글' : '후기'} 신고</h4>
      <label className="field"><span>신고 사유</span><select aria-label="신고 사유" value={reason} onChange={e => setReason(e.target.value)}>{Object.entries(reasons).map(([key, name]) => <option key={key} value={key}>{name}</option>)}</select></label>
      <label className="field"><span>신고 설명 · 선택</span><textarea value={detail} maxLength={1000} onChange={e => setDetail(e.target.value)} /></label>
      <div className="advanced-actions"><button disabled={busy} className="button button-ghost button-small">신고 접수</button><button type="button" disabled={busy} onClick={() => setTarget(null)}>취소</button></div>
    </form>}
    {message && <p role="status">{message}</p>}
  </section>
}
