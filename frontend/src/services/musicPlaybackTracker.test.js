import test from 'node:test'
import assert from 'node:assert/strict'
import {
  createPlaybackSession,
  finishPlayback,
  observePlayback,
  startPlayback,
} from './musicPlaybackTracker.js'

for (const playbackType of ['audio', 'youtube']) {
  test(`${playbackType}: start is emitted once and a quick switch is one skip`, () => {
    const track = { id: `${playbackType}:1`, playbackType }
    let result = startPlayback(createPlaybackSession(track), track)
    assert.equal(result.event.type, 'PLAY_START')
    result = startPlayback(result.state, track)
    assert.equal(result.event, null)
    result = finishPlayback(result.state, 5000, 120000, 'switch')
    assert.deepEqual(result.event, { type: 'SKIP', playbackMs: 5000 })
    assert.equal(finishPlayback(result.state, 5000, 120000, 'unmount').event, null)
  })

  test(`${playbackType}: failures under two seconds are not classified as skip`, () => {
    const track = { id: `${playbackType}:failure`, playbackType }
    const started = startPlayback(createPlaybackSession(track), track)
    assert.equal(finishPlayback(started.state, 1000, 120000, 'unmount').event, null)
  })

  test(`${playbackType}: complete and repeat events are not duplicated`, () => {
    const track = { id: `${playbackType}:complete`, playbackType }
    const started = startPlayback(createPlaybackSession(track), track)
    const completed = observePlayback(started.state, 108000, 120000)
    assert.equal(completed.event.type, 'COMPLETE')
    assert.equal(observePlayback(completed.state, 115000, 120000).event, null)
    const ended = finishPlayback(completed.state, 120000, 120000, 'ended')
    assert.equal(ended.event, null)
    const repeated = startPlayback(ended.state, track)
    assert.deepEqual(repeated.event, { type: 'REPEAT', playbackMs: 120000 })
  })
}
