<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ChevronsDown, ListMusic, Maximize2, Minimize2, Music2, Pause, Play, SkipBack, SkipForward, Trash2, Volume2, VolumeX, X } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import { request } from '../services/api'
import { runMusicExperienceTransition } from '../services/musicExperienceTransition'
import { createPlaybackSession, finishPlayback, observePlayback, startPlayback } from '../services/musicPlaybackTracker'
import { useMusicStore } from '../stores/music'
import MusicTrackActions from './MusicTrackActions.vue'
import { returnState } from '../services/navigation'

const props = defineProps({ immersive: { type: Boolean, default: false } })
const music = useMusicStore()
const router = useRouter()
const route = useRoute()
const POSITION_STORAGE_KEY = 'sonora.music.player.position.v1'
const MODE_STORAGE_KEY = 'sonora.music.player.mode.v1'
const VOLUME_STORAGE_KEY = 'sonora.music.player.volume.v1'
const VIEWPORT_GAP = 8
const paused = ref(true)
const ready = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const volume = ref(readStoredVolume())
const quality = ref('')
const errorMessage = ref('')
const seeking = ref(false)
const queueOpen = ref(false)
const youtubeHost = ref(null)
const playerDock = ref(null)
const queuePanel = ref(null)
const queueToggle = ref(null)
const dragging = ref(false)
const dockPosition = ref(readDockPosition())
const playerMode = ref(readPlayerMode())
const viewport = ref(readViewport())

let audio = null
let youtube = null
let youtubeSdkPromise = null
let youtubeTimer = null
let playback = null
let lastVolume = Number(volume.value) > 0 ? Number(volume.value) : 0.72
let dragState = null
let suppressTrackClick = false

const current = computed(() => music.currentTrack)
const volumePercent = computed(() => Math.round(Number(volume.value) * 100))
const canSeek = computed(() => Boolean(current.value) && duration.value > 0)
const dockStyle = computed(() => ({
  viewTransitionName: props.immersive ? 'none' : 'sonora-player-experience',
  ...(dockPosition.value && playerMode.value !== 'BAR' ? {
    right: `${dockPosition.value.right}px`,
    bottom: `${dockPosition.value.bottom}px`,
  } : {}),
}))
const queueStyle = computed(() => {
  if (props.immersive) return { right: '24px', bottom: '112px', top: 'auto', maxHeight: 'min(420px, calc(100vh - 135px))' }
  if (playerMode.value === 'BAR') return { right: '18px', bottom: '118px', top: 'auto', maxHeight: 'min(420px, calc(100vh - 145px))' }
  const right = dockPosition.value?.right ?? 18
  const bottom = dockPosition.value?.bottom ?? 18
  const playerTop = viewport.value.height - bottom - 128
  if (playerTop >= 180) {
    return { right: `${right}px`, bottom: `${bottom + 138}px`, maxHeight: `${Math.min(390, playerTop - 10)}px` }
  }
  const top = viewport.value.height - bottom + 10
  return { right: `${right}px`, top: `${top}px`, bottom: 'auto', maxHeight: `${Math.max(120, viewport.value.height - top - VIEWPORT_GAP)}px` }
})

onMounted(async () => {
  window.addEventListener('resize', handleViewportResize)
  document.addEventListener('pointerdown', handleQueueOutsidePointerDown, true)
  await nextTick()
  constrainDockPosition()
  initializeAudio()
  if (current.value) await loadCurrentTrack()
})

watch(() => music.playRequestId, async () => {
  if (!music.currentTrack) {
    stopPlayers()
    return
  }
  await loadCurrentTrack()
})

watch(() => music.seekRequestId, () => seekTo(music.seekTargetSeconds))

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleViewportResize)
  document.removeEventListener('pointerdown', handleQueueOutsidePointerDown, true)
  removeDockDragListeners()
  finishCurrent('unmount')
  stopPlayers()
  audio?.removeAttribute('src')
  audio?.load()
  youtube?.destroy?.()
})

function initializeAudio() {
  audio = new Audio()
  audio.preload = 'metadata'
  audio.volume = Number(volume.value)
  audio.addEventListener('play', handleStarted)
  audio.addEventListener('pause', () => {
    paused.value = true
    publishPlaybackState()
  })
  audio.addEventListener('ended', handleEnded)
  audio.addEventListener('timeupdate', () => {
    if (!seeking.value) currentTime.value = Math.max(0, Number(audio.currentTime) || 0)
    if (Number.isFinite(audio.duration) && audio.duration > 0) duration.value = audio.duration
    publishPlaybackState()
    observeProgress()
  })
  audio.addEventListener('loadedmetadata', () => {
    if (Number.isFinite(audio.duration) && audio.duration > 0) duration.value = audio.duration
    publishPlaybackState()
  })
  audio.addEventListener('canplay', () => { ready.value = true })
  audio.addEventListener('error', () => {
    if (current.value?.playbackType !== 'audio') return
    ready.value = false
    paused.value = true
    publishPlaybackState()
    errorMessage.value = '当前歌曲暂时无法播放，请切换下一首。'
  })
}

async function loadCurrentTrack() {
  const track = current.value
  if (!track) return
  await finishCurrent('switch')
  playback = createPlaybackSession(track)
  currentTime.value = 0
  duration.value = Math.max(0, Number(track.durationMs || 0) / 1000)
  quality.value = track.provider === 'qq' ? 'detecting' : ''
  publishPlaybackState()
  ready.value = false
  errorMessage.value = ''
  if (track.playbackType === 'youtube') await playYoutube(track)
  else await playAudio(track)
}

async function playAudio(track) {
  stopYoutubeTimer()
  youtube?.pauseVideo?.()
  let url = track.playbackUrl
  if (track.provider === 'qq') {
    try {
      const result = await request(track.playbackUrl)
      url = result.data?.url
      quality.value = result.data?.quality || ''
      publishPlaybackState()
      if (!url) throw new Error('QQ 音乐没有返回播放地址')
    } catch (error) {
      quality.value = ''
      errorMessage.value = error.message
      paused.value = true
      publishPlaybackState()
      return
    }
  }
  if (audio.src !== url) {
    audio.src = url
    audio.load()
  }
  audio.volume = Number(volume.value)
  ready.value = true
  try {
    await audio.play()
  } catch {
    paused.value = true
    publishPlaybackState()
    errorMessage.value = '浏览器阻止了自动播放，请点击下方播放按钮。'
  }
}

