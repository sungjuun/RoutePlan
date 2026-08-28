import { useEffect, useRef, useState } from 'react'
import { Camera, RotateCcw, Upload } from 'lucide-react'
import { api } from '../api/client'
import type { User } from '../types'
import { UserAvatar } from './UserAvatar'

export function ProfileImageEditor({ user, onChanged }: { user: User; onChanged: (user: User) => void }) {
  const input = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  useEffect(() => {
    if (!file) { setPreview(''); return }
    const url = URL.createObjectURL(file)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])
  const select = (next: File | undefined) => {
    setError(''); setMessage(''); setFile(null)
    if (!next) return
    if (!['image/png', 'image/jpeg'].includes(next.type) || next.size > 2 * 1024 * 1024 || next.size === 0) {
      setError('2MB 이하의 PNG 또는 JPEG 사진을 선택하세요.')
      if (input.current) input.current.value = ''
      return
    }
    setFile(next)
  }
  const save = async (remove = false) => {
    if (!remove && !file) return
    setBusy(true); setError(''); setMessage('')
    try {
      const result = remove ? await api.removeProfileImage() : await api.uploadProfileImage(file!)
      onChanged({ ...user, profileImageUrl: result.profileImageUrl })
      setFile(null)
      if (input.current) input.current.value = ''
      setMessage(remove ? '기본 프로필로 변경했습니다.' : '프로필 사진을 저장했습니다.')
    } catch (reason) { setError(reason instanceof Error ? reason.message : '프로필 사진 저장에 실패했습니다.') }
    finally { setBusy(false) }
  }
  return <section className="profile-image-editor panel" aria-label="프로필 사진 변경">
    <div className="profile-image-preview">{preview ? <img src={preview} alt="새 프로필 사진 미리보기" /> : <UserAvatar user={user} />}</div>
    <div className="profile-image-controls">
      <h2>프로필 사진</h2><p>PNG·JPEG, 최대 2MB · 가운데를 정사각형으로 저장합니다.</p>
      <input ref={input} className="sr-only" type="file" accept="image/png,image/jpeg" aria-label="프로필 사진 파일" disabled={busy} onChange={event => select(event.target.files?.[0])} />
      <div className="advanced-actions">
        <button className="button button-ghost button-small" disabled={busy} onClick={() => input.current?.click()}><Camera size={15} /> 사진 선택</button>
        {file && <><button className="button button-dark button-small" disabled={busy} onClick={() => void save()}><Upload size={15} /> {busy ? '저장 중…' : '사진 저장'}</button><button className="button button-ghost button-small" disabled={busy} onClick={() => { setFile(null); if (input.current) input.current.value = '' }}>선택 취소</button></>}
        {user.profileImageUrl && <button className="button button-ghost button-small" disabled={busy} onClick={() => void save(true)}><RotateCcw size={15} /> 기본 이미지</button>}
      </div>
      {error && <p className="inline-error" role="alert">{error}</p>}
      {message && <p role="status">{message}</p>}
    </div>
  </section>
}
