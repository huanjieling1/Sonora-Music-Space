<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ExternalLink,
  Bookmark,
  Heart,
  KeyRound,
  ListMusic,
  LoaderCircle,
  Music2,
  Pause,
  Play,
  Plus,
  ShieldCheck,
  SkipBack,
  SkipForward,
  Sparkles,
  Trash2,
  ThumbsDown,
  Volume2,
  VolumeX,
  X,
} from 'lucide-vue-next'
import { request } from '../services/api'
import { confirmAction } from '../services/confirm'
import {
  createPlaybackSession,
  finishPlayback,
  observePlayback,
  startPlayback,
} from '../services/musicPlaybackTracker'
import { useMusicStore } from '../stores/music'
import MusicTrackActions from './MusicTrackActions.vue'

const props = defineProps({
  conversationId: { type: String, required: true },
})

const music = useMusicStore()
const router = useRouter()

const status = ref({ ready: false, providers: [], message: '' })
const statusLoading = ref(true)
const qqStatus = ref({ enabled: false, bridgeAvailable: false, sessionConfigured: false, maskedAccount: '', message: '' })
const qqCookie = ref('')
const qqSaving = ref(false)
const recommending = ref(false)
const prompt = ref('')
const recommendations = ref([])
const recommendationText = ref('')
const searchId = ref('')
const understanding = ref(null)
const verifiedCount = ref(0)
const relatedCount = ref(0)
const correctingUnderstanding = ref(false)
const correctedEntityName = ref('')
const correctedEntityType = ref('FRANCHISE')
const feedbackSaving = ref(false)
const playlistSaving = ref(false)
const policyVersion = ref('baseline-v1')
const personalizationStatus = ref('DISABLED')
const profileOpen = ref(false)
const profileLoading = ref(false)
const profile = ref({ explicitPreferences: [], inferredPreferences: [], labeledEvents: 0, exposures: 0, summary: null })
const preferenceType = ref('GENRE')
const preferenceValue = ref('')
const preferencePolarity = ref(1)
const trackActions = ref({})
const currentPage = ref(1)
const pageSize = ref(10)
const hasNextPage = ref(false)
const maxPages = ref(20)
const queue = ref([])
const errorMessage = ref('')
const currentTrack = ref(null)
const paused = ref(true)
const playerReady = ref(false)
const volume = ref(0.7)
const currentTime = ref(0)
const durationSeconds = ref(0)
const currentQuality = ref('')
const seeking = ref(false)
const youtubeHost = ref(null)
const moodSuggestions = ['雨夜爵士与轻微白噪音', '专注编码的极简电子乐', '柔和慵懒的华语独立音乐']

let audioPlayer = null
let youtubePlayer = null
let youtubeSdkPromise = null
let youtubeProgressTimer = null
let lastAudibleVolume = 0.7
let drainingAgentActions = false
let stopActionWatch = null
let activePlayback = null

const canRecommend = computed(() => status.value.ready && props.conversationId && prompt.value.trim() && !recommending.value)
const configuredProviders = computed(() => status.value.providers.filter(item => item.configured))
const canSeek = computed(() => Boolean(currentTrack.value) && durationSeconds.value > 0)
const volumePercent = computed(() => Math.round(Number(volume.value) * 100))
const resultGroups = computed(() => {
  const verified = recommendations.value.filter(track => track.matchType === 'VERIFIED')
  const related = recommendations.value.filter(track => track.matchType !== 'VERIFIED')
  return [
    { key: 'verified', label: '严格匹配结果', hint: '已通过歌名、歌手、专辑或实体元数据校验', tracks: verified },
    { key: 'related', label: '搜索候选与补充', hint: '曲库直接候选、保守扩展或备用曲库补位', tracks: related },
  ].filter(group => group.tracks.length)
})

watch(() => music.currentTrack, track => { currentTrack.value = track || null }, { immediate: true })
watch(() => music.queue, tracks => { queue.value = [...tracks] }, { deep: true, immediate: true })

onMounted(async () => {
  await Promise.all([refreshStatus(), refreshQqStatus(), refreshProfile()])
  stopActionWatch = watch(
    () => music.pendingActions.length,
    () => drainMusicActions(),
    { immediate: true },
  )
})

onBeforeUnmount(() => {
  recordSkipIfNeeded()
  if (audioPlayer) {
    audioPlayer.pause()
    audioPlayer.removeAttribute('src')
    audioPlayer.load()
  }
  stopYoutubeProgress()
  youtubePlayer?.destroy?.()
  stopActionWatch?.()
})

async function drainMusicActions() {
  if (drainingAgentActions) return
  drainingAgentActions = true
  try {
    let action = music.takeNext()
    while (action) {
      await applyAgentAction(action)
      action = music.takeNext()
    }
  } finally {
    drainingAgentActions = false
  }
}

async function applyAgentAction(action) {
  if (action.type === 'SHOW_MUSIC_RESULTS' && action.recommendation) {
    applyRecommendation(action.recommendation)
    errorMessage.value = ''
    return
  }
  if (action.type === 'QUEUE_MUSIC_RESULTS' && action.recommendation) {
    ;(action.recommendation.tracks || []).forEach(addToQueue)
    return
  }
  if (action.type === 'PLAY_TRACK' && action.track) {
    await playTrack(action.track)
  }
}

