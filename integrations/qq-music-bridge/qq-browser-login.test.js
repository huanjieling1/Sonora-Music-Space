import test from 'node:test'
import assert from 'node:assert/strict'
import { sessionFromBrowserCookies, sessionFromLoginResponse } from './qq-browser-login.js'

test('keeps only QQ Music session cookies and creates canonical aliases', () => {
  const session = sessionFromBrowserCookies([
    { domain: '.qq.com', name: 'uin', value: 'o12345678' },
    { domain: '.y.qq.com', name: 'qm_keyst', value: 'music-secret' },
    { domain: '.qq.com', name: 'unrelated', value: 'discard-me' },
    { domain: '.example.com', name: 'qqmusic_key', value: 'foreign-secret' },
  ])

  assert.equal(session.uin, 'o12345678')
  assert.match(session.cookie, /(?:^|; )uin=o12345678(?:;|$)/)
  assert.match(session.cookie, /(?:^|; )qqmusic_key=music-secret(?:;|$)/)
  assert.match(session.cookie, /(?:^|; )qm_keyst=music-secret(?:;|$)/)
  assert.doesNotMatch(session.cookie, /discard-me|foreign-secret|unrelated/)
})

test('does not treat a QQ authorization cookie as a QQ Music session', () => {
  assert.equal(sessionFromBrowserCookies([
    { domain: '.qq.com', name: 'uin', value: 'o12345678' },
    { domain: '.qq.com', name: 'p_skey', value: 'oauth-only' },
  ]), null)
})

test('extracts QQ Music credentials from a nested official login response', () => {
  const session = sessionFromLoginResponse({
    code: 0,
    req_0: {
      code: 0,
      data: {
        musicid: '12345678',
        musickey: 'music-secret',
        openid: 'openid-value',
        loginType: 2,
      },
    },
  })

  assert.equal(session.uin, '12345678')
  assert.match(session.cookie, /qqmusic_key=music-secret/)
  assert.match(session.cookie, /psrf_qqopenid=openid-value/)
  assert.doesNotMatch(session.cookie, /undefined|null/)
})