async function playYoutube(track) {
  audio?.pause()
  try {
    await loadYoutubeSdk()
    await nextTick()
    if (!youtube) {
      youtube = new window.YT.Player(youtubeHost.value, {
        width: '100%', height: '100%', videoId: track.playbackUrl,
        playerVars: { playsinline: 1, origin: window.location.origin },
        events: {
          onReady: event => {
            ready.value = true
            event.target.setVolume(volumePercent.value)
            event.target.playVideo()
          },
          onStateChange: event => {
            if (event.data === window.YT.PlayerState.PLAYING) {
              paused.value = false
              handleStarted()
              startYoutubeTimer()
            }
            if (event.data === window.YT.PlayerState.PAUSED) {
              paused.value = true
              publishPlaybackState()
            }
            if (event.data === window.YT.PlayerState.ENDED) handleEnded()
          },
          onError: () => {
            ready.value = false
            paused.value = true
            publishPlaybackState()
            errorMessage.value = '该 YouTube 内容当前无法嵌入播放。'
          },
        },
      })
    } else {
      ready.value = true
      youtube.setVolume(volumePercent.value)
      youtube.loadVideoById(track.playbackUrl)
    }
  } catch (error) {
    paused.value = true
    publishPlaybackState()
    errorMessage.value = error.message
  }
}

function loadYoutubeSdk() {
  if (window.YT?.Player) return Promise.resolve()
  if (youtubeSdkPromise) return youtubeSdkPromise
  youtubeSdkPromise = new Promise((resolve, reject) => {
    const timeout = window.setTimeout(() => reject(new Error('YouTube 播放器加载超时')), 15000)
    const previous = window.onYouTubeIframeAPIReady
    window.onYouTubeIframeAPIReady = () => {
      window.clearTimeout(timeout)
      previous?.()
      resolve()
    }
    if (!document.querySelector('script[data-youtube-player]')) {
      const script = document.createElement('script')
      script.src = 'https://www.youtube.com/iframe_api'
      script.async = true
      script.dataset.youtubePlayer = 'true'
      script.onerror = () => reject(new Error('YouTube 播放器脚本加载失败'))
      document.head.appendChild(script)
    }
  })
  return youtubeSdkPromise
}

async function toggle() {
  if (!current.value) return
  if (current.value.playbackType === 'youtube') {
    if (!youtube || !ready.value) return
    if (paused.value) youtube.playVideo()
    else youtube.pauseVideo()
  } else if (audio) {
    if (audio.paused) {
      try { await audio.play() } catch { errorMessage.value = '无法继续播放当前歌曲。' }
    } else audio.pause()
  }
}

async function handleStarted() {
  paused.value = false
  publishPlaybackState()
  const result = startPlayback(playback, current.value)
  playback = result.state
  if (result.event) await sendEvent(playback.track, result.event.type, result.event.playbackMs)
}

async function observeProgress() {
  if (!playback || playback.track.id !== current.value?.id) return
  const result = observePlayback(playback, Math.round(currentTime.value * 1000), Math.round(duration.value * 1000))
  playback = result.state
  if (result.event) await sendEvent(playback.track, result.event.type, result.event.playbackMs)
}

async function finishCurrent(reason) {
  if (!playback) return
  const result = finishPlayback(playback, Math.round(currentTime.value * 1000), Math.round(duration.value * 1000), reason)
  playback = result.state
  if (result.event) await sendEvent(playback.track, result.event.type, result.event.playbackMs)
}

async function handleEnded() {
  await finishCurrent('ended')
  music.playNext()
}

async function sendEvent(track, eventType, playbackMs = null) {
  if (!track?._searchId || !track.id) return
  try {
    await request('/api/music/events', {
      method: 'POST', keepalive: true,
      body: JSON.stringify({
        eventId: crypto.randomUUID(), searchId: track._searchId,
        trackId: track.id, eventType,
        playbackMs: playbackMs == null ? null : Math.max(0, Math.round(playbackMs)),
      }),
    })
  } catch (error) {
    errorMessage.value = error.message
  }
}

function seek(event) {
  seekTo(event?.target?.value)
}

function seekTo(value) {
  if (!canSeek.value) return
  const target = Math.min(Math.max(0, Number(value) || 0), duration.value)
  currentTime.value = target
  if (current.value.playbackType === 'youtube') youtube?.seekTo?.(target, true)
  else if (audio) audio.currentTime = target
  publishPlaybackState()
}

function setVolume() {
  if (Number(volume.value) > 0) lastVolume = Number(volume.value)
  if (audio) audio.volume = Number(volume.value)
  youtube?.setVolume?.(volumePercent.value)
  try {
    window.localStorage.setItem(VOLUME_STORAGE_KEY, String(volume.value))
  } catch {
    // 音量调节仍然有效；本地存储不可用时只是不跨刷新保存。
  }
}

function handleVolumeInput(event) {
  volume.value = Math.min(1, Math.max(0, Number(event.target.value) || 0))
  setVolume()
}

function toggleMute() {
  volume.value = Number(volume.value) > 0 ? 0 : lastVolume || 0.72
  setVolume()
}

function syncYoutube() {
  if (!youtube || current.value?.playbackType !== 'youtube') return
  const length = Number(youtube.getDuration?.())
  const position = Number(youtube.getCurrentTime?.())
  if (length > 0) duration.value = length
  if (!seeking.value && position >= 0) currentTime.value = position
  publishPlaybackState()
  observeProgress()
}

function startYoutubeTimer() {
  stopYoutubeTimer()
  youtubeTimer = window.setInterval(syncYoutube, 500)
}

function stopYoutubeTimer() {
  if (youtubeTimer !== null) window.clearInterval(youtubeTimer)
  youtubeTimer = null
}

function stopPlayers() {
  audio?.pause()
  youtube?.pauseVideo?.()
  stopYoutubeTimer()
  paused.value = true
  publishPlaybackState()
}

function publishPlaybackState() {
  music.updatePlaybackTelemetry({
    currentTime: Number(currentTime.value),
    duration: Number(duration.value),
    paused: Boolean(paused.value),
    quality: quality.value,
  })
}

async function openCurrentTrack() {
  if (!current.value?.id) return
  await runMusicExperienceTransition(() => router.push({
      name: 'music-track',
      params: { provider: current.value.provider || 'unknown', trackId: current.value.id },
      state: returnState(route),
    }), 'enter')
}

function startDockDrag(event) {
  if (props.immersive || playerMode.value === 'BAR' || event.button !== 0 || !playerDock.value) return
  const rect = playerDock.value.getBoundingClientRect()
  dragState = {
    pointerId: event.pointerId,
    startX: event.clientX,
    startY: event.clientY,
    startRight: window.innerWidth - rect.right,
    startBottom: window.innerHeight - rect.bottom,
    handle: event.currentTarget,
  }
  event.currentTarget.setPointerCapture?.(event.pointerId)
  window.addEventListener('pointermove', moveDock, { passive: false })
  window.addEventListener('pointerup', endDockDrag)
  window.addEventListener('pointercancel', cancelDockDrag)
}

