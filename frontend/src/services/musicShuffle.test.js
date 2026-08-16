import assert from 'node:assert/strict'
import test from 'node:test'
import { shuffleTracks } from './musicShuffle.js'

test('shuffleTracks returns a shuffled copy without mutating the source queue', () => {
  const source = [{ id: '1' }, { id: '2' }, { id: '3' }, { id: '4' }]
  const values = [0.1, 0.7, 0.2]
  let index = 0
  const shuffled = shuffleTracks(source, () => values[index++])

  assert.deepEqual(source.map(item => item.id), ['1', '2', '3', '4'])
  assert.deepEqual(shuffled.map(item => item.id), ['2', '4', '3', '1'])
  assert.notStrictEqual(shuffled, source)
})

test('shuffleTracks safely handles empty and missing queues', () => {
  assert.deepEqual(shuffleTracks([]), [])
  assert.deepEqual(shuffleTracks(null), [])
})

