<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Headphones, ListMusic, Music2 } from 'lucide-vue-next'
import { returnState } from '../services/navigation'

const props = defineProps({
  actions: { type: Array, default: () => [] },
})

const router = useRouter()
const route = useRoute()

const playlistSearch = computed(() => {
  for (let index = props.actions.length - 1; index >= 0; index -= 1) {
    const action = props.actions[index]
    if (action?.type === 'SHOW_QQ_PLAYLIST_RESULTS' && action.playlistSearch) return action.playlistSearch
  }
  return null
})

const playlists = computed(() => playlistSearch.value?.playlists || [])

function formatCompactCount(value) {
  const count = Math.max(0, Number(value || 0))
  if (count >= 100_000_000) return `${(count / 100_000_000).toFixed(count >= 1_000_000_000 ? 0 : 1)}亿`
  if (count >= 10_000) return `${(count / 10_000).toFixed(count >= 100_000 ? 0 : 1)}万`
  return String(Math.round(count))
}

function openPlaylist(playlist) {
  if (!playlist?.id) return
  router.push({
    name: 'music-qq-playlist',
    params: { qqPlaylistId: playlist.id },
    state: returnState(route),
  })
}

function continueInLibrary() {
  if (!playlistSearch.value?.keyword) return
  router.push({
    name: 'music-home',
    query: { search: playlistSearch.value.keyword, type: 'PLAYLIST', page: playlistSearch.value.page || 1 },
    state: returnState(route),
  })
}
</script>

<template>
  <section v-if="playlistSearch" class="inline-playlist-results" aria-label="QQ 音乐歌单搜索结果">
    <header class="playlist-results-header">
      <div class="playlist-heading-mark"><ListMusic :size="17" /></div>
      <div class="playlist-heading-copy">
        <span>QQ MUSIC · PLAYLISTS</span>
        <strong>“{{ playlistSearch.keyword }}”的公开歌单</strong>
        <small v-if="playlists.length">找到 {{ playlistSearch.total || playlists.length }} 个结果，当前展示 {{ playlists.length }} 个</small>
        <small v-else>QQ 音乐暂未返回匹配的公开歌单</small>
      </div>
      <button v-if="playlists.length" type="button" @click="continueInLibrary">
        <span>查看更多</span><ArrowRight :size="14" />
      </button>
    </header>
    <p v-if="playlistSearch.explanation" class="playlist-profile-reason">{{ playlistSearch.explanation }}</p>

    <div v-if="playlists.length" class="inline-playlist-scroller">
      <button
        v-for="playlist in playlists"
        :key="playlist.id"
        class="inline-playlist-card"
        type="button"
        :aria-label="`打开歌单 ${playlist.name}`"
        @click="openPlaylist(playlist)"
      >
        <span class="playlist-cover">
          <img v-if="playlist.coverUrl" :src="playlist.coverUrl" :alt="`${playlist.name} 封面`" />
          <span v-else><Music2 :size="30" /></span>
          <i><ListMusic :size="13" /> {{ formatCompactCount(playlist.trackCount) }} 首</i>
        </span>
        <span class="playlist-card-copy">
          <strong>{{ playlist.name }}</strong>
          <small>由 {{ playlist.creatorName || 'QQ 音乐用户' }} 创建</small>
          <span><Headphones :size="11" /> {{ formatCompactCount(playlist.listenCount) }} 次播放</span>
          <em>打开歌单 <ArrowRight :size="12" /></em>
        </span>
      </button>
    </div>

    <div v-else class="playlist-empty">
      <ListMusic :size="24" />
      <span>换一个作品名、歌手或场景关键词试试</span>
    </div>
  </section>
</template>

