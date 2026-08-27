import { expect, test } from '@playwright/test'
import { addManualPlace, calculateItinerary, createTrip, signUp, testAccount } from './helpers'

test.describe.serial('인증된 여행 생성과 공개 루트 재사용', () => {
  let ownerTripId = 0
  let routeTitle = ''
  let routeRegion = ''

  test('소유자가 여행을 만들고 계산한 일정을 공개한다', async ({ page }, testInfo) => {
    const seed = `${Date.now()}-${testInfo.workerIndex}-${testInfo.retry}`
    const account = testAccount(seed, 'owner')
    const tripName = `V15 자동 여행 ${seed}`
    const placeName = `V15 실외 명소 ${seed}`
    routeTitle = `V15 공개 루트 ${seed}`
    routeRegion = `E2E-${seed}`

    await signUp(page, account)
    ownerTripId = await createTrip(page, tripName, 'V15 서울 숙소')
    await addManualPlace(page, placeName)
    await calculateItinerary(page, tripName)

    await page.reload()
    await expect(page.getByRole('heading', { name: '오늘의 장면을 골라보세요' })).toBeVisible()
    await page.getByRole('button', { name: '일정 다시 보기', exact: true }).click()
    await expect(page.getByRole('heading', { name: tripName })).toBeVisible()
    await expect(page.getByText('VERSION 1', { exact: true })).toBeVisible()

    await page.getByRole('button', { name: /루트 커뮤니티/ }).click()
    await expect(page.getByRole('heading', { name: /좋은 동선을 발견하고/ })).toBeVisible()
    await page.getByRole('button', { name: /현재 일정 공개/ }).click()
    await page.getByLabel('루트 제목', { exact: true }).fill(routeTitle)
    await page.getByLabel('지역', { exact: true }).fill(routeRegion)
    await page.getByLabel('한 줄 설명', { exact: true }).fill('Playwright가 생성한 V15 날씨 대응 회귀 테스트 루트')
    await page.getByRole('button', { name: '루트 공개하기', exact: true }).click()
    await expect(page.getByText('현재 일정의 Snapshot을 루트 커뮤니티에 공개했습니다.')).toBeVisible()
    await expect(page.locator('.community-detail').getByRole('heading', { name: routeTitle })).toBeVisible()

    await page.getByRole('button', { name: '마이페이지', exact: true }).click()
    await expect(page.getByRole('heading', { name: `${account.nickname}님의 여행 공간` })).toBeVisible()
    await page.getByRole('button', { name: /내 여행 전체보기/ }).click()
    await expect(page.getByRole('button', { name: new RegExp(tripName) })).toBeVisible()
  })

  test('다른 사용자는 소유자 여행에 접근할 수 없고 공개 루트만 가져온다', async ({ page }, testInfo) => {
    expect(ownerTripId).toBeGreaterThan(0)
    expect(routeTitle).not.toBe('')
    const seed = `${Date.now()}-${testInfo.workerIndex}-${testInfo.retry}`
    const account = testAccount(seed, 'guest')
    const baseTripName = `V14 복사용 여행 ${seed}`
    const copiedTripName = `V14 가져온 여행 ${seed}`

    await signUp(page, account)
    await page.goto(`/?tripId=${ownerTripId}`)
    await expect(page.getByRole('heading', { name: /좋은 여행 동선을 발견하고/ })).toBeVisible()
    await expect(page).not.toHaveURL(/tripId=/)

    const baseTripId = await createTrip(page, baseTripName, 'V14 부산 숙소')
    await page.getByRole('button', { name: '커뮤니티', exact: true }).click()
    await expect(page.getByRole('heading', { name: /좋은 동선을 발견하고/ })).toBeVisible()
    await page.getByPlaceholder('지역으로 찾기 · 예: 오사카').fill(routeRegion)
    await page.locator('.community-search').getByRole('button', { name: '검색', exact: true }).click()

    const routeCard = page.locator('.community-route-card').filter({ hasText: routeTitle })
    await expect(routeCard).toBeVisible()
    await routeCard.getByRole('button', { name: /루트 보기/ }).click()
    await expect(page.locator('.community-detail').getByRole('heading', { name: routeTitle })).toBeVisible()

    await page.getByRole('button', { name: '좋아요', exact: true }).click()
    await expect(page.getByRole('button', { name: '좋아요 취소', exact: true })).toBeVisible()
    await page.getByRole('button', { name: /내 여행으로 가져오기/ }).click()
    await page.getByLabel('새 여행 이름', { exact: true }).fill(copiedTripName)
    await page.getByRole('button', { name: '복사 후 재최적화', exact: true }).click()

    await expect(page.getByText(/공개 루트를 복사하고 내 조건으로 일정 버전 1을 만들었습니다/)).toBeVisible()
    await expect(page.getByRole('heading', { name: copiedTripName })).toBeVisible()
    await expect(page.getByText('VERSION 1', { exact: true })).toBeVisible()
    await expect(page).not.toHaveURL(new RegExp(`tripId=${baseTripId}$`))

    await page.getByRole('button', { name: '마이페이지', exact: true }).click()
    await page.getByRole('button', { name: /내 여행 전체보기/ }).click()
    await expect(page.getByRole('button', { name: new RegExp(baseTripName) })).toBeVisible()
    await expect(page.getByRole('button', { name: new RegExp(copiedTripName) })).toBeVisible()
  })
})
