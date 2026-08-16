export function createPlaybackSession(track) {
  return { track, started: false, completed: false, maxMs: 0 }
}

export function startPlayback(session, track) {
  let state = session?.track?.id === track?.id ? { ...session } : createPlaybackSession(track)
  if (state.completed) {
    const playbackMs = state.maxMs
    state = { ...state, started: true, completed: false, maxMs: 0 }
    return { state, event: { type: 'REPEAT', playbackMs } }
  }
  if (!state.started) {
    state.started = true
    return { state, event: { type: 'PLAY_START', playbackMs: 0 } }
  }
  return { state, event: null }
}

export function observePlayback(session, playedMs, durationMs) {
  if (!session) return { state: session, event: null }
  const state = { ...session, maxMs: Math.max(session.maxMs, normalized(playedMs)) }
  const duration = normalized(durationMs)
  if (state.started && !state.completed && duration > 0 && state.maxMs >= duration * 0.9) {
    state.completed = true
    return { state, event: { type: 'COMPLETE', playbackMs: state.maxMs } }
  }
  return { state, event: null }
}

export function finishPlayback(session, playedMs, durationMs, reason = 'switch') {
  if (!session) return { state: session, event: null }
  const state = { ...session, maxMs: Math.max(session.maxMs, normalized(playedMs)) }
  const duration = normalized(durationMs)
  if (reason === 'ended') {
    if (!state.started && !state.completed) return { state, event: null }
    state.started = false
    if (!state.completed) {
      state.completed = true
      return { state, event: { type: 'COMPLETE', playbackMs: Math.max(state.maxMs, duration) } }
    }
    return { state, event: null }
  }
  if (!state.started || state.completed) return { state, event: null }
  const threshold = duration > 0 ? Math.min(30000, Math.max(2000, duration / 4)) : 30000
  state.started = false
  if (state.maxMs >= 2000 && state.maxMs < threshold) {
    return { state, event: { type: 'SKIP', playbackMs: state.maxMs } }
  }
  return { state, event: null }
}

function normalized(value) {
  const number = Number(value)
  return Number.isFinite(number) ? Math.max(0, Math.round(number)) : 0
}
