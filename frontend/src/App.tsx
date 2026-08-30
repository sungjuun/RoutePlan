import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Compass, LoaderCircle, MapPinned, MessageSquareText, Route, Settings2, UsersRound } from 'lucide-react'
import { ApiError, api } from './api/client'
import { AppHeader } from './components/AppHeader'
import { CommunityWorkspace } from './components/CommunityWorkspace'
import { ItineraryWorkspace } from './components/ItineraryWorkspace'
import { LandingPage } from './components/LandingPage'
import { MyTripsPage } from './components/MyTripsPage'
import { NaturalLanguageWorkspace } from './components/NaturalLanguageWorkspace'
import { PlaceWorkspace } from './components/PlaceWorkspace'
import { PublicCommunityPage } from './components/PublicCommunityPage'
import { PublicHeader } from './components/PublicHeader'
import { TripSetup } from './components/TripSetup'
import { Toast } from './components/Toast'
import type { Notification } from './components/NotificationCenter'
import { clearTripReference, saveWorkspace } from './lib/storage'
import type { Itinerary, Place, Trip, User } from './types'
import { parseAccountLink } from './lib/accountSecurity'

const MyPage = lazy(() => import('./components/MyPage').then(module => ({ default: module.MyPage })))
const AuthPage = lazy(() => import('./components/AuthPage').then(module => ({ default: module.AuthPage })))
const AccountLinkPage = lazy(() => import('./components/AccountLinkPage').then(module => ({ default: module.AccountLinkPage })))
const NotificationCenter = lazy(() => import('./components/NotificationCenter').then(module => ({ default: module.NotificationCenter })))

type Section = 'places' | 'itinerary' | 'settings' | 'community' | 'natural-language'
type PublicPage = 'home' | 'community' | 'auth' | 'create-trip' | 'trips' | 'profile' | 'workspace'
type AuthMode = 'login' | 'signup'
type AfterAuth = 'home' | 'create-trip' | 'trips' | 'profile' | 'requested-trip'

interface Notice {
  kind: 'success' | 'error' | 'info'
  message: string
}