<style scoped>
.inline-playlist-results{max-width:100%;margin-top:14px;border:1px solid rgba(116,218,201,.16);border-radius:20px;padding:14px;background:radial-gradient(circle at 8% 0,rgba(80,201,184,.1),transparent 32%),linear-gradient(145deg,rgba(30,32,43,.92),rgba(18,20,28,.94));box-shadow:0 18px 48px rgba(0,0,0,.2)}
.playlist-results-header{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:10px;padding:2px 2px 13px}.playlist-heading-mark{display:grid;width:34px;height:34px;place-items:center;border:1px solid rgba(116,218,201,.18);border-radius:11px;background:rgba(116,218,201,.08);color:#74dac9}.playlist-heading-copy{display:grid;min-width:0;gap:3px}.playlist-heading-copy>span{color:#74dac9;font-size:8px;font-weight:850;letter-spacing:.12em}.playlist-heading-copy>strong{overflow:hidden;color:#f0f2f5;font-size:13px;text-overflow:ellipsis;white-space:nowrap}.playlist-heading-copy>small{color:#737986;font-size:9px}.playlist-results-header>button{display:flex;align-items:center;gap:4px;border:1px solid rgba(255,255,255,.09);border-radius:10px;padding:7px 9px;background:rgba(255,255,255,.04);color:#aeb3bd;font-size:9px}.playlist-results-header>button:hover{border-color:rgba(116,218,201,.28);color:#74dac9}
.playlist-profile-reason{margin:-2px 2px 12px;border-left:2px solid rgba(116,218,201,.5);padding:1px 0 1px 8px;color:#818b94;font-size:9px;line-height:1.55}
.inline-playlist-scroller{display:grid;grid-auto-columns:minmax(150px,172px);grid-auto-flow:column;gap:10px;overflow-x:auto;padding:2px 2px 8px;scroll-snap-type:x proximity;scrollbar-width:thin;scrollbar-color:rgba(255,255,255,.15) transparent}.inline-playlist-card{display:grid;min-width:0;overflow:hidden;border:1px solid rgba(255,255,255,.07);border-radius:16px;padding:0;background:rgba(9,11,17,.46);color:inherit;text-align:left;scroll-snap-align:start;transition:transform .18s,border-color .18s,box-shadow .18s}.inline-playlist-card:hover{border-color:rgba(116,218,201,.25);box-shadow:0 12px 28px rgba(0,0,0,.2);transform:translateY(-2px)}
.playlist-cover{position:relative;display:grid;width:100%;aspect-ratio:1;overflow:hidden;background:linear-gradient(145deg,#263c40,#252737);color:#74dac9;place-items:center}.playlist-cover>img{width:100%;height:100%;object-fit:cover;transition:transform .2s}.inline-playlist-card:hover .playlist-cover>img{transform:scale(1.035)}.playlist-cover:after{position:absolute;inset:45% 0 0;background:linear-gradient(transparent,rgba(5,8,12,.76));content:""}.playlist-cover>i{position:absolute;z-index:2;right:8px;bottom:8px;display:flex;align-items:center;gap:4px;border-radius:8px;padding:4px 6px;background:rgba(7,11,16,.72);color:#e6f7f3;font-size:8px;font-style:normal;backdrop-filter:blur(8px)}
.playlist-card-copy{display:grid;gap:5px;padding:10px}.playlist-card-copy>strong,.playlist-card-copy>small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.playlist-card-copy>strong{color:#eef0f3;font-size:11px}.playlist-card-copy>small{color:#7e8490;font-size:8px}.playlist-card-copy>span{display:flex;align-items:center;gap:4px;color:#68717b;font-size:8px}.playlist-card-copy>em{display:flex;align-items:center;justify-content:space-between;margin-top:2px;border-top:1px solid rgba(255,255,255,.06);padding-top:7px;color:#74dac9;font-size:8px;font-style:normal}.inline-playlist-card:hover .playlist-card-copy>strong{color:#8be8d9}
.playlist-empty{display:flex;min-height:90px;align-items:center;justify-content:center;gap:9px;border:1px dashed rgba(116,218,201,.14);border-radius:14px;background:rgba(7,10,15,.24);color:#707986;font-size:10px}.playlist-empty svg{color:#74dac9}
@media(max-width:720px){.inline-playlist-results{padding:11px}.playlist-results-header>button span{display:none}.inline-playlist-scroller{grid-auto-columns:minmax(150px,67vw)}}
</style>
