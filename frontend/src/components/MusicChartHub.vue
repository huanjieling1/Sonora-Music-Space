<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { BarChart3, Disc3, Play, RefreshCw, TrendingUp, UserRound } from 'lucide-vue-next'
import { request } from '../services/api'
import { useMusicStore } from '../stores/music'

const props = defineProps({ conversationId: { type: String, default: '' } })
const router = useRouter()
const music = useMusicStore()
const mode = ref('CHARTS')
const catalog = ref(null)
const groupName = ref('巅峰榜')
const chartId = ref(0)
const chart = ref(null)
const trend = ref(null)
const windowName = ref('RECENT')
const loading = ref(false)
const error = ref('')

const groups = computed(() => catalog.value?.groups || [])
const charts = computed(() => groups.value.find(item => item.name === groupName.value)?.charts || [])
const tracks = computed(() => (chart.value?.entries || []).map(item => ({ ...item.track, _rank: item.rank })))
const windows = [{ id: 'RECENT', label: '近期' }, { id: 'WEEK', label: '一周' }, { id: 'MONTH', label: '一月' }, { id: 'ALL_TIME', label: '已积累' }]

onMounted(loadCatalog)
watch(groupName, () => { chartId.value = charts.value[0]?.id || 0 })
watch(chartId, value => { if (value) loadChart() })
watch(windowName, () => { if (mode.value === 'ARTISTS') loadArtists() })

async function loadCatalog() {
  loading.value = true; error.value = ''
  try {
    const response = await request('/api/music/qq/charts')
    catalog.value = response.data
    if (!groups.value.some(item => item.name === groupName.value)) groupName.value = groups.value[0]?.name || ''
    chartId.value = charts.value.find(item => item.name.includes('热歌'))?.id || charts.value[0]?.id || 0
  } catch (cause) { error.value = cause.message } finally { loading.value = false }
}
async function loadChart() {
  const meta = charts.value.find(item => item.id === chartId.value)
  if (!meta) return
  loading.value = true; error.value = ''
  try { chart.value = (await request(`/api/music/qq/charts/${meta.id}?period=${encodeURIComponent(meta.period)}&limit=100`)).data }
  catch (cause) { error.value = cause.message } finally { loading.value = false }
}
async function loadArtists() {
  loading.value = true; error.value = ''
  try { trend.value = (await request(`/api/music/qq/trending/artists?window=${windowName.value}&limit=20`)).data }
  catch (cause) { error.value = cause.message } finally { loading.value = false }
}
function switchMode(value) { mode.value = value; if (value === 'ARTISTS' && !trend.value) loadArtists() }
function play(track) { music.playTrack(track, tracks.value) }
function openArtist(artist) { if (artist.artistMid) router.push({ name: 'music-qq-artist', params: { artistMid: artist.artistMid } }) }
</script>

<template>
  <section class="chart-hub">
    <header><div><span>QQ MUSIC CHART SIGNAL</span><h2>排行榜与近期趋势</h2><p>官方榜单保留原始名次；歌手趋势由 Sonora 对榜单快照做可解释聚合。</p></div><button :disabled="loading" @click="mode === 'CHARTS' ? loadChart() : loadArtists()"><RefreshCw :class="{ spin: loading }" :size="15" />刷新</button></header>
    <nav class="hub-tabs"><button :class="{ active: mode === 'CHARTS' }" @click="switchMode('CHARTS')"><BarChart3 :size="15" />官方榜单</button><button :class="{ active: mode === 'ARTISTS' }" @click="switchMode('ARTISTS')"><TrendingUp :size="15" />热门歌手</button></nav>
    <template v-if="mode === 'CHARTS'">
      <div class="selectors"><select v-model="groupName" aria-label="榜单分区"><option v-for="group in groups" :key="group.name">{{ group.name }}</option></select><select v-model.number="chartId" aria-label="榜单"><option v-for="item in charts" :key="item.id" :value="item.id">{{ item.name }}</option></select><small v-if="chart">周期 {{ chart.chart.period }} · {{ chart.sourceType }}</small></div>
      <div v-if="tracks.length" class="chart-track-grid"><button v-for="track in tracks.slice(0, 20)" :key="track.id" @click="play(track)"><b>{{ track._rank }}</b><img v-if="track.imageUrl" :src="track.imageUrl" alt="" /><span v-else><Disc3 :size="20" /></span><div><strong>{{ track.name }}</strong><small>{{ track.artists?.join(' / ') }}</small></div><i><Play :size="14" fill="currentColor" /></i></button></div>
    </template>
    <template v-else>
      <div class="window-tabs"><button v-for="item in windows" :key="item.id" :class="{ active: windowName === item.id }" @click="windowName = item.id">{{ item.label }}</button><small v-if="trend">实际覆盖 {{ trend.coverageStart }} 至 {{ trend.coverageEnd }}</small></div>
      <div v-if="trend?.artists?.length" class="artist-trend-grid"><button v-for="artist in trend.artists" :key="artist.artistMid || artist.name" @click="openArtist(artist)"><b>{{ artist.rank }}</b><img v-if="artist.imageUrl" :src="artist.imageUrl" alt="" /><span v-else><UserRound :size="25" /></span><div><strong>{{ artist.name }}</strong><small>{{ artist.chartedTrackCount }} 首上榜 · 最佳第 {{ artist.bestRank }} 名</small></div><em>{{ artist.score }}</em></button></div>
      <p v-else-if="!loading" class="empty">当前实际覆盖范围内还没有足够的榜单观察。</p>
    </template>
    <p v-if="error" class="error">{{ error }}</p>
  </section>
