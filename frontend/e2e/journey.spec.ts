import { expect, test } from '@playwright/test'
import { addManualPlace, calculateItinerary, createTrip, signUp, testAccount } from './helpers'

test.describe.serial('인증된 여행 생성과 공개 루트 재사용', () => {
  let ownerTripId = 0
  let routeTitle = ''
  let routeRegion = ''

  test('소유자가 여행을 만들고 계산한 일정을 공개한다', async ({ page }, testInfo) => {
    const seed = `${Date.now()}-${testInfo.workerIndex}-${testInfo.retry}`
    const account = testAccount(seed, 'owner')
    const tripName = `V16 자동 여행 ${seed}`
    const placeName = `V16 실외 명소 ${seed}`
    routeTitle = `V16 공개 루트 ${seed}`
    routeRegion = `E2E-${seed}`

    await signUp(page, account)
    ownerTripId = await createTrip(page, tripName, 'V15 서울 숙소')
    await addManualPlace(page, placeName)
    await addManualPlace(page, `${placeName} 고가 체험`)
    await calculateItinerary(page, tripName)

    await page.reload()
    await expect(page.getByRole('heading', { name: '오늘의 장면을 골라보세요' })).toBeVisible()
    await page.getByRole('button', { name: '일정 다시 보기', exact: true }).click()
    await expect(page.getByRole('heading', { name: tripName })).toBeVisible()
    await expect(page.getByText('VERSION 1', { exact: true })).toBeVisible()

    await page.locator('summary').filter({ hasText: '날짜별·항목별 예산과 지출' }).click()
    const ledger = page.getByRole('region', { name: '예산과 실제 지출' })
    await ledger.getByLabel('지출 내용', { exact: true }).fill('E2E 점심')
    await ledger.getByLabel('실제 지출 금액', { exact: true }).fill('15000')
    await ledger.getByRole('button', { name: '지출 기록', exact: true }).click()
    await expect(ledger.locator('.advanced-list').first()).toContainText('E2E 점심')
    await ledger.getByLabel('한도 항목', { exact: true }).selectOption('FOOD')
    await ledger.getByLabel('구간 예산 금액', { exact: true }).fill('10000')
    await ledger.getByRole('button', { name: '구간 한도 저장', exact: true }).click()
    await expect(ledger).toContainText('초과 KRW 5,000')
    await page.setViewportSize({ width: 393, height: 851 })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath('advanced-spending-mobile.png'), fullPage: true })
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.getByRole('button', { name: '실제 도로 경로', exact: true }).click()
    await expect(page.locator('.road-map')).toContainText(/Google Maps|GOOGLE_MAPS_BROWSER_KEY 설정/)

    await page.getByRole('button', { name: /루트 커뮤니티/ }).click()
    await expect(page.getByRole('heading', { name: /좋은 동선을 발견하고/ })).toBeVisible()
    await page.getByRole('button', { name: /현재 일정 공개/ }).click()
    await page.getByLabel('루트 제목', { exact: true }).fill(routeTitle)
    await page.getByLabel('지역', { exact: true }).fill(routeRegion)
    await page.getByLabel('한 줄 설명', { exact: true }).fill('Playwright가 생성한 V15 날씨 대응 회귀 테스트 루트')
    await page.getByRole('button', { name: '루트 공개하기', exact: true }).click()
    await expect(page.locator('.community-detail').getByRole('heading', { name: routeTitle })).toBeVisible()
    await page.getByRole('button', { name: /^알림/ }).click()
    await expect(page.getByText('현재 일정의 Snapshot을 루트 커뮤니티에 공개했습니다.')).toBeVisible()
    await page.getByRole('button', { name: '알림함 닫기' }).click()

    await page.getByLabel('댓글 작성', { exact: true }).fill('E2E 운영 검토 댓글')
    await page.getByRole('button', { name: '댓글 등록', exact: true }).click()
    await expect(page.locator('.discussion-entry')).toContainText('E2E 운영 검토 댓글')

    await page.getByRole('button', { name: '마이페이지', exact: true }).click()
    await expect(page.getByRole('heading', { name: `${account.nickname}님의 여행 공간` })).toBeVisible()
    await page.getByLabel('문화·역사', { exact: true }).check()
    await page.getByLabel(/관심 지역/).fill('서울')
    await page.getByRole('button', { name: '여행 취향 저장' }).click()
    await expect(page.getByText(/취향을 저장했습니다/)).toBeVisible()
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
    await expect(page.getByRole('heading', { name: /마음에 든 여행을 저장하고/ })).toBeVisible()
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
    await page.getByLabel('후기 내용', { exact: true }).fill('E2E 여행 후기')
    await page.getByLabel('별점', { exact: true }).selectOption('4')
    await page.getByRole('button', { name: '후기 저장', exact: true }).click()
    await expect(page.getByText(/후기 1개 · 평균 4.0/)).toBeVisible()
    await page.locator('.discussion-entry').filter({ hasText: 'E2E 운영 검토 댓글' }).getByRole('button', { name: '신고', exact: true }).click()
    await page.getByLabel('신고 설명 · 선택', { exact: true }).fill('E2E 신고 처리 검증')
    await page.getByRole('button', { name: '신고 접수', exact: true }).click()
    await expect(page.getByText(/신고가 접수되었습니다/)).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('community-discussion.png'), fullPage: true })
    await page.getByRole('button', { name: /내 여행으로 가져오기/ }).click()
    await page.getByLabel('새 여행 이름', { exact: true }).fill(copiedTripName)
    await page.getByRole('button', { name: '복사 후 재최적화', exact: true }).click()

    await expect(page.getByRole('heading', { name: copiedTripName })).toBeVisible()
    await page.getByRole('button', { name: /^알림/ }).click()
    await expect(page.getByText(/공개 루트를 복사하고 내 조건으로 일정 버전 1을 만들었습니다/)).toBeVisible()
    await page.getByRole('button', { name: '알림함 닫기' }).click()
    await expect(page.getByText('VERSION 1', { exact: true })).toBeVisible()
    await expect(page).not.toHaveURL(new RegExp(`tripId=${baseTripId}$`))

    await page.getByRole('button', { name: '마이페이지', exact: true }).click()
    await page.getByRole('button', { name: /내 여행 전체보기/ }).click()
    await expect(page.getByRole('button', { name: new RegExp(baseTripName) })).toBeVisible()
    await expect(page.getByRole('button', { name: new RegExp(copiedTripName) })).toBeVisible()
  })

  test('지정된 운영자가 신고된 댓글을 확인하고 숨긴다', async ({ page }) => {
    await signUp(page, { email: 'routeplan-moderator@example.com', nickname: 'E2E 운영자', password: 'RoutePlan-e2e-2026!' })
    await page.getByRole('button', { name: /E2E 운영자/ }).click()
    const panel = page.getByRole('region', { name: '신고 관리' })
    await panel.getByRole('button', { name: '신고 목록 새로고침' }).click()
    await expect(panel).toContainText('E2E 운영 검토 댓글')
    page.once('dialog', dialog => dialog.accept())
    await panel.getByRole('button', { name: '대상 숨김' }).click()
    await expect(panel.getByText('미처리 신고가 없습니다.')).toBeVisible()
  })
})
