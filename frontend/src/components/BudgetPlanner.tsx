import { useEffect, useState, type FormEvent } from 'react'
import { AlertCircle, Save, Wallet } from 'lucide-react'
import { api } from '../api/client'
import { amountInput, currencies, fractionDigits, parseMinor } from '../lib/money'
import type { BudgetCurrency, Trip, TripBudget } from '../types'

interface Props {
  trip: Trip
  onError: (error: unknown) => void
  onBlockedChange: (blocked: boolean) => void
}

type CostDraft = { placeId: number; placeName: string; mustVisit: boolean; amount: string }

export function BudgetPlanner({ trip, onError, onBlockedChange }: Props) {
  const [currency, setCurrency] = useState<BudgetCurrency>('KRW')
  const [limit, setLimit] = useState('')
  const [fixed, setFixed] = useState('0')
  const [costs, setCosts] = useState<CostDraft[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [dirty, setDirty] = useState(false)
  const [saved, setSaved] = useState(false)
  const [failed, setFailed] = useState(false)
  const [validation, setValidation] = useState('')
  const [reload, setReload] = useState(0)
  const placeKey = trip.places.map((place) => place.placeId).join(',')

  const apply = (value: TripBudget) => {
    setCurrency(value.currency)
    setLimit(amountInput(value.limitMinor, value.currency))
    setFixed(amountInput(value.fixedCostMinor, value.currency))
    setCosts(value.placeCosts.map((place) => ({ ...place, amount: amountInput(place.estimatedCostMinor, value.currency) })))
    setDirty(false)
  }

  useEffect(() => {
    let active = true
    setLoading(true)
    setFailed(false)
    setSaved(false)
    setValidation('')
    api.getTripBudget(trip.id)
      .then((value) => { if (active) apply(value) })
      .catch((error) => {
        if (active) { setFailed(true); onError(error) }
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [trip.id, placeKey, onError, reload])

  useEffect(() => {
    onBlockedChange(loading || saving || dirty || failed)
  }, [loading, saving, dirty, failed, onBlockedChange])

  const edit = () => { setDirty(true); setSaved(false); setValidation('') }
  const changeCurrency = (next: BudgetCurrency) => {
    if (next === currency) return
    setCurrency(next)
    setLimit('')
    setFixed('0')
    setCosts((current) => current.map((cost) => ({ ...cost, amount: '' })))
    edit()
  }

  const save = async (event: FormEvent) => {
    event.preventDefault()
    setValidation('')
    let input
    try {
      input = {
        currency,
        limitMinor: parseMinor(limit, currency),
        fixedCostMinor: parseMinor(fixed, currency, false)!,
        placeCosts: costs.map((cost) => ({ placeId: cost.placeId, estimatedCostMinor: parseMinor(cost.amount, currency) })),
      }
    } catch (error) {
      setValidation(error instanceof Error ? error.message : '금액을 확인해 주세요.')
      return
    }
    setSaving(true)
    try {
      const value = await api.replaceTripBudget(trip.id, input)
      apply(value)
      setSaved(true)
    } catch (error) {
      onError(error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="budget-planner" aria-label="여행 예산 설정" onSubmit={(event) => void save(event)}>
      <div className="budget-planner-head"><Wallet size={20} /><div><strong>여행 비용과 예산</strong><small>입력한 비용으로 예산을 계산합니다. 미입력과 무료(0)는 달라요.</small></div></div>
      {loading ? <p className="budget-hint">예산 설정을 불러오는 중…</p> : failed ? (
        <div className="budget-error" role="alert">예산 설정을 불러오지 못했습니다. <button type="button" onClick={() => setReload((value) => value + 1)}>다시 시도</button></div>
      ) : (
        <>
          <fieldset disabled={saving} className="budget-fields">
            <label className="field"><span>통화</span><select aria-label="예산 통화" value={currency} onChange={(event) => changeCurrency(event.target.value as BudgetCurrency)}>{currencies.map((value) => <option key={value}>{value}</option>)}</select></label>
            <label className="field"><span>총예산 · {currency}</span><input aria-label="총예산" inputMode="decimal" value={limit} maxLength={16} placeholder="미입력 시 제한 없음" onChange={(event) => { setLimit(event.target.value); edit() }} /></label>
            <label className="field budget-fixed"><span>고정비 · {currency}</span><input aria-label="고정비" inputMode="decimal" value={fixed} maxLength={16} onChange={(event) => { setFixed(event.target.value); edit() }} /><small>숙박·식비·교통비 등 장소 비용 외의 여행 전체 비용</small></label>
            <div className="budget-place-costs">
              {costs.map((cost) => <label className="budget-cost-row" key={cost.placeId}><span>{cost.placeName}{cost.mustVisit && <em>꼭 가기</em>}</span><input aria-label={`${cost.placeName} 예상 비용`} inputMode="decimal" value={cost.amount} maxLength={16} placeholder="미입력" onChange={(event) => {
                setCosts((current) => current.map((row) => row.placeId === cost.placeId ? { ...row, amount: event.target.value } : row)); edit()
              }} /></label>)}
              {costs.length === 0 && <p className="budget-hint">장소를 담으면 각 장소의 입장료·활동비를 입력할 수 있습니다.</p>}
            </div>
          </fieldset>
          <p className="budget-hint">{currency}는 소수점 {fractionDigits(currency)}자리까지 입력합니다. 통화를 변경하면 금액 입력이 초기화되며 환율 변환은 하지 않습니다.</p>
          {validation && <p className="budget-error" role="alert"><AlertCircle size={15} />{validation}</p>}
          {dirty && <p className="budget-dirty" role="status">변경한 비용을 저장해야 일정에 반영할 수 있습니다.</p>}
          <div className="budget-actions"><small>총예산이 있으면 모든 장소의 비용이 필요합니다.</small><button className="button button-ghost button-small" disabled={saving}><Save size={15} />{saving ? '저장 중…' : saved ? '예산 저장됨' : '예산 저장'}</button></div>
          {dirty && <button type="button" className="budget-reload" disabled={saving} onClick={() => setReload((value) => value + 1)}>변경 취소하고 저장 내용 불러오기</button>}
        </>
      )}
    </form>
  )
}
