import { useState } from 'react'
import { Compass, LogOut, Menu, Plus, Route, X } from 'lucide-react'
import type { User } from '../types'
import type { ReactNode } from 'react'
import { UserAvatar } from './UserAvatar'

interface Props {
  user: User | null
  onHome: () => void
  onCommunity: () => void
  onMyTrip: () => void
  onProfile: () => void
  onNewTrip: () => void
  onLogin: () => void
  onSignup: () => void
  onLogout: () => void
  activePage?: string
  notifications?: ReactNode
}

export function PublicHeader({
  user,
  onHome,
  onCommunity,
  onMyTrip,
  onProfile,
  onNewTrip,
  onLogin,
  onSignup,
  onLogout,
  activePage,
  notifications,
}: Props) {
  const [menuOpen, setMenuOpen] = useState(false)
  const navigate = (action: () => void) => {
    setMenuOpen(false)
    action()
  }

  return (
    <header className="public-header">
      <button className="wordmark public-wordmark" onClick={() => navigate(onHome)}>
        <span className="brand-mark"><Route size={21} /></span>
        <span>RoutePlan</span>
      </button>
      <nav id="public-navigation" className={menuOpen ? 'open' : ''} aria-label="주요 메뉴">
        <button className={activePage === 'home' ? 'active' : ''} aria-current={activePage === 'home' ? 'page' : undefined} onClick={() => navigate(onHome)}>추천 루트</button>
        <button className={activePage === 'community' ? 'active' : ''} aria-current={activePage === 'community' ? 'page' : undefined} onClick={() => navigate(onCommunity)}>커뮤니티</button>
        <button className={activePage === 'trips' ? 'active' : ''} aria-current={activePage === 'trips' ? 'page' : undefined} onClick={() => navigate(onMyTrip)}>내 여행</button>
      </nav>
      <div className="public-header-actions">
        {notifications}
        <button
          className="icon-button public-menu-toggle"
          onClick={() => setMenuOpen((open) => !open)}
          aria-label={menuOpen ? '메뉴 닫기' : '메뉴 열기'}
          aria-expanded={menuOpen}
          aria-controls="public-navigation"
        >
          {menuOpen ? <X size={18} /> : <Menu size={18} />}
        </button>
        {user ? (
          <>
            <button className="button button-ghost button-small" onClick={() => navigate(onNewTrip)}>
              <Plus size={15} /> 새 여행
            </button>
            <button className="public-profile" onClick={() => navigate(onProfile)}><i><UserAvatar user={user} /></i>{user.nickname}</button>
            <button className={`icon-button public-profile-icon ${activePage === 'profile' ? 'active' : ''}`} onClick={() => navigate(onProfile)} aria-label="마이페이지"><UserAvatar user={user} /></button>
            <button className="icon-button" onClick={() => navigate(onLogout)} aria-label="로그아웃">
              <LogOut size={17} />
            </button>
          </>
        ) : (
          <>
            <button className="public-login" onClick={() => navigate(onLogin)}>로그인</button>
            <button className="button button-dark button-small" onClick={() => navigate(onSignup)}>
              <Compass size={15} /> 회원가입
            </button>
          </>
        )}
      </div>
    </header>
  )
}
