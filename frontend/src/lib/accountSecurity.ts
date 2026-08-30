import { ApiError } from '../api/client'

export interface AccountLink {
  kind: 'verify' | 'reset'
  token: string
}

// Link secrets stay in the fragment, never in server access logs, storage, or analytics.
export function parseAccountLink(hash: string): AccountLink | null {
  const prefix = hash.startsWith('#verify-email=') ? '#verify-email='
    : hash.startsWith('#reset-password=') ? '#reset-password=' : null
  if (!prefix) return null
  const token = hash.slice(prefix.length)
  return { kind: prefix === '#verify-email=' ? 'verify' : 'reset', token: /^[A-Za-z0-9_-]{43}$/.test(token) ? token : '' }
}

export function passwordValidation(password: string, confirmation: string): string | null {
  if (password.length < 10) return '비밀번호는 10자 이상이어야 합니다.'
  if (new TextEncoder().encode(password).length > 72) return '비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.'
  if (password !== confirmation) return '비밀번호 확인이 일치하지 않습니다.'
  return null
}

export function accountError(error: unknown): string {
  if (error instanceof ApiError && error.retryAfterSeconds) {
    return `요청이 너무 많습니다. 약 ${Math.ceil(error.retryAfterSeconds / 60)}분 후 다시 시도해 주세요.`
  }
  return error instanceof Error ? error.message : '요청을 처리하지 못했습니다. 다시 시도해 주세요.'
}
