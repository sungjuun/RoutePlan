import { useState, type FormEvent } from 'react'
import { CircleAlert, Mail, Trash2, UserRoundPen } from 'lucide-react'
import { api } from '../api/client'
import { accountError } from '../lib/accountSecurity'
import type { User } from '../types'

interface Props {
  user: User
  onUserChanged: (user: User) => void
  onEmailChanged: () => void
  onAccountDeleted: () => void
}

export function AccountManagementPanel({ user, onUserChanged, onEmailChanged, onAccountDeleted }: Props) {
  const [nickname, setNickname] = useState(user.nickname)
  const [nicknameBusy, setNicknameBusy] = useState(false)
  const [nicknameMessage, setNicknameMessage] = useState('')
  const [nicknameError, setNicknameError] = useState('')
  const [newEmail, setNewEmail] = useState('')
  const [emailPassword, setEmailPassword] = useState('')
  const [emailBusy, setEmailBusy] = useState(false)
  const [emailError, setEmailError] = useState('')
  const [deletePassword, setDeletePassword] = useState('')
  const [deleteConfirmation, setDeleteConfirmation] = useState('')
  const [deleteBusy, setDeleteBusy] = useState(false)
  const [deleteError, setDeleteError] = useState('')

  async function saveNickname(event: FormEvent) {
    event.preventDefault()
    const normalized = nickname.trim()
    if (normalized.length < 2) {
      setNicknameError('닉네임은 공백을 제외하고 2자 이상 입력해 주세요.')
      return
    }
    setNicknameBusy(true)
    setNicknameError('')
    setNicknameMessage('')
    try {
      const updated = await api.changeNickname(normalized)
      setNickname(updated.nickname)
      onUserChanged(updated)
      setNicknameMessage('닉네임을 변경했습니다.')
    } catch (cause) { setNicknameError(accountError(cause)) }
    finally { setNicknameBusy(false) }
  }

  async function saveEmail(event: FormEvent) {
    event.preventDefault()
    setEmailBusy(true)
    setEmailError('')
    try {
      await api.changeEmail(emailPassword, newEmail.trim())
      onEmailChanged()
    } catch (cause) { setEmailError(accountError(cause)) }
    finally { setEmailBusy(false) }
  }

  async function removeAccount(event: FormEvent) {
    event.preventDefault()
    if (deleteConfirmation.trim() !== '회원 탈퇴') {
      setDeleteError('확인 문구에 ‘회원 탈퇴’를 정확히 입력해 주세요.')
      return
    }
    setDeleteBusy(true)
    setDeleteError('')
    try {
      await api.deleteAccount(deletePassword, deleteConfirmation.trim())
      onAccountDeleted()
    } catch (cause) { setDeleteError(accountError(cause)) }
    finally { setDeleteBusy(false) }
  }

  const busy = nicknameBusy || emailBusy || deleteBusy

  return (
    <section className="panel account-management" aria-label="계정 관리">
      <span className="eyebrow">ACCOUNT MANAGEMENT</span><h2><UserRoundPen size={21} /> 계정 관리</h2>
      <form className="auth-form account-operation" onSubmit={saveNickname}>
        <h3><UserRoundPen size={18} /> 닉네임 변경</h3>
        <label><span>닉네임</span><input autoComplete="nickname" required minLength={2} maxLength={50} value={nickname} onChange={event => setNickname(event.target.value)} /></label>
        {nicknameMessage && <p role="status" className="security-success">{nicknameMessage}</p>}
        {nicknameError && <p role="alert" className="security-error">{nicknameError}</p>}
        <button className="button button-secondary" disabled={busy || nickname.trim() === user.nickname}>{nicknameBusy ? '저장하는 중…' : '닉네임 저장'}</button>
      </form>

      <details className="account-operation">
        <summary><Mail size={17} /> 이메일 변경</summary>
        <p className="security-hint">변경한 주소로 인증 메일을 보냅니다. 모든 기기에서 로그아웃되며 새 이메일로 다시 로그인해야 합니다.</p>
        <form className="auth-form" onSubmit={saveEmail}>
          <label><span>새 이메일</span><input type="email" autoComplete="email" required maxLength={254} value={newEmail} onChange={event => setNewEmail(event.target.value)} /></label>
          <label><span>현재 비밀번호</span><input type="password" autoComplete="current-password" required maxLength={72} value={emailPassword} onChange={event => setEmailPassword(event.target.value)} /></label>
          {emailError && <p role="alert" className="security-error">{emailError}</p>}
          <button className="button button-primary" disabled={busy}>{emailBusy ? '변경하는 중…' : '이메일 변경하고 로그아웃'}</button>
        </form>
      </details>

      <details className="account-operation danger-zone">
        <summary><Trash2 size={17} /> 회원 탈퇴</summary>
        <div className="danger-notice"><CircleAlert size={18} /><p>저장한 여행, 일정, 공개 루트, 댓글·후기·좋아요와 프로필 정보가 삭제되며 복구할 수 없습니다.</p></div>
        <form className="auth-form" onSubmit={removeAccount}>
          <label><span>현재 비밀번호</span><input type="password" autoComplete="current-password" required maxLength={72} value={deletePassword} onChange={event => setDeletePassword(event.target.value)} /></label>
          <label><span>확인 문구</span><input required maxLength={20} placeholder="회원 탈퇴" value={deleteConfirmation} onChange={event => setDeleteConfirmation(event.target.value)} /></label>
          <small className="security-hint">위 입력란에 ‘회원 탈퇴’를 정확히 입력해야 삭제할 수 있습니다.</small>
          {deleteError && <p role="alert" className="security-error">{deleteError}</p>}
          <button className="button button-danger-ghost" disabled={busy || deleteConfirmation.trim() !== '회원 탈퇴'}>{deleteBusy ? '삭제하는 중…' : '계정과 모든 데이터 삭제'}</button>
        </form>
      </details>
    </section>
  )
}