function initializeAudio() {
  audioPlayer = new Audio()
  audioPlayer.preload = 'metadata'
  audioPlayer.volume = Number(volume.value)
  audioPlayer.addEventListener('play', () => {
    paused.value = false
    handlePlaybackStarted()
  })
  audioPlayer.addEventListener('pause', () => { paused.value = true })
  audioPlayer.addEventListener('ended', handleTrackEnded)
  audioPlayer.addEventListener('loadedmetadata', syncAudioProgress)
  audioPlayer.addEventListener('durationchange', syncAudioProgress)
  audioPlayer.addEventListener('timeupdate', () => {
    syncAudioProgress()
    observePlaybackProgress()
  })
  audioPlayer.addEventListener('canplay', () => {
    if (currentTrack.value?.playbackType === 'audio') playerReady.value = true
  })
  audioPlayer.addEventListener('error', () => {
    if (currentTrack.value?.playbackType !== 'audio') return
    playerReady.value = false
    paused.value = true
    errorMessage.value = '当前歌曲暂时无法播放，可以切换下一首或前往来源页面。'
  })
}

async function refreshStatus() {
  statusLoading.value = true
  try {
    const result = await request('/api/music/status')
    status.value = result.data
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    statusLoading.value = false
  }
}

async function refreshQqStatus() {
  try {
    const result = await request('/api/music/qq/status')
    qqStatus.value = result.data
  } catch (error) {
    errorMessage.value = error.message
  }
}

async function refreshProfile() {
  profileLoading.value = true
  try {
    const result = await request('/api/music/profile')
    profile.value = result.data || profile.value
  } catch (error) {
    if (profileOpen.value) errorMessage.value = error.message
  } finally {
    profileLoading.value = false
  }
}

async function addPreference() {
  if (!preferenceValue.value.trim() || feedbackSaving.value) return
  feedbackSaving.value = true
  try {
    await request('/api/music/profile/preferences', {
      method: 'POST',
      body: JSON.stringify({
        type: preferenceType.value,
        value: preferenceValue.value.trim(),
        polarity: Number(preferencePolarity.value),
      }),
    })
    preferenceValue.value = ''
    await refreshProfile()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    feedbackSaving.value = false
  }
}

async function deletePreference(item) {
  const id = item?.id
  if (!id || feedbackSaving.value) return
  const accepted = await confirmAction({
    eyebrow: '删除偏好',
    title: '不再保留这条偏好吗？',
    message: '删除后，后续推荐将不再把这条信息作为你的明确偏好依据。',
    subject: item.value || item.name || '这条偏好',
    hint: '后续行为仍可能重新形成相似的学习偏好',
    confirmText: '删除偏好',
    cancelText: '继续保留',
  })
  if (!accepted) return
  feedbackSaving.value = true
  try {
    await request(`/api/music/profile/preferences/${encodeURIComponent(id)}`, { method: 'DELETE' })
    await refreshProfile()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    feedbackSaving.value = false
  }
}

async function clearLearnedProfile() {
  if (feedbackSaving.value) return
  const accepted = await confirmAction({
    eyebrow: '重置用户画像',
    title: '清除全部学习画像吗？',
    message: '系统从播放、跳过和喜欢等行为中推断的偏好将被清除，明确填写的偏好不会受到影响。',
    subject: '全部推断偏好',
    hint: '推荐会暂时回到较少个性化的状态',
    confirmText: '清除画像',
    cancelText: '暂不清除',
  })
  if (!accepted) return
  feedbackSaving.value = true
  try {
    await request('/api/music/profile/learned', { method: 'DELETE' })
    await refreshProfile()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    feedbackSaving.value = false
  }
}

async function saveQqSession() {
  if (!qqCookie.value.trim() || qqSaving.value) return
  qqSaving.value = true
  errorMessage.value = ''
  try {
    const result = await request('/api/music/qq/session', {
      method: 'POST',
      body: JSON.stringify({ cookie: qqCookie.value.trim() }),
    })
    qqStatus.value = result.data
    qqCookie.value = ''
    await refreshStatus()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    qqSaving.value = false
  }
}

async function clearQqSession() {
  if (qqSaving.value) return
  const accepted = await confirmAction({
    eyebrow: '清除登录状态',
    title: '清除本机 QQ 音乐登录态吗？',
    message: '清除后部分需要登录的 QQ 音乐内容可能暂时无法播放，之后可以重新配置 Cookie。',
    subject: qqStatus.value.maskedAccount || '当前 QQ 音乐账号',
    hint: '不会注销你的 QQ 账号，也不会删除线上数据',
    confirmText: '清除登录态',
    cancelText: '保持登录',
  })
  if (!accepted) return
  qqSaving.value = true
  errorMessage.value = ''
  try {
    const result = await request('/api/music/qq/session', { method: 'DELETE' })
    qqStatus.value = result.data
    await refreshStatus()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    qqSaving.value = false
  }
}

