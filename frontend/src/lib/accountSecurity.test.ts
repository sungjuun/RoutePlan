import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/client'
import { accountError, parseAccountLink, passwordValidation } from './accountSecurity'

describe('account security', () => {
  it('reads only exact supported fragment tokens and rejects malformed links', () => {
    const token = 'a'.repeat(43)
    expect(parseAccountLink(`#verify-email=${token}`)).toEqual({ kind: 'verify', token })
    expect(parseAccountLink(`#reset-password=${token}`)).toEqual({ kind: 'reset', token })
    expect(parseAccountLink('?reset-password=' + token)).toBeNull()
    expect(parseAccountLink('#reset-password=short')).toEqual({ kind: 'reset', token: '' })
    expect(parseAccountLink(`#reset-password=${token}&redirect=https://example.com`)).toEqual({ kind: 'reset', token: '' })
  })
  it('checks the UTF-8 limit as well as length and confirmation', () => {
    expect(passwordValidation('short', 'short')).toMatch(/10자/)
    expect(passwordValidation('가'.repeat(25), '가'.repeat(25))).toMatch(/72바이트/)
    expect(passwordValidation('good-password', 'wrong-password')).toMatch(/일치/)
    expect(passwordValidation('가'.repeat(24), '가'.repeat(24))).toBeNull()
  })
  it('shows a useful rate-limit wait without exposing backend internals', () => {
    expect(accountError(new ApiError(429, { code: 'AUTH_RATE_LIMITED', message: 'limited', path: '/auth/login', timestamp: '', violations: [] }, 121))).toContain('3분')
  })
})
