import { expect, test } from '@playwright/test'

test('비회원이 추천 루트를 탐색하고 내 여행 접근 시 로그인으로 이동한다', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: /마음에 든 여행을 저장하고/ })).toBeVisible()
  await page.getByRole('button', { name: /대한민국 서울/ }).click()
  await expect(page.getByRole('heading', { name: '서울 추천 루트' })).toBeVisible()
  await expect(page.getByPlaceholder('도시 또는 지역 · 예: 오사카')).toHaveValue('서울')

  await page.getByRole('button', { name: '내 여행', exact: true }).click()
  await expect(page.getByRole('heading', { name: '다시 여행을 이어가세요' })).toBeVisible()
  await expect(page.getByLabel('이메일', { exact: true })).toBeVisible()
})
