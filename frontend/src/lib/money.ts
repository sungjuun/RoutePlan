import type { BudgetCurrency } from '../types'

export const currencies: BudgetCurrency[] = ['KRW', 'JPY', 'USD', 'EUR', 'GBP', 'CNY']
export const MAX_MINOR_AMOUNT = 1_000_000_000_000

export function fractionDigits(currency: BudgetCurrency): number {
  return currency === 'KRW' || currency === 'JPY' ? 0 : 2
}

export function parseMinor(value: string, currency: BudgetCurrency, optional = true): number | null {
  const normalized = value.trim()
  if (!normalized && optional) return null
  const digits = fractionDigits(currency)
  const pattern = digits === 0 ? /^\d+$/ : /^\d+(?:\.\d{0,2})?$/
  if (!pattern.test(normalized)) {
    throw new Error(`${currency} 금액은 0 이상, 소수점 ${digits}자리까지 입력해 주세요.`)
  }
  const [whole, fraction = ''] = normalized.split('.')
  const amount = BigInt(whole) * BigInt(10 ** digits) + BigInt(fraction.padEnd(digits, '0') || '0')
  if (amount > BigInt(MAX_MINOR_AMOUNT)) throw new Error('입력 가능한 금액 범위를 초과했습니다.')
  return Number(amount)
}

export function amountInput(minor: number | null, currency: BudgetCurrency): string {
  return minor == null ? '' : (minor / 10 ** fractionDigits(currency)).toFixed(fractionDigits(currency))
}

export function moneyLabel(minor: number, currency: BudgetCurrency): string {
  const digits = fractionDigits(currency)
  return `${currency} ${new Intl.NumberFormat('ko-KR', {
    minimumFractionDigits: digits, maximumFractionDigits: digits,
  }).format(minor / 10 ** digits)}`
}
