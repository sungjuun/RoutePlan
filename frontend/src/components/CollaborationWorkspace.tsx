import { useEffect, useState } from 'react'
import {
  ArrowRight,
  Check,
  CircleDollarSign,
  MapPin,
  Navigation,
  ThumbsDown,
  ThumbsUp,
  Trash2,
  UserPlus,
  UsersRound,
} from 'lucide-react'
import { api } from '../api/client'
import { categories, categoryNames, type ExpenseCategory } from '../api/advanced'
import { distanceLabel, durationLabel } from '../lib/format'
import { moneyLabel, parseMinor } from '../lib/money'
import type {
  NearbyPlaceRecommendation,
  Trip,
  TripCollaboration,
  TripMemberRole,
  TripSettlement,
  User,
} from '../types'
import { AsyncState } from './AsyncState'

interface Props {
  trip: Trip
  user: User
  onTripChanged: (trip: Trip, message: string) => void
  onNotify: (kind: 'success' | 'error' | 'info', message: string) => void
  onError: (error: unknown) => void
}

const roleLabel: Record<TripMemberRole, string> = {
  OWNER: '소유자',
  EDITOR: '편집자',
  VIEWER: '조회자',
}

const bandLabel = {
  MUST: '필수 방문',
  HIGH: '모두 희망',
  NORMAL: '다수 희망',
  LOW: '일부 희망',
  UNVOTED: '투표 전',
}

