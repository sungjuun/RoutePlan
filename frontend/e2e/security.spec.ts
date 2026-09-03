import { expect, test, type Page, type APIRequestContext } from '@playwright/test'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { fileURLToPath } from 'node:url'
import { createTrip, signUp, testAccount, type TestAccount } from './helpers'

const mailboxURL = process.env.E2E_MAILBOX_URL
const execFileAsync = promisify(execFile)

async function logIn(page: Page, account: TestAccount) {
  await page.goto('/')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await page.getByLabel('이메일', { exact: true }).fill(account.email)
  await page.getByLabel('비밀번호', { exact: true }).fill(account.password)
  await page.locator('form').getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: /마음에 든 여행을 저장하고/ })).toBeVisible()
}

async function emailLink(request: APIRequestContext, email: string, fragment: string): Promise<string> {
  let link = ''
  await expect.poll(async () => {
    const result = await request.get(`${mailboxURL}/api/v1/messages`)
    if (!result.ok()) return false
    const data = await result.json() as { messages: { ID: string; To: { Address: string }[] }[] }
    for (const message of data.messages.filter(item => item.To.some(to => to.Address === email))) {
      const detail = await request.get(`${mailboxURL}/api/v1/message/${message.ID}`)
      const body = await detail.json() as { Text: string }
      const match = body.Text.match(new RegExp(`http://127\\.0\\.0\\.1:\\d+/#${fragment}=[A-Za-z0-9_-]{43}`))
      if (match) { link = match[0]; return true }
    }
    return false
  }, { timeout: 25_000, message: 'The isolated mailbox should receive the test account email' }).toBe(true)
  return link
}

test('이메일 인증 메일 수신·명시적 확인·재사용 차단', async ({ page, request }) => {
  test.skip(!mailboxURL, 'Requires the isolated Docker E2E mailbox')
  const account = testAccount(`verify-${Date.now()}`, 'owner')
  await signUp(page, account)
  const link = await emailLink(request, account.email, 'verify-email')
  await page.goto(link)
  await expect(page.getByRole('heading', { name: '이메일 주소 인증' })).toBeVisible()
  expect(new URL(page.url()).hash).toBe('')
  expect((await (await page.request.get('/api/v1/auth/me')).json()).user.emailVerified).toBe(false)
  await page.getByRole('button', { name: '이메일 인증하기' }).click()
  await expect(page.getByRole('heading', { name: '이메일 인증 완료' })).toBeVisible()
  await page.getByRole('button', { name: '여행으로 돌아가기' }).click()
  await expect(page.getByText('인증 완료', { exact: true })).toBeVisible()
  await page.goto(link)
  await page.getByRole('button', { name: '이메일 인증하기' }).click()
  await expect(page.getByRole('alert')).toContainText('유효하지 않거나 만료된 링크')
})

test('비밀번호 변경·다른 기기 로그아웃·메일 재설정 후 재로그인', async ({ page, browser, request, baseURL }) => {
  test.skip(!mailboxURL, 'Requires the isolated Docker E2E mailbox')
  test.setTimeout(90_000)
  const account = testAccount(`password-${Date.now()}`, 'owner')
  await signUp(page, account)
  const otherContext = await browser.newContext({ baseURL })
  try {
    const other = await otherContext.newPage()
    await logIn(other, account)
    await page.locator('.public-profile').click()
    const accountSecurity = page.getByRole('region', { name: '계정 보안' })
    await accountSecurity.getByText('비밀번호 변경', { exact: true }).click()
    await page.setViewportSize({ width: 393, height: 851 })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await accountSecurity.getByLabel('현재 비밀번호').fill(account.password)
    await accountSecurity.getByLabel('새 비밀번호', { exact: true }).fill('Changed-e2e-password-2026!')
    await accountSecurity.getByLabel('새 비밀번호 확인').fill('Changed-e2e-password-2026!')
    await accountSecurity.getByRole('button', { name: '비밀번호 변경하고 로그아웃' }).click()
    await expect(page.getByRole('heading', { name: '다시 여행을 이어가세요' })).toBeVisible()
    expect((await (await other.request.get('/api/v1/auth/me')).json()).authenticated).toBe(false)
    await page.getByRole('button', { name: '비밀번호를 잊으셨나요?' }).click()
    await page.getByLabel('이메일', { exact: true }).fill(account.email)
    await page.getByRole('button', { name: '재설정 메일 보내기' }).click()
    await expect(page.getByRole('status')).toContainText('가입된 이메일이라면')
    const link = await emailLink(request, account.email, 'reset-password')
    await page.goto(link)
    await expect(page.getByRole('heading', { name: '새 비밀번호 설정' })).toBeVisible()
    expect(new URL(page.url()).hash).toBe('')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await page.getByLabel('새 비밀번호', { exact: true }).fill('Recovered-e2e-password-2026!')
    await page.getByLabel('새 비밀번호 확인').fill('Recovered-e2e-password-2026!')
    await page.getByRole('button', { name: '비밀번호 재설정하기' }).click()
    await expect(page.getByRole('heading', { name: '비밀번호 재설정 완료' })).toBeVisible()
    await page.getByRole('button', { name: '로그인 화면으로' }).click()
    await page.getByLabel('이메일', { exact: true }).fill(account.email)
    await page.getByLabel('비밀번호', { exact: true }).fill('Recovered-e2e-password-2026!')
    await page.locator('form').getByRole('button', { name: '로그인', exact: true }).click()
    await expect(page.getByRole('heading', { name: /마음에 든 여행을 저장하고/ })).toBeVisible()
  } finally { await otherContext.close() }
})