function moveDock(event) {
  if (!dragState || event.pointerId !== dragState.pointerId || !playerDock.value) return
  const deltaX = event.clientX - dragState.startX
  const deltaY = event.clientY - dragState.startY
  if (!dragging.value && Math.hypot(deltaX, deltaY) < 5) return
  dragging.value = true
  suppressTrackClick = true
  queueOpen.value = false
  const rect = playerDock.value.getBoundingClientRect()
  dockPosition.value = clampDockPosition(
    dragState.startRight - deltaX,
    dragState.startBottom - deltaY,
    rect.width,
    rect.height,
  )
  event.preventDefault()
}

function endDockDrag(event) {
  if (!dragState || event.pointerId !== dragState.pointerId) return
  const dragHandle = dragState.handle
  dragState.handle?.releasePointerCapture?.(event.pointerId)
  if (dragging.value) {
    persistDockPosition()
    dragHandle?.blur?.()
  }
  dragging.value = false
  dragState = null
  removeDockDragListeners()
  window.setTimeout(() => { suppressTrackClick = false }, 0)
}

function cancelDockDrag(event) {
  if (!dragState || event.pointerId !== dragState.pointerId) return
  if (dragging.value) {
    persistDockPosition()
    dragState.handle?.blur?.()
  }
  dragging.value = false
  dragState = null
  removeDockDragListeners()
  suppressTrackClick = false
}

function removeDockDragListeners() {
  window.removeEventListener('pointermove', moveDock)
  window.removeEventListener('pointerup', endDockDrag)
  window.removeEventListener('pointercancel', cancelDockDrag)
}

function handleTrackClick() {
  if (suppressTrackClick) return
  openCurrentTrack()
}

function handleViewportResize() {
  viewport.value = readViewport()
  constrainDockPosition()
}

function handleQueueOutsidePointerDown(event) {
  if (!queueOpen.value) return
  const target = event.target
  if (!(target instanceof Node)) return
  if (queuePanel.value?.contains(target) || queueToggle.value?.contains(target)) return
  queueOpen.value = false
}

function constrainDockPosition() {
  if (!dockPosition.value || !playerDock.value) return
  const rect = playerDock.value.getBoundingClientRect()
  dockPosition.value = clampDockPosition(dockPosition.value.right, dockPosition.value.bottom, rect.width, rect.height)
  persistDockPosition()
}

function clampDockPosition(right, bottom, width, height) {
  const maxRight = Math.max(VIEWPORT_GAP, window.innerWidth - width - VIEWPORT_GAP)
  const maxBottom = Math.max(VIEWPORT_GAP, window.innerHeight - height - VIEWPORT_GAP)
  return {
    right: Math.round(Math.min(Math.max(VIEWPORT_GAP, Number(right) || VIEWPORT_GAP), maxRight)),
    bottom: Math.round(Math.min(Math.max(VIEWPORT_GAP, Number(bottom) || VIEWPORT_GAP), maxBottom)),
  }
}

function persistDockPosition() {
  if (!dockPosition.value) return
  try {
    window.localStorage.setItem(POSITION_STORAGE_KEY, JSON.stringify(dockPosition.value))
  } catch {
    // 拖动仍然可用；浏览器禁用本地存储时只是不跨刷新保存。
  }
}

function readDockPosition() {
  if (typeof window === 'undefined') return null
  try {
    const saved = JSON.parse(window.localStorage.getItem(POSITION_STORAGE_KEY) || 'null')
    if (!Number.isFinite(saved?.right) || !Number.isFinite(saved?.bottom)) return null
    return { right: saved.right, bottom: saved.bottom }
  } catch {
    return null
  }
}

function setPlayerMode(mode) {
  playerMode.value = mode === 'BAR' ? 'BAR' : 'CARD'
  queueOpen.value = false
  try {
    window.localStorage.setItem(MODE_STORAGE_KEY, playerMode.value)
  } catch {
    // 模式仍可切换；本地存储不可用时不跨刷新保存。
  }
}

function readPlayerMode() {
  if (typeof window === 'undefined') return 'CARD'
  try {
    return window.localStorage.getItem(MODE_STORAGE_KEY) === 'BAR' ? 'BAR' : 'CARD'
  } catch {
    return 'CARD'
  }
}

function readStoredVolume() {
  if (typeof window === 'undefined') return 0.72
  try {
    const saved = Number(window.localStorage.getItem(VOLUME_STORAGE_KEY))
    return Number.isFinite(saved) && saved >= 0 && saved <= 1 ? saved : 0.72
  } catch {
    return 0.72
  }
}

function readViewport() {
  if (typeof window === 'undefined') return { width: 0, height: 0 }
  return { width: window.innerWidth, height: window.innerHeight }
}

