import assert from 'node:assert/strict'
import test from 'node:test'
import { readQqHomePage, writeQqHomePage } from './musicDiscoveryCache.js'

function memoryStorage() {
  const values = new Map()
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
  }
}

test('QQ discovery page is restored separately for each user', () => {
  const storage = memoryStorage()
  writeQqHomePage(storage, 7, 4)
  writeQqHomePage(storage, 8, 2)
  assert.equal(readQqHomePage(storage, 7), 4)
  assert.equal(readQqHomePage(storage, 8), 2)
})

test('QQ discovery page falls back to the first page for invalid cache data', () => {
  const storage = memoryStorage()
  storage.setItem('sonora:music:qq-home-page:7', '-3')
  assert.equal(readQqHomePage(storage, 7), 1)
  assert.equal(readQqHomePage(null, 7), 1)
})