test('백엔드를 실제로 재시작해도 로그인과 저장 여행 유지', async ({ page }) => {
  const project = process.env.E2E_RESTART_PROJECT
  test.skip(!project, 'Restart is allowed only for the isolated Docker E2E stack')
  test.setTimeout(120_000)
  if (!project || !/^routeplan-e2e(?:-[a-z0-9-]+)?$/.test(project)) throw new Error('Unsafe restart target')
  const account = testAccount(`restart-${Date.now()}`, 'owner')
  await signUp(page, account)
  const tripId = await createTrip(page, `세션 유지 ${Date.now()}`, '테스트 숙소')
  const composePath = fileURLToPath(new URL('../../compose.yaml', import.meta.url))
  await execFileAsync(process.platform === 'win32' ? 'docker.exe' : 'docker',
    ['compose', '-p', project, '-f', composePath, 'restart', 'backend'], { timeout: 60_000, windowsHide: true })
  await expect.poll(async () => {
    try {
      const result = await page.request.get('/api/v1/auth/me')
      return result.ok() && (await result.json()).user?.email === account.email
    } catch { return false }
  }, { timeout: 60_000, message: 'Session must survive a backend restart' }).toBe(true)
  await page.reload()
  await expect(page.getByRole('heading', { name: '오늘의 장면을 골라보세요' })).toBeVisible()
  expect(new URL(page.url()).searchParams.get('tripId')).toBe(String(tripId))
})

test('마이페이지에서 닉네임·이메일을 변경하고 계정 데이터를 삭제', async ({ page }) => {
  test.setTimeout(90_000)
  const seed = `lifecycle-${Date.now()}`
  const account = testAccount(seed, 'owner')
  const nickname = `새여행자-${seed.slice(-8)}`
  const nextEmail = `changed-${seed}@example.com`
  await signUp(page, account)

  await page.getByRole('button', { name: new RegExp(account.nickname) }).click()
  const management = page.getByRole('region', { name: '계정 관리' })
  await management.getByLabel('닉네임').fill(nickname)
  await management.getByRole('button', { name: '닉네임 저장' }).click()
  await expect(management.getByRole('status')).toContainText('닉네임을 변경')
  await expect(page.getByRole('heading', { name: `${nickname}님의 여행 공간` })).toBeVisible()

  await management.getByText('이메일 변경', { exact: true }).click()
  await management.getByLabel('새 이메일').fill(nextEmail)
  await management.getByLabel('현재 비밀번호').first().fill(account.password)
  await management.getByRole('button', { name: '이메일 변경하고 로그아웃' }).click()
  await expect(page.getByRole('heading', { name: '다시 여행을 이어가세요' })).toBeVisible()

  await page.getByLabel('이메일', { exact: true }).fill(nextEmail)
  await page.getByLabel('비밀번호', { exact: true }).fill(account.password)
  await page.locator('form').getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: /마음에 든 여행을 저장하고/ })).toBeVisible()
  await page.getByRole('button', { name: new RegExp(nickname) }).click()

  const refreshedManagement = page.getByRole('region', { name: '계정 관리' })
  await refreshedManagement.getByText('회원 탈퇴', { exact: true }).click()
  await refreshedManagement.getByLabel('현재 비밀번호').last().fill(account.password)
  await refreshedManagement.getByLabel('확인 문구').fill('회원 탈퇴')
  await refreshedManagement.getByRole('button', { name: '계정과 모든 데이터 삭제' }).click()
  await expect(page.getByRole('heading', { name: /마음에 든 여행을 저장하고/ })).toBeVisible()
  await expect(page.getByRole('button', { name: '로그인', exact: true })).toBeVisible()

  const csrf = await (await page.request.get('/api/v1/auth/csrf')).json() as { headerName: string; token: string }
  const recreated = await page.request.post('/api/v1/auth/signup', {
    headers: { [csrf.headerName]: csrf.token },
    data: { email: nextEmail, nickname, password: account.password },
  })
  expect(recreated.status()).toBe(201)
})
