import { useEffect, useState } from 'react'
import { advanced, categories, categoryNames, type Expense, type ExpenseCategory, type Spending } from '../api/advanced'
import { amountInput, moneyLabel, parseMinor } from '../lib/money'
import type { Trip } from '../types'

export function SpendingPanel({ trip, onError }: { trip: Trip; onError: (e: unknown) => void }) {
  const [value, setValue] = useState<Spending | null>(null)
  const [date, setDate] = useState(trip.startDate)
  const [category, setCategory] = useState<ExpenseCategory>('FOOD')
  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [requestId, setRequestId] = useState<string>(() => crypto.randomUUID())
  const [editing, setEditing] = useState<number>()
  const [limitDate, setLimitDate] = useState('')
  const [limitCategory, setLimitCategory] = useState<ExpenseCategory | ''>('')
  const [limit, setLimit] = useState('')
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)
  useEffect(() => { let active = true; advanced.spending(trip.id).then(v => { if (active) setValue(v) }).catch(onError); return () => { active = false } }, [trip.id, onError, reload])
  const run = async (action: () => Promise<Spending>) => { setBusy(true); try { setValue(await action()) } catch (e) { onError(e) } finally { setBusy(false) } }
  if (!value) return <section className="advanced-panel"><h3>예산·지출 장부</h3><button onClick={() => setReload(r => r + 1)}>지출 장부 불러오기</button></section>
  const edit = (entry: Expense) => { setEditing(entry.id); setRequestId(entry.requestId); setDate(entry.date); setCategory(entry.category); setDescription(entry.description); setAmount(amountInput(entry.amountMinor, value.currency)) }
  return <section className="advanced-panel spending-panel" aria-label="예산과 실제 지출">
    <h3>예산·실제 지출 장부</h3><p>실제 지출 <strong>{moneyLabel(value.spentMinor, value.currency)}</strong>{value.totalLimitMinor != null && ` · 전체 예산 대비 잔액 ${moneyLabel(value.totalLimitMinor - value.spentMinor, value.currency)}`}</p>
    <button type="button" disabled={busy} onClick={() => void run(() => advanced.spending(trip.id))}>장부 새로고침</button>
    <p>날짜·항목 한도는 실제 지출 관리용입니다. 일정 장소 선택에는 여행 전체 예상 예산만 적용합니다.</p>
    <form onSubmit={e => { e.preventDefault(); void run(async () => {
      const result = await advanced.expense(trip.id, value.currency, { requestId, date, category, description, amountMinor: parseMinor(amount, value.currency, false)! }, editing)
      setDescription(''); setAmount(''); setEditing(undefined); setRequestId(crypto.randomUUID()); return result
    }) }}><fieldset className="advanced-grid" disabled={busy}>
      <label className="field"><span>지출 날짜</span><input aria-label="지출 날짜" type="date" value={date} min={trip.startDate} max={trip.endDate} required onChange={e => setDate(e.target.value)} /></label>
      <label className="field"><span>지출 항목</span><select aria-label="지출 항목" value={category} onChange={e => setCategory(e.target.value as ExpenseCategory)}>{categories.map(c => <option key={c} value={c}>{categoryNames[c]}</option>)}</select></label>
      <label className="field"><span>내용</span><input aria-label="지출 내용" value={description} maxLength={200} required onChange={e => setDescription(e.target.value)} /></label>
      <label className="field"><span>금액 · {value.currency}</span><input aria-label="실제 지출 금액" inputMode="decimal" value={amount} maxLength={16} required onChange={e => setAmount(e.target.value)} /></label>
      <button className="button button-ghost">{editing ? '지출 수정 저장' : '지출 기록'}</button>{editing && <button type="button" onClick={() => { setEditing(undefined); setRequestId(crypto.randomUUID()); setDescription(''); setAmount('') }}>수정 취소</button>}
    </fieldset></form>
    <div className="advanced-list">{value.expenses.map(entry => <article key={entry.id}><div><strong>{entry.description}</strong><small>{entry.date} · {categoryNames[entry.category]} · {moneyLabel(entry.amountMinor, value.currency)}</small></div><button disabled={busy} onClick={() => edit(entry)}>수정</button><button disabled={busy} onClick={() => { if (window.confirm('이 지출 기록을 삭제할까요?')) void run(() => advanced.deleteExpense(trip.id, entry.id)) }}>삭제</button></article>)}{!value.expenses.length && <p>아직 기록한 지출이 없습니다.</p>}</div>
    <h4>날짜별·항목별 한도</h4><form onSubmit={e => { e.preventDefault(); void run(() => {
      if (!limitDate && !limitCategory) throw new Error('날짜 또는 항목을 선택해 주세요.')
      const next = { date: limitDate || null, category: limitCategory || null, limitMinor: parseMinor(limit, value.currency, false)! }
      return advanced.allocations(trip.id, value.currency, [...value.scopes.filter(s => s.date !== next.date || s.category !== next.category), next])
    }) }}><fieldset className="advanced-grid" disabled={busy}>
      <label className="field"><span>날짜 · 비우면 전체 기간</span><input aria-label="한도 날짜" type="date" value={limitDate} min={trip.startDate} max={trip.endDate} onChange={e => setLimitDate(e.target.value)} /></label>
      <label className="field"><span>항목</span><select aria-label="한도 항목" value={limitCategory} onChange={e => setLimitCategory(e.target.value as ExpenseCategory | '')}><option value="">전체 항목</option>{categories.map(c => <option key={c} value={c}>{categoryNames[c]}</option>)}</select></label>
      <label className="field"><span>한도 · {value.currency}</span><input aria-label="구간 예산 금액" value={limit} inputMode="decimal" required maxLength={16} onChange={e => setLimit(e.target.value)} /></label><button className="button button-ghost">구간 한도 저장</button>
    </fieldset></form>
    <div className="advanced-list">{value.scopes.map(scope => <article key={`${scope.date}:${scope.category}`}><div><strong>{scope.date ?? '전체 기간'} · {scope.category ? categoryNames[scope.category] : '전체 항목'}</strong><small>{moneyLabel(scope.spentMinor, value.currency)} / {moneyLabel(scope.limitMinor, value.currency)} · {scope.remainingMinor < 0 ? '초과' : '잔액'} {moneyLabel(Math.abs(scope.remainingMinor), value.currency)}</small></div><button disabled={busy} onClick={() => void run(() => advanced.allocations(trip.id, value.currency, value.scopes.filter(s => s !== scope)))}>한도 삭제</button></article>)}</div>
    <small>날짜 한도와 항목 한도는 같은 지출을 각각 비교합니다. 서로 더하지 않습니다. 통화 변경 전에는 기록을 정리해야 합니다.</small>
  </section>
}
