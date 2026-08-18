import { defineStore } from 'pinia'

/** Global music player state and trusted Agent action handler. */
export const useMusicStore = defineStore('music', {
  state: () => ({
    handledActionIds: [],
    activeUserId: '',
    queue: [],
    currentTrack: null,
    playRequestId: 0,
    playbackCurrentTime: 0,
    playbackDuration: 0,
    playbackPaused: true,
    playbackQuality: '',
    seekRequestId: 0,
    seekTargetSeconds: 0,
    trackStates: {},
    playlistRevision: 0,
    lastPlaylistMutation: null,
  }),
  actions: {
    initializePlayer(userId, checkpoint = null) {
      const normalizedUserId = String(userId ?? '').trim()
      const saved = restorePlayerState(normalizedUserId)
      this.activeUserId = normalizedUserId
      this.queue = saved.queue
      this.currentTrack = saved.currentTrack
      if (!this.currentTrack && checkpoint?.track?.id) {
        this.queue = uniqueTracks([...(checkpoint.queue || []), checkpoint.track])
        this.currentTrack = this.queue.find(item => trackKey(item) === trackKey(checkpoint.track)) || checkpoint.track
      }
      if (this.currentTrack && !this.queue.some(item => trackKey(item) === trackKey(this.currentTrack))) {
        this.queue.push(this.currentTrack)
      }
      this.resetPlaybackTelemetry()
      persistPlayerState(this)
    },
    applyAgentActions(actions) {
      const handled = new Set(this.handledActionIds)
      const incoming = Array.isArray(actions) ? actions : []
      for (const action of incoming) {
        if (!action?.id || !action?.type || handled.has(action.id)) continue
        if (action.type === 'QUEUE_MUSIC_RESULTS') {
          for (const track of action.tracks || []) this.addToQueue(track)
        } else if (action.type === 'PLAY_TRACK' && action.track?.id) {
          this.playTrack(action.track)
        }
        handled.add(action.id)
      }
      this.handledActionIds = [...handled].slice(-200)
    },
    playTrack(track, sourceQueue = null) {
      if (!track?.id) return
      if (Array.isArray(sourceQueue) && sourceQueue.length) {
        this.queue = uniqueTracks(sourceQueue)
      } else if (!this.queue.some(item => trackKey(item) === trackKey(track))) {
        this.queue.push(track)
      }
      this.currentTrack = this.queue.find(item => trackKey(item) === trackKey(track)) || track
      this.playRequestId += 1
      this.resetPlaybackTelemetry()
      persistPlayerState(this)
    },
    addToQueue(track) {
      if (!track?.id || this.queue.some(item => trackKey(item) === trackKey(track))) return
      this.queue.push(track)
      persistPlayerState(this)
    },
    playNext() {
      if (!this.queue.length) return
      const index = this.queue.findIndex(item => trackKey(item) === trackKey(this.currentTrack))
      this.currentTrack = this.queue[index < 0 || index >= this.queue.length - 1 ? 0 : index + 1]
      this.playRequestId += 1
      this.resetPlaybackTelemetry()
      persistPlayerState(this)
    },
    playPrevious() {
      if (!this.queue.length) return
      const index = this.queue.findIndex(item => trackKey(item) === trackKey(this.currentTrack))
      this.currentTrack = this.queue[index <= 0 ? this.queue.length - 1 : index - 1]
      this.playRequestId += 1
      this.resetPlaybackTelemetry()
      persistPlayerState(this)
    },
    removeFromQueue(track) {
      const key = trackKey(track)
      this.queue = this.queue.filter(item => trackKey(item) !== key)
      if (trackKey(this.currentTrack) === key) {
        this.currentTrack = null
        this.playRequestId += 1
        this.resetPlaybackTelemetry()
      }
      persistPlayerState(this)
    },
    clearQueue() {
      this.queue = []
      this.currentTrack = null
      this.playRequestId += 1
      this.resetPlaybackTelemetry()
      persistPlayerState(this)
    },
    updatePlaybackTelemetry({ currentTime, duration, paused, quality } = {}) {
      if (Number.isFinite(currentTime)) this.playbackCurrentTime = Math.max(0, currentTime)
      if (Number.isFinite(duration)) this.playbackDuration = Math.max(0, duration)
      if (typeof paused === 'boolean') this.playbackPaused = paused
      if (typeof quality === 'string') this.playbackQuality = quality
    },
    resetPlaybackTelemetry() {
      this.playbackCurrentTime = 0
      this.playbackDuration = Math.max(0, Number(this.currentTrack?.durationMs || 0) / 1000)
      this.playbackPaused = true
      this.playbackQuality = ''
    },
    seekTo(seconds) {
      const target = Number(seconds)
      if (!Number.isFinite(target) || target < 0) return
      this.seekTargetSeconds = target
      this.seekRequestId += 1
    },
    setTrackState(track, patch = {}) {
      const key = trackKey(track)
      if (!key) return
      this.trackStates = {
        ...this.trackStates,
        [key]: { ...(this.trackStates[key] || {}), ...patch },
      }
    },
    recordPlaylistTrackAdded(track, playlist) {
      const key = trackKey(track)
      if (!key || !playlist?.id) return
      this.setTrackState(track, { saved: true })
      this.lastPlaylistMutation = {
        type: 'TRACK_ADDED',
        trackKey: key,
        playlistId: playlist.id,
        playlist,
        occurredAt: Date.now(),
      }
      this.playlistRevision += 1
    },
  },
})

const STORAGE_PREFIX = 'sonora.music.player.v2'

function restorePlayerState(userId) {
  if (typeof window === 'undefined' || !userId) return { queue: [], currentTrack: null }
  try {
    const saved = JSON.parse(window.sessionStorage.getItem(`${STORAGE_PREFIX}.${userId}`) || '{}')
    return {
      queue: Array.isArray(saved.queue) ? uniqueTracks(saved.queue) : [],
      currentTrack: saved.currentTrack?.id ? saved.currentTrack : null,
    }
  } catch {
    return { queue: [], currentTrack: null }
  }
}

function persistPlayerState(store) {
  if (typeof window === 'undefined' || !store.activeUserId) return
  try {
    window.sessionStorage.setItem(`${STORAGE_PREFIX}.${store.activeUserId}`, JSON.stringify({
      queue: store.queue,
      currentTrack: store.currentTrack,
    }))
  } catch {
    // Playback still works when session storage is unavailable.
  }
}

function trackKey(track) {
  return track?.provider && track?.id ? `${track.provider}:${track.id}` : track?.id || ''
}

function uniqueTracks(tracks) {
  const seen = new Set()
  return tracks.filter(track => {
    const key = trackKey(track)
    if (!key || seen.has(key)) return false
    seen.add(key)
    return true
  })
}
