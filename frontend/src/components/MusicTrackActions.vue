<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Bookmark, Check, Heart, LoaderCircle, Plus, X } from 'lucide-vue-next'
import { request } from '../services/api'

const props = defineProps({
  track: { type: Object, required: true },
  compact: { type: Boolean, default: false },
})

const liked = ref(false)
const loading = ref(false)
const dialogOpen = ref(false)
const playlists = ref([])
const selectedPlaylistId = ref('')
const creating = ref(false)
const newPlaylistName = ref('')
const message = ref('')

const searchId = computed(() => props.track?._searchId || props.track?.searchId || '')
const available = computed(() => Boolean(searchId.value && props.track?.id))
const editablePlaylists = computed(() => playlists.value.filter(item => item.editable))

onMounted(loadState)
watch(() => [props.track?.id, searchId.value], loadState)

async function loadState() {
  liked.value = false
  if (!available.value) return
  try {
    const result = await request(`/api/music/track-state?searchId=${encodeURIComponent(searchId.value)}&trackId=${encodeURIComponent(props.track.id)}`)
    liked.value = Boolean(result.data?.liked)
  } catch {
    // 行为按钮仍可使用，点击时服务端会再次校验曝光归属。
  }
}

async function toggleLike() {
  if (!available.value || loading.value) return
  loading.value = true
  message.value = ''
  const eventType = liked.value ? 'UNLIKE' : 'LIKE'
  try {
    await sendEvent(eventType)
    liked.value = !liked.value
    message.value = liked.value ? '已加入我喜欢的音乐' : '已取消喜欢'
  } catch (error) {
    message.value = error.message
  } finally {
    loading.value = false
  }
}

async function openCollection() {
  if (!available.value || loading.value) return
  dialogOpen.value = true
  creating.value = false
  message.value = ''
  loading.value = true
  try {
    const result = await request('/api/music/playlists')
    playlists.value = result.data || []
    selectedPlaylistId.value = editablePlaylists.value[0]?.id || ''
  } catch (error) {
    message.value = error.message
  } finally {
    loading.value = false
  }
}

async function saveToPlaylist() {
  if (!selectedPlaylistId.value || loading.value) return
  loading.value = true
  try {
    await addTrack(selectedPlaylistId.value)
    await sendEvent('SAVE')
    message.value = '已收藏到所选歌单'
    dialogOpen.value = false
  } catch (error) {
    message.value = error.message
  } finally {
    loading.value = false
  }
}

async function createAndSave() {
  const name = newPlaylistName.value.trim()
  if (!name || loading.value) return
  loading.value = true
  try {
    const created = await request('/api/music/playlists', {
      method: 'POST',
      body: JSON.stringify({ name, description: '收藏的歌曲' }),
    })
    await addTrack(created.data.id)
    await sendEvent('SAVE')
    newPlaylistName.value = ''
    message.value = `已创建“${name}”并收藏歌曲`
    dialogOpen.value = false
  } catch (error) {
    message.value = error.message
  } finally {
    loading.value = false
  }
}

function addTrack(playlistId) {
  return request(`/api/music/playlists/${encodeURIComponent(playlistId)}/tracks`, {
    method: 'POST',
    body: JSON.stringify({ searchId: searchId.value, trackId: props.track.id }),
  })
}

function sendEvent(eventType) {
  return request('/api/music/events', {
    method: 'POST',
    body: JSON.stringify({
      eventId: crypto.randomUUID(),
      searchId: searchId.value,
      trackId: props.track.id,
      eventType,
      playbackMs: null,
    }),
  })
}
</script>

