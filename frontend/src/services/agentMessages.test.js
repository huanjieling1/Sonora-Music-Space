import assert from 'node:assert/strict'
import test from 'node:test'
import { restoreHistoryMessage } from './agentMessages.js'

test('restores persisted music card actions after a conversation refresh', () => {
  const action = { id: 'action-1', type: 'SHOW_MUSIC_RESULTS', recommendation: { tracks: [] } }

  assert.deepEqual(restoreHistoryMessage({ id: 2, role: 'ASSISTANT', content: '结果', actions: [action] }), {
    id: 2,
    role: 'ASSISTANT',
    content: '结果',
    actions: [action],
    error: false,
  })
})

test('keeps legacy text-only messages compatible', () => {
  assert.deepEqual(restoreHistoryMessage({ id: 1, role: 'USER', content: '你好' }), {
    id: 1,
    role: 'USER',
    content: '你好',
    actions: [],
    error: false,
  })
})
