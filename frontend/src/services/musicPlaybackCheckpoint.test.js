import test from 'node:test'
import assert from 'node:assert/strict'
import {
  checkpointTrackKey,
  clearPlaybackCheckpoint,
  readPlaybackCheckpoint,
  resumeSecondsFor,
  savePlaybackCheckpoint,
} from './musicPlaybackCheckpoint.js'

function memoryStorage() {
  const values = new Map()
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key),
  }
}

const track = { id: '001', provider: 'qq', name: 'Test' }

test('stores and restores a meaningful checkpoint per user', () => {
  const storage = memoryStorage()
  assert.equal(savePlaybackCheckpoint(storage, 7, {
    track,
    queue: [track],
    positionSeconds: 42.5,
    durationSeconds: 180,
    playbackSessionId: 'session-7',
    listenedMs: 39000,
  }, 1000), true)

  const restored = readPlaybackCheckpoint(storage, 7, 2000)
  assert.equal(restored.positionSeconds, 42.5)
  assert.equal(restored.track.id, '001')
  assert.equal(restored.playbackSessionId, 'session-7')
  assert.equal(restored.listenedMs, 39000)
  assert.equal(readPlaybackCheckpoint(storage, 8, 2000), null)
  assert.equal(resumeSecondsFor(restored, track), 42.5)
})

test('ignores accidental starts and checkpoints near the end', () => {
  const storage = memoryStorage()
  assert.equal(savePlaybackCheckpoint(storage, 7, {
    track, positionSeconds: 3, durationSeconds: 180,
  }), false)
  assert.equal(savePlaybackCheckpoint(storage, 7, {
    track, positionSeconds: 60, durationSeconds: 180,
  }), true)
  assert.equal(savePlaybackCheckpoint(storage, 7, {
    track, positionSeconds: 174, durationSeconds: 180,
  }), false)
  assert.equal(readPlaybackCheckpoint(storage, 7), null)
})

test('expires old checkpoints and only clears the matching track', () => {
  const storage = memoryStorage()
  savePlaybackCheckpoint(storage, 7, {
    track, positionSeconds: 30, durationSeconds: 180,
  }, 1000)
  assert.equal(clearPlaybackCheckpoint(storage, 7, { id: 'other', provider: 'qq' }), false)
  assert.equal(readPlaybackCheckpoint(storage, 7, 2000)?.positionSeconds, 30)
  assert.equal(clearPlaybackCheckpoint(storage, 7, track), true)
  assert.equal(readPlaybackCheckpoint(storage, 7, 2000), null)

  savePlaybackCheckpoint(storage, 7, {
    track, positionSeconds: 30, durationSeconds: 180,
  }, 1000)
  assert.equal(readPlaybackCheckpoint(storage, 7, 31 * 24 * 60 * 60 * 1000), null)
})

test('uses provider and id to distinguish tracks', () => {
  assert.equal(checkpointTrackKey(track), 'qq:001')
  assert.equal(resumeSecondsFor({ track, positionSeconds: 20, durationSeconds: 100 }, {
    ...track, provider: 'audius',
  }), 0)
})