function formatTime(value) {
  const seconds = Math.max(0, Math.floor(Number(value) || 0))
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

function qualityLabel(value) {
  return ({ detecting: '检测中', flac: 'FLAC', 320: '320K', 128: '128K', m4a: 'M4A' })[value] || value
}
</script>

<template>
  <div v-show="current?.playbackType === 'youtube'" class="yt-host" aria-label="YouTube 视频播放器"><div ref="youtubeHost"></div></div>
  <p v-if="errorMessage" class="dock-error">{{ errorMessage }}<button @click="errorMessage = ''"><X :size="14" /></button></p>
  <Transition name="queue-pop">
    <aside v-if="queueOpen" ref="queuePanel" class="dock-queue" :style="queueStyle">
      <header><div><span>UP NEXT</span><strong>播放列表 · {{ music.queue.length }}</strong></div><button :disabled="!music.queue.length" @click="music.clearQueue()"><Trash2 :size="14" /> 清空</button></header>
      <div v-if="!music.queue.length" class="dock-queue-empty"><Music2 :size="20" /><span>播放列表还是空的</span></div>
      <button v-for="track in music.queue" :key="`${track.provider}:${track.id}`" class="dock-queue-row" :class="{ active: current?.id === track.id }" @click="music.playTrack(track)">
        <img v-if="track.imageUrl" :src="track.imageUrl" alt="" /><span v-else class="queue-placeholder"><Music2 :size="16" /></span>
        <span><strong>{{ track.name }}</strong><small>{{ track.artists?.join(' / ') }}</small></span>
        <i title="从播放列表移除" @click.stop="music.removeFromQueue(track)"><X :size="13" /></i>
      </button>
    </aside>
  </Transition>
  <footer ref="playerDock" class="player-dock" :class="{ empty: !current, immersive: props.immersive, expanded: queueOpen, dragging, 'bar-mode': playerMode === 'BAR', 'card-mode': playerMode === 'CARD' }" :style="dockStyle" aria-label="全局音乐播放器">
    <div v-if="playerMode === 'CARD'" class="dock-window-actions" aria-label="播放器显示模式">
      <button title="展开为底部播放条" @click="setPlayerMode('BAR')"><Maximize2 :size="12" /></button>
      <button title="收起到底部播放条" @click="setPlayerMode('BAR')"><X :size="12" /></button>
    </div>
    <button v-else class="dock-restore-card" title="缩小回浮动卡片" @click="setPlayerMode('CARD')"><Minimize2 :size="15" /><span>缩小播放器</span><ChevronsDown :size="13" /></button>
    <button class="dock-track" :aria-disabled="!current" :title="current ? '拖动播放器；点击打开歌词页' : '拖动播放器'" @pointerdown="startDockDrag" @click="handleTrackClick">
      <img v-if="current?.imageUrl" :src="current.imageUrl" :alt="`${current.name} 封面`" draggable="false" />
      <span v-else class="dock-placeholder"><Music2 :size="19" /></span>
      <div class="dock-track-copy">
        <strong>{{ current?.name || '选择一首歌曲' }}</strong>
        <small><span>{{ current?.artists?.join(' / ') || 'Sonora Music' }}</span><em v-if="quality">{{ qualityLabel(quality) }}</em></small>
      </div>
    </button>
    <div class="dock-actions">
      <div class="dock-preferences"><MusicTrackActions v-if="current" :track="current" compact /></div>
      <div class="dock-transport">
        <button :disabled="!current" title="上一首" @click="music.playPrevious()"><SkipBack :size="16" /></button>
        <button class="dock-play" :disabled="!current" :title="paused ? '播放' : '暂停'" @click="toggle"><Play v-if="paused" :size="17" fill="currentColor" /><Pause v-else :size="17" fill="currentColor" /></button>
        <button :disabled="!current" title="下一首" @click="music.playNext()"><SkipForward :size="16" /></button>
      </div>
      <div class="dock-secondary">
        <span class="dock-action-divider"></span>
        <span class="dock-volume-control">
          <button :disabled="!current" :title="volumePercent === 0 ? '恢复音量' : `静音，当前音量 ${volumePercent}%`" @click="toggleMute"><VolumeX v-if="volumePercent === 0" :size="16" /><Volume2 v-else :size="16" /></button>
          <input class="dock-volume-slider" :value="volume" type="range" min="0" max="1" step="0.01" :aria-label="`音量 ${volumePercent}%`" :disabled="!current" @input="handleVolumeInput" />
        </span>
        <button ref="queueToggle" :class="{ selected: queueOpen }" title="播放列表" :aria-label="`播放列表，${music.queue.length} 首`" :aria-expanded="queueOpen" @click="queueOpen = !queueOpen"><ListMusic :size="17" /><small v-if="music.queue.length">{{ music.queue.length }}</small></button>
      </div>
    </div>
    <div class="dock-progress">
      <time>{{ formatTime(currentTime) }}</time>
      <input :value="currentTime" type="range" min="0" :max="Math.max(duration, 1)" step="0.1" aria-label="播放进度" :disabled="!canSeek" @pointerdown="seeking = true" @pointerup="seeking = false; seek($event)" @input="seek" />
      <time>{{ formatTime(duration) }}</time>
    </div>
  </footer>
</template>

<style scoped>
.player-dock{position:fixed;z-index:40;right:18px;bottom:14px;left:274px;display:grid;grid-template-columns:minmax(220px,1fr) minmax(320px,1.15fr) minmax(190px,.7fr);align-items:center;gap:22px;min-height:94px;border:1px solid rgba(255,255,255,.1);border-radius:22px;padding:13px 20px;background:rgba(15,16,26,.92);box-shadow:0 22px 70px rgba(0,0,0,.42);backdrop-filter:blur(24px)}
.player-dock.empty{opacity:.78}.dock-track{display:flex;min-width:0;align-items:center;gap:12px;border:0;padding:0;background:transparent;color:inherit;text-align:left}.dock-track:not(:disabled){cursor:pointer}.dock-track:disabled{cursor:default}.dock-track img,.dock-placeholder{width:58px;height:58px;flex:0 0 58px;border-radius:14px;object-fit:cover}.dock-placeholder{display:grid;place-items:center;background:linear-gradient(145deg,#312958,#1a3e48);color:#c9c2ff}.dock-track div{display:grid;min-width:0;gap:5px}.dock-track strong,.dock-track small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.dock-track strong{font-size:14px}.dock-track small{color:#8e93a2;font-size:11px}.dock-track em{margin-left:6px;color:#63e6c4;font-style:normal}.dock-center{display:grid;gap:8px}.dock-controls{display:flex;justify-content:center;align-items:center;gap:15px}.dock-controls button,.dock-tools button{display:grid;width:34px;height:34px;place-items:center;border:0;border-radius:50%;background:transparent;color:#c5c7d0}.dock-controls button:hover,.dock-tools button:hover,.dock-tools button.selected{background:rgba(255,255,255,.08);color:white}.dock-controls button:disabled,.dock-tools button:disabled{opacity:.35}.dock-controls .dock-play{width:44px;height:44px;background:#b7ff55;color:#11150b}.dock-progress{display:grid;grid-template-columns:38px 1fr 38px;align-items:center;gap:7px}.dock-progress time{color:#747988;font-size:10px;text-align:center}.dock-progress input,.dock-tools input{height:3px;accent-color:#b7ff55}.dock-tools{display:flex;align-items:center;justify-content:flex-end;gap:8px}.dock-tools input{width:84px}.dock-queue{position:fixed;z-index:39;right:20px;bottom:118px;width:360px;max-height:440px;overflow:auto;border:1px solid rgba(255,255,255,.1);border-radius:18px;padding:12px;background:rgba(17,18,27,.97);box-shadow:0 20px 60px rgba(0,0,0,.45)}.dock-queue header{display:flex;align-items:center;justify-content:space-between;padding:5px 6px 10px}.dock-queue header button{display:flex;align-items:center;gap:5px;border:0;background:transparent;color:#838897;font-size:11px}.dock-queue-row{display:grid;width:100%;grid-template-columns:38px minmax(0,1fr) 28px;align-items:center;gap:10px;border:0;border-radius:11px;padding:7px;background:transparent;color:#d8dae2;text-align:left}.dock-queue-row:hover,.dock-queue-row.active{background:rgba(255,255,255,.07)}.dock-queue-row img{width:38px;height:38px;border-radius:8px;object-fit:cover}.dock-queue-row span{display:grid;min-width:0;gap:3px}.dock-queue-row strong,.dock-queue-row small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.dock-queue-row strong{font-size:12px}.dock-queue-row small{color:#777d8b;font-size:10px}.dock-queue-row i{display:grid;place-items:center;color:#737887}.dock-error{position:fixed;z-index:50;right:24px;bottom:118px;display:flex;align-items:center;gap:12px;border:1px solid rgba(255,139,130,.3);border-radius:12px;padding:10px 13px;background:#291b21;color:#ffb1aa;font-size:12px}.dock-error button{display:grid;place-items:center;border:0;background:none;color:inherit}.yt-host{position:fixed;z-index:38;right:24px;bottom:122px;width:320px;height:180px;overflow:hidden;border:1px solid rgba(255,255,255,.12);border-radius:16px;background:#050508}.yt-host>div{width:100%;height:100%}.player-dock.immersive{right:34px;left:34px;border-color:rgba(54,78,96,.14);background:rgba(247,252,253,.72);color:#25323a;box-shadow:0 22px 70px rgba(74,112,132,.18)}.player-dock.immersive .dock-track small,.player-dock.immersive .dock-progress time{color:#667984}.player-dock.immersive .dock-controls button,.player-dock.immersive .dock-tools button{color:#415660}.player-dock.immersive .dock-controls button:hover,.player-dock.immersive .dock-tools button:hover,.player-dock.immersive .dock-tools button.selected{background:rgba(51,93,112,.09);color:#162d38}.player-dock.immersive .dock-controls .dock-play{background:#4f95ff;color:white}.player-dock.immersive .dock-progress input,.player-dock.immersive .dock-tools input{accent-color:#4f95ff}@media(max-width:900px){.player-dock,.player-dock.immersive{left:12px;right:12px;grid-template-columns:1fr auto;padding:10px 13px}.dock-center{grid-row:2;grid-column:1/-1}.dock-tools input{display:none}.dock-track img,.dock-placeholder{width:46px;height:46px;flex-basis:46px}.dock-queue{right:12px;left:12px;width:auto}.yt-host{right:12px;width:280px;height:158px}}
</style>

<style scoped>
.dock-window-actions{position:absolute;z-index:5;top:5px;right:6px;display:flex;gap:2px;opacity:0;transition:opacity .16s}.player-dock.card-mode:hover .dock-window-actions,.player-dock.card-mode:focus-within .dock-window-actions{opacity:1}.dock-window-actions button,.dock-restore-card{display:grid;place-items:center;border:0;background:transparent;color:#6f7480}.dock-window-actions button{width:22px;height:22px;border-radius:7px}.dock-window-actions button:hover,.dock-restore-card:hover{background:rgba(255,255,255,.08);color:#f1f2f5}
.player-dock.card-mode:hover .dock-track,.player-dock.card-mode:focus-within .dock-track,.player-dock.card-mode.expanded .dock-track{right:198px}.player-dock.card-mode:hover,.player-dock.card-mode:focus-within,.player-dock.card-mode.expanded{width:448px}
.player-dock.bar-mode,.player-dock.bar-mode.immersive{right:18px!important;bottom:14px!important;left:18px!important;width:auto;height:94px;overflow:visible;border:1px solid rgba(255,255,255,.16);border-radius:20px;padding:0;background:linear-gradient(110deg,rgba(104,82,156,.17),rgba(13,14,24,.58) 38%,rgba(10,12,20,.5));-webkit-backdrop-filter:blur(30px) saturate(155%);backdrop-filter:blur(30px) saturate(155%);color:#f1f2f5;box-shadow:0 20px 55px rgba(0,0,0,.28),inset 0 1px 0 rgba(255,255,255,.08);transform-origin:center bottom}.player-dock.bar-mode:hover,.player-dock.bar-mode:focus-within,.player-dock.bar-mode.expanded{width:auto;height:94px;border-color:rgba(184,255,84,.23);background:linear-gradient(110deg,rgba(104,82,156,.2),rgba(13,14,24,.62) 38%,rgba(10,12,20,.54))}.player-dock.bar-mode .dock-track{top:13px;right:auto;left:16px;width:min(28vw,360px);height:56px;grid-template-columns:56px minmax(0,1fr);cursor:pointer;touch-action:auto}.player-dock.bar-mode .dock-track img,.player-dock.bar-mode .dock-placeholder{width:56px;height:56px;border-radius:13px}.player-dock.bar-mode .dock-track small{max-height:16px;opacity:1;transform:none}.player-dock.bar-mode .dock-actions{top:12px;right:78px;left:36%;justify-content:center;opacity:1;pointer-events:auto;transform:none}.player-dock.bar-mode .dock-progress{right:82px;bottom:10px;left:36%;opacity:1;pointer-events:auto;transform:none}.dock-restore-card{position:absolute;z-index:4;top:12px;right:14px;display:flex;height:30px;align-items:center;gap:5px;border-radius:9px;padding:0 8px}.dock-restore-card span{font-size:8px}.player-dock.bar-mode .track-actions{margin-right:6px}.player-dock.bar-mode .dock-action-divider{display:block}
@media(max-width:900px){.player-dock.bar-mode,.player-dock.bar-mode.immersive{right:8px!important;bottom:8px!important;left:8px!important;width:auto}.player-dock.bar-mode .dock-track{left:10px;width:46%}.player-dock.bar-mode .dock-actions{right:42px;left:auto}.player-dock.bar-mode .dock-progress{right:12px;left:12px}.dock-restore-card span{display:none}.player-dock.card-mode:hover,.player-dock.card-mode:focus-within,.player-dock.card-mode.expanded{width:min(448px,calc(100vw - 24px))}}
</style>

<style scoped>
.player-dock {
  position: fixed;
  z-index: 90;
  right: 18px;
  bottom: 18px;
  left: auto;
  display: block;
  width: 224px;
  height: 64px;
  min-height: 0;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 17px;
  padding: 0;
  background:
    radial-gradient(circle at 8% 0%, rgba(184, 255, 84, 0.09), transparent 34%),
    rgba(16, 17, 25, 0.94);
  color: #f2f3f6;
  box-shadow: 0 18px 55px rgba(0, 0, 0, 0.38), 0 4px 15px rgba(0, 0, 0, 0.22);
  backdrop-filter: blur(24px) saturate(135%);
  transform-origin: right bottom;
  transition:
    width 0.36s cubic-bezier(0.22, 1, 0.36, 1),
    height 0.32s cubic-bezier(0.22, 1, 0.36, 1),
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.player-dock:hover,
.player-dock:focus-within,
.player-dock.expanded,
.player-dock.dragging {
  width: 414px;
  height: 128px;
  border-color: rgba(184, 255, 84, 0.2);
  box-shadow: 0 24px 75px rgba(0, 0, 0, 0.46), 0 0 0 1px rgba(184, 255, 84, 0.035);
}

.player-dock.dragging {
  transition: none;
}

.player-dock.empty {
  opacity: 0.9;
}

.player-dock .dock-track {
  position: absolute;
  top: 8px;
  right: 8px;
  left: 8px;
  display: grid;
  min-width: 0;
  height: 48px;
  grid-template-columns: 48px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  border: 0;
  border-radius: 11px;
  padding: 0;
  background: transparent;
  color: inherit;
  cursor: grab;
  text-align: left;
  touch-action: none;
  user-select: none;
  transition: right 0.3s cubic-bezier(0.22, 1, 0.36, 1), background 0.18s ease;
}

.player-dock:hover .dock-track,
.player-dock:focus-within .dock-track,
.player-dock.expanded .dock-track,
.player-dock.dragging .dock-track {
  right: 164px;
}

.player-dock.dragging .dock-track {
  cursor: grabbing;
}

.player-dock .dock-track img {
  -webkit-user-drag: none;
  user-select: none;
}

.dock-track:not(:disabled):hover {
  background: rgba(255, 255, 255, 0.04);
}

.dock-track img,
.dock-placeholder {
  display: grid;
  width: 48px;
  height: 48px;
  flex: none;
  place-items: center;
  border-radius: 11px;
  object-fit: cover;
  background: linear-gradient(145deg, #332c4d, #1a3840);
  color: #c9c2ff;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.24);
}

.dock-track > div,
.dock-track-copy {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.dock-track strong,
.dock-track small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dock-track strong {
  display: block;
  min-width: 0;
  font-size: 12px;
  font-weight: 760;
  line-height: 1.25;
  letter-spacing: -0.01em;
}

.dock-track small {
  display: flex;
  min-width: 0;
  align-items: center;
  max-height: 0;
  color: #7e8492;
  font-size: 9px;
  opacity: 0;
  transform: translateY(-3px);
  transition: max-height 0.2s ease, opacity 0.2s ease 0.08s, transform 0.2s ease 0.08s;
}

.dock-track small > span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player-dock:hover .dock-track small,
.player-dock:focus-within .dock-track small,
.player-dock.expanded .dock-track small,
.player-dock.dragging .dock-track small {
  max-height: 16px;
  opacity: 1;
  transform: none;
}

.dock-track em {
  flex: 0 0 auto;
  margin-left: 5px;
  color: #77dfc3;
  font-size: 8px;
  font-style: normal;
}

.player-dock.card-mode:hover .dock-track,
.player-dock.card-mode:focus-within .dock-track,
.player-dock.card-mode.expanded .dock-track {
  top: 12px;
  left: 12px;
  right: 330px;
  height: 62px;
  grid-template-columns: 62px minmax(0, 1fr);
}

.player-dock.card-mode:hover .dock-track img,
.player-dock.card-mode:focus-within .dock-track img,
.player-dock.card-mode.expanded .dock-track img,
.player-dock.card-mode:hover .dock-placeholder,
.player-dock.card-mode:focus-within .dock-placeholder,
.player-dock.card-mode.expanded .dock-placeholder {
  width: 62px;
  height: 62px;
  border-radius: 14px;
}

.player-dock.card-mode:hover .dock-track strong,
.player-dock.card-mode:focus-within .dock-track strong,
.player-dock.card-mode.expanded .dock-track strong {
  font-size: 13px;
}

.player-dock.card-mode:hover,
.player-dock.card-mode:focus-within,
.player-dock.card-mode.expanded {
  width: 600px;
  height: 150px;
}

.player-dock.card-mode:hover .dock-actions,
.player-dock.card-mode:focus-within .dock-actions,
.player-dock.card-mode.expanded .dock-actions {
  top: 22px;
  right: 13px;
  gap: 5px;
}

.player-dock.card-mode:hover .dock-progress,
.player-dock.card-mode:focus-within .dock-progress,
.player-dock.card-mode.expanded .dock-progress {
  right: 18px;
  bottom: 18px;
  left: 18px;
}

.dock-actions {
  position: absolute;
  top: 13px;
  right: 10px;
  display: flex;
  align-items: center;
  gap: 3px;
  opacity: 0;
  pointer-events: none;
  transform: translateX(14px);
  transition: opacity 0.2s ease 0.09s, transform 0.3s cubic-bezier(0.22, 1, 0.36, 1) 0.04s;
}

.player-dock:hover .dock-actions,
.player-dock:focus-within .dock-actions,
.player-dock.expanded .dock-actions,
.player-dock.dragging .dock-actions {
  opacity: 1;
  pointer-events: auto;
  transform: none;
}

.dock-preferences,
.dock-transport,
.dock-secondary {
  display: flex;
  align-items: center;
  gap: 4px;
}

.player-dock.bar-mode .dock-actions {
  top: 12px;
  right: 0;
  left: 0;
  height: 44px;
  justify-content: initial;
  gap: 0;
  opacity: 1;
  pointer-events: none;
  transform: none;
}

.player-dock.bar-mode .dock-transport {
  position: absolute;
  left: 50%;
  pointer-events: auto;
  transform: translateX(-50%);
}

.player-dock.bar-mode .dock-preferences {
  position: absolute;
  right: calc(50% + 92px);
  pointer-events: auto;
}

.player-dock.bar-mode .dock-secondary {
  position: absolute;
  left: calc(50% + 92px);
  pointer-events: auto;
}

.dock-actions button {
  position: relative;
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: #aeb2bd;
  transition: 0.16s ease;
}

.dock-actions button:hover,
.dock-actions button.selected {
  background: rgba(255, 255, 255, 0.075);
  color: #f3f4f7;
}

.dock-actions button:disabled {
  opacity: 0.3;
}

.dock-actions .dock-play {
  width: 34px;
  height: 34px;
  border-radius: 11px;
  background: #b8ff54;
  color: #11150b;
  box-shadow: 0 5px 16px rgba(184, 255, 84, 0.14);
}

.dock-actions .dock-play:hover {
  background: #c5ff70;
  color: #11150b;
  transform: translateY(-1px);
}

.dock-actions button small {
  position: absolute;
  top: -3px;
  right: -2px;
  display: grid;
  min-width: 14px;
  height: 14px;
  place-items: center;
  border: 2px solid #171821;
  border-radius: 999px;
  padding: 0 3px;
  background: #b8ff54;
  color: #11150b;
  font-size: 7px;
  font-weight: 900;
}

.dock-action-divider {
  width: 1px;
  height: 18px;
  margin: 0 2px;
  background: rgba(255, 255, 255, 0.09);
}

.dock-volume-control {
  display: flex;
  min-width: 30px;
  align-items: center;
  gap: 5px;
}

.dock-volume-control > button {
  flex: 0 0 30px;
}

.dock-volume-slider {
  width: 0;
  min-width: 0;
  height: 3px;
  margin: 0;
  accent-color: #b8ff54;
  opacity: 0;
  pointer-events: none;
  transition: width 0.22s ease, opacity 0.16s ease;
}

.player-dock.card-mode:hover .dock-volume-slider,
.player-dock.card-mode:focus-within .dock-volume-slider,
.player-dock.card-mode.expanded .dock-volume-slider,
.player-dock.bar-mode .dock-volume-slider {
  width: 72px;
  opacity: 1;
  pointer-events: auto;
}

.dock-progress {
  position: absolute;
  right: 13px;
  bottom: 15px;
  left: 13px;
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) 30px;
  align-items: center;
  gap: 7px;
  opacity: 0;
  pointer-events: none;
  transform: translateY(10px);
  transition: opacity 0.2s ease 0.1s, transform 0.28s cubic-bezier(0.22, 1, 0.36, 1) 0.05s;
}

.player-dock:hover .dock-progress,
.player-dock:focus-within .dock-progress,
.player-dock.expanded .dock-progress,
.player-dock.dragging .dock-progress {
  opacity: 1;
  pointer-events: auto;
  transform: none;
}

.dock-progress time {
  color: #686e7c;
  font-size: 8px;
  font-variant-numeric: tabular-nums;
  text-align: center;
}

.dock-progress input {
  width: 100%;
  height: 3px;
  margin: 0;
  accent-color: #b8ff54;
  cursor: pointer;
}

.dock-queue {
  position: fixed;
  z-index: 89;
  right: 18px;
  bottom: 156px;
  width: 360px;
  max-height: min(390px, calc(100vh - 180px));
  overflow: auto;
  border: 1px solid rgba(255, 255, 255, 0.11);
  border-radius: 17px;
  padding: 9px;
  background: rgba(16, 17, 25, 0.97);
  color: #edf0f4;
  box-shadow: 0 22px 65px rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(24px);
  scrollbar-color: rgba(255, 255, 255, 0.13) transparent;
  scrollbar-width: thin;
}

.dock-queue header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 7px 10px;
}

.dock-queue header > div {
  display: grid;
  gap: 3px;
}

.dock-queue header span {
  color: #a49bce;
  font-size: 7px;
  font-weight: 850;
  letter-spacing: 0.16em;
}

.dock-queue header strong {
  font-size: 11px;
}

.dock-queue header button {
  display: flex;
  align-items: center;
  gap: 4px;
  border: 0;
  border-radius: 8px;
  padding: 6px 8px;
  background: transparent;
  color: #717784;
  font-size: 8px;
}

.dock-queue header button:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #b8bdc7;
}

.dock-queue header button:disabled {
  opacity: 0.35;
}

.dock-queue-row {
  display: grid;
  width: 100%;
  grid-template-columns: 38px minmax(0, 1fr) 28px;
  align-items: center;
  gap: 9px;
  border: 1px solid transparent;
  border-radius: 11px;
  padding: 6px;
  background: transparent;
  color: #d9dce4;
  text-align: left;
}

.dock-queue-row:hover,
.dock-queue-row.active {
  border-color: rgba(255, 255, 255, 0.055);
  background: rgba(255, 255, 255, 0.045);
}

.dock-queue-row.active {
  border-color: rgba(184, 255, 84, 0.12);
  background: rgba(184, 255, 84, 0.065);
}

.dock-queue-row img,
.queue-placeholder {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 9px;
  object-fit: cover;
  background: linear-gradient(145deg, #302a45, #1c3038);
  color: #9b94bb;
}

.dock-queue-row > span:nth-child(2) {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.dock-queue-row strong,
.dock-queue-row small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dock-queue-row strong {
  font-size: 10px;
}

.dock-queue-row small {
  color: #717784;
  font-size: 8px;
}

.dock-queue-row i {
  display: grid;
  width: 25px;
  height: 25px;
  place-items: center;
  border-radius: 8px;
  color: #646a77;
  font-style: normal;
}

.dock-queue-row i:hover {
  background: rgba(255, 255, 255, 0.07);
  color: #eef0f4;
}

.dock-queue-empty {
  display: grid;
  min-height: 92px;
  place-items: center;
  align-content: center;
  gap: 7px;
  border: 1px dashed rgba(255, 255, 255, 0.075);
  border-radius: 11px;
  color: #656b78;
  font-size: 9px;
}

.queue-pop-enter-active,
.queue-pop-leave-active {
  transition: opacity 0.2s ease, transform 0.28s cubic-bezier(0.22, 1, 0.36, 1);
}

.queue-pop-enter-from,
.queue-pop-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.98);
}

.dock-error {
  z-index: 95;
  right: 18px;
  bottom: 156px;
}

.yt-host {
  z-index: 88;
  right: 18px;
  bottom: 156px;
}

.player-dock.immersive,
.player-dock.immersive.card-mode,
.player-dock.immersive.bar-mode,
.player-dock.immersive:hover,
.player-dock.immersive:focus-within,
.player-dock.immersive.expanded {
  right: 0 !important;
  bottom: 0 !important;
  left: 0 !important;
  width: 100% !important;
  height: 112px !important;
  overflow: visible;
  border: 0;
  border-radius: 0;
  background: linear-gradient(180deg, rgba(226, 246, 249, 0), rgba(221, 242, 246, 0.48));
  color: #25323a;
  box-shadow: none;
  -webkit-backdrop-filter: none;
  backdrop-filter: none;
  transform: none;
}

/* The lyrics experience already presents the current song prominently, so the
   global player becomes a page-native control rail instead of a second card. */
.player-dock.immersive .dock-window-actions,
.player-dock.immersive .dock-restore-card,
.player-dock.immersive .dock-track {
  display: none;
}

.player-dock.immersive .dock-actions {
  top: 2px;
  right: 0;
  left: 0;
  height: 54px;
  justify-content: initial;
  gap: 0;
  opacity: 1;
  pointer-events: none;
  transform: none;
}

.player-dock.immersive .dock-transport {
  position: absolute;
  left: 50%;
  gap: 12px;
  pointer-events: auto;
  transform: translateX(-50%);
}

.player-dock.immersive .dock-preferences {
  position: absolute;
  right: calc(50% + 112px);
  pointer-events: auto;
}

.player-dock.immersive .dock-secondary {
  position: absolute;
  left: calc(50% + 112px);
  pointer-events: auto;
}

.player-dock.immersive .dock-actions button {
  color: #536772;
}

.player-dock.immersive .dock-actions button:hover,
.player-dock.immersive .dock-actions button.selected {
  background: rgba(51, 93, 112, 0.09);
  color: #162d38;
}

.player-dock.immersive .dock-actions .dock-play {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  background: #4f95ff;
  color: white;
  box-shadow: 0 8px 24px rgba(79, 149, 255, 0.24);
}

.player-dock.immersive .dock-actions .dock-play:hover {
  background: #438cff;
  color: white;
}

.player-dock.immersive .dock-volume-slider {
  display: block;
  width: 72px;
  opacity: 1;
  pointer-events: auto;
}

.player-dock.immersive .dock-action-divider {
  background: rgba(47, 78, 91, 0.13);
}

.player-dock.immersive .dock-progress,
.player-dock.immersive:hover .dock-progress,
.player-dock.immersive:focus-within .dock-progress,
.player-dock.immersive.expanded .dock-progress {
  right: 24%;
  bottom: 18px;
  left: 24%;
  grid-template-columns: 42px minmax(0, 1fr) 42px;
  gap: 10px;
  opacity: 1;
  pointer-events: auto;
  transform: none;
}

.player-dock.immersive .dock-progress time {
  color: #687b86;
  font-size: 10px;
}

.player-dock.immersive .dock-progress input,
.player-dock.immersive .dock-volume-slider {
  accent-color: #4f95ff;
}

/*
 * Immersive mode shares the same component with the floating card and bottom
 * bar.  Keep its control rail in a dedicated, fixed grid so card/bar hover
 * geometry can never move the buttons while the pointer crosses the player.
 */
.player-dock.immersive.card-mode .dock-actions,
.player-dock.immersive.bar-mode .dock-actions {
  top: 2px;
  right: auto;
  left: 50%;
  display: grid;
  width: min(640px, 72vw);
  height: 54px;
  grid-template-columns: minmax(72px, 1fr) 132px minmax(190px, 1fr);
  align-items: center;
  gap: 0;
  opacity: 1;
  pointer-events: none;
  transform: translateX(-50%);
}

.player-dock.immersive.card-mode .dock-preferences,
.player-dock.immersive.bar-mode .dock-preferences,
.player-dock.immersive.card-mode .dock-transport,
.player-dock.immersive.bar-mode .dock-transport,
.player-dock.immersive.card-mode .dock-secondary,
.player-dock.immersive.bar-mode .dock-secondary {
  position: static;
  transform: none;
}

.player-dock.immersive.card-mode .dock-preferences,
.player-dock.immersive.bar-mode .dock-preferences {
  justify-self: end;
  justify-content: flex-end;
}

.player-dock.immersive.card-mode .dock-transport,
.player-dock.immersive.bar-mode .dock-transport {
  width: 132px;
  justify-self: center;
  justify-content: center;
}

.player-dock.immersive.card-mode .dock-secondary,
.player-dock.immersive.bar-mode .dock-secondary {
  min-width: 190px;
  justify-self: start;
  justify-content: flex-start;
}

.player-dock.immersive .dock-actions .dock-play:hover {
  transform: none;
}

@media (max-width: 900px) {
  .player-dock,
  .player-dock.immersive {
    right: 12px;
    bottom: 12px;
    left: auto;
    width: 204px;
  }

  .player-dock:hover,
  .player-dock:focus-within,
  .player-dock.expanded,
  .player-dock.dragging {
    width: min(414px, calc(100vw - 24px));
  }

  .player-dock.card-mode:hover,
  .player-dock.card-mode:focus-within,
  .player-dock.card-mode.expanded {
    width: min(600px, calc(100vw - 24px));
    height: 150px;
  }

  .dock-queue {
    right: 12px;
    bottom: 150px;
    left: 12px;
    width: auto;
  }

  .yt-host {
    right: 12px;
    bottom: 150px;
    width: 280px;
    height: 158px;
  }

  .player-dock.immersive .dock-progress,
  .player-dock.immersive:hover .dock-progress,
  .player-dock.immersive:focus-within .dock-progress,
  .player-dock.immersive.expanded .dock-progress {
    right: 14px;
    left: 14px;
  }

  .player-dock.immersive .dock-preferences {
    display: none;
  }

  .player-dock.immersive.card-mode .dock-actions,
  .player-dock.immersive.bar-mode .dock-actions {
    width: min(360px, calc(100vw - 24px));
    grid-template-columns: 132px minmax(116px, 1fr);
  }

  .player-dock.immersive.card-mode .dock-transport,
  .player-dock.immersive.bar-mode .dock-transport {
    grid-column: 1;
  }

  .player-dock.immersive.card-mode .dock-secondary,
  .player-dock.immersive.bar-mode .dock-secondary {
    grid-column: 2;
    min-width: 116px;
  }

  .player-dock.immersive .dock-volume-slider {
    display: none;
  }
}

@media (max-width: 700px) {
  .player-dock.card-mode:hover .dock-track,
  .player-dock.card-mode:focus-within .dock-track,
  .player-dock.card-mode.expanded .dock-track {
    right: 238px;
    grid-template-columns: 52px minmax(0, 1fr);
  }

  .player-dock.card-mode:hover .dock-track img,
  .player-dock.card-mode:focus-within .dock-track img,
  .player-dock.card-mode.expanded .dock-track img,
  .player-dock.card-mode:hover .dock-placeholder,
  .player-dock.card-mode:focus-within .dock-placeholder,
  .player-dock.card-mode.expanded .dock-placeholder {
    width: 52px;
    height: 52px;
  }

  .player-dock.card-mode .dock-volume-slider {
    display: none;
  }

  .player-dock.bar-mode .dock-preferences {
    display: none;
  }

  .player-dock.bar-mode .dock-secondary {
    left: calc(50% + 68px);
  }

  .player-dock.bar-mode .dock-volume-slider {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .player-dock,
  .dock-track,
  .dock-track small,
  .dock-actions,
  .dock-progress,
  .queue-pop-enter-active,
  .queue-pop-leave-active {
    transition-duration: 0.01ms !important;
  }
}
</style>
