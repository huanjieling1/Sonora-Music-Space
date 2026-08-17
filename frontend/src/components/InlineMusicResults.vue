<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Disc3, ListMusic, Play, Sparkles } from 'lucide-vue-next'
import MusicTrackActions from './MusicTrackActions.vue'
import { useMusicStore } from '../stores/music'
import { returnState } from '../services/navigation'

const props = defineProps({
  actions: { type: Array, default: () => [] },
})

const router = useRouter()
const route = useRoute()
const music = useMusicStore()

const recommendation = computed(() => {
  for (let index = props.actions.length - 1; index >= 0; index -= 1) {
    const action = props.actions[index]
    if (action?.type === 'SHOW_MUSIC_RESULTS' && action.recommendation) return action.recommendation
  }
  return null
})

const tracks = computed(() => (recommendation.value?.tracks || []).map(track => ({
  ...track,
  _searchId: recommendation.value.searchId,
})))

const statusLabel = computed(() => {
  const status = recommendation.value?.personalizationStatus
  if (status === 'ACTIVE') return '个性化排序'
  if (status === 'COLD_START') return '正在了解你的偏好'
  if (status === 'DEGRADED') return '基础推荐'
  return '音乐结果'
})

function artists(track) {
  return Array.isArray(track?.artists) && track.artists.length ? track.artists.join(' / ') : '未知歌手'
}

