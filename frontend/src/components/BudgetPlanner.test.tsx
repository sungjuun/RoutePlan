import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import type { Trip, TripBudget } from '../types'
import { BudgetPlanner } from './BudgetPlanner'
import { BudgetSummary } from './BudgetSummary'

const trip = { id: 16, places: [{ placeId: 1 }] } as Trip
const budget: TripBudget = {
  currency: 'USD', limitMinor: null, fixedCostMinor: 0,
  placeCosts: [{ placeId: 1, placeName: '박물관', mustVisit: true, estimatedCostMinor: null }],
}

describe('BudgetPlanner', () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks() })

  it('loads prices, blocks unsaved planning, and saves exact decimal amounts', async () => {
    vi.spyOn(api, 'getTripBudget').mockResolvedValue(budget)
    const replacement = { ...budget, limitMinor: 10099, fixedCostMinor: 1230, placeCosts: [{ ...budget.placeCosts[0], estimatedCostMinor: 29 }] }
    const save = vi.spyOn(api, 'replaceTripBudget').mockResolvedValue(replacement)
    const blocked = vi.fn()
    render(<BudgetPlanner trip={trip} onError={vi.fn()} onBlockedChange={blocked} />)
    await screen.findByLabelText('박물관 예상 비용')
    await waitFor(() => expect(blocked).toHaveBeenLastCalledWith(false))
    fireEvent.change(screen.getByLabelText('총예산'), { target: { value: '100.99' } })
    fireEvent.change(screen.getByLabelText('고정비'), { target: { value: '12.30' } })
    fireEvent.change(screen.getByLabelText('박물관 예상 비용'), { target: { value: '0.29' } })
    expect(blocked).toHaveBeenLastCalledWith(true)
    fireEvent.click(screen.getByRole('button', { name: '예산 저장' }))
    await waitFor(() => expect(save).toHaveBeenCalledWith(16, {
      currency: 'USD', limitMinor: 10099, fixedCostMinor: 1230,
      placeCosts: [{ placeId: 1, estimatedCostMinor: 29 }],
    }))
    expect(await screen.findByRole('button', { name: '예산 저장됨' })).toBeVisible()
    await waitFor(() => expect(blocked).toHaveBeenLastCalledWith(false))
  })

  it('clears amounts on currency change and validates precision before saving', async () => {
    vi.spyOn(api, 'getTripBudget').mockResolvedValue({ ...budget, limitMinor: 10000 })
    const save = vi.spyOn(api, 'replaceTripBudget')
    render(<BudgetPlanner trip={trip} onError={vi.fn()} onBlockedChange={vi.fn()} />)
    await screen.findByLabelText('총예산')
    fireEvent.change(screen.getByLabelText('예산 통화'), { target: { value: 'JPY' } })
    expect(screen.getByLabelText('총예산')).toHaveValue('')
    fireEvent.change(screen.getByLabelText('박물관 예상 비용'), { target: { value: '0.5' } })
    fireEvent.click(screen.getByRole('button', { name: '예산 저장' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('소수점 0자리')
    expect(save).not.toHaveBeenCalled()
  })

  it('keeps planning blocked on load failure and supports retry', async () => {
    vi.spyOn(api, 'getTripBudget').mockRejectedValueOnce(new Error('offline')).mockResolvedValue(budget)
    const blocked = vi.fn()
    render(<BudgetPlanner trip={trip} onError={vi.fn()} onBlockedChange={blocked} />)
    await screen.findByRole('alert')
    expect(blocked).toHaveBeenLastCalledWith(true)
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    await screen.findByLabelText('총예산')
    await waitFor(() => expect(blocked).toHaveBeenLastCalledWith(false))
  })

  it('labels incomplete totals without claiming all costs are included', () => {
    render(<BudgetSummary summary={{ currency: 'KRW', limitMinor: null, fixedCostMinor: 1000,
      knownVisitCostMinor: 0, estimatedTotalMinor: 1000, unpricedPlaceCount: 1, remainingMinor: null }} />)
    expect(screen.getByText('입력된 비용 합계')).toBeVisible()
    expect(screen.getByText(/비용 미입력 장소 1곳/)).toBeVisible()
  })
})
