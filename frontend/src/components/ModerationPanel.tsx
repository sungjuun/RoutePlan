import { useEffect, useState } from 'react'
import { advanced, type ModerationReport } from '../api/advanced'

export function ModerationPanel({ onError }: { onError: (e: unknown) => void }) {
  const [allowed, setAllowed] = useState(false)
  const [reports, setReports] = useState<ModerationReport[]>([])
  const [busy, setBusy] = useState(false)
  const [loaded, setLoaded] = useState(false)
  useEffect(() => { let active = true; advanced.moderator().then(r => { if (active) setAllowed(r.allowed) }).catch(onError); return () => { active = false } }, [onError])
  const load = async () => { setBusy(true); try { setReports(await advanced.reports()); setLoaded(true) } catch (e) { onError(e) } finally { setBusy(false) } }
  const resolve = async (id: number, resolution: 'HIDE' | 'DISMISS') => {
    if (!window.confirm(resolution === 'HIDE' ? '신고 대상을 공개 화면에서 숨길까요?' : '이 신고를 기각할까요?')) return
    setBusy(true)
    try { await advanced.resolve(id, resolution); setReports(await advanced.reports()) }
    catch (e) { onError(e) } finally { setBusy(false) }
  }
  if (!allowed) return null
  return <section className="advanced-panel panel" aria-label="신고 관리"><h2>운영자 신고 관리</h2><p>오래된 미처리 신고부터 최대 100건을 표시합니다.</p><button disabled={busy} onClick={() => void load()}>신고 목록 새로고침</button>{loaded && !reports.length && <p>미처리 신고가 없습니다.</p>}{reports.map(report => <article className="discussion-entry" key={report.id}><strong>#{report.id} · {report.targetType} #{report.targetId} · {report.reason}</strong><blockquote>{report.targetContent ?? '대상 내용이 삭제되었습니다.'}</blockquote><p>{report.detail || '추가 설명 없음'}</p><div className="advanced-actions"><button disabled={busy} onClick={() => void resolve(report.id, 'HIDE')}>대상 숨김</button><button disabled={busy} onClick={() => void resolve(report.id, 'DISMISS')}>신고 기각</button></div></article>)}</section>
}
