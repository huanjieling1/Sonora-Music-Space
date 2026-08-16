const configuredBase = (import.meta.env.VITE_API_BASE_URL || '').trim()
const apiBase = configuredBase.replace(/\/$/, '')
let csrf = null

export class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export function apiUrl(path) {
  return `${apiBase}${path}`
}

export function clearCsrf() {
  csrf = null
}

export async function loadCsrf(force = false) {
  if (csrf && !force) return csrf
  const response = await fetch(apiUrl('/api/auth/csrf'), {
    credentials: 'include',
    cache: 'no-store',
    headers: { Accept: 'application/json' },
  })
  const payload = await parsePayload(response)
  if (!response.ok) throw new ApiError(payload.message || '无法获取安全令牌', response.status)
  if (!payload.data?.headerName || !payload.data?.token) {
    throw new ApiError('服务器返回的安全令牌格式不正确', response.status)
  }
  csrf = payload.data
  return csrf
}

export async function request(path, options = {}, retried = false) {
  const method = (options.method || 'GET').toUpperCase()
  const unsafe = !['GET', 'HEAD', 'OPTIONS'].includes(method)
  const headers = new Headers(options.headers || {})
  if (!headers.has('Accept')) headers.set('Accept', 'application/json')
  if (options.body != null) headers.set('Content-Type', 'application/json')
  if (unsafe) {
    const token = await loadCsrf()
    headers.set(token.headerName, token.token)
  }

  const response = await fetch(apiUrl(path), {
    ...options,
    method,
    headers,
    credentials: 'include',
  })
  const payload = await parsePayload(response)

  if (response.status === 403 && unsafe && !retried) {
    await loadCsrf(true)
    return request(path, options, true)
  }
  if (!response.ok) throw new ApiError(payload.message || '请求失败', response.status)
  return payload
}

async function parsePayload(response) {
  const text = await response.text()
  const normalized = text.replace(/^\uFEFF/, '').trim()

  if (!normalized) {
    return { message: `服务器返回了空响应（HTTP ${response.status}）` }
  }

  try {
    return JSON.parse(normalized)
  } catch {
    const contentType = response.headers.get('content-type') || '未知类型'
    return {
      message: `服务器返回了非 JSON 响应（HTTP ${response.status}，${contentType}）`,
    }
  }
}