<template>
  <span class="track-actions" :class="{ compact }" @click.stop>
    <button :class="{ active: liked }" :disabled="!available || loading" :title="liked ? '取消喜欢' : '喜欢并加入我喜欢的音乐'" @click="toggleLike">
      <Heart :size="compact ? 15 : 17" :fill="liked ? 'currentColor' : 'none'" />
    </button>
    <button :disabled="!available || loading" title="收藏到歌单" @click="openCollection"><Bookmark :size="compact ? 15 : 17" /></button>
    <small v-if="message" class="action-message">{{ message }}</small>
  </span>

  <Teleport to="body">
    <Transition name="picker-fade">
      <div v-if="dialogOpen" class="playlist-picker-backdrop" @mousedown.self="dialogOpen = false">
        <section class="playlist-picker" role="dialog" aria-modal="true" aria-label="收藏歌曲到歌单">
          <header>
            <div><span>COLLECT TRACK</span><h2>收藏到歌单</h2><p>{{ track.name }} · {{ track.artists?.join(' / ') }}</p></div>
            <button title="关闭" @click="dialogOpen = false"><X :size="18" /></button>
          </header>

          <div v-if="loading" class="picker-loading"><LoaderCircle class="spin" :size="23" />正在加载歌单</div>
          <template v-else>
            <div v-if="!creating" class="playlist-options">
              <button v-for="playlist in editablePlaylists" :key="playlist.id" :class="{ selected: selectedPlaylistId === playlist.id }" @click="selectedPlaylistId = playlist.id">
                <img v-if="playlist.coverUrl" :src="playlist.coverUrl" alt="" />
                <span v-else><Bookmark :size="18" /></span>
                <div><strong>{{ playlist.name }}</strong><small>{{ playlist.trackCount }} 首 · {{ playlist.type === 'CUSTOM' ? '自建歌单' : '智能歌单' }}</small></div>
                <Check v-if="selectedPlaylistId === playlist.id" :size="17" />
              </button>
              <button class="create-option" @click="creating = true"><span><Plus :size="19" /></span><div><strong>创建新歌单</strong><small>创建后立即收藏当前歌曲</small></div></button>
            </div>
            <form v-else class="create-playlist-form" @submit.prevent="createAndSave">
              <label>新歌单名称<input v-model="newPlaylistName" maxlength="80" autofocus placeholder="例如：深夜循环" /></label>
              <div><button type="button" @click="creating = false">返回选择</button><button class="primary" :disabled="!newPlaylistName.trim()" type="submit">创建并收藏</button></div>
            </form>
          </template>
          <footer v-if="!creating"><button @click="dialogOpen = false">取消</button><button class="primary" :disabled="!selectedPlaylistId || loading" @click="saveToPlaylist">收藏到这里</button></footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.track-actions{position:relative;display:inline-flex;align-items:center;gap:5px}.track-actions>button{display:grid;width:34px;height:34px;place-items:center;border:0;border-radius:50%;background:transparent;color:#747987}.track-actions>button:hover,.track-actions>button.active{background:rgba(255,255,255,.08);color:#b8ff54}.track-actions>button:disabled{opacity:.35}.track-actions.compact>button{width:29px;height:29px}.action-message{position:absolute;right:0;bottom:calc(100% + 8px);width:max-content;max-width:220px;border:1px solid rgba(255,255,255,.1);border-radius:8px;padding:6px 8px;background:#20202a;color:#cfd1d8;font-size:9px;box-shadow:0 8px 24px rgba(0,0,0,.35)}
.playlist-picker-backdrop{position:fixed;z-index:300;inset:0;display:grid;place-items:center;padding:20px;background:rgba(5,6,10,.68);backdrop-filter:blur(10px)}.playlist-picker{width:min(510px,calc(100vw - 32px));max-height:min(680px,calc(100vh - 40px));overflow:auto;border:1px solid rgba(255,255,255,.11);border-radius:24px;padding:24px;background:linear-gradient(145deg,#20202c,#15151e);color:#f1f2f5;box-shadow:0 30px 100px rgba(0,0,0,.58)}.playlist-picker header{display:flex;align-items:start;justify-content:space-between;border-bottom:1px solid rgba(255,255,255,.08);padding-bottom:18px}.playlist-picker header span{color:#b8ff54;font-size:9px;font-weight:850;letter-spacing:.16em}.playlist-picker h2{margin:6px 0 4px;font-size:25px}.playlist-picker p{overflow:hidden;max-width:380px;margin:0;color:#7c818e;font-size:10px;text-overflow:ellipsis;white-space:nowrap}.playlist-picker header>button{display:grid;width:34px;height:34px;place-items:center;border:0;border-radius:50%;background:rgba(255,255,255,.05);color:#a6a9b2}.playlist-options{display:grid;gap:7px;padding:16px 0}.playlist-options>button{display:grid;grid-template-columns:48px minmax(0,1fr) 24px;align-items:center;gap:12px;border:1px solid transparent;border-radius:14px;padding:9px;background:transparent;color:#e3e4e9;text-align:left}.playlist-options>button:hover,.playlist-options>button.selected{border-color:rgba(184,255,84,.24);background:rgba(184,255,84,.06)}.playlist-options img,.playlist-options>button>span{display:grid;width:48px;height:48px;place-items:center;border-radius:11px;object-fit:cover;background:#292a35;color:#9297a4}.playlist-options div{display:grid;gap:5px;min-width:0}.playlist-options strong,.playlist-options small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.playlist-options strong{font-size:12px}.playlist-options small{color:#747987;font-size:9px}.create-option{border-style:dashed!important}.create-option>span{color:#b8ff54!important}.picker-loading{display:flex;min-height:180px;align-items:center;justify-content:center;gap:9px;color:#858a98;font-size:11px}.playlist-picker footer,.create-playlist-form>div{display:flex;justify-content:flex-end;gap:9px;border-top:1px solid rgba(255,255,255,.08);padding-top:17px}.playlist-picker footer button,.create-playlist-form button{border:1px solid rgba(255,255,255,.1);border-radius:10px;padding:9px 14px;background:#22232d;color:#bfc2cc}.playlist-picker button.primary,.create-playlist-form button.primary{border:0;background:#b8ff54;color:#11150d;font-weight:800}.playlist-picker button:disabled{opacity:.4}.create-playlist-form{display:grid;gap:18px;padding:22px 0 0}.create-playlist-form label{display:grid;gap:8px;color:#a9acb6;font-size:10px}.create-playlist-form input{height:44px;border:1px solid rgba(255,255,255,.12);border-radius:11px;padding:0 12px;background:#15151d;color:white;outline:none}.create-playlist-form input:focus{border-color:rgba(184,255,84,.55)}.picker-fade-enter-active,.picker-fade-leave-active{transition:opacity .18s}.picker-fade-enter-from,.picker-fade-leave-to{opacity:0}.spin{animation:picker-spin 1s linear infinite}@keyframes picker-spin{to{transform:rotate(360deg)}}
</style>
