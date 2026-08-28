import { useEffect, useId, useRef, useState } from 'react'
import { Bell, CheckCheck, Trash2, X } from 'lucide-react'

export interface Notification {
  id: number
  kind: 'success' | 'error' | 'info'
  message: string
  time: string
  read: boolean
}

export function NotificationCenter({ items, onRead, onClear }: {
  items: Notification[]; onRead: () => void; onClear: () => void
}) {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const panelId = useId()
  const unread = items.filter(item => !item.read).length
  useEffect(() => {
    if (!open) return
    const outside = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false) }
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') { setOpen(false); trigger.current?.focus() } }
    document.addEventListener('pointerdown', outside)
    document.addEventListener('keydown', escape)
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape) }
  }, [open])
  return <div ref={root} className="notification-center">
    <button ref={trigger} className="icon-button notification-trigger" aria-label={unread ? `알림 ${unread}개 읽지 않음` : '알림'} aria-expanded={open} aria-controls={panelId} onClick={() => setOpen(!open)}>
      <Bell size={18} />{unread > 0 && <span className="notification-badge" aria-hidden="true">{unread}</span>}
    </button>
    {open && <section id={panelId} className="notification-panel" aria-label="알림함">
      <div className="notification-heading"><h2>알림</h2><button className="icon-button" aria-label="알림함 닫기" onClick={() => { setOpen(false); trigger.current?.focus() }}><X size={16} /></button></div>
      <p className="notification-help">최근 30개 · 새로고침·로그아웃 시 비워집니다.</p>
      {items.length ? <><div className="notification-tools"><button onClick={onRead} disabled={!unread}><CheckCheck size={14} /> 모두 읽음</button><button onClick={onClear}><Trash2 size={14} /> 전체 비우기</button></div>
        <ol>{items.map(item => <li key={item.id} className={`${item.kind} ${item.read ? '' : 'unread'}`}><span>{item.kind === 'error' ? '확인 필요' : item.kind === 'success' ? '완료' : '안내'}</span><p>{item.message}</p><time dateTime={item.time}>{new Date(item.time).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}</time></li>)}</ol></>
        : <p className="notification-empty">새 알림이 없습니다.</p>}
    </section>}
  </div>
}