async function recommend() {
  await loadRecommendations(1)
}

async function loadRecommendations(page) {
  if (!canRecommend.value) return
  const requestedPage = Math.min(20, Math.max(1, Number(page) || 1))
  recommending.value = true
  errorMessage.value = ''
  try {
    const result = await request('/api/music/recommend', {
      method: 'POST',
      body: JSON.stringify({
        conversationId: props.conversationId,
        description: prompt.value.trim(),
        page: requestedPage,
        pageSize: pageSize.value,
      }),
    })
    applyRecommendation(result.data)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    recommending.value = false
  }
}

function applyRecommendation(recommendation) {
  prompt.value = recommendation.description || prompt.value
  recommendations.value = (recommendation.tracks || []).map(track => ({
    ...track,
    _searchId: recommendation.searchId,
  }))
  recommendationText.value = recommendation.explanation || ''
  searchId.value = recommendation.searchId || ''
  understanding.value = recommendation.understanding || null
  verifiedCount.value = Number(recommendation.verifiedCount || 0)
  relatedCount.value = Number(recommendation.relatedCount || 0)
  correctedEntityName.value = recommendation.understanding?.canonicalName || ''
  currentPage.value = recommendation.page || 1
  pageSize.value = recommendation.pageSize || 10
  hasNextPage.value = Boolean(recommendation.hasNext)
  maxPages.value = Math.min(20, Math.max(1, recommendation.maxPages || 20))
  policyVersion.value = recommendation.policyVersion || 'baseline-v1'
  personalizationStatus.value = recommendation.personalizationStatus || 'DISABLED'
}

async function markNotRelevant(track) {
  if (!searchId.value || !understanding.value?.canonicalName || feedbackSaving.value) return
  feedbackSaving.value = true
  errorMessage.value = ''
  try {
    const result = await request('/api/music/feedback', {
      method: 'POST',
      body: JSON.stringify({
        searchId: searchId.value,
        conversationId: props.conversationId,
        action: 'NOT_RELEVANT',
        description: prompt.value.trim(),
        trackId: track.id,
        resolvedEntityName: understanding.value.canonicalName,
      }),
    })
    recommendationText.value = result.data?.message || '已记住这首歌不相关'
    await loadRecommendations(currentPage.value)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    feedbackSaving.value = false
  }
}

async function submitEntityCorrection() {
  if (!searchId.value || !correctedEntityName.value.trim() || feedbackSaving.value) return
  feedbackSaving.value = true
  errorMessage.value = ''
  try {
    const result = await request('/api/music/feedback', {
      method: 'POST',
      body: JSON.stringify({
        searchId: searchId.value,
        conversationId: props.conversationId,
        action: 'CORRECT_ENTITY',
        description: prompt.value.trim(),
        resolvedEntityName: understanding.value?.canonicalName || '',
        correctedEntityName: correctedEntityName.value.trim(),
        correctedEntityType: correctedEntityType.value,
      }),
    })
    recommendationText.value = result.data?.message || '已记住新的理解'
    correctingUnderstanding.value = false
    await loadRecommendations(1)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    feedbackSaving.value = false
  }
}

function resultIndex(track) {
  return recommendations.value.findIndex(item => item.id === track.id) + 1
}

function applyMood(value) {
  prompt.value = value
}

function addToQueue(track) {
  if (!queue.value.some(item => item.id === track.id)) queue.value.push(track)
  music.addToQueue(track)
}

function addAll() {
  recommendations.value.forEach(addToQueue)
}

function openTrackPage(track) {
  if (!track?.id) return
  const sourceQueue = recommendations.value.length ? recommendations.value : queue.value
  music.playTrack(track, sourceQueue)
  router.push({
    name: 'music-track',
    params: { provider: track.provider || 'unknown', trackId: track.id },
  })
}

async function saveRecommendationPlaylist() {
  if (!searchId.value || !recommendations.value.length || playlistSaving.value) return
  playlistSaving.value = true
  errorMessage.value = ''
  const baseName = understanding.value?.canonicalName || prompt.value.trim() || '专属推荐'
  try {
    const result = await request('/api/music/playlists/from-exposure', {
      method: 'POST',
      body: JSON.stringify({
        searchId: searchId.value,
        name: `${baseName.slice(0, 100)} · 推荐歌单`,
        description: recommendationText.value || prompt.value.trim(),
      }),
    })
    recommendationText.value = `已保存为歌单“${result.data.name}”，可以前往音乐库继续播放。`
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    playlistSaving.value = false
  }
}

async function sendMusicEvent(track, eventType, playbackMs = null, silent = true) {
  const exposureId = track?._searchId || searchId.value
  if (!track?.id || !exposureId) return false
  try {
    await request('/api/music/events', {
      method: 'POST',
      keepalive: true,
      body: JSON.stringify({
        eventId: crypto.randomUUID(),
        searchId: exposureId,
        trackId: track.id,
        eventType,
        playbackMs: playbackMs == null ? null : Math.max(0, Math.round(playbackMs)),
      }),
    })
    return true
  } catch (error) {
    if (!silent) errorMessage.value = error.message
    return false
  }
}

