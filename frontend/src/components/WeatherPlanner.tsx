import { useEffect, useMemo, useState } from 'react'
import { CloudRain, CloudSun, Save, ShieldCheck } from 'lucide-react'
import { api } from '../api/client'
import { dateLabel, weatherLabel } from '../lib/format'
import type { Trip, TripWeatherForecastInput, WeatherCondition } from '../types'

interface Props {
  trip: Trip
  onError: (error: unknown) => void
  compact?: boolean
}

const conditions: WeatherCondition[] = [
  'UNKNOWN',
  'CLEAR',
  'CLOUDY',
  'RAIN',
  'SNOW',
  'EXTREME',
]

const defaultPrecipitation: Record<WeatherCondition, number> = {
  UNKNOWN: 0,
  CLEAR: 0,
  CLOUDY: 30,
  RAIN: 80,
  SNOW: 80,
  EXTREME: 100,
}

export function WeatherPlanner({ trip, onError, compact = false }: Props) {
  const dates = useMemo(() => travelDates(trip.startDate, trip.endDate), [trip.startDate, trip.endDate])
  const [forecasts, setForecasts] = useState<TripWeatherForecastInput[]>(() => emptyForecasts(dates))
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    let active = true
    setLoading(true)
    api.getTripWeather(trip.id)
      .then((stored) => {
        if (!active) return
        const byDate = new Map(stored.map((forecast) => [forecast.forecastDate, forecast]))
        setForecasts(dates.map((date) => {
          const forecast = byDate.get(date)
          return forecast
            ? {
                forecastDate: date,
                condition: forecast.condition,
                precipitationProbability: forecast.precipitationProbability,
              }
            : { forecastDate: date, condition: 'UNKNOWN', precipitationProbability: 0 }
        }))
      })
      .catch(onError)
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [dates, onError, trip.id])

  const changeCondition = (date: string, condition: WeatherCondition) => {
    setSaved(false)
    setForecasts((current) => current.map((forecast) => forecast.forecastDate === date
      ? {
          ...forecast,
          condition,
          precipitationProbability: defaultPrecipitation[condition],
        }
      : forecast))
  }

  const changeProbability = (date: string, probability: number) => {
    setSaved(false)
    setForecasts((current) => current.map((forecast) => forecast.forecastDate === date
      ? { ...forecast, precipitationProbability: probability }
      : forecast))
  }

  const save = async () => {
    setSaving(true)
    setSaved(false)
    try {
      await api.replaceTripWeather(trip.id, forecasts)
      setSaved(true)
    } catch (error) {
      onError(error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className={`weather-planner ${compact ? 'compact' : ''}`} aria-label="날짜별 날씨 설정">
      <div className="weather-planner-head">
        <span className="weather-planner-icon"><CloudSun size={20} /></span>
        <div>
          <strong>날짜별 날씨</strong>
          <small>비·눈에는 실내를, 맑은 날에는 실외를 우선합니다.</small>
        </div>
      </div>
      {loading ? (
        <div className="weather-loading">예보 설정을 불러오는 중…</div>
      ) : (
        <div className="weather-day-list">
          {forecasts.map((forecast) => (
            <div className="weather-day-row" key={forecast.forecastDate}>
              <label>
                <span>{dateLabel(forecast.forecastDate)}</span>
                <select
                  aria-label={`${dateLabel(forecast.forecastDate)} 날씨`}
                  value={forecast.condition}
                  onChange={(event) => changeCondition(
                    forecast.forecastDate,
                    event.target.value as WeatherCondition,
                  )}
                >
                  {conditions.map((condition) => (
                    <option key={condition} value={condition}>{weatherLabel(condition)}</option>
                  ))}
                </select>
              </label>
              <label className="weather-rain-field">
                <span><CloudRain size={14} /> 강수확률</span>
                <div><input
                  aria-label={`${dateLabel(forecast.forecastDate)} 강수확률`}
                  type="number"
                  min="0"
                  max="100"
                  value={forecast.precipitationProbability}
                  disabled={forecast.condition === 'UNKNOWN'}
                  onChange={(event) => changeProbability(
                    forecast.forecastDate,
                    Number(event.target.value),
                  )}
                /><span>%</span></div>
              </label>
            </div>
          ))}
        </div>
      )}
      <div className="weather-planner-actions">
        <small><ShieldCheck size={14} /> 꼭 가기 장소는 날씨와 관계없이 유지됩니다.</small>
        <button className="button button-ghost button-small" disabled={loading || saving} onClick={() => void save()}>
          <Save size={15} /> {saving ? '저장 중…' : saved ? '예보 저장됨' : '예보 저장'}
        </button>
      </div>
    </section>
  )
}

function emptyForecasts(dates: string[]): TripWeatherForecastInput[] {
  return dates.map((forecastDate) => ({
    forecastDate,
    condition: 'UNKNOWN',
    precipitationProbability: 0,
  }))
}

function travelDates(startDate: string, endDate: string): string[] {
  const dates: string[] = []
  const current = new Date(`${startDate}T00:00:00Z`)
  const end = new Date(`${endDate}T00:00:00Z`)
  while (current <= end) {
    dates.push(current.toISOString().slice(0, 10))
    current.setUTCDate(current.getUTCDate() + 1)
  }
  return dates
}