function formatDuration(durationMs) {
  const totalSeconds = Math.max(0, Math.round(Number(durationMs || 0) / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  return `${minutes}:${String(totalSeconds % 60).padStart(2, '0')}`
}

function providerLabel(provider) {
  return { qq: 'QQ 音乐', jamendo: 'Jamendo', audius: 'Audius', youtube: 'YouTube' }[provider] || provider || '音乐'
}

function playTrack(track) {
  music.playTrack(track, tracks.value)
}

function addAllToQueue() {
  tracks.value.forEach(track => music.addToQueue(track))
}

function openTrack(track) {
  if (!track?.id) return
  music.playTrack(track, tracks.value)
  router.push({
    name: 'music-track',
    params: { provider: track.provider || 'unknown', trackId: track.id },
    state: returnState(route),
  })
}
</script>

<template>
  <section v-if="recommendation && tracks.length" class="inline-music-results" aria-label="音乐推荐结果">
    <header class="inline-results-header">
      <div>
        <span><Sparkles :size="13" /> {{ statusLabel }}</span>
        <strong>为你找到 {{ tracks.length }} 首音乐</strong>
        <small v-if="recommendation.explanation">{{ recommendation.explanation }}</small>
      </div>
      <button type="button" title="全部加入播放列表" @click="addAllToQueue">
        <ListMusic :size="15" />
        <span>全部加入</span>
      </button>
    </header>

    <div class="inline-track-scroller">
      <article v-for="(track, index) in tracks" :key="`${track.provider || 'music'}:${track.id}`" class="inline-track-card">
        <button class="inline-cover" type="button" :aria-label="`播放 ${track.name}`" @click="playTrack(track)">
          <img v-if="track.imageUrl" :src="track.imageUrl" :alt="`${track.name} 封面`" />
          <span v-else><Disc3 :size="28" /></span>
          <i><Play :size="18" fill="currentColor" /></i>
          <em>{{ String(index + 1).padStart(2, '0') }}</em>
        </button>

        <div class="inline-track-copy">
          <button type="button" :title="`打开 ${track.name} 的歌词页面`" @click="openTrack(track)">
            <strong>{{ track.name }}</strong>
            <small>{{ artists(track) }}</small>
          </button>
          <p v-if="track.reasonText">{{ track.reasonText }}</p>
          <p v-else-if="track.relationLabel">{{ track.relationLabel }}</p>
          <p v-else>{{ track.album || '来自真实在线曲库' }}</p>
          <footer>
            <span>{{ providerLabel(track.provider) }} · {{ formatDuration(track.durationMs) }}</span>
            <MusicTrackActions :track="track" compact />
          </footer>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.inline-music-results{max-width:100%;margin-top:14px;border:1px solid rgba(184,255,84,.14);border-radius:20px;padding:14px;background:radial-gradient(circle at 8% 0,rgba(184,255,84,.08),transparent 30%),linear-gradient(145deg,rgba(31,32,43,.9),rgba(19,20,28,.92));box-shadow:0 18px 48px rgba(0,0,0,.2)}
.inline-results-header{display:flex;align-items:flex-start;justify-content:space-between;gap:14px;padding:2px 2px 12px}.inline-results-header>div{display:grid;min-width:0;gap:4px}.inline-results-header div>span{display:flex;align-items:center;gap:5px;color:#b8ff54;font-size:8px;font-weight:850;letter-spacing:.12em}.inline-results-header strong{font-size:13px;color:#f1f2f5}.inline-results-header small{display:-webkit-box;overflow:hidden;max-width:560px;color:#737886;font-size:9px;line-height:1.5;-webkit-box-orient:vertical;-webkit-line-clamp:2}.inline-results-header>button{display:flex;flex:0 0 auto;align-items:center;gap:5px;border:1px solid rgba(255,255,255,.09);border-radius:10px;padding:7px 9px;background:rgba(255,255,255,.04);color:#aeb2bd;font-size:9px}.inline-results-header>button:hover{border-color:rgba(184,255,84,.25);color:#b8ff54}
.inline-track-scroller{display:grid;grid-auto-columns:minmax(154px,176px);grid-auto-flow:column;gap:10px;overflow-x:auto;padding:2px 2px 8px;scroll-snap-type:x proximity;scrollbar-width:thin;scrollbar-color:rgba(255,255,255,.15) transparent}.inline-track-card{min-width:0;overflow:hidden;border:1px solid rgba(255,255,255,.07);border-radius:15px;background:rgba(9,10,16,.42);scroll-snap-align:start;transition:transform .18s,border-color .18s}.inline-track-card:hover{border-color:rgba(184,255,84,.2);transform:translateY(-2px)}
.inline-cover{position:relative;display:grid;width:100%;aspect-ratio:1.55;overflow:hidden;border:0;padding:0;background:linear-gradient(145deg,#343044,#1c2330);color:#858a98;place-items:center}.inline-cover img{width:100%;height:100%;object-fit:cover;transition:transform .2s}.inline-track-card:hover .inline-cover img{transform:scale(1.035)}.inline-cover:after{position:absolute;inset:0;background:linear-gradient(transparent 45%,rgba(7,8,13,.72));content:""}.inline-cover>i{position:absolute;z-index:2;right:9px;bottom:8px;display:grid;width:33px;height:33px;place-items:center;border-radius:50%;background:#b8ff54;color:#11150d;opacity:0;transform:translateY(4px);transition:.16s}.inline-track-card:hover .inline-cover>i,.inline-cover:focus-visible>i{opacity:1;transform:none}.inline-cover>em{position:absolute;z-index:2;bottom:8px;left:9px;color:rgba(255,255,255,.72);font-size:8px;font-style:normal;letter-spacing:.08em}
.inline-track-copy{display:grid;gap:7px;padding:10px}.inline-track-copy>button{display:grid;min-width:0;gap:3px;border:0;padding:0;background:transparent;color:inherit;text-align:left}.inline-track-copy>button:hover strong{color:#b8ff54}.inline-track-copy strong,.inline-track-copy small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.inline-track-copy strong{color:#eceef2;font-size:11px}.inline-track-copy small{color:#7d828f;font-size:8px}.inline-track-copy>p{display:-webkit-box;min-height:26px;overflow:hidden;margin:0;color:#696f7c;font-size:8px;line-height:1.55;-webkit-box-orient:vertical;-webkit-line-clamp:2}.inline-track-copy footer{display:flex;min-width:0;align-items:center;justify-content:space-between;gap:4px}.inline-track-copy footer>span{overflow:hidden;color:#626875;font-size:7px;text-overflow:ellipsis;white-space:nowrap}
@media(max-width:720px){.inline-music-results{padding:11px}.inline-results-header small{display:none}.inline-results-header>button span{display:none}.inline-track-scroller{grid-auto-columns:minmax(145px,68vw)}}
</style>