async function recordTrackAction(track, eventType) {
  if (feedbackSaving.value) return
  feedbackSaving.value = true
  const accepted = await sendMusicEvent(track, eventType, Math.round(currentTime.value * 1000), false)
  if (accepted) {
    const current = trackActions.value[track.id] || {}
    trackActions.value = {
      ...trackActions.value,
      [track.id]: {
        ...current,
        liked: eventType === 'LIKE' ? true : current.liked,
        disliked: eventType === 'DISLIKE' ? true : (eventType === 'LIKE' ? false : current.disliked),
        ...(eventType === 'DISLIKE' ? { liked: false } : {}),
        saved: eventType === 'SAVE' ? true : (eventType === 'UNSAVE' ? false : current.saved),
      },
    }
    await refreshProfile()
  }
  feedbackSaving.value = false
}

async function toggleSaved(track) {
  const saved = Boolean(trackActions.value[track.id]?.saved)
  await recordTrackAction(track, saved ? 'UNSAVE' : 'SAVE')
}

async function playTrack(track) {
  const resolvedTrack = recommendations.value.find(item => item.id === track.id) || track
  addToQueue(resolvedTrack)
  currentTrack.value = resolvedTrack
  const sourceQueue = recommendations.value.length ? recommendations.value : queue.value
  music.playTrack(resolvedTrack, sourceQueue)
  errorMessage.value = ''
}

async function handlePlaybackStarted() {
  if (!currentTrack.value) return
  const result = startPlayback(activePlayback, currentTrack.value)
  activePlayback = result.state
  if (result.event) await sendMusicEvent(activePlayback.track, result.event.type, result.event.playbackMs)
}

async function observePlaybackProgress() {
  if (!activePlayback || activePlayback.track.id !== currentTrack.value?.id) return
  const playedMs = Math.max(0, Math.round(currentTime.value * 1000))
  const durationMs = Math.max(0, Math.round(durationSeconds.value * 1000))
  const result = observePlayback(activePlayback, playedMs, durationMs)
  activePlayback = result.state
  if (result.event) await sendMusicEvent(activePlayback.track, result.event.type, result.event.playbackMs)
}

async function recordSkipIfNeeded() {
  if (!activePlayback) return
  const playedMs = Math.round(currentTime.value * 1000)
  const durationMs = Math.max(0, Math.round(durationSeconds.value * 1000))
  const result = finishPlayback(activePlayback, playedMs, durationMs, 'switch')
  activePlayback = result.state
  if (result.event) await sendMusicEvent(activePlayback.track, result.event.type, result.event.playbackMs)
}

async function handleTrackEnded() {
  if (activePlayback) {
    const result = finishPlayback(activePlayback, Math.round(currentTime.value * 1000),
      Math.round(durationSeconds.value * 1000), 'ended')
    activePlayback = result.state
    if (result.event) await sendMusicEvent(activePlayback.track, result.event.type, result.event.playbackMs)
  }
  await nextTrack()
}

async function playAudio(track) {
  stopYoutubeProgress()
  youtubePlayer?.pauseVideo?.()
  if (!audioPlayer) initializeAudio()
  let playbackUrl = track.playbackUrl
  if (track.provider === 'qq') {
    currentQuality.value = 'detecting'
    try {
      const result = await request(track.playbackUrl)
      playbackUrl = result.data?.url
      currentQuality.value = result.data?.quality || ''
      if (!playbackUrl) throw new Error('QQ 音乐没有返回可播放地址')
    } catch (error) {
      currentQuality.value = ''
      paused.value = true
      playerReady.value = false
      errorMessage.value = error.message
      await refreshQqStatus()
      return
    }
  }
  if (audioPlayer.src !== playbackUrl) {
    audioPlayer.src = playbackUrl
    audioPlayer.load()
  }
  audioPlayer.volume = Number(volume.value)
  playerReady.value = true
  try {
    await audioPlayer.play()
  } catch {
    paused.value = true
    errorMessage.value = '浏览器阻止了自动播放，请再次点击播放按钮。'
  }
}

async function playYoutube(track) {
  audioPlayer?.pause()
  try {
    await loadYoutubeSdk()
    await nextTick()
    if (!youtubePlayer) {
      youtubePlayer = new window.YT.Player(youtubeHost.value, {
        width: '100%',
        height: '100%',
        videoId: track.playbackUrl,
        playerVars: { playsinline: 1, origin: window.location.origin },
        events: {
          onReady: event => {
            playerReady.value = true
            event.target.setVolume(Math.round(Number(volume.value) * 100))
            syncYoutubeProgress()
            startYoutubeProgress()
            event.target.playVideo()
          },
          onStateChange: event => {
            if (event.data === window.YT.PlayerState.PLAYING) {
              paused.value = false
              startYoutubeProgress()
              handlePlaybackStarted()
            }
            if (event.data === window.YT.PlayerState.PAUSED) paused.value = true
            if (event.data === window.YT.PlayerState.ENDED) {
              stopYoutubeProgress()
              handleTrackEnded()
            }
          },
          onError: () => {
            playerReady.value = false
            paused.value = true
            errorMessage.value = '该 YouTube 内容当前无法嵌入播放，请切换下一首或前往来源页面。'
          },
        },
      })
    } else {
      playerReady.value = true
      youtubePlayer.setVolume(Math.round(Number(volume.value) * 100))
      youtubePlayer.loadVideoById(track.playbackUrl)
      startYoutubeProgress()
    }
  } catch (error) {
    paused.value = true
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
      script.onerror = () => {
        window.clearTimeout(timeout)
        reject(new Error('YouTube 播放器脚本加载失败'))
      }
      document.head.appendChild(script)
    }
  })
  return youtubeSdkPromise
}

