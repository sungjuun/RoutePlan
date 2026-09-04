import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import type { Trip, TripCollaboration, TripSettlement, User } from '../types'
import { CollaborationWorkspace } from './CollaborationWorkspace'

const user: User = {
  id: 1,
  nickname: '성준',
  email: 'owner@routeplan.test',
  createdAt: '2026-01-01T00:00:00Z',
}

const trip = {
  id: 7,
  startDate: '2026-09-10',
  endDate: '2026-09-11',
  dailyStartTime: '09:00:00',
  accommodationLatitude: 35.681236,
  accommodationLongitude: 139.767125,
  places: [{ placeId: 31, name: '도쿄 타워' }],
} as Trip

const collaboration: TripCollaboration = {
  tripId: 7,
  currentUserId: 1,
  currentRole: 'OWNER',
  members: [
    { memberId: 11, userId: 1, nickname: '성준', email: 'owner@routeplan.test', role: 'OWNER', joinedAt: '2026-09-01T00:00:00Z' },
    { memberId: 12, userId: 2, nickname: '민수', email: 'editor@routeplan.test', role: 'EDITOR', joinedAt: '2026-09-01T00:00:00Z' },
  ],
  places: [{
    placeId: 31,
    placeName: '도쿄 타워',
    mustVisit: false,
    configuredPriority: 50,
    effectivePriority: 60,
    priorityBand: 'NORMAL',
    yesCount: 1,
    noCount: 0,
    pendingCount: 1,
    myVote: null,
  }],
}

const settlement: TripSettlement = {
  tripId: 7,
  currency: 'JPY',
  expenses: [],
  balances: [
    { userId: 1, nickname: '성준', paidMinor: 0, owedMinor: 0, netMinor: 0 },
    { userId: 2, nickname: '민수', paidMinor: 0, owedMinor: 0, netMinor: 0 },
  ],
  transfers: [],
  exactMinimum: true,
}

function mockInitial(role: TripCollaboration['currentRole'] = 'OWNER') {
  vi.spyOn(api, 'getTripCollaboration').mockResolvedValue({ ...collaboration, currentRole: role })
  vi.spyOn(api, 'getTripSettlement').mockResolvedValue(settlement)
}

describe('CollaborationWorkspace', () => {
  afterEach(() => vi.restoreAllMocks())

  it('lets the owner add a member and lets every member vote', async () => {
    mockInitial()
    const addMember = vi.spyOn(api, 'addTripMember').mockResolvedValue(collaboration)
    const vote = vi.spyOn(api, 'voteTripPlace').mockResolvedValue({
      ...collaboration,
      places: [{ ...collaboration.places[0], myVote: 'YES', yesCount: 2, pendingCount: 0 }],
    })
    render(<CollaborationWorkspace trip={trip} user={user} onTripChanged={vi.fn()} onNotify={vi.fn()} onError={vi.fn()} />)

    fireEvent.change(await screen.findByLabelText('가입 이메일'), { target: { value: 'friend@routeplan.test' } })
    fireEvent.click(screen.getByRole('button', { name: '동행자 추가' }))
    await waitFor(() => expect(addMember).toHaveBeenCalledWith(7, 'friend@routeplan.test', 'EDITOR'))

    fireEvent.click(screen.getByRole('button', { name: '도쿄 타워 가고 싶어요' }))
    await waitFor(() => expect(vote).toHaveBeenCalledWith(7, 31, 'YES'))
  })

  it('keeps editing controls hidden from a viewer while preserving voting', async () => {
    mockInitial('VIEWER')
    render(<CollaborationWorkspace trip={trip} user={user} onTripChanged={vi.fn()} onNotify={vi.fn()} onError={vi.fn()} />)

    expect(await screen.findByText('조회자')).toBeVisible()
    expect(screen.queryByLabelText('가입 이메일')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '공동 지출 기록' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '도쿄 타워 가고 싶어요' })).toBeEnabled()
  })

  it('does not erase a shared expense draft when saving fails', async () => {
    mockInitial()
    const failure = new Error('network')
    vi.spyOn(api, 'addSharedExpense').mockRejectedValue(failure)
    const onError = vi.fn()
    render(<CollaborationWorkspace trip={trip} user={user} onTripChanged={vi.fn()} onNotify={vi.fn()} onError={onError} />)

    const description = await screen.findByPlaceholderText('저녁 식사')
    const amount = screen.getByLabelText('금액 · JPY')
    fireEvent.change(description, { target: { value: '야키니쿠' } })
    fireEvent.change(amount, { target: { value: '12000' } })
    fireEvent.click(screen.getByRole('button', { name: '공동 지출 기록' }))

    await waitFor(() => expect(onError).toHaveBeenCalledWith(failure))
    expect(description).toHaveValue('야키니쿠')
    expect(amount).toHaveValue('12000')
  })
})
