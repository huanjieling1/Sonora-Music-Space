<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft, ChevronRight, Clock3, Disc3, Heart, Home, Library, ListMusic, LogOut,
  Music2, Play, Plus, Search, Sparkles, Trash2, UserRound, WandSparkles, X,
} from 'lucide-vue-next'
import { ApiError, request } from '../services/api'
import { confirmAction } from '../services/confirm'
import { nextQqHomePage, readQqHomePage, writeQqHomePage } from '../services/musicDiscoveryCache'
import { getMusicGreeting } from '../services/musicGreeting'
import { navigateBack, returnState } from '../services/navigation'
import { shuffleTracks } from '../services/musicShuffle'
import { useAuthStore } from '../stores/auth'
import { useMusicStore } from '../stores/music'
import MusicTrackActions from '../components/MusicTrackActions.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const music = useMusicStore()

const playlists = ref([])
const detail = ref(null)
const conversationId = ref('')
const loading = ref(true)
const opening = ref(false)
const generatingKey = ref('')
const errorMessage = ref('')
const createOpen = ref(false)
const createName = ref('')
const sidebarPlaylistQuery = ref('')
const sidebarPlaylistFilter = ref('ALL')
const searchKeyword = ref('')
const searchType = ref('TRACK')
const searchResult = ref(null)
const searching = ref(false)
const selectedOnlinePlaylist = ref(null)
const renaming = ref(false)
const editName = ref('')
const editDescription = ref('')
const qqHomePlaylists = ref([])
const qqHomeLoading = ref(false)
const qqHomeError = ref('')
const qqHomePage = ref(1)
const artistDetail = ref(null)
const artistOpening = ref(false)
const artistTab = ref('SONGS')
const albumDetail = ref(null)
const albumOpening = ref(false)
const currentMoment = ref(new Date())
let greetingClock = null

const activePlaylistId = computed(() => typeof route.params.playlistId === 'string' ? route.params.playlistId : '')
const activeQqPlaylistId = computed(() => typeof route.params.qqPlaylistId === 'string' ? route.params.qqPlaylistId : '')
const activeQqArtistMid = computed(() => typeof route.params.artistMid === 'string' ? route.params.artistMid : '')
const activeQqAlbumMid = computed(() => typeof route.params.albumMid === 'string' ? route.params.albumMid : '')
const isArtistView = computed(() => Boolean(activeQqArtistMid.value))
const isAlbumView = computed(() => Boolean(activeQqAlbumMid.value))
const isHome = computed(() => !activePlaylistId.value && !activeQqPlaylistId.value && !activeQqArtistMid.value && !activeQqAlbumMid.value)
const isSearchView = computed(() => isHome.value && Boolean(searchResult.value))
const detailTracks = computed(() => (detail.value?.tracks || []).map(item => ({
  ...item.track,
  _searchId: detail.value.searchId,
  _playlistTrackId: item.playlistTrackId,
})))
const systemPlaylists = computed(() => playlists.value.filter(item => ['FAVORITES', 'RECENT'].includes(item.type)))
const personalPlaylists = computed(() => playlists.value.filter(item => !['FAVORITES', 'RECENT'].includes(item.type)))
const visiblePersonalPlaylists = computed(() => {
  const query = sidebarPlaylistQuery.value.trim().toLocaleLowerCase('zh-CN')
  return personalPlaylists.value
    .filter(item => sidebarPlaylistFilter.value === 'ALL' || item.type === sidebarPlaylistFilter.value)
    .filter(item => !query || `${item.name} ${item.description || ''}`.toLocaleLowerCase('zh-CN').includes(query))
    .sort((left, right) => new Date(right.updatedAt || 0) - new Date(left.updatedAt || 0))
})
const greetingState = computed(() => getMusicGreeting(currentMoment.value))
const searchTracks = computed(() => withSearchExposure(searchResult.value?.tracks || []))
const activeSearchTracks = computed(() => searchTracks.value)
const artistTracks = computed(() => (artistDetail.value?.tracks || []).map(track => ({
  ...track,
  _searchId: artistDetail.value?.searchId,
})))
const albumTracks = computed(() => (albumDetail.value?.tracks || []).map(track => ({ ...track, _searchId: albumDetail.value?.searchId })))

const searchTabs = [
  { type: 'TRACK', label: '歌曲' },
  { type: 'VIDEO', label: '视频' },
  { type: 'ALBUM', label: '专辑' },
  { type: 'PLAYLIST', label: '歌单' },
  { type: 'LYRIC', label: '歌词' },
  { type: 'ARTIST', label: '歌手' },
  { type: 'USER', label: '用户' },
]

const sidebarPlaylistFilters = [
  { type: 'ALL', label: '全部' },
  { type: 'CUSTOM', label: '自建' },
  { type: 'RECOMMENDED', label: '智能' },
]

const mixes = [
  { key: 'focus', eyebrow: 'FOCUS FLOW', name: '深度专注', description: '适合长时间阅读与学习，克制、低干扰、有稳定节拍', color: 'lime' },
  { key: 'night', eyebrow: 'NIGHT RADIO', name: '霓虹夜行', description: '深夜城市、合成器、朦胧氛围与一点未来感', color: 'violet' },
  { key: 'reset', eyebrow: 'SOFT RESET', name: '柔软重启', description: '温柔、松弛、清亮的人声和轻盈器乐', color: 'peach' },
  { key: 'discover', eyebrow: 'DISCOVERY', name: '偏好之外', description: '保留熟悉感，同时探索一些没有听过的新艺人与风格', color: 'blue' },
]

onMounted(() => {
  void initialize()
  greetingClock = window.setInterval(() => { currentMoment.value = new Date() }, 60_000)
})
onBeforeUnmount(() => window.clearInterval(greetingClock))
watch(() => [activePlaylistId.value, activeQqPlaylistId.value], async () => { await loadActivePlaylist() })
watch(activeQqArtistMid, async () => { await loadArtistDetail() })
watch(activeQqAlbumMid, async () => { await loadAlbumDetail() })
watch(() => music.playlistRevision, async () => {
  const mutation = music.lastPlaylistMutation
  if (!mutation?.playlistId) return
  const index = playlists.value.findIndex(item => item.id === mutation.playlistId)
  if (mutation.playlist) {
    if (index >= 0) playlists.value.splice(index, 1, mutation.playlist)
    else playlists.value.unshift(mutation.playlist)
  } else {
    await refreshPlaylists()
  }
  if (activePlaylistId.value === mutation.playlistId) await loadActivePlaylist()
})

