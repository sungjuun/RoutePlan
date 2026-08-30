import { useState, type FormEvent } from 'react'
import { MailCheck, ShieldCheck } from 'lucide-react'
import { api } from '../api/client'
import { accountError, passwordValidation } from '../lib/accountSecurity'
import type { User } from '../types'
import { AuthMailHelp } from './AuthMailHelp'

interface Props {
  user: User
  onUserChanged: (user: User) => void
  onPasswordChanged: () => void
}

export function AccountSecurityPanel({ user, onUserChanged, onPasswordChanged }: Props) {
  const [current, setCurrent] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [mailBusy, setMailBusy] = useState(false)
  const [mailError, setMailError] = useState('')

  async function sendVerification() {
    setMailBusy(true)
    setMailError('')
    setMessage('')
    try {
      await api.requestEmailVerification()
      setMessage('인증 메일을 요청했습니다. 링크는 24시간 동안 유효하며, 재발송은 1분 후 가능합니다.')
    } catch (cause) { setMailError(accountError(cause)) }
    finally { setMailBusy(false) }
  }

  async function refreshStatus() {
    setMailBusy(true)
    setMailError('')
    try {
      const session = await api.getAuthSession()
      if (!session.user) throw new Error('로그인 세션이 만료되었습니다. 다시 로그인해 주세요.')
      onUserChanged({ ...user, emailVerified: session.user.emailVerified })
      setMessage(session.user.emailVerified ? '이메일 인증 상태를 확인했습니다.' : '아직 인증되지 않았습니다. 메일의 인증 버튼을 눌러 주세요.')
    } catch (cause) { setMailError(accountError(cause)) }
    finally { setMailBusy(false) }
  }

  async function changePassword(event: FormEvent) {
    event.preventDefault()
    const validation = passwordValidation(password, confirmation)
    if (validation) { setError(validation); return }
    setBusy(true)
    setError('')
    try {
      await api.changePassword(current, password)
      setCurrent('')
      setPassword('')
      setConfirmation('')
      onPasswordChanged()
    } catch (cause) { setError(accountError(cause)) }
    finally { setBusy(false) }
  }

  return (
    <section className="panel account-security" aria-label="계정 보안">
      <span className="eyebrow">ACCOUNT SECURITY</span><h2><ShieldCheck size={21} /> 계정 보안</h2>
      <div className="email-verification">
        <h3><MailCheck size={19} /> 이메일 인증 <span className={`verification-badge ${user.emailVerified ? 'verified' : ''}`}>{user.emailVerified ? '인증 완료' : '인증 필요'}</span></h3>
        <p>{user.email}</p>
        {!user.emailVerified && <>
          <p className="security-hint">이메일 소유 여부를 확인해 주세요. 인증 전에도 기존 여행은 이용할 수 있습니다.</p>
          <div className="security-actions">
            <button className="button button-secondary" disabled={mailBusy || busy} onClick={() => void sendVerification()}>{mailBusy ? '확인하는 중…' : '인증 메일 보내기'}</button>
            <button className="button button-ghost" disabled={mailBusy || busy} onClick={() => void refreshStatus()}>인증 상태 새로고침</button>
          </div>
          <AuthMailHelp />
        </>}
        {message && <p role="status" className="security-success">{message}</p>}
        {mailError && <p role="alert" className="security-error">{mailError}</p>}
      </div>
      <details className="password-change">
        <summary>비밀번호 변경</summary>
        <p className="security-hint">변경하면 이 기기를 포함해 모든 기기에서 로그아웃합니다.</p>
        <form className="auth-form" onSubmit={changePassword}>
          <label><span>현재 비밀번호</span><input type="password" autoComplete="current-password" required maxLength={72} value={current} onChange={event => setCurrent(event.target.value)} /></label>
          <label><span>새 비밀번호</span><input type="password" autoComplete="new-password" required minLength={10} maxLength={72} value={password} onChange={event => setPassword(event.target.value)} /></label>
          <label><span>새 비밀번호 확인</span><input type="password" autoComplete="new-password" required minLength={10} maxLength={72} value={confirmation} onChange={event => setConfirmation(event.target.value)} /></label>
          <small className="security-hint">10자 이상, UTF-8 기준 72바이트 이하</small>
          {error && <p role="alert" className="security-error">{error}</p>}
          <button className="button button-primary" disabled={busy || mailBusy}>{busy ? '변경하는 중…' : '비밀번호 변경하고 로그아웃'}</button>
        </form>
      </details>
      <p className="security-hint">로그인은 서버 재시작 후에도 유지됩니다. 일정 시간 활동하지 않거나 로그아웃·비밀번호 변경 시 종료됩니다.</p>
    </section>
  )
}
