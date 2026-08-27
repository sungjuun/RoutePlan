import { expect, type Page } from '@playwright/test'

export interface TestAccount {
  email: string
  nickname: string
  password: string
}

export function testAccount(seed: string, role: 'owner' | 'guest'): TestAccount {
  return {
    email: `routeplan-${role}-${seed}@example.com`,
    nickname: `${role === 'owner' ? '루트주인' : '루트여행자'}-${seed.slice(-8)}`,
    password: 'RoutePlan-e2e-2026!',
  }
}

export async function signUp(page: Page, account: TestAccount): Promise<void> {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: /좋은 여행 동선을 발견하고/ })).toBeVisible()
  await page.getByRole('button', { name: '회원가입', exact: true }).click()
  await expect(page.getByRole('heading', { name: '나만의 여행을 시작하세요' })).toBeVisible()

  await page.getByLabel('이메일', { exact: true }).fill(account.email)
  await page.getByLabel('닉네임', { exact: true }).fill(account.nickname)
  await page.getByLabel('비밀번호', { exact: true }).fill(account.password)
  await page.getByLabel('비밀번호 확인', { exact: true }).fill(account.password)
  await page.getByRole('button', { name: /계정 만들기/ }).click()

  await expect(page.getByRole('heading', { name: /좋은 여행 동선을 발견하고/ })).toBeVisible()
  await expect(page.getByRole('button', { name: new RegExp(account.nickname) })).toBeVisible()
}

export async function createTrip(page: Page, name: string, accommodationName: string): Promise<number> {
  await page.getByRole('button', { name: '새 여행', exact: true }).click()
  await expect(page.getByRole('heading', { name: '어떤 여행을 떠나볼까요?' })).toBeVisible()

  await page.getByLabel('여행 이름', { exact: true }).fill(name)
  await page.getByLabel('숙소 이름', { exact: true }).fill(accommodationName)
  await page.getByText('대중교통', { exact: true }).click()
  await expect(page.getByRole('radio', { name: /대중교통/ })).toBeChecked()
  await page.getByRole('button', { name: /장소를 담으러 가기/ }).click()

  await expect(page.getByRole('heading', { name: '오늘의 장면을 골라보세요' })).toBeVisible()
  await page.waitForURL(/\?tripId=\d+$/)
  const tripId = Number(new URL(page.url()).searchParams.get('tripId'))
  expect(tripId).toBeGreaterThan(0)
  return tripId
}

export async function addManualPlace(page: Page, name: string): Promise<void> {
  await page.getByRole('tab', { name: '좌표로 추가' }).click()
  const form = page.locator('.manual-place-form')
  await form.getByLabel('장소 이름', { exact: true }).fill(name)
  await form.getByLabel('카테고리', { exact: true }).fill('E2E 명소')
  await form.getByLabel('공간 유형', { exact: true }).selectOption('OUTDOOR')
  await form.getByLabel('위도', { exact: true }).fill('37.5701')
  await form.getByLabel('경도', { exact: true }).fill('126.9820')
  await form.getByRole('button', { name: /이 장소 담기/ }).click()
  await expect(page.getByRole('button', { name: new RegExp(name) })).toBeVisible()
}

export async function calculateItinerary(page: Page, tripName: string): Promise<void> {
  await page.getByRole('button', { name: '일정 만들기', exact: true }).click()
  await expect(page.getByRole('heading', { name: '선택한 장면을 여행 날짜마다' })).toBeVisible()
  const weatherPlanner = page.getByRole('region', { name: '날짜별 날씨 설정' })
  await weatherPlanner.getByRole('combobox', { name: /날씨/ }).first().selectOption('RAIN')
  await weatherPlanner.getByRole('button', { name: '예보 저장' }).click()
  await expect(weatherPlanner.getByRole('button', { name: '예보 저장됨' })).toBeVisible()
  await page.getByRole('button', { name: /내 일정 계산하기/ }).click()
  await expect(page.getByText('VERSION 1', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: tripName })).toBeVisible()
  await expect(page.getByText('비 · 강수 80%', { exact: true })).toBeVisible()
  await expect(page.getByText('날씨 -30', { exact: true })).toBeVisible()
}
