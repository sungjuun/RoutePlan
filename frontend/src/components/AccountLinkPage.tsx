import { useState, type FormEvent } from 'react'
import { ArrowLeft, CheckCircle2, ShieldCheck } from 'lucide-react'
import { api } from '../api/client'
import { accountError, passwordValidation, type AccountLink } from '../lib/accountSecurity'

interface Props {
  link: AccountLink
  onClose: () => void
  onSessionRevoked: () => void
}

export function AccountLinkPage({ link, onClose, onSessionRevoked }: Props) {
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState(link.token ? '' : '유효하지 않은 링크입니다. 메일을 다시 요청해 주세요.')
  const verify = link.kind === 'verify'

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!verify) {
      const validation = passwordValidation(password, confirmation)
      if (validation) { setError(validation); return }
    }
    setBusy(true)
    setError('')
    try {
      if (verify) await api.verifyEmail(link.token)
      else {
        await api.resetPassword(link.token, password)
        setPassword('')
        setConfirmation('')
        onSessionRevoked()
      }
      setDone(true)
    } catch (cause) { setError(accountError(cause)) }
    finally { setBusy(false) }
  }

  return (
    <main className="account-link-page">
      <section className="panel account-link-card">
        {done ? <CheckCircle2 size={32} /> : <ShieldCheck size={32} />}
        <span className="eyebrow">ROUTEPLAN ACCOUNT</span>
        <h1>{done ? (verify ? '이메일 인증 완료' : '비밀번호 재설정 완료') : (verify ? '이메일 주소 인증' : '새 비밀번호 설정')}</h1>
        <p>{done ? (verify ? '이메일 주소를 확인했습니다. 안심하고 여행을 이어가세요.' : '모든 기기에서 로그아웃했습니다. 새 비밀번호로 다시 로그인해 주세요.')
          : (verify ? '본인이 요청한 메일이라면 아래 버튼을 눌러 인증을 완료하세요. 링크를 여는 것만으로는 인증되지 않습니다.' : '다른 곳에서 사용하지 않는 비밀번호를 정해 주세요. 변경하면 모든 기기의 로그인이 해제됩니다.')}</p>
        {!done && <form className="auth-form" onSubmit={submit}>
          {!verify && <>
            <label><span>새 비밀번호</span><input type="password" autoComplete="new-password" minLength={10} maxLength={72} required value={password} onChange={event => setPassword(event.target.value)} /></label>
            <label><span>새 비밀번호 확인</span><input type="password" autoComplete="new-password" minLength={10} maxLength={72} required value={confirmation} onChange={event => setConfirmation(event.target.value)} /></label>
            <small className="security-hint">10자 이상, UTF-8 기준 72바이트 이하</small>
          </>}
          {error && <p className="security-error" role="alert">{error}</p>}
          <button className="button button-primary" disabled={busy || !link.token}>{busy ? '확인하는 중…' : verify ? '이메일 인증하기' : '비밀번호 재설정하기'}</button>
        </form>}
        <button className="button button-ghost" onClick={onClose} disabled={busy}><ArrowLeft size={16} />{done && verify ? '여행으로 돌아가기' : '로그인 화면으로'}</button>
      </section>
    </main>
  )
}
