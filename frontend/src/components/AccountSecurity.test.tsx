import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { AccountLinkPage } from './AccountLinkPage'
import { AccountSecurityPanel } from './AccountSecurityPanel'
import { AuthPage } from './AuthPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/client')>(),
  api: {
    verifyEmail: vi.fn(), resetPassword: vi.fn(), changePassword: vi.fn(),
    getAuthOptions: vi.fn(), requestEmailVerification: vi.fn(), getAuthSession: vi.fn(),
    requestPasswordReset: vi.fn(),
  },
}))
const user = { id: 1, email: 'test@example.com', nickname: '여행자', createdAt: '2026-08-28T00:00:00Z', emailVerified: false }
const token = 'a'.repeat(43)

describe('account security screens', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getAuthOptions).mockResolvedValue({ mailMode: 'LOCAL' })
  })

  it('requires an explicit click before consuming an email link', async () => {
    vi.mocked(api.verifyEmail).mockResolvedValue(undefined)
    render(<AccountLinkPage link={{ kind: 'verify', token }} onClose={vi.fn()} onSessionRevoked={vi.fn()} />)
    expect(api.verifyEmail).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: '이메일 인증하기' }))
    expect(await screen.findByRole('heading', { name: '이메일 인증 완료' })).toBeInTheDocument()
    expect(api.verifyEmail).toHaveBeenCalledWith(token)
  })

  it('rejects mismatched passwords and clears the account session after reset', async () => {
    vi.mocked(api.resetPassword).mockResolvedValue(undefined)
    const revoked = vi.fn()
    render(<AccountLinkPage link={{ kind: 'reset', token }} onClose={vi.fn()} onSessionRevoked={revoked} />)
    fireEvent.change(screen.getByLabelText('새 비밀번호', { exact: true }), { target: { value: 'new-password-2026' } })
    fireEvent.change(screen.getByLabelText('새 비밀번호 확인'), { target: { value: 'mismatch-2026' } })
    fireEvent.click(screen.getByRole('button', { name: '비밀번호 재설정하기' }))
    expect(screen.getByRole('alert')).toHaveTextContent('일치하지')
    expect(api.resetPassword).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText('새 비밀번호 확인'), { target: { value: 'new-password-2026' } })
    fireEvent.click(screen.getByRole('button', { name: '비밀번호 재설정하기' }))
    expect(await screen.findByRole('heading', { name: '비밀번호 재설정 완료' })).toBeInTheDocument()
    expect(revoked).toHaveBeenCalledOnce()
  })

  it('shows an expired link error without exposing or consuming another token', async () => {
    vi.mocked(api.verifyEmail).mockRejectedValue(new Error('유효하지 않거나 만료된 링크입니다.'))
    render(<AccountLinkPage link={{ kind: 'verify', token }} onClose={vi.fn()} onSessionRevoked={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: '이메일 인증하기' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('만료된 링크')
    expect(document.body.textContent).not.toContain(token)
  })

  it('requests a recovery email and distinguishes the local development mailbox', async () => {
    vi.mocked(api.requestPasswordReset).mockResolvedValue({ message: '가입된 이메일이라면 재설정 메일을 보냈습니다.' })
    render(<AuthPage initialMode="login" onAuthenticated={vi.fn()} onBack={vi.fn()} onError={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: '비밀번호를 잊으셨나요?' }))
    expect(await screen.findByText(/로컬 개발 메일함/)).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText(/이메일/), { target: { value: user.email } })
    fireEvent.click(screen.getByRole('button', { name: '재설정 메일 보내기' }))
    expect(await screen.findByRole('status')).toHaveTextContent('가입된 이메일이라면')
    expect(api.requestPasswordReset).toHaveBeenCalledWith(user.email)
  })

  it('resends verification, refreshes status and requires current password for changes', async () => {
    vi.mocked(api.requestEmailVerification).mockResolvedValue(undefined)
    vi.mocked(api.getAuthSession).mockResolvedValue({ authenticated: true, user: { ...user, emailVerified: true } })
    vi.mocked(api.changePassword).mockResolvedValue(undefined)
    const changed = vi.fn()
    const updated = vi.fn()
    render(<AccountSecurityPanel user={user} onUserChanged={updated} onPasswordChanged={changed} />)
    fireEvent.click(screen.getByRole('button', { name: '인증 메일 보내기' }))
    expect(await screen.findByRole('status')).toHaveTextContent('24시간')
    fireEvent.click(screen.getByRole('button', { name: '인증 상태 새로고침' }))
    await waitFor(() => expect(updated).toHaveBeenCalledWith({ ...user, emailVerified: true }))
    fireEvent.click(screen.getByText('비밀번호 변경', { exact: true }))
    fireEvent.change(screen.getByLabelText('현재 비밀번호'), { target: { value: 'current-password' } })
    fireEvent.change(screen.getByLabelText('새 비밀번호', { exact: true }), { target: { value: 'new-password-2026' } })
    fireEvent.change(screen.getByLabelText('새 비밀번호 확인'), { target: { value: 'new-password-2026' } })
    fireEvent.click(screen.getByRole('button', { name: '비밀번호 변경하고 로그아웃' }))
    await waitFor(() => expect(changed).toHaveBeenCalledOnce())
    expect(api.changePassword).toHaveBeenCalledWith('current-password', 'new-password-2026')
  })
})
