import { CheckCircle2, Info, X, XCircle } from 'lucide-react'

interface Props {
  notice: { kind: 'success' | 'error' | 'info'; message: string } | null
  onClose: () => void
}

export function Toast({ notice, onClose }: Props) {
  if (!notice) return null
  const Icon = notice.kind === 'success' ? CheckCircle2 : notice.kind === 'error' ? XCircle : Info
  return (
    <div className={`toast toast-${notice.kind}`} role={notice.kind === 'error' ? 'alert' : 'status'}>
      <Icon size={20} />
      <span>{notice.message}</span>
      <button onClick={onClose} aria-label="알림 닫기"><X size={17} /></button>
    </div>
  )
}