export function App() {
  const initialAccountLink = useMemo(() => parseAccountLink(window.location.hash), [])
  const [accountLink, setAccountLink] = useState(initialAccountLink)
  const requestedTripId = useMemo(() => {
    const value = Number(new URLSearchParams(window.location.search).get('tripId'))
    return Number.isSafeInteger(value) && value > 0 ? value : null
  }, [])
  const [user, setUser] = useState<User | null>(null)
  const [page, setPage] = useState<PublicPage>('home')
  const [authMode, setAuthMode] = useState<AuthMode>('login')
  const [afterAuth, setAfterAuth] = useState<AfterAuth>('home')
  const [communityRegion, setCommunityRegion] = useState('')
  const [trip, setTrip] = useState<Trip | null>(null)
  const [itinerary, setItinerary] = useState<Itinerary | null>(null)
  const [previousItinerary, setPreviousItinerary] = useState<Itinerary | null>(null)
  const [itineraryPlaces, setItineraryPlaces] = useState<Record<number, Place>>({})
  const [section, setSection] = useState<Section>('places')
  const [booting, setBooting] = useState(true)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [notifications, setNotifications] = useState<Notification[]>([])
  const noticeId = useRef(0)

  useEffect(() => {
    if (parseAccountLink(window.location.hash)) window.history.replaceState(null, '', window.location.pathname)
    const onLink = () => {
      const next = parseAccountLink(window.location.hash)
      if (next) {
        setAccountLink(next)
        window.history.replaceState(null, '', window.location.pathname)
      }
    }
    window.addEventListener('hashchange', onLink)
    return () => window.removeEventListener('hashchange', onLink)
  }, [])

  const notify = useCallback((kind: Notice['kind'], message: string) => {
    setNotice(kind === 'error' ? { kind, message } : null)
    const item: Notification = { id: ++noticeId.current, kind, message, time: new Date().toISOString(), read: false }
    setNotifications(current => {
      const last = current[0]
      if (last?.kind === kind && last.message === message && Date.now() - Date.parse(last.time) < 10_000) return current
      return [item, ...current].slice(0, 30)
    })
  }, [])

  const reportError = useCallback((error: unknown) => {
    if (error instanceof ApiError) {
      const details = error.body.violations?.map((violation) => violation.message).join(' · ')
      notify('error', details ? `${error.message} — ${details}` : error.message)
      return
    }
    notify('error', error instanceof Error ? error.message : '예상하지 못한 오류가 발생했습니다.')
  }, [notify])

  const loadItineraryContext = useCallback(async (latest: Itinerary | null) => {
    setItinerary(latest)
    if (!latest) {
      setPreviousItinerary(null)
      setItineraryPlaces({})
      return
    }
    const [parent, places] = await Promise.all([
      latest.parentItineraryId ? api.getItinerary(latest.parentItineraryId) : Promise.resolve(null),
      Promise.all([...new Set(latest.items.map((item) => item.placeId))].map(api.getPlace)),
    ])
    setPreviousItinerary(parent)
    setItineraryPlaces(Object.fromEntries(places.map((place) => [place.id, place])))
  }, [])

  const loadTrip = useCallback(async (tripId: number) => {
    setBooting(true)
    try {
      const restoredTrip = await api.getTrip(tripId)
      setTrip(restoredTrip)
      saveWorkspace({ tripId: restoredTrip.id })
      window.history.replaceState(null, '', `?tripId=${restoredTrip.id}`)
      try {
        await loadItineraryContext(await api.getLatestItinerary(restoredTrip.id))
      } catch (error) {
        if (!(error instanceof ApiError) || error.body.code !== 'ITINERARY_NOT_FOUND') throw error
        await loadItineraryContext(null)
      }
      setPage('workspace')
    } catch (error) {
      clearTripReference()
      setTrip(null)
      setPage('home')
      window.history.replaceState(null, '', window.location.pathname)
      reportError(error)
    } finally {
      setBooting(false)
    }
  }, [loadItineraryContext, reportError])

  useEffect(() => {
    let cancelled = false
    const boot = async () => {
      try {
        const session = await api.getAuthSession()
        if (cancelled) return
        setUser(session.user)
        if (requestedTripId != null && !initialAccountLink) {
          if (session.user) await loadTrip(requestedTripId)
          else {
            setAfterAuth('requested-trip')
            setAuthMode('login')
            setPage('auth')
            notify('info', '이 여행을 보려면 먼저 로그인해 주세요.')
          }
        }
      } catch (error) {
        if (!cancelled) reportError(error)
      } finally {
        if (!cancelled) setBooting(false)
      }
    }
    void boot()
    return () => { cancelled = true }
  }, [loadTrip, notify, reportError, requestedTripId, initialAccountLink])

  const showAuth = (mode: AuthMode, next: AfterAuth = 'home') => {
    setAuthMode(mode)
    setAfterAuth(next)
    setPage('auth')
  }

  const showHome = () => {
    setPage('home')
    window.history.replaceState(null, '', window.location.pathname)
  }

  const clearAccountSession = () => {
    setUser(null)
    setTrip(null)
    setItinerary(null)
    setPreviousItinerary(null)
    setItineraryPlaces({})
    setNotifications([])
    setNotice(null)
    clearTripReference()
    window.history.replaceState(null, '', window.location.pathname)
  }

  const passwordChanged = () => {
    clearAccountSession()
    showAuth('login')
    notify('info', '비밀번호를 변경했습니다. 모든 기기에서 로그아웃했으니 새 비밀번호로 로그인해 주세요.')
  }

  const closeAccountLink = async () => {
    setAccountLink(null)
    try {
      const session = await api.getAuthSession()
      setUser(session.user)
      if (session.user) setPage('profile')
      else showAuth('login')
    } catch (error) { reportError(error); showAuth('login') }
  }

  const showCommunity = (region = '') => {
    setCommunityRegion(region)
    setPage('community')
    window.history.replaceState(null, '', window.location.pathname)
  }

  const createTrip = () => {
    if (!user) {
      showAuth('login', 'create-trip')
      return
    }
    clearTripReference()
    setTrip(null)
    setItinerary(null)
    setPreviousItinerary(null)
    setItineraryPlaces({})
    setPage('create-trip')
    window.history.replaceState(null, '', window.location.pathname)
  }

  const openMyTrips = () => {
    if (!user) {
      showAuth('login', 'trips')
      return
    }
    setPage('trips')
    window.history.replaceState(null, '', window.location.pathname)
  }

  const openProfile = () => {
    if (!user) {
      showAuth('login', 'profile')
      return
    }
    setPage('profile')
    window.history.replaceState(null, '', window.location.pathname)
  }

  const handleAuthenticated = async (nextUser: User) => {
    setNotifications([])
    setUser(nextUser)
    notify('success', `${nextUser.nickname}님, 반갑습니다.`)
    if (afterAuth === 'create-trip') {
      setPage('create-trip')
      return
    }
    if (afterAuth === 'requested-trip') {
      if (requestedTripId != null) {
        await loadTrip(requestedTripId)
        return
      }
      setPage('trips')
      return
    }
    if (afterAuth === 'trips') {
      setPage('trips')
      return
    }
    if (afterAuth === 'profile') {
      setPage('profile')
      return
    }
    setPage('home')
  }

  const logout = async () => {
    try {
      await api.logout()
      setNotifications([])
      setNotice(null)
      setUser(null)
      setTrip(null)
      await loadItineraryContext(null)
      clearTripReference()
      showHome()
      notify('success', '안전하게 로그아웃했습니다.')
    } catch (error) {
      reportError(error)
    }
  }

  const handleTripReady = (nextTrip: Trip) => {
    setTrip(nextTrip)
    setItinerary(null)
    setPreviousItinerary(null)
    setItineraryPlaces({})
    saveWorkspace({ tripId: nextTrip.id })
    window.history.replaceState(null, '', `?tripId=${nextTrip.id}`)
    setSection('places')
    setPage('workspace')
    notify('success', `${nextTrip.name} 작업공간을 만들었습니다.`)
  }

  const handleItineraryChanged = async (next: Itinerary, message: string) => {
    await loadItineraryContext(next)
    setSection('itinerary')
    notify('success', message)
  }

  const handleRouteCopied = async (nextTrip: Trip, nextItinerary: Itinerary | null, message: string) => {
    setTrip(nextTrip)
    saveWorkspace({ tripId: nextTrip.id })
    window.history.replaceState(null, '', `?tripId=${nextTrip.id}`)
    await loadItineraryContext(nextItinerary)
    setSection(nextItinerary ? 'itinerary' : 'places')
    setPage('workspace')
    notify('success', message)
  }

  if (booting) {
    return <main className="app-loading" aria-live="polite"><div className="brand-mark"><Route size={25} /></div><LoaderCircle className="spin" size={25} /><p>RoutePlan을 준비하는 중입니다</p></main>
  }

  if (accountLink) {
    return <Suspense fallback={<p role="status">계정 보안 화면을 불러오는 중…</p>}><AccountLinkPage key={`${accountLink.kind}-${accountLink.token}`} link={accountLink} onSessionRevoked={clearAccountSession} onClose={() => void closeAccountLink()} /></Suspense>
  }

  const notificationCenter = <Suspense fallback={<span className="icon-button" aria-label="알림 준비 중" />}><NotificationCenter items={notifications} onRead={() => setNotifications(current => current.map(item => ({ ...item, read: true })))} onClear={() => setNotifications([])} /></Suspense>
  const errorNotice = <Toast notice={notice} onClose={() => setNotice(null)} />

  if (page === 'auth') {
    return <>{errorNotice}<Suspense fallback={<p role="status">로그인 화면을 불러오는 중…</p>}><AuthPage initialMode={authMode} onAuthenticated={handleAuthenticated} onBack={showHome} onError={reportError} /></Suspense></>
  }

  if (page !== 'workspace') {
    return (
      <div className="public-shell">
        <PublicHeader notifications={notificationCenter} user={user} activePage={page} onHome={showHome} onCommunity={() => showCommunity()} onMyTrip={openMyTrips} onProfile={openProfile} onNewTrip={createTrip} onLogin={() => showAuth('login')} onSignup={() => showAuth('signup')} onLogout={() => void logout()} />
        {errorNotice}
        {page === 'home' && <LandingPage user={user} onExplore={showCommunity} onCreateTrip={createTrip} onError={reportError} />}
        {page === 'community' && <PublicCommunityPage user={user} initialRegion={communityRegion} onRequireAuth={() => showAuth('login', 'create-trip')} onCreateTrip={createTrip} onError={reportError} />}
        {page === 'create-trip' && <TripSetup onReady={handleTripReady} onError={reportError} />}
        {page === 'trips' && <MyTripsPage onOpenTrip={(tripId) => void loadTrip(tripId)} onNewTrip={createTrip} onError={reportError} />}
        {page === 'profile' && user && <Suspense fallback={<p role="status">마이페이지를 불러오는 중…</p>}><MyPage user={user} onUserChanged={next => setUser(current => current?.id === next.id ? { ...current, ...next } : current)} onPasswordChanged={passwordChanged} onOpenTrips={openMyTrips} onNewTrip={createTrip} onLogout={() => void logout()} onError={reportError} /></Suspense>}
      </div>
    )
  }

  if (!trip || !user) return null

  return (
    <div className="app-shell">
      <AppHeader notifications={notificationCenter} trip={trip} user={user} onNewTrip={createTrip} onHome={showHome} onCommunity={() => setSection('community')} onProfile={openProfile} onLogout={() => void logout()} />
      {errorNotice}
      <div className="workspace-shell">
        <nav className="workspace-nav" aria-label="여행 작업 단계">
          <button className={section === 'places' ? 'active' : ''} onClick={() => setSection('places')}><span><MapPinned size={19} /></span><span><strong>장소 담기</strong><small>{trip.places.length}곳 선택됨</small></span></button>
          <button className={section === 'itinerary' ? 'active' : ''} onClick={() => setSection('itinerary')}><span><Compass size={19} /></span><span><strong>일정 보기</strong><small>{itinerary ? `버전 ${itinerary.version}` : '계산 전'}</small></span></button>
          <button className={section === 'natural-language' ? 'active' : ''} onClick={() => setSection('natural-language')}><span><MessageSquareText size={19} /></span><span><strong>자연어 조건</strong><small>말로 취향 설정</small></span></button>
          <button className={section === 'settings' ? 'active' : ''} onClick={() => setSection('settings')}><span><Settings2 size={19} /></span><span><strong>여행 설정</strong><small>시간·숙소·이동</small></span></button>
          <button className={section === 'community' ? 'active' : ''} onClick={() => setSection('community')}><span><UsersRound size={19} /></span><span><strong>루트 커뮤니티</strong><small>공개·탐색·가져오기</small></span></button>
        </nav>
        <main className="workspace-main">
          {section === 'places' && <PlaceWorkspace trip={trip} hasItinerary={itinerary != null} onTripChanged={(next, message) => { setTrip(next); if (message) notify('success', message) }} onGoToItinerary={() => setSection('itinerary')} onError={reportError} />}
          {section === 'itinerary' && <ItineraryWorkspace trip={trip} itinerary={itinerary} previousItinerary={previousItinerary} itineraryPlaces={itineraryPlaces} onItineraryChanged={handleItineraryChanged} onError={reportError} onGoToPlaces={() => setSection('places')} />}
          {section === 'settings' && <TripSetup trip={trip} embedded onUpdated={(next) => { setTrip(next); notify('success', '여행 설정을 저장했습니다.') }} onError={reportError} />}
          {section === 'natural-language' && <NaturalLanguageWorkspace trip={trip} onTripChanged={setTrip} onGoToPlaces={() => setSection('places')} onGoToItinerary={() => setSection('itinerary')} onError={reportError} />}
          {section === 'community' && <CommunityWorkspace user={user} trip={trip} itinerary={itinerary} onTripCopied={handleRouteCopied} onNotify={notify} onError={reportError} />}
        </main>
      </div>
    </div>
  )
}
