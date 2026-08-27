import { useState, type FormEvent } from 'react'
import { ArrowRight, LockKeyhole, Mail, Route, UserRound } from 'lucide-react'
import { api } from '../api/client'
import type { User } from '../types'

interface Props {
  initialMode: 'login' | 'signup'
  onAuthenticated: (user: User) => void
  onBack: () => void
  onError: (error: unknown) => void
}

export function AuthPage({ initialMode, onAuthenticated, onBack, onError }: Props) {
  const [mode, setMode] = useState(initialMode)
  const [email, setEmail] = useState('')
  const [nickname, setNickname] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (mode === 'signup' && password !== confirmPassword) {
      onError(new Error('비밀번호 확인이 일치하지 않습니다.'))
      return
    }
    setSubmitting(true)
    try {
      const session = mode === 'login'
        ? await api.login(email, password)
        : await api.signup({ email, nickname, password })
      if (!session.user) throw new Error('로그인 정보를 확인하지 못했습니다.')
      onAuthenticated(session.user)
    } catch (error) {
      onError(error)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-story">
        <div className="wordmark wordmark-light"><span className="brand-mark"><Route size={21} /></span>RoutePlan</div>
        <div><span className="eyebrow eyebrow-light">YOUR ROUTE, YOUR RHYTHM</span><h1>여행의 좋은 순간을<br />놓치지 않도록</h1><p>계정에 여행과 일정 버전을 안전하게 연결하고, 공개 루트를 내 조건으로 다시 계산하세요.</p></div>
        <button onClick={onBack}>← 추천 루트로 돌아가기</button>
      </section>
      <section className="auth-form-wrap">
        <div className="auth-tabs">
          <button className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>로그인</button>
          <button className={mode === 'signup' ? 'active' : ''} onClick={() => setMode('signup')}>회원가입</button>
        </div>
        <div className="auth-copy"><span className="eyebrow">ROUTEPLAN ACCOUNT</span><h2>{mode === 'login' ? '다시 여행을 이어가세요' : '나만의 여행을 시작하세요'}</h2><p>{mode === 'login' ? '저장한 여행과 일정으로 돌아갑니다.' : '공개 루트를 저장하고 내 조건으로 최적화할 수 있습니다.'}</p></div>
        <form className="auth-form" onSubmit={submit}>
          <label><span><Mail size={15} /> 이메일</span><input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" maxLength={254} required placeholder="traveler@example.com" /></label>
          {mode === 'signup' && <label><span><UserRound size={15} /> 닉네임</span><input value={nickname} onChange={(event) => setNickname(event.target.value)} minLength={2} maxLength={50} required placeholder="여행자 이름" /></label>}
          <label><span><LockKeyhole size={15} /> 비밀번호</span><input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={10} maxLength={72} required placeholder="10자 이상 입력" /></label>
          {mode === 'signup' && <label><span><LockKeyhole size={15} /> 비밀번호 확인</span><input type="password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} autoComplete="new-password" minLength={10} maxLength={72} required /></label>}
          <button className="button button-primary button-large" disabled={submitting}>{submitting ? '확인하는 중…' : mode === 'login' ? '로그인' : '계정 만들기'}{!submitting && <ArrowRight size={17} />}</button>
        </form>
      </section>
    </main>
  )
}
