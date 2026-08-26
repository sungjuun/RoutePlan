import { CalendarDays, ChevronDown, Plus, Route } from 'lucide-react'
import { dateLabel, paceLabel, transportLabel } from '../lib/format'
import type { Trip, User } from '../types'

interface Props {
  trip: Trip
  user: User | null
  onNewTrip: () => void
}

export function AppHeader({ trip, user, onNewTrip }: Props) {
  return (
    <header className="app-header">
      <div className="wordmark">
        <span className="brand-mark"><Route size={21} /></span>
        <span>RoutePlan</span>
      </div>
      <div className="trip-context">
        <div>
          <strong>{trip.name}</strong>
          <span><CalendarDays size={14} /> {dateLabel(trip.startDate)}{trip.startDate !== trip.endDate ? ` – ${dateLabel(trip.endDate)}` : ''}</span>
        </div>
        <span className="context-chip">{transportLabel(trip.transportMode)}</span>
        <span className="context-chip">{paceLabel(trip.pace)}</span>
      </div>
      <div className="header-actions">
        <button className="button button-ghost button-small" onClick={onNewTrip}>
          <Plus size={16} /> 새 여행
        </button>
        <button className="profile-button" aria-label="사용자 메뉴">
          <span>{user?.nickname?.slice(0, 1) ?? 'R'}</span>
          <span className="profile-name">{user?.nickname ?? '여행자'}</span>
          <ChevronDown size={15} />
        </button>
      </div>
    </header>
  )
}