async function togglePlayback() {
  if (!currentTrack.value) return
  if (currentTrack.value.playbackType === 'youtube') {
    if (!youtubePlayer || !playerReady.value) return
    if (paused.value) youtubePlayer.playVideo()
    else youtubePlayer.pauseVideo()
    return
  }
  if (!audioPlayer) return
  if (audioPlayer.paused) {
    try {
      await audioPlayer.play()
    } catch {
      errorMessage.value = '无法继续播放当前歌曲，请切换下一首。'
    }
  } else {
    audioPlayer.pause()
  }
}

async function previousTrack() {
  if (!queue.value.length) return
  const currentIndex = queue.value.findIndex(item => item.id === currentTrack.value?.id)
  const targetIndex = currentIndex <= 0 ? queue.value.length - 1 : currentIndex - 1
  await playTrack(queue.value[targetIndex])
}

async function nextTrack() {
  if (!queue.value.length) return
  const currentIndex = queue.value.findIndex(item => item.id === currentTrack.value?.id)
  const targetIndex = currentIndex < 0 || currentIndex >= queue.value.length - 1 ? 0 : currentIndex + 1
  await playTrack(queue.value[targetIndex])
}

async function playQueue() {
  if (queue.value.length) await playTrack(queue.value[0])
}

function updateVolume() {
  if (Number(volume.value) > 0) lastAudibleVolume = Number(volume.value)
  if (audioPlayer) audioPlayer.volume = Number(volume.value)
  youtubePlayer?.setVolume?.(Math.round(Number(volume.value) * 100))
}

function toggleMute() {
  if (Number(volume.value) > 0) {
    lastAudibleVolume = Number(volume.value)
    volume.value = 0
  } else {
    volume.value = lastAudibleVolume || 0.7
  }
  updateVolume()
}

function resetProgress(track) {
  currentTime.value = 0
  durationSeconds.value = Math.max(0, Number(track?.durationMs || 0) / 1000)
  currentQuality.value = track?.provider === 'qq' ? 'detecting' : ''
}

function qualityLabel(value) {
  return {
    detecting: '音质检测中',
    flac: 'FLAC 无损',
    320: '320 kbps',
    128: '128 kbps',
    m4a: 'M4A',
  }[value] || value
}

function syncAudioProgress() {
  if (!audioPlayer || currentTrack.value?.playbackType !== 'audio') return
  if (!seeking.value && Number.isFinite(audioPlayer.currentTime)) {
    currentTime.value = Math.max(0, audioPlayer.currentTime)
  }
  if (Number.isFinite(audioPlayer.duration) && audioPlayer.duration > 0) {
    durationSeconds.value = audioPlayer.duration
  }
}

function syncYoutubeProgress() {
  if (!youtubePlayer || currentTrack.value?.playbackType !== 'youtube') return
  const duration = Number(youtubePlayer.getDuration?.())
  const position = Number(youtubePlayer.getCurrentTime?.())
  if (Number.isFinite(duration) && duration > 0) durationSeconds.value = duration
  if (!seeking.value && Number.isFinite(position) && position >= 0) currentTime.value = position
  observePlaybackProgress()
}

function startYoutubeProgress() {
  stopYoutubeProgress()
  syncYoutubeProgress()
  youtubeProgressTimer = window.setInterval(syncYoutubeProgress, 500)
}

function stopYoutubeProgress() {
  if (youtubeProgressTimer !== null) {
    window.clearInterval(youtubeProgressTimer)
    youtubeProgressTimer = null
  }
}

function seekPlayback(event) {
  if (!canSeek.value) return
  const requested = Number(event.target.value)
  if (!Number.isFinite(requested)) return
  const target = Math.min(Math.max(0, requested), durationSeconds.value)
  currentTime.value = target
  if (currentTrack.value.playbackType === 'youtube') {
    youtubePlayer?.seekTo?.(target, true)
  } else if (audioPlayer) {
    audioPlayer.currentTime = target
  }
}

function finishSeeking(event) {
  seekPlayback(event)
  seeking.value = false
}

function removeFromQueue(id) {
  queue.value = queue.value.filter(item => item.id !== id)
}

function providerName(provider) {
  return status.value.providers.find(item => item.id === provider)?.name || provider
}

