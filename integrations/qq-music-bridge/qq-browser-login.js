import { randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright-core'

const loginTtlMs = 5 * 60_000
const completedTtlMs = 30_000
const attempts = new Map()
const defaultProfileDirectory = fileURLToPath(
  new URL('../../runtime-data/qq-music-edge-profile/', import.meta.url),
)

const cookieNames = new Map([
  ['uin', 'uin'],
  ['wxuin', 'wxuin'],
  ['musicid', 'musicid'],
  ['ptui_loginuin', 'ptui_loginuin'],
  ['p_uin', 'p_uin'],
  ['pt2gguin', 'pt2gguin'],
  ['qqmusic_key', 'qqmusic_key'],
  ['qm_keyst', 'qm_keyst'],
  ['musickey', 'musickey'],
  ['psrf_qqopenid', 'psrf_qqopenid'],
  ['psrf_qqaccess_token', 'psrf_qqaccess_token'],
  ['psrf_qqrefresh_token', 'psrf_qqrefresh_token'],
  ['psrf_qqunionid', 'psrf_qqunionid'],
  ['psrf_musickey_createtime', 'psrf_musickey_createtime'],
  ['psrf_qqrefresh_key', 'psrf_qqrefresh_key'],
  ['tmelogintype', 'tmeLoginType'],
  ['login_type', 'login_type'],
])

function loginUrl() {
  const url = new URL('https://graph.qq.com/oauth2.0/authorize')
  Object.entries({
    response_type: 'code',
    client_id: '100497308',
    redirect_uri: 'https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/',
    state: 'sonora-browser-login',
    scope: 'get_user_info,get_app_friends',
    display: 'pc',
  }).forEach(([name, value]) => url.searchParams.set(name, value))
  return url.toString()
}

function loginFlowError(code, message) {
  const error = new Error(code)
  error.publicCode = code
  error.publicMessage = message
  error.stage = 'browser_login'
  return error
}

function isQqDomain(domain = '') {
  const normalized = domain.replace(/^\./, '').toLowerCase()
  return normalized === 'qq.com' || normalized.endsWith('.qq.com')
}

function firstValue(values, names) {
  for (const name of names) {
    const value = values.get(name)?.trim()
    if (value) return value
  }
  return ''
}

function toCookieString(values) {
  return [...values.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([name, value]) => `${name}=${value}`)
    .join('; ')
}

export function sessionFromBrowserCookies(cookies) {
  const values = new Map()
  for (const cookie of cookies || []) {
    if (!isQqDomain(cookie?.domain) || !cookie?.value) continue
    const canonicalName = cookieNames.get(String(cookie.name || '').toLowerCase())
    if (canonicalName) values.set(canonicalName, String(cookie.value))
  }

  const uin = firstValue(values, ['uin', 'musicid', 'wxuin', 'ptui_loginuin', 'p_uin', 'pt2gguin'])
  const musicKey = firstValue(values, ['qqmusic_key', 'qm_keyst', 'musickey'])
  if (!uin || !musicKey) return null
  values.set('uin', uin)
  values.set('qqmusic_key', musicKey)
  values.set('qm_keyst', musicKey)
  return { uin, cookie: toCookieString(values) }
}

function findCredential(value, depth = 0) {
  if (!value || typeof value !== 'object' || depth > 6) return null
  const uin = String(value.musicid || value.str_musicid || '').trim()
  const musicKey = String(value.musickey || '').trim()
  if (uin && musicKey) return value
  for (const child of Object.values(value)) {
    const credential = findCredential(child, depth + 1)
    if (credential) return credential
  }
  return null
}

export function sessionFromLoginResponse(payload) {
  const credential = findCredential(payload)
  if (!credential) return null
  const values = new Map([
    ['uin', String(credential.musicid || credential.str_musicid).trim()],
    ['qqmusic_key', String(credential.musickey).trim()],
    ['qm_keyst', String(credential.musickey).trim()],
  ])
  const optional = [
    ['psrf_qqopenid', credential.openid],
    ['psrf_qqaccess_token', credential.access_token],
    ['psrf_qqrefresh_token', credential.refresh_token],
    ['psrf_qqunionid', credential.unionid],
    ['psrf_musickey_createtime', credential.musickeyCreateTime],
    ['psrf_qqrefresh_key', credential.refresh_key],
    ['tmeLoginType', credential.loginType || 2],
  ]
  for (const [name, value] of optional) {
    const normalized = String(value ?? '').trim()
    if (normalized) values.set(name, normalized)
  }
  return { uin: values.get('uin'), cookie: toCookieString(values) }
}

function publicAttempt(attempt) {
  const response = {
    loginId: attempt.id,
    loginMode: 'BROWSER',
    status: attempt.status,
    message: attempt.message,
    expiresAt: new Date(attempt.expiresAt).toISOString(),
  }
  if (attempt.status === 'SUCCESS') response.cookie = attempt.cookie
  if (attempt.errorCode) response.errorCode = attempt.errorCode
  return response
}

async function closeAttemptBrowser(attempt) {
  if (!attempt?.context || attempt.browserClosed) return
  attempt.browserClosed = true
  try {
    await attempt.context.close()
  } catch {
    // The user may already have closed the dedicated Edge window.
  }
}

async function removeAttempt(id) {
  const attempt = attempts.get(id)
  if (!attempt) return false
  attempts.delete(id)
  attempt.cookie = ''
  await closeAttemptBrowser(attempt)
  return true
}

async function cleanupAttempts() {
  const now = Date.now()
  for (const [id, attempt] of attempts.entries()) {
    if (attempt.expiresAt <= now || attempt.completedAt && attempt.completedAt + completedTtlMs <= now) {
      await removeAttempt(id)
    }
  }
}

async function captureLoginResponse(attempt, response) {
  if (attempt.status !== 'WAITING_BROWSER' || !response.url().includes('musicu.fcg')) return
  try {
    const session = sessionFromLoginResponse(await response.json())
    if (session) attempt.session = session
  } catch {
    // Most musicu.fcg responses are unrelated to login and can be ignored.
  }
}

function watchPage(attempt, page) {
  page.on('response', response => {
    void captureLoginResponse(attempt, response)
  })
}

async function launchLoginBrowser() {
  const profileDirectory = process.env.QQ_MUSIC_BROWSER_PROFILE_DIR?.trim() || defaultProfileDirectory
  const executablePath = process.env.QQ_MUSIC_EDGE_PATH?.trim()
  const browserChoice = executablePath ? { executablePath } : { channel: 'msedge' }
  return chromium.launchPersistentContext(profileDirectory, {
    ...browserChoice,
    headless: false,
    viewport: null,
    acceptDownloads: false,
    args: ['--start-maximized', '--no-first-run', '--no-default-browser-check'],
    timeout: 30_000,
  })
}

export async function startQrLogin() {
  await cleanupAttempts()
  for (const id of [...attempts.keys()]) await removeAttempt(id)

  let context
  try {
    context = await launchLoginBrowser()
  } catch (error) {
    throw loginFlowError(
      'QQ_BROWSER_START_FAILED',
      `无法打开 Microsoft Edge 登录窗口${error?.message?.includes('msedge') ? '，请确认已安装 Edge' : ''}`,
    )
  }

  const attempt = {
    id: randomUUID(),
    context,
    status: 'WAITING_BROWSER',
    message: '已打开独立 Edge 窗口，请在 QQ 官方页面完成扫码或账号登录',
    expiresAt: Date.now() + loginTtlMs,
    browserClosed: false,
    session: null,
    cookie: '',
  }
  attempts.set(attempt.id, attempt)
  context.pages().forEach(page => watchPage(attempt, page))
  context.on('page', page => watchPage(attempt, page))
  context.on('close', () => {
    if (attempt.status === 'WAITING_BROWSER' && !attempt.browserClosed) {
      attempt.status = 'FAILED'
      attempt.errorCode = 'QQ_BROWSER_CLOSED'
      attempt.message = '登录窗口已关闭，请重新打开后完成登录'
      attempt.completedAt = Date.now()
    }
  })

  try {
    const page = context.pages()[0] || await context.newPage()
    await page.goto(loginUrl(), { waitUntil: 'domcontentloaded', timeout: 30_000 })
    await page.bringToFront()
  } catch {
    await removeAttempt(attempt.id)
    throw loginFlowError('QQ_BROWSER_NAVIGATION_FAILED', 'QQ 官方登录页面打开失败，请检查网络后重试')
  }
  return publicAttempt(attempt)
}

export async function pollQrLogin(loginId) {
  await cleanupAttempts()
  const attempt = attempts.get(loginId)
  if (!attempt) {
    return {
      loginId,
      loginMode: 'BROWSER',
      status: 'EXPIRED',
      message: '登录窗口已失效，请重新打开',
    }
  }
  if (attempt.status !== 'WAITING_BROWSER') return publicAttempt(attempt)
  if (attempt.expiresAt <= Date.now()) {
    attempt.status = 'EXPIRED'
    attempt.message = '登录等待已超时，请重新打开登录窗口'
    attempt.completedAt = Date.now()
    await closeAttemptBrowser(attempt)
    return publicAttempt(attempt)
  }

  let session = attempt.session
  if (!session) {
    try {
      session = sessionFromBrowserCookies(await attempt.context.cookies())
    } catch {
      attempt.status = 'FAILED'
      attempt.errorCode = 'QQ_BROWSER_UNAVAILABLE'
      attempt.message = '无法读取登录窗口状态，请重新打开'
      attempt.completedAt = Date.now()
      return publicAttempt(attempt)
    }
  }
  if (!session) return publicAttempt(attempt)

  attempt.status = 'SUCCESS'
  attempt.message = 'QQ 音乐登录成功，凭据已交给本机服务加密保存'
  attempt.cookie = session.cookie
  attempt.completedAt = Date.now()
  attempt.session = null
  await closeAttemptBrowser(attempt)
  return publicAttempt(attempt)
}

export async function cancelQrLogin(loginId) {
  const removed = await removeAttempt(loginId)
  return { loginId, status: 'CANCELLED', cancelled: removed }
}

export async function shutdownBrowserLogins() {
  for (const id of [...attempts.keys()]) await removeAttempt(id)
}