export function CollaborationWorkspace({ trip, user, onTripChanged, onNotify, onError }: Props) {
  const [collaboration, setCollaboration] = useState<TripCollaboration | null>(null)
  const [settlement, setSettlement] = useState<TripSettlement | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteRole, setInviteRole] = useState<Exclude<TripMemberRole, 'OWNER'>>('EDITOR')
  const [date, setDate] = useState(trip.startDate)
  const [category, setCategory] = useState<ExpenseCategory>('FOOD')
  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [payerId, setPayerId] = useState(user.id)
  const [participantIds, setParticipantIds] = useState<number[]>([])
  const [placeId, setPlaceId] = useState<number | null>(null)
  const [currentLatitude, setCurrentLatitude] = useState(String(trip.accommodationLatitude))
  const [currentLongitude, setCurrentLongitude] = useState(String(trip.accommodationLongitude))
  const [currentTime, setCurrentTime] = useState(trip.dailyStartTime.slice(0, 5))
  const [availableMinutes, setAvailableMinutes] = useState('90')
  const [nextPlaceId, setNextPlaceId] = useState<number | undefined>()
  const [nearby, setNearby] = useState<NearbyPlaceRecommendation[]>([])

  useEffect(() => {
    let active = true
    Promise.all([api.getTripCollaboration(trip.id), api.getTripSettlement(trip.id)])
      .then(([nextCollaboration, nextSettlement]) => {
        if (!active) return
        setCollaboration(nextCollaboration)
        setSettlement(nextSettlement)
        setPayerId(nextCollaboration.members.some(member => member.userId === user.id)
          ? user.id : nextCollaboration.members[0]?.userId ?? user.id)
        setParticipantIds(nextCollaboration.members.map(member => member.userId))
      })
      .catch(onError)
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [onError, trip.id, user.id])

  const canManage = collaboration?.currentRole === 'OWNER'
  const canEdit = collaboration?.currentRole !== 'VIEWER'
  const runCollaboration = async (action: () => Promise<TripCollaboration>, message: string) => {
    setBusy(true)
    try {
      const next = await action()
      setCollaboration(next)
      setParticipantIds(current => current.filter(id => next.members.some(member => member.userId === id)))
      setPayerId(current => next.members.some(member => member.userId === current)
        ? current : next.members[0]?.userId ?? user.id)
      onNotify('success', message)
      return true
    } catch (error) {
      onError(error)
      return false
    } finally {
      setBusy(false)
    }
  }

  const runSettlement = async (action: () => Promise<TripSettlement>, message: string) => {
    setBusy(true)
    try {
      setSettlement(await action())
      onNotify('success', message)
      return true
    } catch (error) {
      onError(error)
      return false
    } finally {
      setBusy(false)
    }
  }

  const locate = () => {
    if (!navigator.geolocation) {
      onNotify('info', '이 브라우저에서는 현재 위치를 사용할 수 없습니다.')
      return
    }
    navigator.geolocation.getCurrentPosition(
      position => {
        setCurrentLatitude(position.coords.latitude.toFixed(6))
        setCurrentLongitude(position.coords.longitude.toFixed(6))
        onNotify('success', '현재 위치를 입력했습니다.')
      },
      () => onNotify('info', '위치 권한을 허용하거나 좌표를 직접 입력해 주세요.'),
      { enableHighAccuracy: true, timeout: 8_000 },
    )
  }

  if (loading) {
    return <section className="content-section"><AsyncState kind="loading" title="동행 여행을 불러오는 중입니다" /></section>
  }
  if (!collaboration || !settlement) {
    return <section className="content-section"><AsyncState kind="error" title="동행 여행을 불러오지 못했습니다" /></section>
  }

  return (
    <section className="content-section collaboration-section">
      <header className="collaboration-titlebar">
        <div><span className="eyebrow">TRAVEL TOGETHER</span><h1>동행 여행</h1><p>함께 고르고, 같이 쓰고, 마지막 송금까지 한곳에서 정리하세요.</p></div>
        <span className={`role-chip role-${collaboration.currentRole.toLowerCase()}`}>{roleLabel[collaboration.currentRole]}</span>
      </header>

      <div className="collaboration-grid">
        <section className="panel collaboration-panel">
          <div className="panel-heading"><div><UsersRound size={19} /><h2>여행 동행자</h2></div><span>{collaboration.members.length}/20명</span></div>
          {canManage && (
            <form className="member-invite-form" onSubmit={event => {
              event.preventDefault()
              void runCollaboration(
                () => api.addTripMember(trip.id, inviteEmail, inviteRole),
                `${inviteEmail} 사용자를 동행자로 추가했습니다.`,
              ).then(success => { if (success) setInviteEmail('') })
            }}>
              <label className="field"><span>가입 이메일</span><input type="email" value={inviteEmail} maxLength={254} required placeholder="traveler@example.com" onChange={event => setInviteEmail(event.target.value)} /></label>
              <label className="field"><span>권한</span><select value={inviteRole} onChange={event => setInviteRole(event.target.value as Exclude<TripMemberRole, 'OWNER'>)}><option value="EDITOR">편집자</option><option value="VIEWER">조회자</option></select></label>
              <button className="button button-primary" disabled={busy}><UserPlus size={16} /> 동행자 추가</button>
            </form>
          )}
          <div className="member-list">
            {collaboration.members.map(member => (
              <article key={member.memberId}>
                <span className="member-avatar">{member.nickname.slice(0, 1)}</span>
                <div><strong>{member.nickname}{member.userId === user.id && ' · 나'}</strong><small>{member.email ?? '이메일 미등록'} · {roleLabel[member.role]}</small></div>
                {canManage && member.role !== 'OWNER' && <>
                  <select aria-label={`${member.nickname} 권한`} value={member.role} disabled={busy} onChange={event => void runCollaboration(
                    () => api.updateTripMember(trip.id, member.memberId, event.target.value as Exclude<TripMemberRole, 'OWNER'>),
                    `${member.nickname}님의 권한을 변경했습니다.`,
                  )}><option value="EDITOR">편집자</option><option value="VIEWER">조회자</option></select>
                  <button className="icon-button" aria-label={`${member.nickname} 내보내기`} disabled={busy} onClick={() => {
                    if (window.confirm(`${member.nickname}님을 이 여행에서 내보낼까요?`)) void runCollaboration(
                      () => api.removeTripMember(trip.id, member.memberId),
                      `${member.nickname}님을 여행에서 내보냈습니다.`,
                    )
                  }}><Trash2 size={15} /></button>
                </>}
              </article>
            ))}
          </div>
          {!canManage && <small>동행자 추가와 권한 변경은 여행 소유자만 할 수 있습니다.</small>}
        </section>

        <section className="panel collaboration-panel vote-panel">
          <div className="panel-heading"><div><ThumbsUp size={19} /><h2>가고 싶은 장소 투표</h2></div><span>투표는 다음 최적화에 반영</span></div>
          <div className="vote-list">
            {collaboration.places.map(place => (
              <article key={place.placeId}>
                <div><strong>{place.placeName}</strong><small>{bandLabel[place.priorityBand]} · 적용 우선순위 {place.effectivePriority}</small></div>
                <div className="vote-counts"><span><ThumbsUp size={14} /> {place.yesCount}</span><span><ThumbsDown size={14} /> {place.noCount}</span><span>대기 {place.pendingCount}</span></div>
                <div className="vote-actions">
                  <button className={place.myVote === 'YES' ? 'active yes' : ''} disabled={busy} aria-label={`${place.placeName} 가고 싶어요`} onClick={() => void runCollaboration(
                    () => api.voteTripPlace(trip.id, place.placeId, 'YES'),
                    `${place.placeName}에 찬성했습니다.`,
                  )}><ThumbsUp size={15} /></button>
                  <button className={place.myVote === 'NO' ? 'active no' : ''} disabled={busy} aria-label={`${place.placeName} 제외 희망`} onClick={() => void runCollaboration(
                    () => api.voteTripPlace(trip.id, place.placeId, 'NO'),
                    `${place.placeName}에 반대했습니다.`,
                  )}><ThumbsDown size={15} /></button>
                  {place.myVote && <button disabled={busy} onClick={() => void runCollaboration(
                    () => api.removeTripPlaceVote(trip.id, place.placeId),
                    `${place.placeName} 투표를 취소했습니다.`,
                  )}>취소</button>}
                </div>
              </article>
            ))}
            {collaboration.places.length === 0 && <p>장소를 먼저 여행에 추가하면 함께 투표할 수 있습니다.</p>}
          </div>
          <small>필수 방문 장소는 투표보다 우선합니다. 모두 찬성 90점, 3분의 2 이상 60점, 일부 찬성 30점으로 다음 일정 계산에 반영됩니다.</small>
        </section>
      </div>

      <section className="panel collaboration-panel settlement-panel">
        <div className="panel-heading"><div><CircleDollarSign size={19} /><h2>N분의 1 정산</h2></div><span>{settlement.exactMinimum ? '최소 송금안 계산' : '빠른 송금안 계산'}</span></div>
        {canEdit && <form className="settlement-form" onSubmit={event => {
          event.preventDefault()
          const amountMinor = parseMinor(amount, settlement.currency, false)
          if (amountMinor == null) return
          void runSettlement(() => api.addSharedExpense(trip.id, {
            requestId: crypto.randomUUID(), date, category, description, amountMinor,
            placeId, currency: settlement.currency, payerUserId: payerId,
            participantUserIds: participantIds,
          }), '공동 지출을 기록하고 정산안을 다시 계산했습니다.').then(success => {
            if (success) { setDescription(''); setAmount(''); setPlaceId(null) }
          })
        }}>
          <div className="advanced-grid">
            <label className="field"><span>날짜</span><input type="date" min={trip.startDate} max={trip.endDate} value={date} required onChange={event => setDate(event.target.value)} /></label>
            <label className="field"><span>항목</span><select value={category} onChange={event => setCategory(event.target.value as ExpenseCategory)}>{categories.map(value => <option key={value} value={value}>{categoryNames[value]}</option>)}</select></label>
            <label className="field"><span>내용</span><input value={description} maxLength={200} required placeholder="저녁 식사" onChange={event => setDescription(event.target.value)} /></label>
            <label className="field"><span>금액 · {settlement.currency}</span><input value={amount} inputMode="decimal" required onChange={event => setAmount(event.target.value)} /></label>
            <label className="field"><span>결제자</span><select value={payerId} onChange={event => setPayerId(Number(event.target.value))}>{collaboration.members.map(member => <option key={member.userId} value={member.userId}>{member.nickname}</option>)}</select></label>
            <label className="field"><span>연결 장소 · 선택</span><select value={placeId ?? ''} onChange={event => setPlaceId(event.target.value ? Number(event.target.value) : null)}><option value="">장소 연결 안 함</option>{trip.places.map(place => <option key={place.placeId} value={place.placeId}>{place.name}</option>)}</select></label>
          </div>
          <fieldset className="participant-picker"><legend>분담자</legend>{collaboration.members.map(member => <label key={member.userId}><input type="checkbox" checked={participantIds.includes(member.userId)} onChange={event => setParticipantIds(current => event.target.checked ? [...current, member.userId] : current.filter(id => id !== member.userId))} /><span>{member.nickname}</span></label>)}</fieldset>
          <button className="button button-primary" disabled={busy || participantIds.length === 0}><Check size={16} /> 공동 지출 기록</button>
        </form>}

        <div className="settlement-summary">
          <div className="balance-grid">{settlement.balances.map(balance => <article key={balance.userId}><strong>{balance.nickname}</strong><span>결제 {moneyLabel(balance.paidMinor, settlement.currency)}</span><span>부담 {moneyLabel(balance.owedMinor, settlement.currency)}</span><em className={balance.netMinor < 0 ? 'owes' : balance.netMinor > 0 ? 'receives' : ''}>{balance.netMinor > 0 ? '받을 금액' : balance.netMinor < 0 ? '보낼 금액' : '정산 완료'} {balance.netMinor === 0 ? '' : moneyLabel(Math.abs(balance.netMinor), settlement.currency)}</em></article>)}</div>
          <div className="transfer-list"><h3>최종 송금</h3>{settlement.transfers.map((transfer, index) => <article key={`${transfer.fromUserId}-${transfer.toUserId}-${index}`}><strong>{transfer.fromNickname}</strong><ArrowRight size={16} /><strong>{transfer.toNickname}</strong><span>{moneyLabel(transfer.amountMinor, settlement.currency)}</span></article>)}{settlement.transfers.length === 0 && <p>현재 주고받을 금액이 없습니다.</p>}</div>
        </div>
        <div className="advanced-list shared-expense-list">{settlement.expenses.map(expense => <article key={expense.expenseId}><div><strong>{expense.description}</strong><small>{expense.date} · {categoryNames[expense.category]} · {expense.payerNickname} 결제 · {expense.participants.length}명 분담 · {moneyLabel(expense.amountMinor, settlement.currency)}</small></div>{canEdit && (collaboration.currentRole === 'OWNER' || expense.createdByUserId === user.id) && <button disabled={busy} onClick={() => {
          if (window.confirm('이 공동 지출을 삭제할까요?')) void runSettlement(
            () => api.removeSharedExpense(trip.id, expense.expenseId), '공동 지출을 삭제했습니다.',
          )
        }}><Trash2 size={14} /> 삭제</button>}</article>)}{settlement.expenses.length === 0 && <p>공동 지출을 기록하면 자동으로 N분의 1과 송금안을 계산합니다.</p>}</div>
      </section>

      <section className="panel collaboration-panel nearby-panel">
        <div className="panel-heading"><div><MapPin size={19} /><h2>빈 시간 주변 추천</h2></div><span>도보 이동 추정</span></div>
        <form onSubmit={event => {
          event.preventDefault()
          setBusy(true)
          api.getNearbyRecommendations(trip.id, {
            date, currentTime, currentLatitude: Number(currentLatitude), currentLongitude: Number(currentLongitude),
            nextPlaceId, availableMinutes: Number(availableMinutes), maxResults: 6,
          }).then(setNearby).catch(onError).finally(() => setBusy(false))
        }}>
          <div className="advanced-grid">
            <label className="field"><span>현재 위도</span><input type="number" step="0.000001" min="-90" max="90" value={currentLatitude} required onChange={event => setCurrentLatitude(event.target.value)} /></label>
            <label className="field"><span>현재 경도</span><input type="number" step="0.000001" min="-180" max="180" value={currentLongitude} required onChange={event => setCurrentLongitude(event.target.value)} /></label>
            <label className="field"><span>현재 시각</span><input type="time" value={currentTime} required onChange={event => setCurrentTime(event.target.value)} /></label>
            <label className="field"><span>남는 시간 · 분</span><input type="number" min="15" max="720" value={availableMinutes} required onChange={event => setAvailableMinutes(event.target.value)} /></label>
            <label className="field"><span>다음 일정 · 선택</span><select value={nextPlaceId ?? ''} onChange={event => setNextPlaceId(event.target.value ? Number(event.target.value) : undefined)}><option value="">현재 위치로 복귀</option>{trip.places.map(place => <option key={place.placeId} value={place.placeId}>{place.name}</option>)}</select></label>
          </div>
          <div className="nearby-actions"><button type="button" className="button button-ghost" onClick={locate}><Navigation size={16} /> 현재 위치 사용</button><button className="button button-primary" disabled={busy}><MapPin size={16} /> 주변 장소 찾기</button></div>
        </form>
        <div className="nearby-results">{nearby.map(place => <article key={place.placeId}><div><strong>{place.name}</strong><small>{place.category ?? '장소'} · 현재 위치에서 {distanceLabel(place.distanceFromCurrentMeters)} · 총 {durationLabel(place.requiredMinutes)}</small><div>{place.interestMatch && <span>내 취향</span>}{place.openingHoursKnown ? <span>영업시간 확인</span> : <span>영업시간 미확인</span>}<span>우회 {distanceLabel(place.detourMeters)}</span></div></div>{canEdit && <button className="button button-ghost" disabled={busy} onClick={() => {
            setBusy(true)
            api.addTripPlace(trip.id, place.placeId, { priority: 50, mustVisit: false, preferredStartTime: null, preferredEndTime: null, minimumStayMinutes: null, maximumStayMinutes: null })
              .then(nextTrip => { onTripChanged(nextTrip, `${place.name}을 여행에 추가했습니다.`); return api.getTripCollaboration(trip.id) })
              .then(setCollaboration).then(() => setNearby(current => current.filter(item => item.placeId !== place.placeId)))
              .catch(onError).finally(() => setBusy(false))
          }}>일정에 추가</button>}</article>)}{nearby.length === 0 && <p>현재 위치와 남는 시간을 입력하면 기존 일정에 없는 장소를 추천합니다.</p>}</div>
        <small>거리와 도보 시간은 직선거리 기반 추정치입니다. 영업시간 데이터가 없는 장소는 미확인으로 표시하며, 실제 방문 전 확인이 필요합니다.</small>
      </section>
    </section>
  )
}
