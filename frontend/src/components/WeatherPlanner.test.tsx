import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import type { Trip } from '../types'
import { WeatherPlanner } from './WeatherPlanner'

const trip: Trip = {
  id: 15,
  userId: 1,
  name: '서울 날씨 여행',
  startDate: '2026-09-10',
  endDate: '2026-09-11',
  dailyStartTime: '09:00:00',
  dailyEndTime: '20:00:00',
  accommodationName: '서울 숙소',
  accommodationLatitude: 37.57,
  accommodationLongitude: 126.98,
  transportMode: 'WALKING',
  pace: 'STANDARD',
  status: 'DRAFT',
  createdAt: '2026-08-27T00:00:00Z',
  updatedAt: '2026-08-27T00:00:00Z',
  places: [],
}

describe('WeatherPlanner', () => {
  afterEach(() => vi.restoreAllMocks())

  it('loads daily forecasts and saves changed weather for the whole trip', async () => {
    vi.spyOn(api, 'getTripWeather').mockResolvedValue([{
      forecastDate: '2026-09-10',
      condition: 'RAIN',
      precipitationProbability: 80,
      updatedAt: '2026-08-27T00:00:00Z',
    }])
    const replace = vi.spyOn(api, 'replaceTripWeather').mockResolvedValue([])

    render(<WeatherPlanner trip={trip} onError={vi.fn()} />)

    const firstDay = await screen.findByRole('combobox', { name: /9월 10일.*날씨/ })
    expect(firstDay).toHaveValue('RAIN')
    expect(screen.getByRole('combobox', { name: /9월 11일.*날씨/ })).toHaveValue('UNKNOWN')

    fireEvent.change(screen.getByRole('combobox', { name: /9월 11일.*날씨/ }), {
      target: { value: 'CLEAR' },
    })
    fireEvent.click(screen.getByRole('button', { name: '예보 저장' }))

    await waitFor(() => expect(replace).toHaveBeenCalledWith(15, [
      { forecastDate: '2026-09-10', condition: 'RAIN', precipitationProbability: 80 },
      { forecastDate: '2026-09-11', condition: 'CLEAR', precipitationProbability: 0 },
    ]))
    expect(screen.getByRole('button', { name: '예보 저장됨' })).toBeVisible()
  })
})