function formatDuration(milliseconds) {
  if (!milliseconds) return '--:--'
  const seconds = Math.floor(milliseconds / 1000)
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

function formatPlaybackTime(value) {
  const seconds = Math.max(0, Math.floor(Number(value) || 0))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const remainder = String(seconds % 60).padStart(2, '0')
  return hours
    ? `${hours}:${String(minutes).padStart(2, '0')}:${remainder}`
    : `${minutes}:${remainder}`
}
</script>

<template>
  <aside class="music-panel" aria-label="音乐推荐与播放器">
    <div v-if="currentTrack?.imageUrl" class="music-ambience" aria-hidden="true">
      <img :src="currentTrack.imageUrl" alt="" />
    </div>
    <header class="music-header">
      <div>
        <span class="section-kicker">SONORA · OPEN MUSIC</span>
        <h2>为此刻配乐</h2>
      </div>
      <div class="music-health">
        <span class="connection-state" :class="{ online: status.ready }">
          <i aria-hidden="true"></i>{{ status.ready ? '曲库就绪' : '未配置' }}
        </span>
        <button type="button" class="personalization-state" :class="personalizationStatus.toLowerCase()" @click="profileOpen = !profileOpen">
          {{ { ACTIVE: '个性化已启用', COLD_START: '画像冷启动', DEGRADED: '个性化降级', DISABLED: '基础排序' }[personalizationStatus] }}
        </button>
      </div>
    </header>

    <section v-if="profileOpen" class="music-profile" aria-label="我的音乐偏好">
      <header>
        <div><span class="section-kicker">MEMORY</span><strong>我的偏好</strong></div>
        <small>{{ profile.labeledEvents }} 条有效行为 · {{ profile.exposures }} 次推荐 · {{ policyVersion }}</small>
      </header>
      <article v-if="profile.summary" class="profile-summary-card" :data-stage="profile.summary.stage">
        <header>
          <span>{{ profile.summary.stageLabel }}</span>
          <small>结论可信度 · {{ profile.summary.confidenceLabel }}</small>
        </header>
        <h3>{{ profile.summary.headline }}</h3>
        <p>{{ profile.summary.overview }}</p>
        <div v-if="profile.summary.likes.length || profile.summary.avoids.length" class="profile-insight-groups">
          <section v-if="profile.summary.likes.length">
            <strong>偏好</strong>
            <span v-for="item in profile.summary.likes" :key="`like-${item.type}-${item.value}`">
              {{ item.typeLabel }} · {{ item.value }}
              <small>{{ item.layer === 'L1' ? '明确设置' : `${Math.round(item.confidence * 100)}%` }}</small>
            </span>
          </section>
          <section v-if="profile.summary.avoids.length" class="avoid">
            <strong>避开</strong>
            <span v-for="item in profile.summary.avoids" :key="`avoid-${item.type}-${item.value}`">
              {{ item.typeLabel }} · {{ item.value }}
              <small>{{ item.layer === 'L1' ? '明确设置' : `${Math.round(item.confidence * 100)}%` }}</small>
            </span>
          </section>
        </div>
        <details v-if="profile.summary.observations.length" class="profile-observations">
          <summary>画像依据与边界</summary>
          <ul><li v-for="item in profile.summary.observations" :key="item">{{ item }}</li></ul>
        </details>
      </article>
      <form class="preference-form" @submit.prevent="addPreference">
        <select v-model="preferenceType" aria-label="偏好类型">
          <option value="GENRE">风格</option><option value="MOOD">情绪</option>
          <option value="SCENE">场景</option><option value="LANGUAGE">语言</option>
          <option value="ARTIST">艺人</option><option value="TAG">标签</option>
        </select>
        <select v-model="preferencePolarity" aria-label="偏好方向">
          <option :value="1">喜欢</option><option :value="-1">避开</option>
        </select>
        <input v-model="preferenceValue" maxlength="200" placeholder="例如 ambient、安静、Mili" />
        <button type="submit" :disabled="feedbackSaving || !preferenceValue.trim()">保存</button>
      </form>
      <div v-if="profileLoading" class="profile-empty"><LoaderCircle class="spin" :size="14" /> 正在读取画像</div>
      <template v-else>
        <div class="preference-list">
          <span v-for="item in profile.explicitPreferences" :key="item.id" :class="{ avoid: item.polarity < 0 }">
            {{ item.polarity > 0 ? '喜欢' : '避开' }} · {{ item.value }}
            <button type="button" title="删除这条偏好" @click="deletePreference(item)">×</button>
          </span>
        </div>
        <details v-if="profile.inferredPreferences.length" class="inferred-preferences">
          <summary>学习到的偏好 {{ profile.inferredPreferences.length }} 条</summary>
          <div class="preference-list">
            <span v-for="item in profile.inferredPreferences" :key="item.id" :class="{ avoid: item.polarity < 0 }">
              {{ item.value }} · {{ Math.round(item.confidence * 100) }}%
              <button type="button" title="删除这条推断" @click="deletePreference(item)">×</button>
            </span>
          </div>
          <button class="clear-learned" type="button" @click="clearLearnedProfile">清除全部学习画像</button>
        </details>
        <p v-if="!profile.explicitPreferences.length && !profile.inferredPreferences.length" class="profile-empty">还没有偏好记录；播放和反馈会逐步形成只属于你的排序。</p>
      </template>
    </section>

    <details v-if="qqStatus.enabled" class="qq-connect" :open="!qqStatus.sessionConfigured">
      <summary>
        <span><KeyRound :size="15" /> QQ 音乐</span>
        <small :class="{ ready: qqStatus.bridgeAvailable && qqStatus.sessionConfigured }">
          {{ qqStatus.sessionConfigured ? qqStatus.maskedAccount : (qqStatus.bridgeAvailable ? '待导入登录态' : 'Bridge 未启动') }}
        </small>
      </summary>
      <div class="qq-connect-body">
        <p>{{ qqStatus.message }}</p>
        <template v-if="!qqStatus.sessionConfigured">
          <input
            v-model="qqCookie"
            type="password"
            autocomplete="off"
            maxlength="16384"
            placeholder="粘贴你在 y.qq.com 登录后的完整 Cookie"
            :disabled="qqSaving || !qqStatus.bridgeAvailable"
            @keyup.enter="saveQqSession"
          />
          <div class="qq-connect-actions">
            <a href="https://y.qq.com" target="_blank" rel="noreferrer">打开 QQ 音乐官网</a>
            <button type="button" :disabled="qqSaving || !qqStatus.bridgeAvailable || !qqCookie.trim()" @click="saveQqSession">
              {{ qqSaving ? '正在保存' : '仅保存到本机' }}
            </button>
          </div>
          <small>Cookie 相当于账号凭证，请勿发送给他人；Sonora 会加密保存且不会写入日志。</small>
        </template>
        <button v-else class="qq-disconnect" type="button" :disabled="qqSaving" @click="clearQqSession">清除本机登录态</button>
      </div>
    </details>

    <div v-if="statusLoading" class="music-status muted-status">
      <LoaderCircle class="spin" :size="17" /> 正在检查开放曲库
    </div>
    <div v-else-if="!status.ready" class="music-status catalog-setup">
      <ShieldCheck :size="22" />
      <p>{{ status.message }}</p>
      <small>无需用户绑定音乐账号，密钥只保存在服务端。</small>
    </div>

    <template v-else>
      <form class="music-search" @submit.prevent="recommend">
        <div class="music-search-heading">
          <div>
            <span class="section-kicker">MOOD</span>
            <label for="music-prompt">描述你想进入的氛围</label>
          </div>
          <div class="provider-pills" aria-label="可用曲库">
            <span v-for="provider in configuredProviders" :key="provider.id">{{ provider.name }}</span>
          </div>
        </div>
        <textarea
          id="music-prompt"
          v-model="prompt"
          rows="2"
          maxlength="500"
          placeholder="例如：深夜写代码，安静、轻盈，有一点未来感"
          :disabled="recommending"
        ></textarea>
        <button class="button wide" type="submit" :disabled="!canRecommend">
          <LoaderCircle v-if="recommending" class="spin" :size="17" />
          <Sparkles v-else :size="17" />
          <span>{{ recommending ? '正在聆听你的描述' : '生成专属歌单' }}</span>
        </button>
      </form>

      <div v-if="!recommendations.length" class="mood-presets">
        <span>试试这些氛围</span>
        <button v-for="item in moodSuggestions" :key="item" type="button" @click="applyMood(item)">{{ item }}</button>
      </div>

      <section v-if="recommendations.length || currentPage > 1" class="music-section">
        <div class="music-section-title">
          <div>
            <span class="section-kicker">RESULTS</span>
            <h3>为你推荐 <small>{{ recommendations.length }} 首</small></h3>
          </div>
          <div class="queue-actions">
            <button class="text-command" type="button" :disabled="playlistSaving" title="保存为可重复打开的歌单" @click="saveRecommendationPlaylist">{{ playlistSaving ? '保存中' : '保存歌单' }}</button>
            <button class="text-command" type="button" title="全部加入待播清单" @click="addAll">全部加入</button>
            <RouterLink class="text-command" to="/music">音乐库</RouterLink>
          </div>
        </div>
        <p class="recommendation-copy">{{ recommendationText }}</p>
        <div v-if="understanding" class="music-understanding">
          <div>
            <span>理解为</span>
            <strong>{{ understanding.canonicalName }}</strong>
            <small>{{ understanding.entityType }} · {{ Math.round(understanding.confidence * 100) }}%</small>
          </div>
          <button type="button" @click="correctingUnderstanding = !correctingUnderstanding">更正理解</button>
        </div>
        <form v-if="correctingUnderstanding" class="understanding-correction" @submit.prevent="submitEntityCorrection">
          <input v-model="correctedEntityName" maxlength="160" placeholder="正确的作品、赛事、歌手或歌曲名称" />
          <select v-model="correctedEntityType" aria-label="实体类型">
            <option value="GAME">游戏</option>
            <option value="EVENT">赛事</option>
            <option value="ANIME">动漫</option>
            <option value="FILM">影视</option>
            <option value="FRANCHISE">作品系列</option>
            <option value="SOUNDTRACK">原声带</option>
            <option value="TRACK">歌曲</option>
            <option value="ARTIST">歌手</option>
            <option value="ALBUM">专辑</option>
          </select>
          <button type="submit" :disabled="feedbackSaving || !correctedEntityName.trim()">
            {{ feedbackSaving ? '保存中' : '保存并重新搜索' }}
          </button>
        </form>
        <p v-if="!recommendations.length" class="page-empty">这一页暂时没有更多结果，可以返回上一页继续选择。</p>
        <div v-else class="result-groups">
          <section v-for="group in resultGroups" :key="group.key" class="result-group" :class="`result-group-${group.key}`">
            <header>
              <strong>{{ group.label }}</strong>
              <span>{{ group.tracks.length }} 首 · {{ group.hint }}</span>
            </header>
            <div class="track-list">
              <article v-for="track in group.tracks" :key="track.id" class="track-row" :class="{ active: currentTrack?.id === track.id }">
                <span class="track-index">{{ String(resultIndex(track)).padStart(2, '0') }}</span>
                <img v-if="track.imageUrl" :src="track.imageUrl" :alt="`${track.album || track.name} 封面`" />
                <span v-else class="track-art-placeholder"><Music2 :size="18" /></span>
                <button class="track-main" type="button" :title="`打开 ${track.name} 歌词页`" @click="openTrackPage(track)">
                  <strong>{{ track.name }}</strong>
                  <span>{{ track.artists.join(' / ') }}</span>
                  <small v-if="track.relationLabel">{{ track.relationLabel }}</small>
                  <small v-if="track.reasonText" class="track-reason">{{ track.reasonText }}</small>
                </button>
                <span class="track-provider">{{ providerName(track.provider) }}</span>
                <span class="track-duration">{{ formatDuration(track.durationMs) }}</span>
                <div class="track-controls">
                  <button class="icon-command" type="button" title="加入播放列表" @click="addToQueue(track)"><Plus :size="15" /></button>
                  <MusicTrackActions :track="{ ...track, _searchId: searchId }" compact />
                  <button class="icon-command" :class="{ selected: trackActions[track.id]?.disliked }" type="button" title="不喜欢" :disabled="feedbackSaving || trackActions[track.id]?.disliked" @click="recordTrackAction(track, 'DISLIKE')"><ThumbsDown :size="14" /></button>
                  <a :href="track.externalUrl" target="_blank" rel="noreferrer" class="icon-command" :title="`在 ${providerName(track.provider)} 打开`"><ExternalLink :size="14" /></a>
                </div>
                <button
                  v-if="understanding"
                  class="track-feedback"
                  type="button"
                  title="标记为与当前理解不相关"
                  :disabled="feedbackSaving"
                  @click="markNotRelevant(track)"
                >不相关</button>
                <a v-if="track.licenseUrl" :href="track.licenseUrl" target="_blank" rel="noreferrer" class="track-license" title="查看歌曲许可">CC</a>
              </article>
            </div>
          </section>
        </div>
        <nav class="result-pagination" aria-label="音乐搜索分页">
          <button
            type="button"
            :disabled="recommending || currentPage <= 1"
            @click="loadRecommendations(currentPage - 1)"
          >上一页</button>
          <span>第 <strong>{{ currentPage }}</strong> 页 <small>· 最多 {{ maxPages }} 页</small></span>
          <button
            type="button"
            :disabled="recommending || !hasNextPage || currentPage >= maxPages"
            @click="loadRecommendations(currentPage + 1)"
          >下一页</button>
        </nav>
      </section>

      <section class="music-section queue-section">
        <div class="music-section-title">
          <div>
            <span class="section-kicker">QUEUE</span>
            <h3>待播清单 <small>{{ queue.length }}</small></h3>
          </div>
          <div v-if="queue.length" class="queue-actions">
            <button class="icon-command" type="button" title="播放待播清单" @click="playQueue"><Play :size="15" /></button>
            <button class="icon-command" type="button" title="清空待播清单" @click="queue = []"><Trash2 :size="15" /></button>
          </div>
        </div>
        <p v-if="!queue.length" class="queue-empty"><ListMusic :size="18" /> 暂无歌曲</p>
        <div v-else class="queue-list">
          <div v-for="track in queue" :key="track.id" class="queue-row" :class="{ active: currentTrack?.id === track.id }">
            <button type="button" :title="`播放 ${track.name}`" @click="playTrack(track)">
              <Play :size="13" /><span>{{ track.name }}</span>
            </button>
            <button class="icon-command" type="button" title="从播放列表移除" @click="removeFromQueue(track.id)"><X :size="14" /></button>
          </div>
        </div>
      </section>
    </template>

    <p v-if="errorMessage" class="music-error">
      <span>{{ errorMessage }}</span>
      <button type="button" title="关闭提示" aria-label="关闭提示" @click="errorMessage = ''"><X :size="15" /></button>
    </p>
  </aside>
</template>
