import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const e2eDirectory = dirname(fileURLToPath(import.meta.url))
const frontendDirectory = resolve(e2eDirectory, '..')
const repositoryDirectory = resolve(frontendDirectory, '..')
const composeFile = join(repositoryDirectory, 'compose.yaml')
const v25Mode = process.argv.slice(2).includes('--v25')
const playwrightArguments = process.argv.slice(2).filter((argument) => argument !== '--v25')
const v25ComposeFile = join(repositoryDirectory, 'compose.e2e-v25.yaml')
const projectName = process.env.E2E_COMPOSE_PROJECT ?? (v25Mode ? 'routeplan-e2e-v25' : 'routeplan-e2e')
if (!/^routeplan-e2e(?:-[a-z0-9-]+)?$/.test(projectName)) {
  throw new Error('E2E_COMPOSE_PROJECT must start with routeplan-e2e; development stacks must never be removed.')
}
const frontendPort = process.env.E2E_FRONTEND_PORT ?? '3200'
const backendPort = process.env.E2E_BACKEND_PORT ?? '8280'
const postgresPort = process.env.E2E_POSTGRES_PORT ?? '55432'
const redisPort = process.env.E2E_REDIS_PORT ?? '6479'
const mailboxPort = process.env.E2E_MAILPIT_PORT ?? '8027'
const googleStubPort = process.env.E2E_GOOGLE_STUB_PORT ?? '8390'
const baseURL = `http://127.0.0.1:${frontendPort}`
const dockerCommand = process.platform === 'win32' ? 'docker.exe' : 'docker'
const playwrightCli = join(
  frontendDirectory,
  'node_modules',
  '@playwright',
  'test',
  'cli.js',
)
const composeArguments = ['compose', '-p', projectName, '-f', composeFile]
if (v25Mode) composeArguments.push('-f', v25ComposeFile)
const commandEnvironment = {
  ...process.env,
  POSTGRES_DB: 'routeplan',
  POSTGRES_USER: 'routeplan',
  POSTGRES_PASSWORD: 'routeplan-e2e-local',
  POSTGRES_PORT: postgresPort,
  REDIS_PORT: redisPort,
  BACKEND_PORT: backendPort,
  FRONTEND_PORT: frontendPort,
  E2E_BASE_URL: baseURL,
  MAILPIT_PORT: mailboxPort,
  E2E_MAILBOX_URL: `http://127.0.0.1:${mailboxPort}`,
  E2E_RESTART_PROJECT: projectName,
  E2E_BACKEND_URL: `http://127.0.0.1:${backendPort}`,
  E2E_GOOGLE_STUB_URL: `http://127.0.0.1:${googleStubPort}`,
  E2E_GOOGLE_STUB_PORT: googleStubPort,
  E2E_V25: v25Mode ? 'true' : 'false',
  E2E_BACKEND_REPLICAS: v25Mode ? '2' : '1',
  ROUTEPLAN_PUBLIC_URL: baseURL,
  ROUTEPLAN_AUTH_MAIL_MODE: 'LOCAL',
  ROUTEPLAN_AUTH_TRUSTED_PROXIES: '',
  ROUTEPLAN_SESSION_COOKIE_SECURE: 'false',
  SMTP_HOST: 'mailpit',
  SMTP_PORT: '1025',
  SMTP_USERNAME: '',
  SMTP_PASSWORD: '',
  SMTP_AUTH: 'false',
  SMTP_STARTTLS: 'false',
  SMTP_SSL: 'false',
  SMTP_FROM: 'RoutePlan E2E <noreply@routeplan.test>',
  // Never reuse developer credentials or paid providers in the disposable test stack.
  ROUTEPLAN_PLACE_PROVIDER: 'DISABLED',
  ROUTEPLAN_ROUTE_PROVIDER: v25Mode ? 'GOOGLE' : 'SIMPLE',
  GOOGLE_MAPS_API_KEY: v25Mode ? 'routeplan-e2e-google-stub-key' : '',
  GOOGLE_MAPS_BROWSER_KEY: '',
  ROUTEPLAN_AI_PROVIDER: 'RULE_BASED',
  OPENAI_API_KEY: '',
  ROUTEPLAN_ROUTE_CACHE_ENABLED: v25Mode ? 'true' : 'false',
  ROUTEPLAN_ROUTE_DB_CACHE_ENABLED: v25Mode ? 'true' : 'false',
  ROUTEPLAN_TIME_DEPENDENT_GLOBAL_ENABLED: v25Mode ? 'true' : 'false',
  ROUTEPLAN_WEATHER_AUTO_REFRESH_ENABLED: 'false',
  ROUTEPLAN_MODERATOR_EMAILS: 'routeplan-moderator@example.com',
}

function run(command, args, options = {}) {
  return spawnSync(command, args, {
    cwd: repositoryDirectory,
    env: commandEnvironment,
    stdio: 'inherit',
    ...options,
  })
}

async function waitForFrontend() {
  const deadline = Date.now() + 180_000
  while (Date.now() < deadline) {
    try {
      const response = await fetch(baseURL)
      if (response.ok) return
    } catch {
      // The dedicated stack can take a little longer on the first image build.
    }
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 2_000))
  }
  throw new Error(`E2E frontend did not become ready: ${baseURL}`)
}

function saveComposeLogs() {
  const result = spawnSync(dockerCommand, [...composeArguments, 'logs', '--no-color'], {
    cwd: repositoryDirectory,
    env: commandEnvironment,
    encoding: 'utf8',
  })
  const outputDirectory = join(frontendDirectory, 'test-results')
  mkdirSync(outputDirectory, { recursive: true })
  const logs = `${result.stdout ?? ''}${result.stderr ?? ''}`
  writeFileSync(join(outputDirectory, 'compose.log'), logs, 'utf8')
  if (logs) process.stderr.write(logs)
}

let exitCode = 1
try {
  const startupArguments = [...composeArguments, 'up', '-d', '--build', '--wait', '--wait-timeout', '180']
  if (v25Mode) startupArguments.push('--scale', 'backend=2')
  const startup = run(dockerCommand, startupArguments)
  if (startup.status !== 0) throw new Error('Could not start the dedicated E2E Docker stack.')
  await waitForFrontend()

  const result = run(process.execPath, [playwrightCli, 'test', ...playwrightArguments], {
    cwd: frontendDirectory,
  })
  if (result.error) throw result.error
  exitCode = result.status ?? 1
  if (exitCode !== 0) saveComposeLogs()
} catch (error) {
  process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`)
  saveComposeLogs()
} finally {
  run(dockerCommand, [...composeArguments, 'down', '--volumes', '--remove-orphans'])
}

process.exit(exitCode)
