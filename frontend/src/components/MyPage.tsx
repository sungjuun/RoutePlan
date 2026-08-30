import { useEffect, useMemo, useState } from 'react'
import { ArrowRight, CalendarCheck, LogOut, Mail, MapPinned, Plus, Route, ShieldCheck, UserRound } from 'lucide-react'
import { api } from '../api/client'
import type { TripSummary, User } from '../types'
import { AsyncState } from './AsyncState'
import { PreferencesPanel } from './PreferencesPanel'
import { ModerationPanel } from './ModerationPanel'
import { ProfileImageEditor } from './ProfileImageEditor'
import { UserAvatar } from './UserAvatar'
import { AccountSecurityPanel } from './AccountSecurityPanel'

interface Props {
  user: User
  onOpenTrips: () => void
  onNewTrip: () => void
  onLogout: () => void
  onError: (error: unknown) => void
  onUserChanged: (user: Pick<User, 'id'> & Partial<Pick<User, 'profileImageUrl' | 'emailVerified'>>) => void
  onPasswordChanged: () => void
}

export function MyPage({ user, onOpenTrips, onNewTrip, onLogout, onError, onUserChanged, onPasswordChanged }: Props) {
  const [trips, setTrips] = useState<TripSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const totalPlaces = useMemo(
    () => trips.reduce((total, trip) => total + trip.placeCount, 0),
    [trips],
  )
  const optimized = useMemo(
    () => trips.filter((trip) => trip.status === 'OPTIMIZED').length,
    [trips],
  )

  useEffect(() => {
    let cancelled = false
    api.getTrips()
      .then((result) => { if (!cancelled) setTrips(result) })
      .catch((error) => {
        if (!cancelled) {
          setLoadFailed(true)
          onError(error)
        }
      })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [onError, reloadKey])

  return (
    <main className="account-page profile-page">
      <section className="profile-hero">
        <div className="profile-avatar"><UserAvatar user={user} /></div>
        <div><span className="eyebrow eyebrow-light">MY ROUTEPLAN</span><h1>{user.nickname}님의 여행 공간</h1><p>{new Date(user.createdAt).toLocaleDateString('ko-KR')}부터 RoutePlan과 여행 중입니다.</p></div>
      </section>

      <section className="account-content profile-layout">
        <div className="profile-main">
          <ProfileImageEditor user={user} onChanged={next => onUserChanged({ id: next.id, profileImageUrl: next.profileImageUrl })} />
          <AccountSecurityPanel user={user} onUserChanged={next => onUserChanged({ id: next.id, emailVerified: next.emailVerified })} onPasswordChanged={onPasswordChanged} />
          {loading ? (
            <AsyncState kind="loading" title="여행 현황을 불러오는 중입니다" className="profile-data-state" />
          ) : loadFailed ? (
            <AsyncState kind="error" title="여행 현황을 불러오지 못했습니다" message="계정 정보는 안전하게 유지되고 있습니다. 다시 불러와 주세요." actionLabel="다시 시도" onAction={() => { setLoading(true); setLoadFailed(false); setReloadKey((key) => key + 1) }} className="profile-data-state" />
          ) : (
            <div className="profile-stats">
              <div><Route size={20} /><span>저장한 여행</span><strong>{trips.length}</strong></div>
              <div><CalendarCheck size={20} /><span>완성한 일정</span><strong>{optimized}</strong></div>
              <div><MapPinned size={20} /><span>담은 장소</span><strong>{totalPlaces}</strong></div>
            </div>
          )}
          <section className="profile-actions panel">
            <div><span className="eyebrow">QUICK START</span><h2>다음 여행을 이어가세요</h2></div>
            <button onClick={onOpenTrips}><Route size={18} /><span><strong>내 여행 전체보기</strong><small>만든 여행과 가져온 루트 확인</small></span><ArrowRight size={17} /></button>
            <button onClick={onNewTrip}><Plus size={18} /><span><strong>새 여행 만들기</strong><small>숙소와 날짜부터 새로 계획</small></span><ArrowRight size={17} /></button>
          </section>
          <PreferencesPanel onError={onError} />
          <ModerationPanel onError={onError} />
        </div>
        <aside className="profile-account panel">
          <span className="eyebrow">ACCOUNT</span><h2>계정 정보</h2>
          <div><UserRound size={17} /><span><small>닉네임</small><strong>{user.nickname}</strong></span></div>
          <div><Mail size={17} /><span><small>이메일</small><strong>{user.email}</strong></span></div>
          <div><ShieldCheck size={17} /><span><small>보안</small><strong>세션 보호 적용</strong></span></div>
          <button className="button button-ghost" onClick={onLogout}><LogOut size={16} /> 로그아웃</button>
        </aside>
      </section>
    </main>
  )
}
