import assert from 'node:assert/strict'
import test from 'node:test'
import { navigateBack, returnState } from './navigation.js'

function routerDouble() {
  const calls = []
  return {
    calls,
    back() { calls.push(['back']) },
    push(value) { calls.push(['push', value]) },
  }
}

test('navigateBack uses the real browser history entry first', () => {
  const router = routerDouble()
  navigateBack(router, '/music', { state: { back: '/music?search=英雄联盟&type=VIDEO&page=2' } })
  assert.deepEqual(router.calls, [['back']])
})

test('navigateBack restores the recorded source when browser back state is unavailable', () => {
  const router = routerDouble()
  navigateBack(router, '/music', { state: { returnTo: '/agent?conversation=abc' } })
  assert.deepEqual(router.calls, [['push', '/agent?conversation=abc']])
})

test('navigateBack falls back safely for a directly opened detail URL', () => {
  const router = routerDouble()
  navigateBack(router, '/music', { state: {} })
  assert.deepEqual(router.calls, [['push', '/music']])
})

test('returnState records the complete source route', () => {
  assert.deepEqual(returnState({ fullPath: '/music?search=lol&type=VIDEO&page=1' }), {
    returnTo: '/music?search=lol&type=VIDEO&page=1',
  })
})