async function initialize() {
  loading.value = true
  try {
    await ensureConversation()
    await Promise.all([refreshPlaylists(), loadQqHome(readQqHomePage(window.localStorage, auth.user?.id))])
    await Promise.all([loadActivePlaylist(), loadArtistDetail(), loadAlbumDetail()])
    await restoreSearchFromRoute()
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      await router.replace('/login')
      return
    }
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

async function restoreSearchFromRoute() {
  if (!isHome.value || typeof route.query.search !== 'string' || !route.query.search.trim()) return
  const requestedType = String(route.query.type || 'TRACK').toUpperCase()
  searchType.value = searchTabs.some(tab => tab.type === requestedType) ? requestedType : 'TRACK'
  searchKeyword.value = route.query.search.trim()
  const requestedPage = Math.max(1, Number.parseInt(String(route.query.page || '1'), 10) || 1)
  await performSearch(requestedPage, searchKeyword.value, { syncRoute: false })
}

async function loadArtistDetail(options = {}) {
  if (!activeQqArtistMid.value || !conversationId.value) {
    artistDetail.value = null
    return
  }
  artistOpening.value = true
  errorMessage.value = ''
  const current = artistDetail.value
  const songPage = options.songPage || current?.songPage || 1
  const albumPage = options.albumPage || current?.albumPage || 1
  try {
    const params = new URLSearchParams({
      conversationId: conversationId.value,
      songPage: String(songPage),
      songPageSize: '20',
      albumPage: String(albumPage),
      albumPageSize: '12',
    })
    const result = await request(`/api/music/qq/artists/${encodeURIComponent(activeQqArtistMid.value)}?${params}`)
    artistDetail.value = result.data
  } catch (error) {
    errorMessage.value = error.message
    if (error.status === 404) await router.replace('/music')
  } finally {
    artistOpening.value = false
  }
}

async function loadAlbumDetail() {
  if (!activeQqAlbumMid.value || !conversationId.value) { albumDetail.value = null; return }
  albumOpening.value = true
  errorMessage.value = ''
  try {
    const result = await request(`/api/music/qq/albums/${encodeURIComponent(activeQqAlbumMid.value)}?conversationId=${encodeURIComponent(conversationId.value)}`)
    albumDetail.value = result.data
  } catch (error) {
    errorMessage.value = error.message
    if (error.status === 404) await router.replace('/music')
  } finally { albumOpening.value = false }
}

async function ensureConversation() {
  const result = await request('/api/agent/conversations')
  if (result.data?.length) {
    conversationId.value = result.data[0].id
    return
  }
  const created = await request('/api/agent/conversations', { method: 'POST' })
  conversationId.value = created.data.id
}

async function refreshPlaylists() {
  const result = await request('/api/music/playlists')
  playlists.value = result.data || []
}

async function loadQqHome(page = 1) {
  qqHomeLoading.value = true
  qqHomeError.value = ''
  try {
    const result = await request(`/api/music/qq/home?page=${page}&pageSize=12`)
    const nextPlaylists = result.data || []
    if (!nextPlaylists.length && page > 1) return loadQqHome(1)
    qqHomePlaylists.value = nextPlaylists
    qqHomePage.value = page
    writeQqHomePage(window.localStorage, auth.user?.id, page)
  } catch (error) {
    qqHomeError.value = error.message || 'QQ 音乐公开歌单暂时无法加载'
  } finally {
    qqHomeLoading.value = false
  }
}

async function loadNextQqHome() {
  if (qqHomeLoading.value) return
  await loadQqHome(nextQqHomePage(qqHomePage.value))
}

async function loadActivePlaylist() {
  if ((!activePlaylistId.value && !activeQqPlaylistId.value) || !conversationId.value) {
    detail.value = null
    return
  }
  opening.value = true
  errorMessage.value = ''
  try {
    if (activeQqPlaylistId.value) {
      const result = await request(`/api/music/qq/playlists/${encodeURIComponent(activeQqPlaylistId.value)}?conversationId=${encodeURIComponent(conversationId.value)}&limit=80`)
      const playlist = result.data
      detail.value = {
        playlist: {
          ...playlist,
          type: 'QQ_PUBLIC',
          editable: false,
          trackCount: playlist.trackCount || playlist.tracks?.length || 0,
        },
        searchId: playlist.searchId,
        policyVersion: playlist.policyVersion,
        personalizationStatus: playlist.personalizationStatus,
        tracks: (playlist.tracks || []).map((track, index) => ({
          playlistTrackId: `qq-${playlist.id}-${index}`,
          position: index + 1,
          track: { ...track, _searchId: playlist.searchId },
        })),
      }
      renaming.value = false
      return
    }
    const result = await request(`/api/music/playlists/${encodeURIComponent(activePlaylistId.value)}/open`, {
      method: 'POST',
      body: JSON.stringify({ conversationId: conversationId.value }),
    })
    detail.value = result.data
    editName.value = detail.value.playlist.name
    editDescription.value = detail.value.playlist.description || ''
  } catch (error) {
    errorMessage.value = error.message
    if (error.status === 404) await router.replace('/music')
  } finally {
    opening.value = false
  }
}

async function generateMix(mix = null) {
  const description = (mix?.description || '').trim()
  if (!description || generatingKey.value) return
  const name = mix?.name || '此刻的专属歌单'
  generatingKey.value = mix?.key || 'custom'
  errorMessage.value = ''
  try {
    const result = await request('/api/music/playlists/recommended', {
      method: 'POST',
      body: JSON.stringify({ conversationId: conversationId.value, description, name }),
    })
    await refreshPlaylists()
    await router.push(`/music/playlists/${result.data.id}`)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    generatingKey.value = ''
  }
}

async function playRandomMix(mix) {
  if (!mix?.description || generatingKey.value || !conversationId.value) return
  generatingKey.value = mix.key
  errorMessage.value = ''
  try {
    const result = await request('/api/music/recommend', {
      method: 'POST',
      body: JSON.stringify({ conversationId: conversationId.value, description: mix.description, page: 1, pageSize: 10 }),
    })
    const queue = shuffleTracks((result.data?.tracks || []).map(track => ({ ...track, _searchId: result.data.searchId })))
    if (!queue.length) throw new Error('这个场景暂时没有找到可播放歌曲')
    music.playTrack(queue[0], queue)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    generatingKey.value = ''
  }
}

async function performSearch(page = 1, overrideKeyword = '', options = {}) {
  const keyword = (overrideKeyword || searchKeyword.value).trim()
  if (!keyword || searching.value) return
  searching.value = true
  errorMessage.value = ''
  selectedOnlinePlaylist.value = null
  searchKeyword.value = keyword
  try {
    const params = new URLSearchParams({
      conversationId: conversationId.value,
      keyword,
      type: searchType.value,
      page: String(page),
      pageSize: '20',
    })
    const result = await request(`/api/music/qq/search?${params}`)
    searchResult.value = result.data
    const searchLocation = {
      path: '/music',
      query: { search: keyword, type: searchType.value, page: String(page) },
    }
    if (!isHome.value) await router.push(searchLocation)
    else if (options.syncRoute !== false && (
      route.query.search !== keyword
      || route.query.type !== searchType.value
      || String(route.query.page || '') !== String(page)
    )) await router.replace(searchLocation)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    searching.value = false
  }
}

async function changeSearchType(type) {
  if (searchType.value === type && searchResult.value?.type === type) return
  searchType.value = type
  await performSearch(1)
}

async function clearSearch() {
  searchResult.value = null
  selectedOnlinePlaylist.value = null
  searchType.value = 'TRACK'
  if (route.query.search) await router.replace('/music')
}

async function handleTopbarBack() {
  if (isSearchView.value) await clearSearch()
  else navigateBack(router)
}

function openSearchArtist(artist) {
  const mid = String(artist?.mid || artist?.id || '').trim()
  if (!/^[A-Za-z0-9]+$/.test(mid)) return
  router.push({ name: 'music-qq-artist', params: { artistMid: mid }, state: returnState(route) })
}

function openSearchAlbum(album) {
  const mid = String(album?.mid || album?.id || '').trim()
  if (mid) router.push({ name: 'music-qq-album', params: { albumMid: mid }, state: returnState(route) })
}

function openSearchVideo(video) {
  const id = String(video?.id || '').trim()
  if (!id) return
  router.push({ name: 'music-qq-video', params: { videoId: id }, query: { title: video.name || '', cover: video.coverUrl || '', artists: video.artists?.join(' / ') || '', durationMs: String(video.durationMs || 0), publishDate: video.publishDate || '' }, state: returnState(route) })
}

function playAlbumTrack(index) {
  if (albumTracks.value[index]) music.playTrack(albumTracks.value[index], albumTracks.value)
}

function openAlbumTrack(index) {
  const track = albumTracks.value[index]
  if (!track) return
  music.playTrack(track, albumTracks.value)
  router.push({ name: 'music-track', params: { provider: track.provider || 'qq', trackId: track.id }, state: returnState(route) })
}

function openSearchPlaylist(playlist) {
  openQqPlaylist(playlist.id)
}

function withSearchExposure(tracks) {
  return tracks.map(track => ({ ...track, _searchId: searchResult.value?.searchId }))
}

function playSearchTrack(index) {
  const tracks = activeSearchTracks.value
  if (tracks[index]) music.playTrack(tracks[index], tracks)
}

function openSearchTrack(index) {
  const tracks = activeSearchTracks.value
  const track = tracks[index]
  if (!track) return
  music.playTrack(track, tracks)
  router.push({ name: 'music-track', params: { provider: track.provider || 'unknown', trackId: track.id }, state: returnState(route) })
}

function openLyricResult(item) {
  const track = { ...item.track, _searchId: searchResult.value?.searchId }
  if (!track.id) return
  music.playTrack(track, [track])
  router.push({ name: 'music-track', params: { provider: track.provider || 'qq', trackId: track.id }, state: returnState(route) })
}

async function createPlaylist() {
  if (!createName.value.trim()) return
  try {
    const result = await request('/api/music/playlists', {
      method: 'POST', body: JSON.stringify({ name: createName.value.trim(), description: '' }),
    })
    createName.value = ''
    createOpen.value = false
    await refreshPlaylists()
    await router.push(`/music/playlists/${result.data.id}`)
  } catch (error) { errorMessage.value = error.message }
}

async function savePlaylistEdit() {
  if (!editName.value.trim()) return
  try {
    await request(`/api/music/playlists/${encodeURIComponent(activePlaylistId.value)}`, {
      method: 'PATCH',
      body: JSON.stringify({ name: editName.value.trim(), description: editDescription.value.trim() }),
    })
    renaming.value = false
    await refreshPlaylists()
    await loadActivePlaylist()
  } catch (error) { errorMessage.value = error.message }
}

async function deletePlaylist() {
  if (!detail.value?.playlist.editable) return
  const accepted = await confirmAction({
    eyebrow: '删除歌单',
    title: '要删除这个歌单吗？',
    message: '歌单和其中的编排将从你的音乐库中移除，歌曲本身不会受到影响。',
    subject: detail.value.playlist.name,
    hint: '删除后无法恢复该歌单',
    confirmText: '删除歌单',
    cancelText: '保留歌单',
  })
  if (!accepted) return
  try {
    await request(`/api/music/playlists/${encodeURIComponent(activePlaylistId.value)}`, { method: 'DELETE' })
    await refreshPlaylists()
    await router.replace('/music')
  } catch (error) { errorMessage.value = error.message }
}

async function removeTrack(item) {
  const accepted = await confirmAction({
    eyebrow: '移出歌单',
    title: '要移出这首歌曲吗？',
    message: '歌曲只会从当前歌单中移除，不会影响你的喜欢记录和其他歌单。',
    subject: item.track?.name || item.name || '当前歌曲',
    hint: '之后仍可通过搜索重新加入',
    confirmText: '移出歌曲',
    cancelText: '继续保留',
  })
  if (!accepted) return
  try {
    await request(`/api/music/playlists/${encodeURIComponent(activePlaylistId.value)}/tracks/${item.playlistTrackId}`, { method: 'DELETE' })
    await refreshPlaylists()
    await loadActivePlaylist()
  } catch (error) { errorMessage.value = error.message }
}

function playAll() {
  if (detailTracks.value.length) music.playTrack(detailTracks.value[0], detailTracks.value)
}

function playShuffledDetail() {
  const queue = shuffleTracks(detailTracks.value)
  if (queue.length) music.playTrack(queue[0], queue)
}

function playAt(index) {
  music.playTrack(detailTracks.value[index], detailTracks.value)
}

function openTrack(index) {
  const track = detailTracks.value[index]
  if (!track) return
  music.playTrack(track, detailTracks.value)
  router.push({
    name: 'music-track',
    params: { provider: track.provider || 'unknown', trackId: track.id },
    state: returnState(route),
  })
}

function openPlaylist(id) {
  router.push(`/music/playlists/${id}`)
}

function openQqPlaylist(id) {
  router.push(`/music/qq/playlists/${id}`)
}

function playArtistTrack(index) {
  if (artistTracks.value[index]) music.playTrack(artistTracks.value[index], artistTracks.value)
}

function openArtistTrack(index) {
  const track = artistTracks.value[index]
  if (!track) return
  music.playTrack(track, artistTracks.value)
  router.push({ name: 'music-track', params: { provider: track.provider || 'qq', trackId: track.id }, state: returnState(route) })
}

function playArtistTopTracks() {
  if (artistTracks.value.length) music.playTrack(artistTracks.value[0], artistTracks.value)
}

async function logout() {
  music.clearQueue()
  await auth.logout().catch(() => undefined)
  await router.replace('/login')
}

function formatDuration(ms) {
  const seconds = Math.floor(Number(ms || 0) / 1000)
  return seconds ? `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}` : '--:--'
}

function formatDate(value) {
  if (!value) return ''
  return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric' }).format(new Date(value))
}

function playlistTypeLabel(type) {
  return type === 'RECOMMENDED' ? '智能推荐' : '自建歌单'
}

function formatCompactCount(value) {
  const count = Math.max(0, Number(value) || 0)
  if (count >= 100_000_000) return `${(count / 100_000_000).toFixed(count >= 1_000_000_000 ? 0 : 1)}亿`
  if (count >= 10_000) return `${(count / 10_000).toFixed(count >= 1_000_000 ? 0 : 1)}万`
  return String(count)
}
</script>

<template>
  <main class="music-app">
    <aside class="music-nav">
      <RouterLink class="music-brand" to="/music"><span><Disc3 :size="22" /></span><div><strong>Sonora</strong><small>MUSIC SPACE</small></div></RouterLink>
      <nav>
        <RouterLink to="/music" :class="{ active: isHome }"><Home :size="18" /> 为你推荐</RouterLink>
        <button v-for="item in systemPlaylists" :key="item.id" :class="{ active: activePlaylistId === item.id }" @click="openPlaylist(item.id)">
          <Heart v-if="item.type === 'FAVORITES'" :size="18" /><Clock3 v-else :size="18" />{{ item.name }}
          <small>{{ item.trackCount }}</small>
        </button>
      </nav>
      <section class="nav-library">
        <header class="library-heading">
          <div><span>我的歌单</span><small>{{ personalPlaylists.length }}</small></div>
          <button class="library-add" type="button" title="创建歌单" aria-label="创建歌单" :aria-expanded="createOpen" @click="createOpen = !createOpen"><Plus :size="17" /></button>
        </header>

        <form v-if="createOpen" class="library-create" @submit.prevent="createPlaylist">
          <div class="library-create-title"><span><Plus :size="15" /></span><div><strong>创建新歌单</strong><small>稍后可以继续添加歌曲</small></div></div>
          <input v-model="createName" maxlength="120" autofocus aria-label="新歌单名称" placeholder="给歌单起个名字" />
          <div class="library-create-actions"><button type="button" @click="createOpen = false; createName = ''">取消</button><button type="submit" :disabled="!createName.trim()">创建</button></div>
        </form>

        <div class="library-tools">
          <label class="library-search"><Search :size="14" /><input v-model="sidebarPlaylistQuery" type="search" maxlength="80" autocomplete="off" aria-label="搜索我的歌单" placeholder="搜索我的歌单" /></label>
          <div class="library-filters" role="tablist" aria-label="歌单类型筛选">
            <button v-for="filter in sidebarPlaylistFilters" :key="filter.type" type="button" role="tab" :aria-selected="sidebarPlaylistFilter === filter.type" :class="{ active: sidebarPlaylistFilter === filter.type }" @click="sidebarPlaylistFilter = filter.type">{{ filter.label }}</button>
          </div>
        </div>

        <div class="nav-playlist-scroll">
          <button v-for="item in visiblePersonalPlaylists" :key="item.id" class="nav-playlist" :class="{ active: activePlaylistId === item.id }" :aria-current="activePlaylistId === item.id ? 'page' : undefined" @click="openPlaylist(item.id)">
            <span class="nav-playlist-cover">
              <img v-if="item.coverUrl" :src="item.coverUrl" :alt="`${item.name} 封面`" />
              <span v-else><Sparkles v-if="item.type === 'RECOMMENDED'" :size="17" /><ListMusic v-else :size="17" /></span>
            </span>
            <span class="nav-playlist-copy"><strong>{{ item.name }}</strong><small>{{ item.trackCount }} 首 · {{ playlistTypeLabel(item.type) }}</small></span>
            <span class="nav-playlist-arrow" aria-hidden="true"><ChevronRight :size="15" /></span>
          </button>
          <div v-if="!personalPlaylists.length" class="library-empty"><span><ListMusic :size="20" /></span><strong>还没有个人歌单</strong><p>创建歌单，或让 Agent 为你生成一份专属推荐。</p><button type="button" @click="createOpen = true">创建第一个歌单</button></div>
          <div v-else-if="!visiblePersonalPlaylists.length" class="library-empty compact"><span><Search :size="18" /></span><strong>没有匹配的歌单</strong><p>换个关键词或筛选条件试试。</p><button type="button" @click="sidebarPlaylistQuery = ''; sidebarPlaylistFilter = 'ALL'">清除筛选</button></div>
        </div>
      </section>
      <div class="nav-bottom">
        <RouterLink to="/agent"><WandSparkles :size="17" /> 返回 Agent</RouterLink>
        <button @click="logout"><LogOut :size="17" /> 退出登录</button>
      </div>
    </aside>

    <section class="music-content">
      <header class="music-topbar">
        <button v-if="!isHome || isSearchView" class="round-button" title="返回音乐首页" @click="handleTopbarBack"><ArrowLeft :size="19" /></button>
        <div v-else class="topbar-label"><Sparkles :size="16" /> PERSONALIZED MUSIC</div>
        <form class="mood-search" role="search" @submit.prevent="performSearch(1)"><Search :size="17" /><input v-model="searchKeyword" maxlength="160" autocomplete="off" aria-label="搜索 QQ 音乐" placeholder="搜索 QQ 音乐中的歌曲、视频、专辑、歌单、歌词或歌手" /><button :disabled="!searchKeyword.trim() || searching || !conversationId">{{ searching ? '搜索中' : '搜索' }}</button></form>
        <div class="user-chip"><UserRound :size="16" /><span>{{ auth.user?.username || '音乐用户' }}</span></div>
      </header>

      <div v-if="loading" class="page-state"><Disc3 class="spin" :size="28" /> 正在准备你的音乐空间</div>
      <template v-else-if="isAlbumView">
        <section class="album-detail-page">
          <div v-if="albumOpening && !albumDetail" class="page-state"><Disc3 class="spin" :size="28" /> 正在加载专辑与歌曲</div>
          <template v-else-if="albumDetail">
            <header class="album-profile-hero">
              <img v-if="albumDetail.coverUrl" :src="albumDetail.coverUrl" :alt="albumDetail.name" /><span v-else><Disc3 :size="62" /></span>
              <div><small>QQ MUSIC ALBUM</small><h1>{{ albumDetail.name }}</h1><p>{{ albumDetail.artists?.join(' / ') || '未知歌手' }}</p><em>{{ albumDetail.publishDate || '发行日期未知' }} · {{ albumDetail.genre || '音乐专辑' }} · {{ albumDetail.trackCount }} 首</em><p class="album-description">{{ albumDetail.description || [albumDetail.language, albumDetail.company].filter(Boolean).join(' · ') || '来自 QQ 音乐的专辑。' }}</p><div class="artist-hero-actions"><button :disabled="!albumTracks.length" @click="playAlbumTrack(0)"><Play :size="17" fill="currentColor" />播放专辑</button><button v-if="albumDetail.artistMid" @click="openSearchArtist({ mid: albumDetail.artistMid })"><UserRound :size="16" />进入歌手主页</button></div></div>
            </header>
            <section class="album-track-section"><header><span>TRACK LIST</span><h2>专辑歌曲</h2></header><div class="album-track-table"><article v-for="(track,index) in albumTracks" :key="track.id"><button class="artist-track-play" @click="playAlbumTrack(index)"><Play :size="14" fill="currentColor" /><i>{{ index + 1 }}</i></button><button class="artist-track-title" @click="openAlbumTrack(index)"><img v-if="track.imageUrl" :src="track.imageUrl" alt="" /><span v-else><Music2 :size="18" /></span><div><strong>{{ track.name }}</strong><small>{{ track.artists?.join(' / ') }}</small></div></button><time>{{ formatDuration(track.durationMs) }}</time><MusicTrackActions :track="track" compact /></article></div></section>
          </template>
        </section>
      </template>

      <template v-else-if="isArtistView">
        <section class="artist-detail-page">
          <div v-if="artistOpening && !artistDetail" class="page-state"><Disc3 class="spin" :size="28" /> 正在加载歌手生涯资料</div>
          <template v-else-if="artistDetail">
            <header class="artist-profile-hero">
              <div class="artist-profile-photo"><img v-if="artistDetail.imageUrl" :src="artistDetail.imageUrl" :alt="artistDetail.name" /><UserRound v-else :size="72" /></div>
              <div class="artist-profile-copy">
                <span>QQ MUSIC ARTIST</span>
                <h1>{{ artistDetail.name }}</h1>
                <p class="artist-meta"><b v-if="artistDetail.foreignName">外文名：{{ artistDetail.foreignName }}</b><b v-if="artistDetail.area">地区：{{ artistDetail.area }}</b><b v-if="artistDetail.birthday">生日：{{ artistDetail.birthday }}</b></p>
                <p class="artist-intro">{{ artistDetail.description || 'QQ 音乐暂未提供这位歌手的详细介绍。' }}</p>
                <div class="artist-stat-row"><span><strong>{{ formatCompactCount(artistDetail.songTotal) }}</strong> 首歌曲</span><span><strong>{{ formatCompactCount(artistDetail.albumTotal) }}</strong> 张专辑</span></div>
                <div class="artist-hero-actions"><button :disabled="!artistTracks.length" @click="playArtistTopTracks"><Play :size="17" fill="currentColor" />播放热门歌曲</button><a :href="artistDetail.externalUrl" target="_blank" rel="noreferrer">在 QQ 音乐查看</a></div>
              </div>
            </header>

            <nav class="artist-tabs" aria-label="歌手内容分类">
              <button :class="{ active: artistTab === 'SONGS' }" @click="artistTab = 'SONGS'">热门歌曲 <small>{{ artistDetail.songTotal }}</small></button>
              <button :class="{ active: artistTab === 'ALBUMS' }" @click="artistTab = 'ALBUMS'">专辑 <small>{{ artistDetail.albumTotal }}</small></button>
              <button :class="{ active: artistTab === 'BIO' }" @click="artistTab = 'BIO'">歌手资料</button>
            </nav>

            <section v-if="artistTab === 'SONGS'" class="artist-song-section">
              <header><div><span>POPULAR TRACKS</span><h2>热门歌曲</h2></div><small>第 {{ artistDetail.songPage }} 页</small></header>
              <div class="artist-track-table">
                <div class="artist-track-head"><span>#</span><span>歌曲</span><span>专辑</span><span>时长</span><span>操作</span></div>
                <article v-for="(track, index) in artistTracks" :key="track.id" :class="{ playing: music.currentTrack?.id === track.id && music.currentTrack?.provider === track.provider }">
                  <button class="artist-track-play" :title="`播放 ${track.name}`" @click="playArtistTrack(index)"><Play :size="14" fill="currentColor" /><i>{{ (artistDetail.songPage - 1) * artistDetail.songPageSize + index + 1 }}</i></button>
                  <button class="artist-track-title" :title="`打开 ${track.name} 歌词页`" @click="openArtistTrack(index)"><img v-if="track.imageUrl" :src="track.imageUrl" alt="" /><span v-else><Music2 :size="18" /></span><div><strong>{{ track.name }}</strong><small>{{ track.artists?.join(' / ') }}</small></div></button>
                  <span class="artist-track-album">{{ track.album || '—' }}</span><time>{{ formatDuration(track.durationMs) }}</time><MusicTrackActions :track="track" compact />
                </article>
              </div>
              <nav v-if="artistDetail.songPage > 1 || artistDetail.hasMoreSongs" class="search-pagination"><button :disabled="artistOpening || artistDetail.songPage <= 1" @click="loadArtistDetail({ songPage: artistDetail.songPage - 1 })">上一页</button><span>第 {{ artistDetail.songPage }} 页</span><button :disabled="artistOpening || !artistDetail.hasMoreSongs" @click="loadArtistDetail({ songPage: artistDetail.songPage + 1 })">下一页</button></nav>
            </section>

            <section v-else-if="artistTab === 'ALBUMS'" class="artist-album-section">
              <header><div><span>DISCOGRAPHY</span><h2>专辑生涯</h2></div><small>第 {{ artistDetail.albumPage }} 页</small></header>
              <div class="artist-album-grid"><button v-for="album in artistDetail.albums" :key="album.mid" @click="openSearchAlbum(album)"><img v-if="album.coverUrl" :src="album.coverUrl" :alt="album.name" /><span v-else><Disc3 :size="30" /></span><strong>{{ album.name }}</strong><small>{{ album.publishDate || '发行日期未知' }}</small><em>{{ album.type || '音乐专辑' }}<template v-if="album.trackCount"> · {{ album.trackCount }} 首</template></em></button></div>
              <nav v-if="artistDetail.albumPage > 1 || artistDetail.hasMoreAlbums" class="search-pagination"><button :disabled="artistOpening || artistDetail.albumPage <= 1" @click="loadArtistDetail({ albumPage: artistDetail.albumPage - 1 })">上一页</button><span>第 {{ artistDetail.albumPage }} 页</span><button :disabled="artistOpening || !artistDetail.hasMoreAlbums" @click="loadArtistDetail({ albumPage: artistDetail.albumPage + 1 })">下一页</button></nav>
            </section>

            <section v-else class="artist-biography"><span>ABOUT THE ARTIST</span><h2>关于 {{ artistDetail.name }}</h2><p>{{ artistDetail.description || 'QQ 音乐暂未提供这位歌手的详细介绍。' }}</p><dl><div v-if="artistDetail.foreignName"><dt>外文名</dt><dd>{{ artistDetail.foreignName }}</dd></div><div v-if="artistDetail.area"><dt>地区</dt><dd>{{ artistDetail.area }}</dd></div><div v-if="artistDetail.birthday"><dt>生日</dt><dd>{{ artistDetail.birthday }}</dd></div></dl></section>
          </template>
        </section>
      </template>
      <template v-else-if="isSearchView">
        <section class="catalog-search-page">
          <header class="search-summary">
            <div><span>SEARCH ON QQ MUSIC</span><h1>搜索“{{ searchResult.keyword }}”</h1><p>结果直接来自 QQ 音乐，支持歌曲、视频、专辑、歌单、歌词、歌手和用户分类。</p></div>
            <small>找到 {{ formatCompactCount(searchResult.total) }} 条结果 · QQ 音乐</small>
          </header>

          <nav class="search-tabs" aria-label="搜索分类">
            <button v-for="tab in searchTabs" :key="tab.type" :class="{ active: searchType === tab.type }" :disabled="searching" @click="changeSearchType(tab.type)">{{ tab.label }}</button>
          </nav>

          <section v-if="searchType === 'TRACK'" class="search-result-section track-search-section">
            <header><h2>歌曲</h2><span>{{ activeSearchTracks.length }} 首可播放结果</span></header>
            <div v-if="activeSearchTracks.length" class="search-track-list">
              <article v-for="(track, index) in activeSearchTracks" :key="`${track.provider}:${track.id}`" :class="{ playing: music.currentTrack?.id === track.id && music.currentTrack?.provider === track.provider }">
                <button class="search-track-play" :title="`播放 ${track.name}`" @click="playSearchTrack(index)"><Play :size="15" fill="currentColor" /></button>
                <button class="search-track-main" :title="`打开 ${track.name} 歌词页`" @click="openSearchTrack(index)"><img v-if="track.imageUrl" :src="track.imageUrl" alt="" /><span v-else><Music2 :size="18" /></span><div><strong>{{ track.name }}</strong><small>{{ track.artists?.join(' / ') }}</small></div></button>
                <span class="search-track-album">{{ track.album || '—' }}</span><span class="search-track-provider">{{ track.provider }}</span><time>{{ formatDuration(track.durationMs) }}</time><MusicTrackActions :track="track" compact />
              </article>
            </div>
            <p v-else class="search-category-empty">没有找到可播放单曲，试试更短的关键词或检查曲库状态</p>
          </section>

          <section v-else-if="searchType === 'VIDEO'" class="search-result-section">
            <div v-if="searchResult.videos.length" class="qq-video-grid">
              <button v-for="video in searchResult.videos" :key="video.id" @click="openSearchVideo(video)">
                <span><img v-if="video.coverUrl" :src="video.coverUrl" :alt="`${video.name} 封面`" /><i><Play :size="22" fill="currentColor" /></i><time>{{ formatDuration(video.durationMs) }}</time></span>
                <strong>{{ video.name }}</strong><small>{{ video.artists?.join(' / ') }} · {{ formatCompactCount(video.playCount) }} 次播放</small>
              </button>
            </div>
            <p v-else class="search-category-empty">没有找到相关视频</p>
          </section>

          <section v-else-if="searchType === 'ALBUM'" class="search-result-section">
            <div v-if="searchResult.albums.length" class="qq-entity-list">
              <button v-for="album in searchResult.albums" :key="album.id" @click="openSearchAlbum(album)">
                <img v-if="album.coverUrl" :src="album.coverUrl" :alt="`${album.name} 封面`" /><span v-else><Disc3 :size="25" /></span>
                <strong>{{ album.name }}</strong><small>{{ album.artists?.join(' / ') || '未知歌手' }}</small><time>{{ album.publishDate || '—' }}</time><em>{{ album.trackCount }} 首</em>
              </button>
            </div>
            <p v-else class="search-category-empty">没有找到相关专辑</p>
          </section>

          <section v-else-if="searchType === 'PLAYLIST'" class="search-result-section">
            <div v-if="searchResult.playlists.length" class="qq-search-playlist-list">
              <button v-for="playlist in searchResult.playlists" :key="playlist.id" @click="openSearchPlaylist(playlist)">
                <img v-if="playlist.coverUrl" :src="playlist.coverUrl" :alt="`${playlist.name} 封面`" /><span v-else><ListMusic :size="25" /></span>
                <strong>{{ playlist.name }}</strong><small>{{ playlist.creatorName }}</small><em>{{ formatCompactCount(playlist.listenCount) }} 次播放</em><time>{{ playlist.trackCount }} 首</time>
              </button>
            </div>
            <p v-else class="search-category-empty">没有找到相关歌单</p>
          </section>

          <section v-else-if="searchType === 'LYRIC'" class="search-result-section">
            <div v-if="searchResult.lyrics.length" class="qq-lyric-list">
              <button v-for="item in searchResult.lyrics" :key="item.track.id" @click="openLyricResult(item)">
                <div><strong>{{ item.track.name }}</strong><small>{{ item.track.artists?.join(' / ') }} · {{ item.track.album || '未知专辑' }}</small><p>{{ item.snippet || '点击查看完整歌词' }}</p></div>
                <span>查看歌词</span><time>{{ formatDuration(item.track.durationMs) }}</time>
              </button>
            </div>
            <p v-else class="search-category-empty">没有找到包含该关键词的歌词</p>
          </section>

          <section v-else-if="searchType === 'ARTIST'" class="search-result-section">
            <div v-if="searchResult.artists.length" class="qq-artist-grid">
              <button v-for="artist in searchResult.artists" :key="artist.id" :title="`进入 ${artist.name} 的歌手主页`" @click="openSearchArtist(artist)">
                <img v-if="artist.imageUrl" :src="artist.imageUrl" :alt="artist.name" /><span v-else><UserRound :size="35" /></span>
                <strong>{{ artist.name }}</strong><small>单曲 {{ artist.songCount }} · 专辑 {{ artist.albumCount }}</small>
              </button>
            </div>
            <p v-else class="search-category-empty">没有找到相关歌手</p>
          </section>

          <section v-else class="search-result-section">
            <div v-if="searchResult.users.length" class="qq-user-list">
              <a v-for="user in searchResult.users" :key="user.id" :href="user.externalUrl" target="_blank" rel="noreferrer">
                <img v-if="user.avatarUrl" :src="user.avatarUrl" :alt="user.name" /><span v-else><UserRound :size="25" /></span>
                <strong>{{ user.name }}</strong><small>{{ user.badge || 'QQ 音乐用户' }}</small><em>{{ formatCompactCount(user.followerCount) }} 粉丝</em><time>{{ user.playlistCount }} 个歌单</time>
              </a>
            </div>
            <p v-else class="search-category-empty">没有找到相关用户</p>
          </section>

          <nav v-if="searchResult.page > 1 || searchResult.hasNext" class="search-pagination">
            <button :disabled="searching || searchResult.page <= 1" @click="performSearch(searchResult.page - 1)">上一页</button><span>第 {{ searchResult.page }} 页</span><button :disabled="searching || !searchResult.hasNext" @click="performSearch(searchResult.page + 1)">下一页</button>
          </nav>
        </section>
      </template>
      <template v-else-if="isHome">
        <section class="music-hero" :class="[`is-${greetingState.theme}`, { 'is-holiday': greetingState.isHoliday }]">
          <div class="hero-copy"><div class="hero-kicker"><span>{{ greetingState.eyebrow }}</span><small>{{ greetingState.status }}</small></div><h1>{{ greetingState.salutation }}，{{ auth.user?.username || '音乐用户' }}</h1><p>{{ greetingState.message }}</p></div>
          <button class="hero-play" :disabled="!personalPlaylists.length" @click="personalPlaylists[0] && openPlaylist(personalPlaylists[0].id)"><Play :size="22" fill="currentColor" />播放最近生成</button>
        </section>

        <section class="content-section">
          <header><div><span class="eyebrow">MADE FOR YOU</span><h2>今天为你推荐</h2></div><small>结合当前场景与长期偏好</small></header>
          <div class="mix-grid">
            <button v-for="mix in mixes" :key="mix.key" class="mix-card" :class="mix.color" :disabled="Boolean(generatingKey)" :title="`随机播放“${mix.name}”`" @click="playRandomMix(mix)">
              <span>{{ mix.eyebrow }}</span><strong>{{ mix.name }}</strong><p>{{ mix.description }}</p>
              <i><Disc3 v-if="generatingKey === mix.key" class="spin" :size="21" /><Play v-else :size="19" fill="currentColor" /></i>
            </button>
          </div>
        </section>

        <section class="content-section qq-discovery-section">
          <header><div><span class="eyebrow">DISCOVER ON QQ MUSIC</span><h2>大家正在听的歌单</h2></div><button class="section-action" :disabled="qqHomeLoading" @click="loadNextQqHome"><Disc3 :class="{ spin: qqHomeLoading }" :size="15" />换一批</button></header>
          <p class="qq-discovery-copy">来自 QQ 音乐公开歌单广场，由其他用户创建；点击即可查看真实曲目并加入全局播放器。</p>
          <div v-if="qqHomeLoading && !qqHomePlaylists.length" class="qq-playlist-grid" aria-label="正在加载 QQ 音乐公开歌单">
            <div v-for="index in 6" :key="index" class="qq-playlist-skeleton"><span></span><i></i><i></i></div>
          </div>
          <div v-else-if="qqHomePlaylists.length" class="qq-playlist-grid">
            <button v-for="playlist in qqHomePlaylists" :key="playlist.id" class="qq-playlist-card" @click="openQqPlaylist(playlist.id)">
              <span class="qq-playlist-cover"><img :src="playlist.coverUrl" :alt="`${playlist.name} 封面`" /><i><Play :size="17" fill="currentColor" /></i><small><Disc3 :size="11" />{{ formatCompactCount(playlist.listenCount) }}</small></span>
              <strong>{{ playlist.name }}</strong>
              <span>由 {{ playlist.creatorName || 'QQ 音乐用户' }} 创建</span>
            </button>
          </div>
          <div v-else class="qq-discovery-empty"><ListMusic :size="24" /><span>{{ qqHomeError || '暂时没有加载到公开歌单' }}</span><button @click="loadQqHome(1)">重新加载</button></div>
        </section>

        <section class="content-section">
          <header><div><span class="eyebrow">YOUR COLLECTION</span><h2>歌单宝藏库</h2></div><button class="section-action" @click="createOpen = true"><Plus :size="15" />新建歌单</button></header>
          <div v-if="playlists.length" class="playlist-grid">
            <button v-for="item in playlists" :key="item.id" class="playlist-card" @click="openPlaylist(item.id)">
              <div class="playlist-cover"><img v-if="item.coverUrl" :src="item.coverUrl" :alt="`${item.name} 封面`" /><span v-else><Music2 :size="34" /></span><i><Play :size="18" fill="currentColor" /></i></div>
              <strong>{{ item.name }}</strong><span>{{ item.trackCount }} 首 · {{ item.type === 'RECOMMENDED' ? '智能推荐' : item.type === 'CUSTOM' ? '自建歌单' : '自动更新' }}</span>
            </button>
          </div>
        </section>
      </template>

      <section v-else class="playlist-detail">
        <div v-if="opening" class="page-state"><Disc3 class="spin" :size="28" /> 正在打开歌单并准备可信播放记录</div>
        <template v-else-if="detail">
          <header class="playlist-hero">
            <div class="detail-cover"><img v-if="detail.playlist.coverUrl" :src="detail.playlist.coverUrl" alt="" /><Music2 v-else :size="48" /></div>
            <div class="detail-copy"><span>{{ detail.playlist.type === 'QQ_PUBLIC' ? `QQ 音乐公开歌单 · ${detail.playlist.creatorName || '音乐用户'}` : detail.playlist.type === 'RECOMMENDED' ? '智能推荐歌单' : detail.playlist.type === 'CUSTOM' ? '我的歌单' : '自动歌单' }}</span><h1>{{ detail.playlist.name }}</h1><p>{{ detail.playlist.description || '把喜欢的声音留在这里。' }}</p><small>{{ detail.playlist.trackCount }} 首歌曲 · {{ detail.playlist.type === 'QQ_PUBLIC' ? `${formatCompactCount(detail.playlist.listenCount)} 次播放` : `${detail.policyVersion} · ${detail.personalizationStatus}` }}</small></div>
          </header>
          <div class="detail-actions"><button class="primary-play" :disabled="!detailTracks.length" @click="playAll"><Play :size="18" fill="currentColor" />播放全部</button><button v-if="detail.playlist.type === 'QQ_PUBLIC'" @click="playShuffledDetail"><Disc3 :size="16" />随机播放</button><a v-if="detail.playlist.type === 'QQ_PUBLIC' && detail.playlist.externalUrl" class="qq-external-link" :href="detail.playlist.externalUrl" target="_blank" rel="noreferrer">在 QQ 音乐查看</a><button v-if="detail.playlist.editable" @click="renaming = !renaming">编辑信息</button><button v-if="detail.playlist.editable" class="danger-button" title="删除歌单" @click="deletePlaylist"><Trash2 :size="16" /></button></div>
          <form v-if="renaming" class="rename-form" @submit.prevent="savePlaylistEdit"><input v-model="editName" maxlength="120" /><input v-model="editDescription" maxlength="500" placeholder="歌单简介" /><button>保存</button></form>
          <div class="track-table">
            <div class="track-table-head"><span>#</span><span>歌曲</span><span>专辑</span><span>来源</span><span>时长</span><span></span></div>
            <div v-for="(item, index) in detail.tracks" :key="item.playlistTrackId" class="library-track" :class="{ playing: music.currentTrack?.id === item.track.id && music.currentTrack?.provider === item.track.provider }" @dblclick="playAt(index)">
              <span class="track-number"><i>{{ index + 1 }}</i><Play :size="14" fill="currentColor" @click.stop="playAt(index)" /></span>
              <button class="track-title" title="打开歌曲歌词页" @click.stop="openTrack(index)"><img v-if="item.track.imageUrl" :src="item.track.imageUrl" alt="" /><span><strong>{{ item.track.name }}</strong><small>{{ item.track.artists?.join(' / ') }}</small></span></button>
              <span class="track-album">{{ item.track.album || '—' }}</span><span class="track-source">{{ item.track.provider }}</span><span>{{ formatDuration(item.track.durationMs) }}</span>
              <span class="library-track-actions"><MusicTrackActions :track="item.track" compact /><button v-if="detail.playlist.editable" class="remove-track" title="移出歌单" @click.stop="removeTrack(item)"><X :size="15" /></button></span>
            </div>
            <div v-if="!detail.tracks.length" class="empty-playlist"><Library :size="30" /><strong>这个歌单还是空的</strong><p>可以在 Agent 音乐推荐面板中生成歌曲，然后保存整张推荐歌单。</p><RouterLink to="/agent">前往 Agent 推荐</RouterLink></div>
          </div>
        </template>
      </section>
      <p v-if="errorMessage" class="library-error">{{ errorMessage }}<button @click="errorMessage = ''">×</button></p>
    </section>
  </main>
</template>

<style scoped>
.music-app{--music-bg:#101018;--music-panel:#171721;--music-text:#f4f3f7;display:grid;height:100vh;grid-template-columns:256px minmax(0,1fr);overflow:hidden;background:radial-gradient(circle at 73% -10%,rgba(120,80,200,.13),transparent 34%),var(--music-bg);color:var(--music-text)}.music-nav{display:flex;min-height:0;flex-direction:column;border-right:1px solid rgba(255,255,255,.07);padding:24px 16px 126px;background:rgba(13,13,20,.94)}.music-brand{display:flex;align-items:center;gap:11px;padding:0 8px 24px;color:white;text-decoration:none}.music-brand>span{display:grid;width:40px;height:40px;place-items:center;border-radius:13px;background:#b8ff54;color:#15180e}.music-brand div{display:grid}.music-brand strong{font-size:18px}.music-brand small{margin-top:2px;color:#6d7180;font-size:8px;letter-spacing:.16em}.music-nav nav{display:grid;gap:5px}.music-nav nav a,.music-nav nav button,.nav-bottom a,.nav-bottom button{display:flex;width:100%;align-items:center;gap:11px;border:0;border-radius:11px;padding:11px 12px;background:transparent;color:#9296a4;font-size:13px;text-decoration:none}.music-nav nav a:hover,.music-nav nav button:hover,.music-nav nav .active,.nav-bottom a:hover,.nav-bottom button:hover{background:rgba(255,255,255,.065);color:white}.music-nav nav small{margin-left:auto;color:#626775}.nav-library{min-height:0;margin-top:26px;overflow:auto}.nav-library>header{display:flex;align-items:center;justify-content:space-between;padding:0 9px 9px;color:#696e7c;font-size:10px;font-weight:800;letter-spacing:.12em;text-transform:uppercase}.nav-library>header button{display:grid;place-items:center;border:0;background:none;color:#8b909e}.nav-library form{display:flex;gap:5px;margin-bottom:8px}.nav-library form input{min-width:0;border:1px solid rgba(255,255,255,.1);border-radius:8px;padding:7px 8px;background:#20202a;color:white;font-size:11px}.nav-library form button{border:0;border-radius:8px;background:#b8ff54;color:#15180e;font-size:10px;font-weight:700}.nav-playlist{display:grid!important;grid-template-columns:34px minmax(0,1fr);gap:9px!important;text-align:left}.nav-playlist img,.nav-playlist>span{display:grid;width:34px;height:34px;place-items:center;border-radius:8px;object-fit:cover;background:#242431}.nav-playlist i{display:grid;min-width:0;color:#b7bac4;font-size:11px;font-style:normal}.nav-playlist i small{margin-top:4px;color:#626775;font-size:9px}.nav-library>p{padding:0 9px;color:#565b68;font-size:10px;line-height:1.5}.nav-bottom{display:grid;gap:4px;margin-top:auto}.music-content{min-width:0;overflow-y:auto;padding:0 42px 148px}.music-topbar{position:sticky;z-index:15;top:0;display:grid;grid-template-columns:auto minmax(300px,620px) auto;align-items:center;gap:22px;min-height:84px;background:linear-gradient(#101018 55%,transparent);backdrop-filter:blur(12px)}.round-button{display:grid;width:38px;height:38px;place-items:center;border:0;border-radius:50%;background:#252532;color:#d5d6de}.topbar-label{display:flex;align-items:center;gap:8px;color:#7d8291;font-size:10px;font-weight:800;letter-spacing:.15em}.mood-search{display:grid;grid-template-columns:20px minmax(0,1fr) auto;align-items:center;gap:8px;border:1px solid rgba(255,255,255,.09);border-radius:15px;padding:6px 7px 6px 14px;background:#1d1d28;color:#747987}.mood-search input{height:34px;border:0;outline:0;background:transparent;color:white}.mood-search button{height:34px;border:0;border-radius:10px;padding:0 13px;background:#b8ff54;color:#15180e;font-size:11px;font-weight:750}.mood-search button:disabled{opacity:.45}.user-chip{display:flex;align-items:center;gap:8px;justify-self:end;color:#a9acb7;font-size:12px}.music-hero{display:flex;align-items:flex-end;justify-content:space-between;min-height:230px;border:1px solid rgba(255,255,255,.07);border-radius:26px;padding:38px;background:radial-gradient(circle at 78% 28%,rgba(184,255,84,.19),transparent 25%),linear-gradient(130deg,#211a37,#171825 57%,#162522)}.music-hero span,.eyebrow{color:#a69bd5;font-size:9px;font-weight:850;letter-spacing:.17em}.music-hero h1{margin:12px 0 9px;font-size:36px;letter-spacing:-.045em}.music-hero p{margin:0;color:#a6a8b2;font-size:13px}.hero-play,.primary-play{display:flex;align-items:center;gap:8px;border:0;border-radius:999px;padding:13px 19px;background:#b8ff54;color:#12150c;font-size:12px;font-weight:800}.content-section{margin-top:38px}.content-section>header{display:flex;align-items:end;justify-content:space-between;margin-bottom:17px}.content-section h2{margin:7px 0 0;font-size:22px}.content-section header>small{color:#6f7482;font-size:11px}.mix-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:15px}.mix-card{position:relative;display:flex;min-height:210px;flex-direction:column;align-items:flex-start;overflow:hidden;border:1px solid rgba(255,255,255,.08);border-radius:20px;padding:23px;color:#15151b;text-align:left;transition:transform .18s,border-color .18s}.mix-card:hover{transform:translateY(-3px);border-color:rgba(255,255,255,.2)}.mix-card:disabled{cursor:wait}.mix-card:after{position:absolute;right:-36px;bottom:-57px;width:160px;height:160px;border:28px solid rgba(255,255,255,.18);border-radius:50%;content:""}.mix-card.lime{background:linear-gradient(145deg,#dfff9e,#9ed985)}.mix-card.violet{background:linear-gradient(145deg,#d2c5ff,#9287d5)}.mix-card.peach{background:linear-gradient(145deg,#ffd7bd,#df9f91)}.mix-card.blue{background:linear-gradient(145deg,#b8e3ff,#79aacb)}.mix-card>span{font-size:9px;font-weight:850;letter-spacing:.14em}.mix-card strong{margin-top:42px;font-size:24px;letter-spacing:-.04em}.mix-card p{max-width:88%;margin:8px 0 0;font-size:11px;line-height:1.55}.mix-card i{position:absolute;z-index:2;right:19px;bottom:18px;display:grid;width:40px;height:40px;place-items:center;border-radius:50%;background:#171820;color:#b8ff54}.section-action,.detail-actions>button:not(.primary-play),.rename-form button{display:flex;align-items:center;gap:6px;border:1px solid rgba(255,255,255,.1);border-radius:10px;padding:8px 11px;background:rgba(255,255,255,.04);color:#b9bcc7;font-size:11px}.playlist-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:20px}.playlist-card{min-width:0;border:0;background:transparent;color:white;text-align:left}.playlist-cover{position:relative;aspect-ratio:1;overflow:hidden;border-radius:17px;background:linear-gradient(145deg,#292634,#171823)}.playlist-cover img{width:100%;height:100%;object-fit:cover;transition:transform .25s}.playlist-card:hover img{transform:scale(1.035)}.playlist-cover>span{display:grid;width:100%;height:100%;place-items:center;color:#757080}.playlist-cover i{position:absolute;right:12px;bottom:12px;display:grid;width:42px;height:42px;place-items:center;border-radius:50%;background:#b8ff54;color:#14160e;opacity:0;transform:translateY(6px);transition:.18s}.playlist-card:hover .playlist-cover i{opacity:1;transform:none}.playlist-card>strong{display:block;overflow:hidden;margin-top:11px;text-overflow:ellipsis;white-space:nowrap;font-size:13px}.playlist-card>span{display:block;margin-top:5px;color:#6f7480;font-size:10px}.playlist-detail{padding-top:18px}.playlist-hero{display:flex;align-items:flex-end;gap:28px;min-height:285px;padding:36px;border-radius:25px;background:linear-gradient(125deg,rgba(110,91,178,.48),rgba(26,27,38,.65))}.detail-cover{display:grid;width:210px;height:210px;flex:0 0 210px;place-items:center;overflow:hidden;border-radius:20px;background:#252431;color:#777185;box-shadow:0 22px 60px rgba(0,0,0,.34)}.detail-cover img{width:100%;height:100%;object-fit:cover}.detail-copy>span{color:#bab2dd;font-size:10px;font-weight:800;letter-spacing:.12em}.detail-copy h1{margin:12px 0 10px;font-size:44px;letter-spacing:-.055em}.detail-copy p{max-width:620px;margin:0;color:#b1b2bc;font-size:13px;line-height:1.6}.detail-copy small{display:block;margin-top:18px;color:#7f8390;font-size:10px}.detail-actions{display:flex;align-items:center;gap:10px;padding:24px 2px}.detail-actions .danger-button{margin-left:auto;color:#df8b89}.rename-form{display:grid;grid-template-columns:240px 1fr auto;gap:9px;margin-bottom:20px}.rename-form input{height:40px;border:1px solid rgba(255,255,255,.1);border-radius:10px;padding:0 11px;background:#1a1a24;color:white}.track-table{border-top:1px solid rgba(255,255,255,.08)}.track-table-head,.library-track{display:grid;grid-template-columns:42px minmax(240px,1.4fr) minmax(130px,.8fr) 90px 65px 38px;align-items:center;gap:12px}.track-table-head{padding:13px 10px;color:#646977;font-size:9px;text-transform:uppercase}.library-track{width:100%;border:0;border-radius:10px;padding:8px 10px;background:transparent;color:#979ba8;text-align:left}.library-track:hover,.library-track.playing{background:rgba(255,255,255,.055)}.track-number{display:grid;place-items:center}.track-number svg{display:none;color:#b8ff54}.library-track:hover .track-number i,.library-track.playing .track-number i{display:none}.library-track:hover .track-number svg,.library-track.playing .track-number svg{display:block}.track-number i{font-size:10px;font-style:normal}.track-title{display:flex;min-width:0;align-items:center;gap:11px}.track-title img{width:43px;height:43px;flex:0 0 43px;border-radius:8px;object-fit:cover}.track-title>span{display:grid;min-width:0;gap:4px}.track-title strong,.track-title small,.track-album{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.track-title strong{color:#e9e9ee;font-size:12px}.track-title small{font-size:10px}.track-album,.track-source,.library-track>span{font-size:10px}.track-source{text-transform:uppercase}.remove-track{display:grid;width:30px;height:30px;place-items:center;border:0;border-radius:8px;background:transparent;color:#747987}.remove-track:hover{background:rgba(255,255,255,.08);color:white}.empty-playlist{display:grid;place-items:center;padding:70px 20px;color:#737887}.empty-playlist strong{margin-top:12px;color:#c8cad2}.empty-playlist p{margin:7px 0 14px;font-size:11px}.empty-playlist a{color:#b8ff54;font-size:11px}.page-state{display:flex;min-height:420px;align-items:center;justify-content:center;gap:12px;color:#858a98;font-size:12px}.library-error{position:fixed;z-index:60;right:24px;top:92px;display:flex;align-items:center;gap:12px;border:1px solid rgba(255,139,130,.3);border-radius:12px;padding:11px 14px;background:#2b1b22;color:#ffb2aa;font-size:11px}.library-error button{border:0;background:none;color:inherit}.spin{animation:music-spin 1s linear infinite}@keyframes music-spin{to{transform:rotate(360deg)}}@media(max-width:1200px){.mix-grid{grid-template-columns:repeat(2,1fr)}.playlist-grid{grid-template-columns:repeat(4,1fr)}}@media(max-width:900px){.music-app{display:block;height:auto;min-height:100vh}.music-nav{display:none}.music-content{min-height:100vh;padding:0 18px 142px}.music-topbar{grid-template-columns:auto 1fr}.user-chip{display:none}.mix-grid{grid-template-columns:1fr 1fr}.playlist-grid{grid-template-columns:repeat(2,1fr)}.music-hero{min-height:210px;padding:25px}.music-hero h1{font-size:29px}.hero-play{display:none}.playlist-hero{align-items:center;min-height:220px;padding:23px}.detail-cover{width:130px;height:130px;flex-basis:130px}.detail-copy h1{font-size:30px}.track-table-head,.library-track{grid-template-columns:30px minmax(180px,1fr) 55px 32px}.track-table-head span:nth-child(3),.track-table-head span:nth-child(4),.library-track>.track-album,.library-track>.track-source{display:none}}
</style>

<style scoped>
.album-detail-page{padding:18px 0 38px}.album-profile-hero{display:grid;grid-template-columns:260px minmax(0,1fr);align-items:center;gap:42px;min-height:350px;border:1px solid rgba(255,255,255,.08);border-radius:28px;padding:38px 48px;background:radial-gradient(circle at 10% 35%,rgba(184,255,84,.13),transparent 27%),linear-gradient(135deg,#28203e,#191a28 64%,#151c20)}.album-profile-hero>img,.album-profile-hero>span{display:grid;width:260px;height:260px;place-items:center;border-radius:22px;object-fit:cover;background:#252531;color:#7f8490;box-shadow:0 28px 70px rgba(0,0,0,.4)}.album-profile-hero small,.album-track-section>header>span{color:#b8ff54;font-size:9px;font-weight:850;letter-spacing:.17em}.album-profile-hero h1{margin:10px 0 8px;font-size:44px;line-height:1.08;letter-spacing:-.05em}.album-profile-hero p{margin:0;color:#c0c2ca;font-size:13px}.album-profile-hero em{display:block;margin-top:12px;color:#777c89;font-size:10px;font-style:normal}.album-profile-hero .album-description{max-width:720px;margin-top:17px;color:#8f939f;font-size:11px;line-height:1.7}.album-track-section{margin-top:30px}.album-track-section h2{margin:7px 0 15px;font-size:24px}.album-track-table{border-top:1px solid rgba(255,255,255,.08)}.album-track-table article{display:grid;grid-template-columns:44px minmax(240px,1fr) 60px 70px;align-items:center;gap:12px;border-radius:11px;padding:8px 10px;color:#858a97}.album-track-table article:hover{background:rgba(255,255,255,.055)}
.artist-album-grid button{min-width:0;border:0;padding:0;background:transparent;color:#ecedf1;text-align:left}.artist-album-grid img,.artist-album-grid button>span{display:grid;width:100%;aspect-ratio:1;place-items:center;border-radius:16px;object-fit:cover;background:#242430;color:#797e8d;transition:transform .2s}.artist-album-grid button:hover img,.artist-album-grid button:hover>span{transform:translateY(-3px)}.artist-album-grid button strong,.artist-album-grid button small,.artist-album-grid button em{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.artist-album-grid button strong{margin-top:11px;font-size:12px}.artist-album-grid button small{margin-top:5px;color:#757a87;font-size:9px}.artist-album-grid button em{margin-top:4px;color:#5d626f;font-size:9px;font-style:normal}.qq-video-grid>button{min-width:0;border:0;padding:0;background:transparent;color:#eceef2;text-align:left}.qq-video-grid>button>span:first-child{position:relative;display:block;aspect-ratio:16/9;overflow:hidden;border-radius:15px;background:#20212b}.qq-video-grid>button:hover img{transform:scale(1.035)}.qq-entity-list>button{display:grid;width:100%;grid-template-columns:62px minmax(0,1fr) minmax(140px,.6fr) 90px 60px;align-items:center;gap:14px;border:0;border-radius:13px;padding:9px;background:transparent;color:#dfe1e7;text-align:left}.qq-entity-list>button:hover{background:rgba(255,255,255,.05)}
@media(max-width:900px){.album-profile-hero{grid-template-columns:1fr;justify-items:center;padding:26px;text-align:center}.album-profile-hero>img,.album-profile-hero>span{width:180px;height:180px}.album-profile-hero h1{font-size:32px}.album-track-table article{grid-template-columns:36px minmax(170px,1fr) 48px 65px}}
</style>

<style scoped>
.artist-track-head,.artist-track-table article{grid-template-columns:46px minmax(240px,1.4fr) minmax(130px,.8fr) 55px 70px}.search-track-list article{grid-template-columns:36px minmax(220px,1.4fr) minmax(120px,.8fr) 60px 50px 70px}.track-table-head,.library-track{grid-template-columns:42px minmax(220px,1.4fr) minmax(120px,.8fr) 70px 55px 86px}.library-track-actions{display:flex!important;align-items:center;justify-content:flex-end;gap:2px}
@media(max-width:900px){.artist-track-head,.artist-track-table article{grid-template-columns:36px minmax(180px,1fr) 50px 65px}.search-track-list article{grid-template-columns:28px minmax(180px,1fr) 48px 65px}.track-table-head,.library-track{grid-template-columns:30px minmax(170px,1fr) 50px 70px}}
</style>

<style scoped>
.artist-detail-page{padding:18px 0 34px}.artist-profile-hero{display:grid;grid-template-columns:250px minmax(0,1fr);align-items:center;gap:42px;min-height:350px;overflow:hidden;border:1px solid rgba(255,255,255,.08);border-radius:28px;padding:38px 48px;background:radial-gradient(circle at 9% 30%,rgba(184,255,84,.16),transparent 25%),radial-gradient(circle at 92% 12%,rgba(121,85,203,.25),transparent 31%),linear-gradient(135deg,#211a36,#181927 62%,#141b20)}.artist-profile-photo{display:grid;width:250px;height:250px;place-items:center;overflow:hidden;border:1px solid rgba(255,255,255,.1);border-radius:50%;background:linear-gradient(145deg,#373249,#202632);color:#9ca1ae;box-shadow:0 26px 65px rgba(0,0,0,.36)}.artist-profile-photo img{width:100%;height:100%;object-fit:cover}.artist-profile-copy>span,.artist-song-section>header span,.artist-album-section>header span,.artist-biography>span{color:#b8ff54;font-size:9px;font-weight:850;letter-spacing:.18em}.artist-profile-copy h1{margin:11px 0 12px;font-size:54px;letter-spacing:-.055em}.artist-meta{display:flex;flex-wrap:wrap;gap:16px;margin:0;color:#c5c6ce;font-size:11px}.artist-meta b{font-weight:500}.artist-intro{display:-webkit-box;max-width:760px;overflow:hidden;margin:17px 0 0;color:#8f93a0;font-size:12px;line-height:1.75;-webkit-box-orient:vertical;-webkit-line-clamp:3}.artist-stat-row{display:flex;gap:24px;margin-top:20px}.artist-stat-row span{color:#7c818f;font-size:10px}.artist-stat-row strong{margin-right:4px;color:#e7e8ed;font-size:15px}.artist-hero-actions{display:flex;align-items:center;gap:10px;margin-top:23px}.artist-hero-actions button,.artist-hero-actions a{display:flex;align-items:center;gap:7px;border:1px solid rgba(255,255,255,.12);border-radius:999px;padding:11px 16px;background:rgba(255,255,255,.055);color:#d5d7de;font-size:11px;font-weight:750;text-decoration:none}.artist-hero-actions button{border:0;background:#b8ff54;color:#12150d}.artist-hero-actions button:disabled{opacity:.45}.artist-tabs{position:sticky;z-index:9;top:83px;display:flex;gap:32px;margin-top:22px;border-bottom:1px solid rgba(255,255,255,.08);background:rgba(16,16,24,.92);backdrop-filter:blur(15px)}.artist-tabs button{position:relative;border:0;padding:16px 2px;background:transparent;color:#767b89;font-size:12px;font-weight:750}.artist-tabs button small{margin-left:5px;color:#565b68;font-size:9px}.artist-tabs button.active{color:#f2f2f5}.artist-tabs button.active:after{position:absolute;right:0;bottom:-1px;left:0;height:2px;border-radius:3px;background:#b8ff54;content:""}.artist-song-section,.artist-album-section,.artist-biography{margin-top:30px}.artist-song-section>header,.artist-album-section>header{display:flex;align-items:end;justify-content:space-between;margin-bottom:17px}.artist-song-section h2,.artist-album-section h2,.artist-biography h2{margin:7px 0 0;font-size:24px}.artist-song-section>header>small,.artist-album-section>header>small{color:#686d79;font-size:10px}.artist-track-table{border-top:1px solid rgba(255,255,255,.08)}.artist-track-head,.artist-track-table article{display:grid;grid-template-columns:46px minmax(260px,1.4fr) minmax(150px,.8fr) 65px;align-items:center;gap:12px}.artist-track-head{padding:13px 10px;color:#626775;font-size:9px}.artist-track-table article{border-radius:11px;padding:8px 10px;color:#838895}.artist-track-table article:hover,.artist-track-table article.playing{background:rgba(255,255,255,.055)}.artist-track-play{display:grid;width:30px;height:30px;place-items:center;border:0;border-radius:50%;background:transparent;color:#747987}.artist-track-play svg{display:none}.artist-track-play i{font-size:9px;font-style:normal}.artist-track-table article:hover .artist-track-play svg,.artist-track-table article.playing .artist-track-play svg{display:block;color:#b8ff54}.artist-track-table article:hover .artist-track-play i,.artist-track-table article.playing .artist-track-play i{display:none}.artist-track-title{display:grid;min-width:0;grid-template-columns:43px minmax(0,1fr);align-items:center;gap:11px;border:0;background:transparent;color:inherit;text-align:left}.artist-track-title img,.artist-track-title>span{display:grid;width:43px;height:43px;place-items:center;border-radius:8px;object-fit:cover;background:#262631}.artist-track-title div{display:grid;min-width:0;gap:4px}.artist-track-title strong,.artist-track-title small,.artist-track-album{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.artist-track-title strong{color:#e9e9ed;font-size:12px}.artist-track-title:hover strong{color:#b8ff54}.artist-track-title small,.artist-track-album,.artist-track-table time{font-size:9px}.artist-album-grid{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:20px}.artist-album-grid a{min-width:0;color:#ecedf1;text-decoration:none}.artist-album-grid img,.artist-album-grid a>span{display:grid;width:100%;aspect-ratio:1;place-items:center;border-radius:16px;object-fit:cover;background:#242430;color:#797e8d;transition:transform .2s}.artist-album-grid a:hover img,.artist-album-grid a:hover>span{transform:translateY(-3px)}.artist-album-grid strong,.artist-album-grid small,.artist-album-grid em{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.artist-album-grid strong{margin-top:11px;font-size:12px}.artist-album-grid small{margin-top:5px;color:#757a87;font-size:9px}.artist-album-grid em{margin-top:4px;color:#5d626f;font-size:9px;font-style:normal}.artist-biography{max-width:900px;border:1px solid rgba(255,255,255,.08);border-radius:22px;padding:30px;background:rgba(255,255,255,.025)}.artist-biography>p{color:#a1a4ae;font-size:12px;line-height:1.9;white-space:pre-wrap}.artist-biography dl{display:flex;flex-wrap:wrap;gap:12px 35px;margin-top:24px}.artist-biography dl div{min-width:150px}.artist-biography dt{color:#626775;font-size:9px}.artist-biography dd{margin:5px 0 0;color:#d4d5dc;font-size:11px}@media(max-width:1200px){.artist-album-grid{grid-template-columns:repeat(4,1fr)}}@media(max-width:900px){.artist-profile-hero{grid-template-columns:1fr;justify-items:center;padding:28px;text-align:center}.artist-profile-photo{width:170px;height:170px}.artist-profile-copy h1{font-size:38px}.artist-meta,.artist-stat-row,.artist-hero-actions{justify-content:center}.artist-album-grid{grid-template-columns:repeat(2,1fr)}.artist-track-head,.artist-track-table article{grid-template-columns:36px minmax(190px,1fr) 55px}.artist-track-album{display:none}}
</style>

<style scoped>
.catalog-search-page{padding:20px 0 28px}.search-summary{display:flex;align-items:end;justify-content:space-between;border-bottom:1px solid rgba(255,255,255,.08);padding:15px 2px 25px}.search-summary>div>span{color:#b8ff54;font-size:9px;font-weight:850;letter-spacing:.18em}.search-summary h1{margin:9px 0 6px;font-size:30px;letter-spacing:-.04em}.search-summary p{margin:0;color:#777c8a;font-size:11px}.search-summary>small{color:#686d7b;font-size:10px}.search-tabs{position:sticky;z-index:10;top:83px;display:flex;gap:28px;border-bottom:1px solid rgba(255,255,255,.08);padding:0 2px;background:rgba(16,16,24,.92);backdrop-filter:blur(14px)}.search-tabs button{position:relative;border:0;padding:18px 2px 14px;background:transparent;color:#7d8290;font-size:12px;font-weight:700}.search-tabs button.active{color:#f1f2f5}.search-tabs button.active:after{position:absolute;right:0;bottom:-1px;left:0;height:2px;border-radius:2px;background:#b8ff54;content:""}.search-result-section{margin-top:30px}.search-result-section>header{display:flex;align-items:end;justify-content:space-between;margin-bottom:15px}.search-result-section h2{margin:0;font-size:20px}.search-result-section header>span{color:#676c79;font-size:10px}.artist-result-grid{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:14px}.artist-result-grid button{display:grid;justify-items:center;border:1px solid rgba(255,255,255,.06);border-radius:17px;padding:17px 10px 15px;background:#171720;color:#e8e9ed;text-align:center;transition:transform .18s,border-color .18s}.artist-result-grid button:hover{transform:translateY(-3px);border-color:rgba(184,255,84,.25)}.artist-result-grid img,.artist-result-grid button>span{display:grid;width:92px;height:92px;place-items:center;border-radius:50%;object-fit:cover;background:linear-gradient(145deg,#332e48,#1f2832);color:#9298a7}.artist-result-grid strong{overflow:hidden;width:100%;margin-top:12px;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.artist-result-grid small{margin-top:5px;color:#6c7180;font-size:9px}.genre-result-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:13px}.genre-result-grid button{display:grid;min-height:155px;align-content:start;justify-items:start;overflow:hidden;border:0;border-radius:18px;padding:19px;color:#171920;text-align:left;transition:transform .18s}.genre-result-grid button:hover{transform:translateY(-3px)}.genre-result-grid button.genre-0{background:linear-gradient(140deg,#dafe97,#a7d97c)}.genre-result-grid button.genre-1{background:linear-gradient(140deg,#cdc1ff,#9588d7)}.genre-result-grid button.genre-2{background:linear-gradient(140deg,#ffd3b8,#dc9d8e)}.genre-result-grid button.genre-3{background:linear-gradient(140deg,#b8e6ff,#79abc9)}.genre-result-grid strong{margin-top:17px;font-size:19px}.genre-result-grid p{margin:7px 0;color:rgba(19,23,29,.68);font-size:10px;line-height:1.45}.genre-result-grid span{margin-top:auto;font-size:9px;font-weight:800}.search-playlist-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:18px}.search-playlist-grid>button{min-width:0;border:0;padding:0;background:transparent;color:#e9e9ee;text-align:left}.search-playlist-cover{position:relative;display:grid;width:100%;aspect-ratio:1;place-items:center;overflow:hidden;border-radius:16px;background:linear-gradient(145deg,#2b2938,#191a24);color:#747988}.search-playlist-cover img{width:100%;height:100%;object-fit:cover;transition:transform .2s}.search-playlist-grid button:hover img{transform:scale(1.04)}.search-playlist-cover i{position:absolute;right:9px;bottom:9px;border-radius:999px;padding:5px 7px;background:rgba(13,14,20,.82);color:#b8ff54;font-size:8px;font-style:normal}.search-playlist-grid>button>strong{display:block;overflow:hidden;margin-top:10px;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.search-playlist-grid>button>small{display:block;margin-top:5px;color:#696e7b;font-size:9px}.online-playlist-banner{display:grid;grid-template-columns:105px minmax(0,1fr) auto;align-items:center;gap:20px;margin-top:25px;border:1px solid rgba(255,255,255,.08);border-radius:20px;padding:18px;background:linear-gradient(120deg,rgba(104,82,164,.34),rgba(26,28,39,.7))}.online-playlist-banner>img,.online-playlist-banner>span{display:grid;width:105px;height:105px;place-items:center;border-radius:15px;object-fit:cover;background:#242431}.online-playlist-banner small{color:#afa6d4;font-size:9px;font-weight:800;letter-spacing:.12em}.online-playlist-banner h2{margin:8px 0 5px;font-size:23px}.online-playlist-banner p{margin:0;color:#858996;font-size:10px}.online-playlist-banner>button{display:flex;align-items:center;gap:6px;border:1px solid rgba(255,255,255,.1);border-radius:10px;padding:9px 11px;background:rgba(255,255,255,.05);color:#bdc0ca;font-size:10px}.search-track-list{display:grid;gap:3px;border-top:1px solid rgba(255,255,255,.07);padding-top:6px}.search-track-list article{display:grid;grid-template-columns:36px minmax(240px,1.4fr) minmax(130px,.8fr) 70px 55px;align-items:center;gap:12px;border-radius:11px;padding:8px 10px;color:#858a97}.search-track-list article:hover,.search-track-list article.playing{background:rgba(255,255,255,.055)}.search-track-play{display:grid;width:30px;height:30px;place-items:center;border:0;border-radius:50%;background:transparent;color:#717684}.search-track-list article:hover .search-track-play,.search-track-list article.playing .search-track-play{color:#b8ff54}.search-track-main{display:grid;min-width:0;grid-template-columns:43px minmax(0,1fr);align-items:center;gap:11px;border:0;padding:0;background:transparent;color:inherit;text-align:left}.search-track-main>img,.search-track-main>span{display:grid;width:43px;height:43px;place-items:center;border-radius:8px;object-fit:cover;background:#262631}.search-track-main>div{display:grid;min-width:0;gap:4px}.search-track-main strong,.search-track-main small,.search-track-album{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.search-track-main strong{color:#e9e9ed;font-size:12px}.search-track-main:hover strong{color:#b8ff54}.search-track-main small,.search-track-album,.search-track-provider,.search-track-list time{font-size:9px}.search-track-provider{text-transform:uppercase}.search-category-empty{display:grid;min-height:90px;place-items:center;border:1px dashed rgba(255,255,255,.07);border-radius:14px;color:#606573;font-size:10px}.search-pagination{display:flex;align-items:center;justify-content:center;gap:15px;margin-top:30px}.search-pagination button{border:1px solid rgba(255,255,255,.1);border-radius:9px;padding:8px 12px;background:#1c1c26;color:#b7bac4;font-size:10px}.search-pagination button:disabled{opacity:.35}.search-pagination span{color:#6f7482;font-size:10px}@media(max-width:1200px){.artist-result-grid{grid-template-columns:repeat(4,1fr)}.genre-result-grid{grid-template-columns:repeat(2,1fr)}.search-playlist-grid{grid-template-columns:repeat(4,1fr)}}@media(max-width:900px){.search-summary{align-items:start;flex-direction:column;gap:12px}.search-summary h1{font-size:24px}.search-tabs{top:83px;gap:19px;overflow-x:auto}.artist-result-grid{grid-template-columns:repeat(2,1fr)}.genre-result-grid{grid-template-columns:1fr}.search-playlist-grid{grid-template-columns:repeat(2,1fr)}.online-playlist-banner{grid-template-columns:72px 1fr}.online-playlist-banner>img,.online-playlist-banner>span{width:72px;height:72px}.online-playlist-banner>button{grid-column:1/-1;justify-self:start}.search-track-list article{grid-template-columns:28px minmax(190px,1fr) 52px}.search-track-album,.search-track-provider{display:none}}
</style>

<style scoped>
.track-title {
  border: 0;
  padding: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.track-title:hover strong {
  color: #b8ff54;
}

.qq-discovery-section {
  position: relative;
}

.qq-discovery-copy {
  margin: -8px 0 18px;
  color: #6f7481;
  font-size: 10px;
}

.qq-playlist-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 17px;
}

.qq-playlist-card {
  min-width: 0;
  border: 0;
  padding: 0;
  background: transparent;
  color: #f1f1f4;
  text-align: left;
}

.qq-playlist-cover {
  position: relative;
  display: block;
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: 16px;
  background: linear-gradient(145deg, #292735, #181923);
  box-shadow: 0 14px 35px rgba(0, 0, 0, 0.18);
}

.qq-playlist-cover::after {
  position: absolute;
  inset: 45% 0 0;
  background: linear-gradient(transparent, rgba(8, 9, 14, 0.78));
  content: '';
}

.qq-playlist-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.28s cubic-bezier(0.22, 1, 0.36, 1);
}

.qq-playlist-card:hover .qq-playlist-cover img { transform: scale(1.055); }
.qq-playlist-cover > i { position: absolute; z-index: 2; right: 10px; bottom: 10px; display: grid; width: 38px; height: 38px; place-items: center; border-radius: 50%; background: #b8ff54; color: #14170d; opacity: 0; transform: translateY(7px); transition: 0.2s ease; }
.qq-playlist-card:hover .qq-playlist-cover > i { opacity: 1; transform: none; }
.qq-playlist-cover > small { position: absolute; z-index: 2; left: 10px; bottom: 10px; display: flex; align-items: center; gap: 4px; color: #f1f2f4; font-size: 9px; }
.qq-playlist-card > strong { display: -webkit-box; overflow: hidden; margin-top: 10px; font-size: 12px; line-height: 1.45; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.qq-playlist-card > span:last-child { display: block; overflow: hidden; margin-top: 5px; color: #696e7b; font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }

.qq-playlist-skeleton span,
.qq-playlist-skeleton i {
  display: block;
  border-radius: 8px;
  background: linear-gradient(100deg, #1c1c26 20%, #282833 38%, #1c1c26 55%);
  background-size: 220% 100%;
  animation: qq-skeleton 1.3s ease infinite;
}

.qq-playlist-skeleton span { aspect-ratio: 1; border-radius: 16px; }
.qq-playlist-skeleton i { width: 88%; height: 10px; margin-top: 10px; }
.qq-playlist-skeleton i:last-child { width: 58%; height: 8px; margin-top: 7px; }
.qq-discovery-empty { display: flex; min-height: 150px; align-items: center; justify-content: center; gap: 10px; border: 1px dashed rgba(255, 255, 255, 0.08); border-radius: 18px; color: #727784; font-size: 11px; }
.qq-discovery-empty button { border: 0; background: transparent; color: #b8ff54; font-size: 11px; }
.qq-external-link { margin-left: auto; color: #858a98; font-size: 10px; text-decoration: none; }
.qq-external-link:hover { color: #b8ff54; }

@keyframes qq-skeleton { to { background-position-x: -220%; } }

@media (max-width: 1280px) {
  .qq-playlist-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}

@media (max-width: 900px) {
  .qq-playlist-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .qq-external-link { margin-left: 0; }
}
</style>

<style scoped>
.music-nav {
  position: relative;
  padding: 24px 14px 126px;
  background:
    radial-gradient(circle at 28% 2%, rgba(184, 255, 84, 0.055), transparent 21%),
    linear-gradient(180deg, rgba(15, 15, 23, 0.98), rgba(11, 11, 18, 0.98));
}

.nav-library {
  display: flex;
  min-height: 220px;
  flex: 1 1 auto;
  flex-direction: column;
  margin: 24px 0 12px;
  overflow: hidden;
}

.nav-library > .library-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 7px 11px;
  color: #777c8b;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0;
  text-transform: none;
}

.library-heading > div {
  display: flex;
  align-items: center;
  gap: 7px;
}

.library-heading > div > span {
  color: #a9adba;
  font-size: 11px;
  letter-spacing: 0.08em;
}

.library-heading > div > small {
  display: grid;
  min-width: 19px;
  height: 19px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  padding: 0 5px;
  background: rgba(255, 255, 255, 0.035);
  color: #707583;
  font-size: 9px;
}

.nav-library > .library-heading .library-add {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #858a98;
  transition: 0.18s ease;
}

.nav-library > .library-heading .library-add:hover,
.nav-library > .library-heading .library-add[aria-expanded="true"] {
  border-color: rgba(184, 255, 84, 0.22);
  background: rgba(184, 255, 84, 0.09);
  color: #b8ff54;
}

.nav-library > .library-create {
  display: grid;
  gap: 9px;
  margin: 0 3px 12px;
  border: 1px solid rgba(184, 255, 84, 0.18);
  border-radius: 14px;
  padding: 11px;
  background: linear-gradient(145deg, rgba(184, 255, 84, 0.08), rgba(120, 88, 190, 0.08));
  box-shadow: 0 12px 35px rgba(0, 0, 0, 0.14);
}

.library-create-title {
  display: flex;
  align-items: center;
  gap: 9px;
}

.library-create-title > span {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  place-items: center;
  border-radius: 9px;
  background: #b8ff54;
  color: #14170e;
}

.library-create-title > div {
  display: grid;
  gap: 2px;
}

.library-create-title strong {
  color: #edf0e9;
  font-size: 11px;
}

.library-create-title small {
  color: #747a87;
  font-size: 8px;
}

.nav-library > .library-create > input {
  width: 100%;
  height: 34px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 9px;
  outline: 0;
  padding: 0 10px;
  background: rgba(12, 13, 19, 0.78);
  color: #f4f5f1;
  font-size: 10px;
}

.nav-library > .library-create > input:focus {
  border-color: rgba(184, 255, 84, 0.45);
  box-shadow: 0 0 0 3px rgba(184, 255, 84, 0.07);
}

.library-create-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}

.library-create-actions button {
  height: 27px;
  border: 0;
  border-radius: 8px;
  padding: 0 10px;
  background: rgba(255, 255, 255, 0.06);
  color: #8f94a1;
  font-size: 9px;
}

.library-create-actions button[type="submit"] {
  background: #b8ff54;
  color: #11150b;
  font-weight: 800;
}

.library-create-actions button:disabled {
  opacity: 0.35;
}

.library-tools {
  display: grid;
  gap: 9px;
  padding: 0 3px 10px;
}

.library-search {
  display: grid;
  height: 34px;
  grid-template-columns: 18px minmax(0, 1fr);
  align-items: center;
  gap: 5px;
  border: 1px solid rgba(255, 255, 255, 0.075);
  border-radius: 10px;
  padding: 0 9px;
  background: rgba(255, 255, 255, 0.035);
  color: #656b79;
  transition: 0.18s ease;
}

.library-search:focus-within {
  border-color: rgba(184, 255, 84, 0.28);
  background: rgba(255, 255, 255, 0.055);
  color: #a4aaaf;
}

.library-search input {
  min-width: 0;
  height: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: #dfe1e7;
  font-size: 10px;
}

.library-search input::placeholder {
  color: #5d6270;
}

.library-search input::-webkit-search-cancel-button {
  filter: invert(0.7);
}

.library-filters {
  display: flex;
  gap: 5px;
}

.library-filters button {
  height: 25px;
  border: 1px solid rgba(255, 255, 255, 0.065);
  border-radius: 999px;
  padding: 0 9px;
  background: transparent;
  color: #686e7c;
  font-size: 9px;
  transition: 0.16s ease;
}

.library-filters button:hover {
  color: #bbc0ca;
}

.library-filters button.active {
  border-color: rgba(184, 255, 84, 0.2);
  background: rgba(184, 255, 84, 0.1);
  color: #c8ff7c;
}

.nav-playlist-scroll {
  min-height: 0;
  flex: 1 1 auto;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 0 3px 18px;
  scrollbar-color: rgba(255, 255, 255, 0.13) transparent;
  scrollbar-width: thin;
}

.nav-playlist-scroll::-webkit-scrollbar {
  width: 4px;
}

.nav-playlist-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.13);
}

.nav-playlist-scroll > .nav-playlist {
  position: relative;
  display: grid !important;
  width: 100%;
  min-height: 58px;
  grid-template-columns: 42px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 9px !important;
  overflow: hidden;
  border: 1px solid transparent;
  border-radius: 12px;
  padding: 7px 7px 7px 8px;
  background: transparent;
  color: #aeb2bd;
  text-align: left;
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.18s ease;
}

.nav-playlist-scroll > .nav-playlist + .nav-playlist {
  margin-top: 3px;
}

.nav-playlist-scroll > .nav-playlist::before {
  position: absolute;
  top: 13px;
  bottom: 13px;
  left: 0;
  width: 2px;
  border-radius: 0 4px 4px 0;
  background: #b8ff54;
  content: "";
  opacity: 0;
  transform: scaleY(0.45);
  transition: 0.18s ease;
}

.nav-playlist-scroll > .nav-playlist:hover {
  border-color: rgba(255, 255, 255, 0.075);
  background: rgba(255, 255, 255, 0.045);
  transform: translateX(1px);
}

.nav-playlist-scroll > .nav-playlist.active {
  border-color: rgba(184, 255, 84, 0.14);
  background: linear-gradient(90deg, rgba(184, 255, 84, 0.105), rgba(184, 255, 84, 0.028));
  box-shadow: inset 0 0 20px rgba(184, 255, 84, 0.018);
}

.nav-playlist-scroll > .nav-playlist.active::before {
  opacity: 1;
  transform: scaleY(1);
}

.nav-playlist-cover {
  position: relative;
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  overflow: hidden;
  border-radius: 10px;
  background: linear-gradient(145deg, #302b40, #1b2430);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.22);
}

.nav-playlist-cover img,
.nav-playlist-cover > span {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  object-fit: cover;
}

.nav-playlist-cover > span {
  background: radial-gradient(circle at 72% 22%, rgba(184, 255, 84, 0.18), transparent 32%);
  color: #9da58f;
}

.nav-playlist-copy {
  display: grid;
  min-width: 0;
  gap: 5px;
}

.nav-playlist-copy strong,
.nav-playlist-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-playlist-copy strong {
  color: #c7cad2;
  font-size: 11px;
  font-weight: 680;
}

.nav-playlist-copy small {
  color: #636977;
  font-size: 8.5px;
}

.nav-playlist.active .nav-playlist-copy strong,
.nav-playlist:hover .nav-playlist-copy strong {
  color: #f2f4ee;
}

.nav-playlist-arrow {
  display: grid;
  place-items: center;
  color: #4f5561;
  opacity: 0;
  transform: translateX(-3px);
  transition: 0.18s ease;
}

.nav-playlist:hover .nav-playlist-arrow,
.nav-playlist.active .nav-playlist-arrow {
  opacity: 1;
  transform: none;
}

.nav-playlist.active .nav-playlist-arrow {
  color: #aeea57;
}

.nav-playlist:focus-visible,
.library-add:focus-visible,
.library-filters button:focus-visible {
  outline: 2px solid rgba(184, 255, 84, 0.72);
  outline-offset: 2px;
}

.library-empty {
  display: grid;
  justify-items: center;
  margin: 6px 2px 0;
  border: 1px dashed rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 22px 13px;
  color: #6c7280;
  text-align: center;
}

.library-empty > span {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border-radius: 12px;
  background: rgba(184, 255, 84, 0.07);
  color: #9fc84f;
}

.library-empty strong {
  margin-top: 10px;
  color: #aeb2bc;
  font-size: 10px;
}

.library-empty p {
  margin: 6px 0 11px;
  padding: 0;
  color: #626775;
  font-size: 9px;
  line-height: 1.55;
}

.library-empty > button {
  border: 0;
  border-radius: 8px;
  padding: 7px 10px;
  background: rgba(184, 255, 84, 0.1);
  color: #b8ff54;
  font-size: 9px;
}

.library-empty.compact {
  padding-block: 17px;
}

.nav-bottom {
  position: relative;
  gap: 3px;
  padding-top: 11px;
}

.nav-bottom::before {
  position: absolute;
  top: 0;
  right: 7px;
  left: 7px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.08), transparent);
  content: "";
}

.qq-video-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 22px;
}

.qq-video-grid a,
.qq-entity-list a,
.qq-user-list a {
  color: inherit;
  text-decoration: none;
}

.qq-video-grid a > span {
  position: relative;
  display: block;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-radius: 14px;
  background: #1d1d28;
}

.qq-video-grid img { width: 100%; height: 100%; object-fit: cover; transition: transform .25s ease; }
.qq-video-grid a:hover img { transform: scale(1.035); }
.qq-video-grid i { position: absolute; inset: 0; display: grid; place-items: center; color: #b8ff54; background: rgba(8,8,13,.12); }
.qq-video-grid i svg { padding: 13px; border-radius: 50%; box-sizing: content-box; background: rgba(13,14,20,.82); }
.qq-video-grid time { position: absolute; right: 9px; bottom: 8px; border-radius: 6px; padding: 3px 6px; background: rgba(0,0,0,.68); font-size: 9px; }
.qq-video-grid strong,.qq-video-grid small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qq-video-grid strong { margin-top: 10px; font-size: 13px; }
.qq-video-grid small { margin-top: 5px; color: #747987; font-size: 10px; }

.qq-entity-list,
.qq-search-playlist-list,
.qq-user-list,
.qq-lyric-list { display: grid; gap: 4px; }

.qq-entity-list a,
.qq-search-playlist-list button,
.qq-user-list a {
  display: grid;
  grid-template-columns: 58px minmax(0,1.6fr) minmax(0,1fr) 120px 70px;
  align-items: center;
  gap: 15px;
  min-height: 68px;
  border: 0;
  border-radius: 12px;
  padding: 7px 14px;
  background: transparent;
  color: #ececf1;
  text-align: left;
}

.qq-entity-list a:hover,
.qq-search-playlist-list button:hover,
.qq-user-list a:hover,
.qq-lyric-list button:hover { background: rgba(255,255,255,.045); }
.qq-entity-list img,.qq-search-playlist-list img,.qq-user-list img,
.qq-entity-list a > span,.qq-search-playlist-list button > span,.qq-user-list a > span { width: 52px; height: 52px; border-radius: 10px; object-fit: cover; display: grid; place-items: center; background: #22222d; color: #8d92a0; }
.qq-user-list img,.qq-user-list a > span { border-radius: 50%; }
.qq-entity-list strong,.qq-search-playlist-list strong,.qq-user-list strong,
.qq-entity-list small,.qq-search-playlist-list small,.qq-user-list small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qq-entity-list strong,.qq-search-playlist-list strong,.qq-user-list strong { font-size: 13px; }
.qq-entity-list small,.qq-search-playlist-list small,.qq-user-list small,
.qq-entity-list time,.qq-search-playlist-list time,.qq-user-list time,
.qq-entity-list em,.qq-search-playlist-list em,.qq-user-list em { color: #777c89; font-size: 10px; font-style: normal; }

.qq-lyric-list button {
  display: grid;
  grid-template-columns: minmax(0,1fr) 90px 60px;
  align-items: center;
  gap: 18px;
  border: 0;
  border-radius: 12px;
  padding: 14px 16px;
  background: transparent;
  color: #e8e8ed;
  text-align: left;
}
.qq-lyric-list button div { min-width: 0; }
.qq-lyric-list strong,.qq-lyric-list small,.qq-lyric-list p { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qq-lyric-list small { margin-top: 5px; color: #808592; font-size: 10px; }
.qq-lyric-list p { margin: 7px 0 0; color: #626775; font-size: 10px; }
.qq-lyric-list button > span { color: #b8ff54; font-size: 10px; }
.qq-lyric-list time { color: #777c89; font-size: 10px; text-align: right; }

.qq-artist-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0,1fr));
  gap: 26px 20px;
}
.qq-artist-grid button { min-width: 0; border: 0; background: transparent; color: #eeeef2; text-align: center; }
.qq-artist-grid img,.qq-artist-grid button > span { display: grid; width: 100%; aspect-ratio: 1; place-items: center; border-radius: 50%; object-fit: cover; background: #22222d; transition: transform .2s ease; }
.qq-artist-grid button:hover img,.qq-artist-grid button:hover > span { transform: translateY(-3px); }
.qq-artist-grid strong,.qq-artist-grid small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qq-artist-grid strong { margin-top: 12px; font-size: 13px; }
.qq-artist-grid small { margin-top: 5px; color: #747987; font-size: 9px; }

@media (max-width: 1100px) {
  .qq-video-grid { grid-template-columns: repeat(2,minmax(0,1fr)); }
  .qq-artist-grid { grid-template-columns: repeat(4,minmax(0,1fr)); }
  .qq-entity-list a,.qq-search-playlist-list button,.qq-user-list a { grid-template-columns: 52px minmax(0,1.5fr) minmax(0,1fr) 90px; }
  .qq-entity-list em,.qq-search-playlist-list em,.qq-user-list em { display: none; }
}

@media (max-width: 760px) {
  .qq-video-grid { grid-template-columns: 1fr; }
  .qq-artist-grid { grid-template-columns: repeat(2,minmax(0,1fr)); }
  .qq-entity-list a,.qq-search-playlist-list button,.qq-user-list a { grid-template-columns: 46px minmax(0,1fr); }
  .qq-entity-list time,.qq-search-playlist-list time,.qq-user-list time,.qq-entity-list em,.qq-search-playlist-list em,.qq-user-list em { display: none; }
  .qq-entity-list img,.qq-search-playlist-list img,.qq-user-list img,.qq-entity-list a > span,.qq-search-playlist-list button > span,.qq-user-list a > span { grid-row: span 2; width: 44px; height: 44px; }
  .qq-lyric-list button { grid-template-columns: minmax(0,1fr) 60px; }
  .qq-lyric-list time { display: none; }
}

.music-hero {
  --hero-glow: rgba(184,255,84,.19);
  --hero-start: #211a37;
  --hero-middle: #171825;
  --hero-end: #162522;
  position: relative;
  overflow: hidden;
  background: radial-gradient(circle at 78% 28%,var(--hero-glow),transparent 27%),linear-gradient(130deg,var(--hero-start),var(--hero-middle) 57%,var(--hero-end));
  transition: background .45s ease,border-color .45s ease;
}
.music-hero.is-morning { --hero-glow: rgba(255,213,79,.27); --hero-start: #29203c; --hero-middle: #263126; --hero-end: #18362c; }
.music-hero.is-noon { --hero-glow: rgba(255,160,86,.24); --hero-start: #38252e; --hero-middle: #28242d; --hero-end: #2d2922; }
.music-hero.is-afternoon { --hero-glow: rgba(132,167,255,.22); --hero-start: #211d3b; --hero-middle: #192335; --hero-end: #172b2b; }
.music-hero.is-evening { --hero-glow: rgba(255,111,127,.19); --hero-start: #2c1c35; --hero-middle: #1c1a2e; --hero-end: #17262a; }
.music-hero.is-night { --hero-glow: rgba(91,126,255,.2); --hero-start: #171a35; --hero-middle: #141622; --hero-end: #111d2c; }
.music-hero.is-holiday { border-color: rgba(255,215,126,.2); box-shadow: inset 0 1px 0 rgba(255,255,255,.05); }
.music-hero > div,.music-hero > .hero-play { position: relative; z-index: 1; }
.hero-copy { max-width: 760px; }
.hero-kicker { display: flex; align-items: center; gap: 10px; }
.music-hero .hero-kicker small { border: 1px solid rgba(255,255,255,.1); border-radius: 999px; padding: 5px 8px; background: rgba(255,255,255,.055); color: #d7d1e8; font-size: 8px; font-weight: 760; letter-spacing: .06em; }
.music-hero.is-holiday .hero-kicker small { border-color: rgba(255,215,126,.24); background: rgba(255,201,91,.09); color: #ffe1a2; }
.music-hero .hero-copy p { max-width: 690px; line-height: 1.65; }
</style>
