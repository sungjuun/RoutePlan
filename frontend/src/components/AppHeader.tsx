import { CalendarDays, Home, LogOut, Plus, Route, UsersRound } from 'lucide-react'
import { dateLabel, paceLabel, transportLabel } from '../lib/format'
import type { Trip, User } from '../types'

interface Props {
  trip: Trip
  user: User | null
  onNewTrip: () => void
  onHome: () => void
  onCommunity: () => void
  onLogout: () => void
}

export function AppHeader({ trip, user, onNewTrip, onHome, onCommunity, onLogout }: Props) {
  return (
    <header className="app-header">
      <button className="wordmark header-wordmark" onClick={onHome}>
        <span className="brand-mark"><Route size={21} /></span>
        <span>RoutePlan</span>
      </button>
      <div className="trip-context">
        <div>
          <strong>{trip.name}</strong>
          <span><CalendarDays size={14} /> {dateLabel(trip.startDate)}{trip.startDate !== trip.endDate ? ` – ${dateLabel(trip.endDate)}` : ''}</span>
        </div>
        <span className="context-chip">{transportLabel(trip.transportMode)}</span>
        <span className="context-chip">{paceLabel(trip.pace)}</span>
      </div>
      <div className="header-actions">
        <button className="icon-button" onClick={onHome} aria-label="메인으로"><Home size={16} /></button>
        <button className="icon-button" onClick={onCommunity} aria-label="커뮤니티"><UsersRound size={16} /></button>
        <button className="button button-ghost button-small" onClick={onNewTrip}>
          <Plus size={16} /> 새 여행
        </button>
        <span className="profile-button">
          <span>{user?.nickname?.slice(0, 1) ?? 'R'}</span>
          <span className="profile-name">{user?.nickname ?? '여행자'}</span>
        </span>
        <button className="icon-button" onClick={onLogout} aria-label="로그아웃"><LogOut size={16} /></button>
      </div>
    </header>
  )
}
