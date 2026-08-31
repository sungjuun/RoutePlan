import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { AccountLinkPage } from './AccountLinkPage'
import { AccountSecurityPanel } from './AccountSecurityPanel'
import { AccountManagementPanel } from './AccountManagementPanel'
import { AuthPage } from './AuthPage'

vi.mock('../api/client', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/client')>(),
  api: {
    verifyEmail: vi.fn(), resetPassword: vi.fn(), changePassword: vi.fn(),
    changeNickname: vi.fn(), changeEmail: vi.fn(), deleteAccount: vi.fn(),
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

  it('changes the nickname and protects email change and account deletion', async () => {
    const renamed = { ...user, nickname: '새 여행자' }
    vi.mocked(api.changeNickname).mockResolvedValue(renamed)
    vi.mocked(api.changeEmail).mockResolvedValue(undefined)
    vi.mocked(api.deleteAccount).mockResolvedValue(undefined)
    const updated = vi.fn()
    const emailChanged = vi.fn()
    const deleted = vi.fn()
    render(<AccountManagementPanel user={user} onUserChanged={updated} onEmailChanged={emailChanged} onAccountDeleted={deleted} />)

    fireEvent.change(screen.getByLabelText('닉네임'), { target: { value: renamed.nickname } })
    fireEvent.click(screen.getByRole('button', { name: '닉네임 저장' }))
    await waitFor(() => expect(updated).toHaveBeenCalledWith(renamed))
    expect(api.changeNickname).toHaveBeenCalledWith(renamed.nickname)

    fireEvent.click(screen.getByText('이메일 변경', { exact: true }))
    fireEvent.change(screen.getByLabelText('새 이메일'), { target: { value: 'new@example.com' } })
    fireEvent.change(screen.getAllByLabelText('현재 비밀번호')[0], { target: { value: 'current-password' } })
    fireEvent.click(screen.getByRole('button', { name: '이메일 변경하고 로그아웃' }))
    await waitFor(() => expect(emailChanged).toHaveBeenCalledOnce())
    expect(api.changeEmail).toHaveBeenCalledWith('current-password', 'new@example.com')

    fireEvent.click(screen.getByText('회원 탈퇴', { exact: true }))
    const deleteButton = screen.getByRole('button', { name: '계정과 모든 데이터 삭제' })
    expect(deleteButton).toBeDisabled()
    fireEvent.change(screen.getAllByLabelText('현재 비밀번호')[1], { target: { value: 'current-password' } })
    fireEvent.change(screen.getByLabelText('확인 문구'), { target: { value: '회원 탈퇴' } })
    fireEvent.click(deleteButton)
    await waitFor(() => expect(deleted).toHaveBeenCalledOnce())
    expect(api.deleteAccount).toHaveBeenCalledWith('current-password', '회원 탈퇴')
  })
})
