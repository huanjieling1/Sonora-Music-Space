const STORAGE_PREFIX = 'sonora.music.resume.v1'
const MIN_RESUME_SECONDS = 5
const END_GUARD_SECONDS = 10
const MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000

export function checkpointTrackKey(track) {
  return track?.provider && track?.id ? `${track.provider}:${track.id}` : track?.id || ''
}

export function readPlaybackCheckpoint(storage, userId, now = Date.now()) {
  const key = storageKey(userId)
  if (!storage || !key) return null
  try {
    const value = JSON.parse(storage.getItem(key) || 'null')
    const positionSeconds = finitePositive(value?.positionSeconds)
    const durationSeconds = finitePositive(value?.durationSeconds)
    const updatedAt = Number(value?.updatedAt)
    if (!value?.track?.id || !checkpointTrackKey(value.track) || !Number.isFinite(updatedAt)) return null
    if (updatedAt + MAX_AGE_MS < now || !isResumable(positionSeconds, durationSeconds)) {
      storage.removeItem(key)
      return null
    }
    return {
      track: value.track,
      queue: Array.isArray(value.queue) ? value.queue.filter(track => track?.id) : [],
      positionSeconds,
      durationSeconds,
      playbackSessionId: typeof value.playbackSessionId === 'string' ? value.playbackSessionId : '',
      listenedMs: finitePositive(value.listenedMs),
      updatedAt,
    }
  } catch {
    return null
  }
}

export function savePlaybackCheckpoint(storage, userId, state, now = Date.now()) {
  const key = storageKey(userId)
  const track = state?.track
  const positionSeconds = finitePositive(state?.positionSeconds)
  const durationSeconds = finitePositive(state?.durationSeconds)
  if (!storage || !key || !track?.id) return false
  if (!isResumable(positionSeconds, durationSeconds)) {
    if (positionSeconds >= MIN_RESUME_SECONDS && durationSeconds > 0) {
      clearPlaybackCheckpoint(storage, userId, track)
    }
    return false
  }
  try {
    storage.setItem(key, JSON.stringify({
      track,
      queue: Array.isArray(state.queue) ? state.queue.filter(item => item?.id) : [],
      positionSeconds,
      durationSeconds,
      playbackSessionId: typeof state.playbackSessionId === 'string' ? state.playbackSessionId : '',
      listenedMs: finitePositive(state.listenedMs),
      updatedAt: now,
    }))
    return true
  } catch {
    return false
  }
}

export function clearPlaybackCheckpoint(storage, userId, track = null) {
  const key = storageKey(userId)
  if (!storage || !key) return false
  try {
    if (track) {
      const saved = JSON.parse(storage.getItem(key) || 'null')
      if (checkpointTrackKey(saved?.track) !== checkpointTrackKey(track)) return false
    }
    storage.removeItem(key)
    return true
  } catch {
    return false
  }
}

export function resumeSecondsFor(checkpoint, track) {
  if (checkpointTrackKey(checkpoint?.track) !== checkpointTrackKey(track)) return 0
  return isResumable(checkpoint.positionSeconds, checkpoint.durationSeconds)
    ? checkpoint.positionSeconds
    : 0
}

function isResumable(positionSeconds, durationSeconds) {
  if (positionSeconds < MIN_RESUME_SECONDS) return false
  if (durationSeconds <= 0) return true
  return positionSeconds < durationSeconds - END_GUARD_SECONDS
    && positionSeconds / durationSeconds < 0.95
}

function storageKey(userId) {
  const normalized = String(userId ?? '').trim()
  return normalized ? `${STORAGE_PREFIX}.${normalized}` : ''
}

function finitePositive(value) {
  const number = Number(value)
  return Number.isFinite(number) ? Math.max(0, number) : 0
}
