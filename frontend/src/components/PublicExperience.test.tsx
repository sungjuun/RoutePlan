import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import type { AuthSession, SharedRoutePage } from '../types'
import { AuthPage } from './AuthPage'
import { LandingPage } from './LandingPage'

vi.mock('../api/client', () => ({
  api: {
    discoverRoutes: vi.fn(),
    login: vi.fn(),
    signup: vi.fn(),
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

describe('public RoutePlan experience', () => {
  beforeEach(() => {
    vi.mocked(api.discoverRoutes).mockResolvedValue(emptyRoutes)
    vi.mocked(api.login).mockResolvedValue(authenticated)
  })

  it('opens country recommendations and searches community routes', async () => {
    const onExplore = vi.fn()
    render(
      <LandingPage
        user={null}
        onExplore={onExplore}
        onCreateTrip={vi.fn()}
        onError={vi.fn()}
      />,
    )

    expect(await screen.findByText('나라별 추천 루트')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /대한민국.*서울/ }))
    expect(onExplore).toHaveBeenCalledWith('서울')

    fireEvent.change(screen.getByPlaceholderText(/어디로 떠나시나요/), {
      target: { value: '파리' },
    })
    fireEvent.click(screen.getByRole('button', { name: /루트 찾기/ }))
    expect(onExplore).toHaveBeenCalledWith('파리')
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
})
