<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { BarChart3, Disc3, Play, TrendingUp } from 'lucide-vue-next'
import { useMusicStore } from '../stores/music'

const props = defineProps({ actions: { type: Array, default: () => [] } })
const router = useRouter()
const music = useMusicStore()

const result = computed(() => {
  for (let index = props.actions.length - 1; index >= 0; index -= 1) {
    const action = props.actions[index]
    if (action?.type === 'SHOW_QQ_CHART_RESULTS' && action.chartResult) return action.chartResult
  }
  return null
})
const chart = computed(() => result.value?.officialChart || null)
const trend = computed(() => result.value?.trendReport || null)
const chartTracks = computed(() => (chart.value?.entries || []).map(item => ({ ...item.track, _rank: item.rank })))
const trendTracks = computed(() => (trend.value?.tracks || []).map(item => ({ ...item.track, _rank: item.rank, _score: item.score })))

function artists(track) { return track?.artists?.length ? track.artists.join(' / ') : '未知歌手' }
function play(track, queue) { music.playTrack(track, queue) }
function openArtist(artist) {
  if (artist?.artistMid) router.push({ name: 'music-qq-artist', params: { artistMid: artist.artistMid } })
}
</script>

<template>
  <section v-if="result" class="chart-results">
    <header>
      <div><span><BarChart3 :size="13" /> {{ chart ? 'QQ MUSIC · OFFICIAL' : 'SONORA · CHART SIGNAL' }}</span>
        <strong>{{ chart?.chart?.name || trend?.title }}</strong>
        <small v-if="chart">{{ chart.chart.group }} · 周期 {{ chart.chart.period }} · QQ 音乐官方名次</small>
        <small v-else>{{ trend.methodology }} 实际覆盖 {{ trend.coverageStart }} 至 {{ trend.coverageEnd }}</small>
      </div>
    </header>

    <div v-if="chartTracks.length || trendTracks.length" class="track-list">
      <button v-for="track in (chartTracks.length ? chartTracks : trendTracks)" :key="track.id" @click="play(track, chartTracks.length ? chartTracks : trendTracks)">
        <b>{{ String(track._rank).padStart(2, '0') }}</b><img v-if="track.imageUrl" :src="track.imageUrl" alt="" /><span v-else><Disc3 :size="20" /></span>
        <div><strong>{{ track.name }}</strong><small>{{ artists(track) }}</small></div><i><Play :size="14" fill="currentColor" /></i>
      </button>
    </div>

    <div v-else-if="trend?.artists?.length" class="artist-list">
      <button v-for="artist in trend.artists" :key="artist.artistMid || artist.name" @click="openArtist(artist)">
        <b>{{ String(artist.rank).padStart(2, '0') }}</b><img v-if="artist.imageUrl" :src="artist.imageUrl" alt="" /><span v-else><TrendingUp :size="20" /></span>
        <div><strong>{{ artist.name }}</strong><small>{{ artist.chartedTrackCount }} 首上榜 · 最佳第 {{ artist.bestRank }} 名</small></div><em>{{ artist.score }}</em>
      </button>
    </div>
    <p v-else>当前实际覆盖范围内还没有足够的榜单观察。</p>
  </section>
</template>

<style scoped>
.chart-results{margin-top:14px;border:1px solid rgba(143,116,255,.24);border-radius:20px;padding:15px;background:radial-gradient(circle at 12% 0,rgba(143,116,255,.13),transparent 32%),linear-gradient(145deg,rgba(29,29,43,.94),rgba(16,18,25,.94))}.chart-results>header div{display:grid;gap:4px}.chart-results>header span{display:flex;align-items:center;gap:5px;color:#a996ff;font-size:8px;font-weight:850;letter-spacing:.13em}.chart-results>header strong{color:#f3f3f7;font-size:14px}.chart-results>header small{color:#747986;font-size:8px;line-height:1.5}.track-list,.artist-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px;margin-top:13px}.track-list>button,.artist-list>button{display:grid;grid-template-columns:25px 42px minmax(0,1fr) 28px;align-items:center;gap:8px;min-width:0;border:1px solid rgba(255,255,255,.07);border-radius:13px;padding:7px;background:rgba(8,9,15,.38);color:#858a98;text-align:left}.track-list>button:hover,.artist-list>button:hover{border-color:rgba(169,150,255,.32);background:rgba(143,116,255,.07)}.track-list img,.track-list>button>span,.artist-list img,.artist-list>button>span{display:grid;width:42px;height:42px;object-fit:cover;border-radius:10px;background:#252633;place-items:center}.track-list b,.artist-list b{font-size:8px;font-weight:700}.track-list div,.artist-list div{display:grid;min-width:0;gap:3px}.track-list strong,.artist-list strong,.track-list small,.artist-list small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.track-list strong,.artist-list strong{color:#e7e8ed;font-size:10px}.track-list small,.artist-list small{font-size:8px}.track-list i{display:grid;width:27px;height:27px;border-radius:50%;background:#b8ff54;color:#11150d;place-items:center}.artist-list em{color:#b8ff54;font-size:9px;font-style:normal}.chart-results>p{margin:13px 0 0;color:#777c89;font-size:9px}@media(max-width:760px){.track-list,.artist-list{grid-template-columns:1fr}}
</style>
