import { AlertTriangle, Inbox, LoaderCircle, RotateCw } from 'lucide-react'

interface Props {
  kind: 'loading' | 'error' | 'empty'
  title: string
  message?: string
  actionLabel?: string
  onAction?: () => void
  className?: string
}

export function AsyncState({ kind, title, message, actionLabel, onAction, className = '' }: Props) {
  const Icon = kind === 'loading' ? LoaderCircle : kind === 'error' ? AlertTriangle : Inbox

  return (
    <div
      className={`async-state panel async-state-${kind} ${className}`.trim()}
      role={kind === 'error' ? 'alert' : 'status'}
      aria-live="polite"
    >
      <Icon className={kind === 'loading' ? 'spin' : undefined} size={30} />
      <h3>{title}</h3>
      {message && <p>{message}</p>}
      {onAction && actionLabel && (
        <button className="button button-ghost button-small" onClick={onAction}>
          {kind === 'error' && <RotateCw size={15} />}
          {actionLabel}
        </button>
      )}
    </div>
  )
}
