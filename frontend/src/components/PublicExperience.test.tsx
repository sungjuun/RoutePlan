import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import type { AuthSession, SharedRoutePage, TripSummary } from '../types'
import { AuthPage } from './AuthPage'
import { LandingPage } from './LandingPage'
import { MyPage } from './MyPage'
import { MyTripsPage } from './MyTripsPage'
import { PublicHeader } from './PublicHeader'
import { advanced } from '../api/advanced'

vi.mock('../api/advanced', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/advanced')>(),
  advanced: { preferences: vi.fn(), moderator: vi.fn(), recommendations: vi.fn() },
}))

vi.mock('../api/client', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/client')>(),
  api: {
    discoverRoutes: vi.fn(),
    login: vi.fn(),
    signup: vi.fn(),
    getTrips: vi.fn(),
    getAuthOptions: vi.fn().mockResolvedValue({ mailMode: 'LOCAL' }),
  },
}))

const emptyRoutes: SharedRoutePage = {
  content: [],
  page: 0,
  size: 6,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
}

const authenticated: AuthSession = {
  authenticated: true,
  user: {
    id: 7,
    email: 'traveler@example.com',
    nickname: '여행자',
    createdAt: '2026-08-27T00:00:00Z',
  },
}

const savedTrips: TripSummary[] = [{
  id: 21,
  name: '가져온 오사카 여행',
  startDate: '2026-09-10',
  endDate: '2026-09-12',
  accommodationName: '난바 숙소',
  transportMode: 'PUBLIC_TRANSIT',
  pace: 'RELAXED',
  status: 'OPTIMIZED',
  placeCount: 5,
  createdAt: '2026-08-27T00:00:00Z',
  updatedAt: '2026-08-27T00:00:00Z',
}]

describe('public RoutePlan experience', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(api.discoverRoutes).mockResolvedValue(emptyRoutes)
    vi.mocked(api.login).mockResolvedValue(authenticated)
    vi.mocked(api.getTrips).mockResolvedValue(savedTrips)
    vi.mocked(advanced.preferences).mockResolvedValue({ interests: [], regions: [], pace: null, transportMode: null })
    vi.mocked(advanced.moderator).mockResolvedValue({ allowed: false })
  })

  it('opens country recommendations and searches community routes', async () => {
    const onExplore = vi.fn()
    const onDiscover = vi.fn()
    render(
      <LandingPage
        user={null}
        onExplore={onExplore}
        onDiscover={onDiscover}
        onCreateTrip={vi.fn()}
        onError={vi.fn()}
      />,
    )

    expect(await screen.findByText('나라별 추천 루트')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /대한민국.*서울/ }))
    expect(onExplore).toHaveBeenCalledWith('서울')

    fireEvent.change(screen.getByPlaceholderText(/SNS URL 또는 도시/), {
      target: { value: '파리' },
    })
    fireEvent.click(screen.getByRole('button', { name: /여행 발견하기/ }))
    expect(onExplore).toHaveBeenCalledWith('파리')

    fireEvent.change(screen.getByPlaceholderText(/SNS URL 또는 도시/), {
      target: { value: 'https://www.instagram.com/p/example/' },
    })
    fireEvent.click(screen.getByRole('button', { name: /여행 발견하기/ }))
    expect(onDiscover).toHaveBeenCalledWith('https://www.instagram.com/p/example/')
  })

  it('authenticates from the login form and returns the server user', async () => {
    const onAuthenticated = vi.fn()
    render(
      <AuthPage
        initialMode="login"
        onAuthenticated={onAuthenticated}
        onBack={vi.fn()}
        onError={vi.fn()}
      />,
    )

    fireEvent.change(screen.getByLabelText(/이메일/), {
      target: { value: 'traveler@example.com' },
    })
    fireEvent.change(screen.getByLabelText(/^비밀번호$/), {
      target: { value: 'routeplan12!' },
    })
    const loginButtons = screen.getAllByRole('button', { name: /^로그인$/ })
    fireEvent.click(loginButtons[loginButtons.length - 1])

    await waitFor(() => expect(api.login).toHaveBeenCalledWith(
      'traveler@example.com',
      'routeplan12!',
    ))
    expect(onAuthenticated).toHaveBeenCalledWith(authenticated.user)
  })

  it('shows every saved trip and opens the selected workspace', async () => {
    const onOpenTrip = vi.fn()
    render(<MyTripsPage onOpenTrip={onOpenTrip} onNewTrip={vi.fn()} onError={vi.fn()} />)

    expect(await screen.findByText('가져온 오사카 여행')).toBeInTheDocument()
    expect(screen.getByText('5곳')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /가져온 오사카 여행/ }))
    expect(onOpenTrip).toHaveBeenCalledWith(21)
  })

  it('shows account information and travel totals on my page', async () => {
    render(
      <MyPage
        onPasswordChanged={vi.fn()}
        onEmailChanged={vi.fn()}
        onAccountDeleted={vi.fn()}
        onUserChanged={vi.fn()}
        user={authenticated.user!}
        onOpenTrips={vi.fn()}
        onNewTrip={vi.fn()}
        onLogout={vi.fn()}
        onError={vi.fn()}
      />,
    )

    expect(screen.getByText('여행자님의 여행 공간')).toBeInTheDocument()
    expect(screen.getAllByText('traveler@example.com')).toHaveLength(2)
    await waitFor(() => expect(api.getTrips).toHaveBeenCalled())
    expect(screen.getByText('담은 장소')).toBeInTheDocument()
  })

  it('opens the compact public menu and closes it after navigation', () => {
    const onCommunity = vi.fn()
    const onProfile = vi.fn()
    render(
      <PublicHeader
        user={authenticated.user}
        activePage="home"
        onHome={vi.fn()}
        onDiscover={vi.fn()}
        onCommunity={onCommunity}
        onMyTrip={vi.fn()}
        onProfile={onProfile}
        onNewTrip={vi.fn()}
        onLogin={vi.fn()}
        onSignup={vi.fn()}
        onLogout={vi.fn()}
      />,
    )

    const toggle = screen.getByRole('button', { name: '메뉴 열기' })
    fireEvent.click(toggle)
    expect(screen.getByRole('button', { name: '메뉴 닫기' })).toHaveAttribute('aria-expanded', 'true')
    fireEvent.click(screen.getByRole('button', { name: '커뮤니티' }))
    expect(onCommunity).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: '메뉴 열기' })).toHaveAttribute('aria-expanded', 'false')
    const profile = screen.getByRole('button', { name: '여행자 마이페이지' })
    expect(document.querySelectorAll('.public-profile')).toHaveLength(1)
    expect(document.querySelector('.public-profile-icon')).toBeNull()
    fireEvent.click(profile)
    expect(onProfile).toHaveBeenCalledOnce()
  })

  it('shows a recoverable error when saved trips fail to load', async () => {
    vi.mocked(api.getTrips)
      .mockRejectedValueOnce(new Error('temporary failure'))
      .mockResolvedValueOnce(savedTrips)

    const onError = vi.fn()
    render(<MyTripsPage onOpenTrip={vi.fn()} onNewTrip={vi.fn()} onError={onError} />)

    expect(await screen.findByText('여행 목록을 불러오지 못했습니다')).toBeInTheDocument()
    expect(onError).toHaveBeenCalledOnce()
    fireEvent.click(screen.getByRole('button', { name: /다시 시도/ }))
    expect(await screen.findByText('가져온 오사카 여행')).toBeInTheDocument()
    expect(api.getTrips).toHaveBeenCalledTimes(2)
  })
})
