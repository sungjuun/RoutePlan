import { expect, test } from '@playwright/test'

test('모바일 주요 메뉴가 열리고 이동 후 자동으로 닫힌다', async ({ page }) => {
  await page.goto('/')
  const navigation = page.getByRole('navigation', { name: '주요 메뉴' })

  await expect(page.getByRole('button', { name: '메뉴 열기' })).toBeVisible()
  await expect(navigation).toBeHidden()
  await page.getByRole('button', { name: '메뉴 열기' }).click()
  await expect(navigation).toBeVisible()
  await expect(page.getByRole('button', { name: '메뉴 닫기' })).toHaveAttribute('aria-expanded', 'true')

  await navigation.getByRole('button', { name: '커뮤니티', exact: true }).click()
  await expect(page.getByRole('heading', { name: /다른 여행자의 좋은 동선을/ })).toBeVisible()
  await expect(navigation).toBeHidden()
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})

test('모바일 내 여행 메뉴도 비회원 인증 경계를 지킨다', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: '메뉴 열기' }).click()
  await page.getByRole('navigation', { name: '주요 메뉴' }).getByRole('button', { name: '내 여행', exact: true }).click()
  await expect(page.getByRole('heading', { name: '다시 여행을 이어가세요' })).toBeVisible()
})
