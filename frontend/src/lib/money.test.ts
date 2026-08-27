import { describe, expect, it } from 'vitest'
import { amountInput, moneyLabel, parseMinor } from './money'

describe('integer minor-unit amounts', () => {
  it('converts decimal prices without floating point rounding', () => {
    expect(parseMinor('0.29', 'USD')).toBe(29)
    expect(parseMinor('12.3', 'EUR')).toBe(1230)
    expect(amountInput(1230, 'EUR')).toBe('12.30')
    expect(moneyLabel(1230, 'USD')).toBe('USD 12.30')
  })

  it('distinguishes unknown from free and rejects empty required amounts', () => {
    expect(parseMinor('', 'KRW')).toBeNull()
    expect(parseMinor('0', 'KRW')).toBe(0)
    expect(() => parseMinor('', 'KRW', false)).toThrow()
  })

  it('rejects negative, fractional yen, excess precision, unsafe and nonnumeric values', () => {
    for (const input of ['-1', '0.001', '1e3', 'NaN', 'Infinity', '10000000000.01']) {
      expect(() => parseMinor(input, 'USD')).toThrow()
    }
    expect(() => parseMinor('1.5', 'JPY')).toThrow()
    expect(parseMinor('10000000000.00', 'USD')).toBe(1_000_000_000_000)
  })
})
