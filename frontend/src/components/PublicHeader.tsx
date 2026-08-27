import { Compass, LogOut, Plus, Route } from 'lucide-react'
import type { User } from '../types'

interface Props {
  user: User | null
  onHome: () => void
  onCommunity: () => void
  onMyTrip: () => void
  onNewTrip: () => void
  onLogin: () => void
  onSignup: () => void
  onLogout: () => void
}

export function PublicHeader({
  user,
  onHome,
  onCommunity,
  onMyTrip,
  onNewTrip,
  onLogin,
  onSignup,
  onLogout,
}: Props) {
  return (
    <header className="public-header">
      <button className="wordmark public-wordmark" onClick={onHome}>
        <span className="brand-mark"><Route size={21} /></span>
        <span>RoutePlan</span>
      </button>
      <nav aria-label="주요 메뉴">
        <button onClick={onHome}>추천 루트</button>
        <button onClick={onCommunity}>커뮤니티</button>
        <button onClick={onMyTrip}>내 여행</button>
      </nav>
      <div className="public-header-actions">
        {user ? (
          <>
            <button className="button button-ghost button-small" onClick={onNewTrip}>
              <Plus size={15} /> 새 여행
            </button>
            <span className="public-profile"><i>{user.nickname.slice(0, 1)}</i>{user.nickname}</span>
            <button className="icon-button" onClick={onLogout} aria-label="로그아웃">
              <LogOut size={17} />
            </button>
          </>
        ) : (
          <>
            <button className="public-login" onClick={onLogin}>로그인</button>
            <button className="button button-dark button-small" onClick={onSignup}>
              <Compass size={15} /> 회원가입
            </button>
          </>
        )}
      </div>
    </header>
  )
}
