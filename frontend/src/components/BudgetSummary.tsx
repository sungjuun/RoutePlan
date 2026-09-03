import { useEffect, useState } from 'react'
import { RefreshCw, Wallet } from 'lucide-react'
import { api } from '../api/client'
import { fractionDigits, moneyLabel } from '../lib/money'
import type { CostSummary, ExchangeRateQuote } from '../types'

export function BudgetSummary({ summary, tripId }: { summary: CostSummary; tripId?: number }) {
  const money = (value: number) => moneyLabel(value, summary.currency)
  return (
    <section className="budget-summary panel" aria-label="예산 요약">
      <div className="budget-total"><Wallet size={21} /><div><small>{summary.unpricedPlaceCount ? '입력된 비용 합계' : '예상 총비용'}</small><strong>{money(summary.estimatedTotalMinor)}</strong></div><span>{summary.limitMinor == null ? '예산 제한 없음' : '예산 적용'}</span></div>
      <dl><div><dt>고정비</dt><dd>{money(summary.fixedCostMinor)}</dd></div><div><dt>방문 장소 비용</dt><dd>{money(summary.knownVisitCostMinor)}</dd></div>{summary.limitMinor != null && <div><dt>총예산</dt><dd>{money(summary.limitMinor)}</dd></div>}{summary.remainingMinor != null && <div><dt>남은 예산</dt><dd>{money(summary.remainingMinor)}</dd></div>}</dl>
      <p>{summary.unpricedPlaceCount > 0 ? `비용 미입력 장소 ${summary.unpricedPlaceCount}곳은 합계에 포함되지 않았습니다. ` : ''}계산 당시 입력한 예상 비용이며 실제 결제 금액이나 실시간 교통요금이 아닙니다.</p>
      {tripId != null && summary.currency !== 'KRW' && <ExchangeRateNote
        key={`${tripId}-${summary.currency}`}
        tripId={tripId}
        summary={summary}
      />}
    </section>
  )
}

function ExchangeRateNote({ tripId, summary }: { tripId: number; summary: CostSummary }) {
  const [quote, setQuote] = useState<ExchangeRateQuote | null>(null)
  const [rateError, setRateError] = useState(false)
  useEffect(() => {
    let active = true
    api.getTripExchangeRate(tripId)
      .then((value) => { if (active) setQuote(value) })
      .catch(() => { if (active) setRateError(true) })
    return () => { active = false }
  }, [tripId])
  if (rateError) return <p className="exchange-rate-note muted">환율을 불러오지 못했습니다. 예산과 지출 기록은 원래 통화로 계속 사용할 수 있습니다.</p>
  if (quote == null) return <p className="exchange-rate-note muted"><RefreshCw size={13} /> 현재 참고 환율을 불러오는 중입니다.</p>
  const converted = Math.round(
    summary.estimatedTotalMinor / 10 ** fractionDigits(summary.currency)
    * quote.rate * 10 ** fractionDigits(quote.quote),
  )
  return <p className="exchange-rate-note"><RefreshCw size={13} /> 현재 참고 환율 기준 약 <strong>{moneyLabel(converted, quote.quote)}</strong> · 1 {quote.base} = {quote.rate.toLocaleString('ko-KR', { maximumFractionDigits: 6 })} {quote.quote} · {quote.rateDate}</p>
}
