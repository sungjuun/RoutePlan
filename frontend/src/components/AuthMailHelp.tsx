import { useEffect, useState } from 'react'
import { api } from '../api/client'

export function AuthMailHelp() {
  const [mode, setMode] = useState<'DISABLED' | 'LOCAL' | 'SMTP' | null>(null)
  useEffect(() => {
    let active = true
    api.getAuthOptions().then(result => { if (active) setMode(result.mailMode) }).catch(() => {})
    return () => { active = false }
  }, [])
  if (mode === 'LOCAL') return <p className="security-hint">개발 환경: 실제 수신함이 아닌 로컬 개발 메일함에서 메일을 확인해 주세요. 기본 주소는 localhost:8026입니다.</p>
  if (mode === 'DISABLED') return <p className="security-hint">이메일 발송이 아직 설정되지 않았습니다. 관리자에게 문의해 주세요.</p>
  return <p className="security-hint">메일이 보이지 않으면 스팸함도 확인해 주세요. 전송까지 잠시 걸릴 수 있습니다.</p>
}
