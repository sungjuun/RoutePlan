import { useState } from 'react'
import { UserRound } from 'lucide-react'
import type { User } from '../types'

export function UserAvatar({ user }: { user: User | null }) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  return user?.profileImageUrl && failedUrl !== user.profileImageUrl
    ? <img className="user-avatar-image" src={user.profileImageUrl} alt="" onError={() => setFailedUrl(user.profileImageUrl ?? null)} />
    : <UserRound aria-hidden="true" size={20} />
}
