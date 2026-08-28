import { request } from './client'
import type { BudgetCurrency, SharedRouteSummary, TransportMode, TripPace } from '../types'

const send = <T>(path: string, method: string, body?: unknown) => request<T>(path, { method, ...(body === undefined ? {} : { body: JSON.stringify(body) }) })
export const categories = ['ACCOMMODATION', 'FOOD', 'TRANSPORT', 'ACTIVITY', 'SHOPPING', 'OTHER'] as const
export type ExpenseCategory = typeof categories[number]
export const categoryNames: Record<ExpenseCategory, string> = { ACCOMMODATION: '숙박', FOOD: '식비', TRANSPORT: '교통', ACTIVITY: '입장·활동', SHOPPING: '쇼핑', OTHER: '기타' }
export interface Allocation { date: string | null; category: ExpenseCategory | null; limitMinor: number }
export interface Expense { id: number; requestId: string; date: string; category: ExpenseCategory; description: string; amountMinor: number }
export interface Spending { currency: BudgetCurrency; totalLimitMinor: number | null; spentMinor: number; scopes: (Allocation & { spentMinor: number; remainingMinor: number })[]; expenses: Expense[] }
export const interests = ['CULTURE', 'NATURE', 'FOOD', 'SHOPPING', 'RELAXATION', 'ADVENTURE'] as const
export type Interest = typeof interests[number]
export const interestNames: Record<Interest, string> = { CULTURE: '문화·역사', NATURE: '자연', FOOD: '미식', SHOPPING: '쇼핑', RELAXATION: '휴식', ADVENTURE: '체험·모험' }
export interface Preferences { interests: Interest[]; regions: string[]; pace: TripPace | null; transportMode: TransportMode | null }
export interface Recommendation { route: SharedRouteSummary; score: number; reasons: string[] }
export interface Entry { id: number; userId: number; nickname: string; body: string; rating: number | null; createdAt: string }
export interface Discussion { comments: Entry[]; reviews: Entry[]; averageRating: number; reviewCount: number; commentCount: number; page: number }
export type Target = 'ROUTE' | 'COMMENT' | 'REVIEW'
export interface ReportInput { targetType: Target; targetId: number; reason: string; detail: string }
export interface ModerationReport { id: number; routeId: number; targetType: Target; targetId: number; reason: string; detail: string; targetContent: string | null }
export interface Usage { operation: string; month: string; attemptedUnits: number; limit: number }
export interface WeatherRefreshSettings { enabled: boolean; nextRefreshAt: string | null; lastSuccessAt: string | null; lastError: string | null }
export const advanced = {
  weatherRefreshSettings: (id: number) => request<WeatherRefreshSettings>(`/trips/${id}/weather/auto-refresh`),
  saveWeatherRefreshSettings: (id: number, enabled: boolean) => send<WeatherRefreshSettings>(`/trips/${id}/weather/auto-refresh`, 'PUT', { enabled }),
  zone: (id: number) => request<{ timeZoneId: string }>(`/trips/${id}/time-zone`),
  saveZone: (id: number, timeZoneId: string) => send<{ timeZoneId: string }>(`/trips/${id}/time-zone`, 'PUT', { timeZoneId }),
  weather: (id: number) => send<{ updatedDates: number; preservedManualDates: number; timeZoneId: string; message: string }>(`/trips/${id}/weather/refresh`, 'POST'),
  hours: (tripId: number, placeId: number) => send<{ weekdayDescriptions: string[]; warning: string }>(`/trips/${tripId}/places/${placeId}/opening-hours/refresh`, 'POST'),
  usage: () => request<Usage[]>('/integrations/usage'),
  maps: () => request<{ browserKey: string }>('/integrations/maps-config'),
  geometry: (id: number, date: string) => send<{ encodedPolylines: string[] }>(`/itineraries/${id}/road-geometry?date=${encodeURIComponent(date)}`, 'POST'),
  spending: (id: number) => request<Spending>(`/trips/${id}/spending`),
  allocations: (id: number, currency: BudgetCurrency, allocations: Allocation[]) => send<Spending>(`/trips/${id}/spending/allocations`, 'PUT', { currency, allocations }),
  expense: (id: number, currency: BudgetCurrency, value: Omit<Expense, 'id'>, expenseId?: number) => send<Spending>(`/trips/${id}/spending/expenses${expenseId ? `/${expenseId}` : ''}`, expenseId ? 'PUT' : 'POST', { ...value, currency }),
  deleteExpense: (id: number, expenseId: number) => send<Spending>(`/trips/${id}/spending/expenses/${expenseId}`, 'DELETE'),
  preferences: () => request<Preferences>('/me/preferences'),
  savePreferences: (value: Preferences) => send<Preferences>('/me/preferences', 'PUT', value),
  recommendations: () => request<Recommendation[]>('/me/recommendations'),
  discussion: (id: number, page = 0) => request<Discussion>(`/routes/${id}/discussion?page=${page}`),
  comment: (id: number, body: string, commentId?: number) => send<Discussion>(`/routes/${id}/comments${commentId ? `/${commentId}` : ''}`, commentId ? 'PUT' : 'POST', { body }),
  review: (id: number, rating: number, body: string) => send<Discussion>(`/routes/${id}/review`, 'PUT', { rating, body }),
  removeEntry: (id: number, entryId: number, type: 'COMMENT' | 'REVIEW') => send<Discussion>(`/routes/${id}/${type === 'COMMENT' ? 'comments' : 'reviews'}/${entryId}`, 'DELETE'),
  report: (id: number, value: ReportInput) => send<{ id: number; status: string }>(`/routes/${id}/reports`, 'POST', value),
  moderator: () => request<{ allowed: boolean }>('/moderation/access'),
  reports: () => request<ModerationReport[]>('/moderation/reports'),
  resolve: (id: number, resolution: 'HIDE' | 'DISMISS') => send<void>(`/moderation/reports/${id}/resolve`, 'POST', { resolution }),
}
