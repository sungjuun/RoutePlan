import { Wallet } from 'lucide-react'
import { moneyLabel } from '../lib/money'
import type { CostSummary } from '../types'

export function BudgetSummary({ summary }: { summary: CostSummary }) {
  const money = (value: number) => moneyLabel(value, summary.currency)
  return (
    <section className="budget-summary panel" aria-label="예산 요약">
      <div className="budget-total"><Wallet size={21} /><div><small>{summary.unpricedPlaceCount ? '입력된 비용 합계' : '예상 총비용'}</small><strong>{money(summary.estimatedTotalMinor)}</strong></div><span>{summary.limitMinor == null ? '예산 제한 없음' : '예산 적용'}</span></div>
      <dl><div><dt>고정비</dt><dd>{money(summary.fixedCostMinor)}</dd></div><div><dt>방문 장소 비용</dt><dd>{money(summary.knownVisitCostMinor)}</dd></div>{summary.limitMinor != null && <div><dt>총예산</dt><dd>{money(summary.limitMinor)}</dd></div>}{summary.remainingMinor != null && <div><dt>남은 예산</dt><dd>{money(summary.remainingMinor)}</dd></div>}</dl>
      <p>{summary.unpricedPlaceCount > 0 ? `비용 미입력 장소 ${summary.unpricedPlaceCount}곳은 합계에 포함되지 않았습니다. ` : ''}계산 당시 입력한 예상 비용이며 실제 결제 금액이나 실시간 교통요금이 아닙니다.</p>
    </section>
  )
}