</template>

<style scoped>
.chart-hub{margin:22px 0;border:1px solid rgba(146,122,255,.2);border-radius:24px;padding:20px;background:radial-gradient(circle at 10% 0,rgba(137,108,255,.13),transparent 30%),linear-gradient(145deg,rgba(25,26,38,.93),rgba(15,17,23,.95))}.chart-hub>header{display:flex;justify-content:space-between;gap:18px}.chart-hub>header div{display:grid;gap:4px}.chart-hub>header span{color:#a996ff;font-size:8px;font-weight:850;letter-spacing:.15em}.chart-hub h2{margin:0;color:#f3f3f6;font-size:21px}.chart-hub p{margin:0;color:#737986;font-size:9px}.chart-hub>header>button{display:flex;height:34px;align-items:center;gap:6px;border:1px solid rgba(255,255,255,.09);border-radius:10px;padding:0 10px;background:rgba(255,255,255,.04);color:#aeb2bd}.hub-tabs,.window-tabs{display:flex;align-items:center;gap:7px;margin-top:15px}.hub-tabs button,.window-tabs button{display:flex;align-items:center;gap:5px;border:1px solid rgba(255,255,255,.08);border-radius:10px;padding:7px 10px;background:transparent;color:#858a98}.hub-tabs button.active,.window-tabs button.active{border-color:rgba(184,255,84,.25);background:rgba(184,255,84,.08);color:#b8ff54}.selectors{display:flex;align-items:center;gap:8px;margin:12px 0}.selectors select{border:1px solid rgba(255,255,255,.1);border-radius:10px;padding:8px 10px;background:#191a24;color:#d5d7dd}.selectors small,.window-tabs small{margin-left:auto;color:#6f7583;font-size:8px}.chart-track-grid,.artist-trend-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px}.chart-track-grid>button,.artist-trend-grid>button{display:grid;grid-template-columns:24px 44px minmax(0,1fr) 28px;align-items:center;gap:8px;min-width:0;border:1px solid rgba(255,255,255,.06);border-radius:13px;padding:7px;background:rgba(6,8,13,.32);color:#7e8491;text-align:left}.chart-track-grid>button:hover,.artist-trend-grid>button:hover{border-color:rgba(169,150,255,.28);transform:translateY(-1px)}.chart-track-grid img,.chart-track-grid>button>span,.artist-trend-grid img,.artist-trend-grid>button>span{display:grid;width:44px;height:44px;object-fit:cover;border-radius:10px;background:#252633;place-items:center}.chart-track-grid div,.artist-trend-grid div{display:grid;min-width:0;gap:3px}.chart-track-grid strong,.artist-trend-grid strong,.chart-track-grid small,.artist-trend-grid small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.chart-track-grid strong,.artist-trend-grid strong{color:#e7e8ed;font-size:10px}.chart-track-grid small,.artist-trend-grid small{font-size:8px}.chart-track-grid i{display:grid;width:27px;height:27px;border-radius:50%;background:#b8ff54;color:#11150d;place-items:center}.artist-trend-grid em{color:#b8ff54;font-size:9px;font-style:normal}.empty,.error{padding-top:18px!important}.error{color:#ff8f9a!important}@media(max-width:760px){.chart-track-grid,.artist-trend-grid{grid-template-columns:1fr}.chart-hub>header p{display:none}.selectors{flex-wrap:wrap}.selectors small,.window-tabs small{width:100%;margin:0}}
</style>
