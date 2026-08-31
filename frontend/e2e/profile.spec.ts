import { expect, test } from '@playwright/test'
import { createTrip, signUp, testAccount } from './helpers'

test('프로필 사진 저장·새로고침·초기화와 알림함·날씨 설정', async ({ page }) => {
  const account = testAccount(`profile-${Date.now()}`, 'owner')
  await signUp(page, account)
  await expect(page.locator('.toast')).toHaveCount(0)
  await expect(page.getByText(`${account.nickname}님, 반갑습니다.`)).toHaveCount(0)
  await page.getByRole('button', { name: /^알림/ }).click()
  await expect(page.getByRole('region', { name: '알림함' })).toBeVisible()
  await expect(page.getByText(`${account.nickname}님, 반갑습니다.`)).toBeVisible()
  await page.getByRole('button', { name: '모두 읽음' }).click()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('button', { name: '알림', exact: true })).toHaveAttribute('aria-expanded', 'false')

  await page.locator('.public-profile').click()
  const operations = page.getByRole('region', { name: '외부 API 품질과 비용' })
  await expect(operations).toBeVisible()
  await operations.getByRole('button', { name: '이번 달 운영 지표 조회' }).click()
  await expect(operations.getByText('Google 경로 행렬')).toBeVisible()
  await expect(operations.getByText('OpenAI 여행 조건 해석')).toBeVisible()
  // A synthetic 1px PNG, not a personal photo or an external URL.
  await page.getByLabel('프로필 사진 파일').setInputFiles({
    name: 'test-profile.png', mimeType: 'image/png',
    buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGPQKIn+DwADtgH3BBxnVwAAAABJRU5ErkJggg==', 'base64'),
  })
  await expect(page.getByAltText('새 프로필 사진 미리보기')).toBeVisible()
  await page.getByRole('button', { name: '사진 저장', exact: true }).click()
  await expect(page.getByText('프로필 사진을 저장했습니다.')).toBeVisible()
  await expect(page.locator('.profile-avatar img')).toHaveAttribute('src', /\/api\/v1\/profile\/avatar\?v=/)
  await page.reload()
  await page.locator('.public-profile').click()
  await expect(page.locator('.profile-avatar img')).toBeVisible()
  await page.setViewportSize({ width: 393, height: 851 })
  await page.getByRole('button', { name: /^알림/ }).click()
  await expect(page.getByText('새 알림이 없습니다.')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.getByRole('button', { name: '알림함 닫기' }).click()
  await page.getByRole('button', { name: '기본 이미지', exact: true }).click()
  await expect(page.getByText('기본 프로필로 변경했습니다.')).toBeVisible()
  await expect(page.locator('.profile-avatar img')).toHaveCount(0)
  await page.setViewportSize({ width: 1440, height: 1000 })

  await createTrip(page, `자동 예보 ${Date.now()}`, '서울역 숙소')
  await page.getByRole('button', { name: /일정 보기/ }).click()
  const toggle = page.getByRole('checkbox', { name: '날씨를 3시간마다 자동 갱신' })
  await expect(toggle).toBeEnabled()
  await toggle.check()
  await expect(page.getByText(/자동 갱신 켜짐/)).toBeVisible()
  await toggle.uncheck()
  await expect(toggle).not.toBeChecked()
})
