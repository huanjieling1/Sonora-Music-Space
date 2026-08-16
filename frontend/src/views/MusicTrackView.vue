<script setup>
import { computed, nextTick, onBeforeUpdate, ref, watch } from 'vue'
import { ArrowLeft, Disc3, ExternalLink, Languages, Music2 } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import { request } from '../services/api'
import { useMusicStore } from '../stores/music'
import MusicTrackActions from '../components/MusicTrackActions.vue'

const route = useRoute()
const router = useRouter()
const music = useMusicStore()

const lyrics = ref(null)
const loading = ref(false)
const errorMessage = ref('')
const showTranslations = ref(true)
let lyricElements = []
let requestSequence = 0

const provider = computed(() => String(route.params.provider || '').toLowerCase())
const trackId = computed(() => String(route.params.trackId || ''))
const track = computed(() => {
  const candidates = [music.currentTrack, ...music.queue].filter(Boolean)
  return candidates.find(item => item.provider === provider.value && item.id === trackId.value) || null
})
const lyricLines = computed(() => lyrics.value?.lines || [])
const hasTranslations = computed(() => lyricLines.value.some(line => line.translation || line.romanization))
const activeIndex = computed(() => {
  if (!lyrics.value?.synced) return -1
  const currentMs = Math.max(0, Number(music.playbackCurrentTime) * 1000)
  let active = -1
  for (let index = 0; index < lyricLines.value.length; index += 1) {
    const time = lyricLines.value[index].timeMs
    if (time == null || Number(time) > currentMs + 120) break
    active = index
  }
  return active
})
const artistText = computed(() => track.value?.artists?.join(' / ') || '未知艺人')
const sourceLabel = computed(() => ({ qq: 'QQ 音乐', jamendo: 'Jamendo', audius: 'Audius', youtube: 'YouTube' })[provider.value] || provider.value)

onBeforeUpdate(() => { lyricElements = [] })

watch([provider, trackId], loadLyrics, { immediate: true })
watch(activeIndex, async index => {
  if (index < 0) return
  await nextTick()
  lyricElements[index]?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
})

async function loadLyrics() {
  const sequence = ++requestSequence
  loading.value = true
  lyrics.value = null
  errorMessage.value = ''
  try {
    const result = await request(`/api/music/lyrics?provider=${encodeURIComponent(provider.value)}&trackId=${encodeURIComponent(trackId.value)}`)
    if (sequence === requestSequence) lyrics.value = result.data
  } catch (error) {
    if (sequence === requestSequence) errorMessage.value = error.message
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function setLyricElement(element, index) {
  if (element) lyricElements[index] = element
}

function seekToLine(line) {
  if (line.timeMs == null) return
  music.seekTo(Number(line.timeMs) / 1000)
}

function goBack() {
  const previous = window.history.state?.back
  if (typeof previous === 'string' && previous.startsWith('/music')) router.back()
  else router.push('/music')
}
</script>

<template>
  <main class="track-experience">
    <img v-if="track?.imageUrl" class="ambient-cover" :src="track.imageUrl" alt="" aria-hidden="true" />
    <header class="experience-topbar">
      <button title="返回歌单" @click="goBack"><ArrowLeft :size="22" /></button>
      <RouterLink to="/music"><Disc3 :size="20" /><strong>Sonora</strong><span>NOW PLAYING</span></RouterLink>
      <a v-if="track?.externalUrl" :href="track.externalUrl" target="_blank" rel="noreferrer" title="在来源网站查看"><ExternalLink :size="18" /></a>
      <span v-else></span>
    </header>

    <section v-if="track" class="experience-grid">
      <div class="artwork-stage">
        <div class="turntable">
          <div class="record" :class="{ spinning: !music.playbackPaused }">
            <img v-if="track.imageUrl" :src="track.imageUrl" :alt="`${track.name} 封面`" />
            <span v-else><Music2 :size="54" /></span>
          </div>
          <i class="record-hole"></i>
          <div class="tonearm"><span></span></div>
          <small>{{ sourceLabel }}</small>
        </div>
        <div class="artwork-meta">
          <span>正在播放</span>
          <h1>{{ track.name }}</h1>
          <p>{{ artistText }}<template v-if="track.album"> · {{ track.album }}</template></p>
          <MusicTrackActions :track="track" />
        </div>
      </div>

      <section class="lyrics-panel" aria-live="polite">
        <header>
          <div><span>LYRICS</span><h2>{{ track.name }}</h2><p>{{ artistText }}</p></div>
          <button v-if="hasTranslations" :class="{ active: showTranslations }" title="显示或隐藏翻译与音译" @click="showTranslations = !showTranslations"><Languages :size="18" />译</button>
        </header>

        <div class="lyrics-scroller">
          <div v-if="loading" class="lyrics-loading"><i v-for="index in 7" :key="index"></i></div>
          <div v-else-if="errorMessage" class="lyrics-state"><strong>歌词加载失败</strong><p>{{ errorMessage }}</p><button @click="loadLyrics">重新加载</button></div>
          <div v-else-if="!lyrics?.available" class="lyrics-state"><Music2 :size="32" /><strong>暂时没有歌词</strong><p>{{ lyrics?.message || '当前曲库暂未提供这首歌曲的歌词。' }}</p></div>
          <div v-else class="lyrics-list" :class="{ plain: !lyrics.synced }">
            <button
              v-for="(line, index) in lyricLines"
              :key="`${line.timeMs ?? 'plain'}-${index}`"
              :ref="element => setLyricElement(element, index)"
              :class="{ active: index === activeIndex, passed: index < activeIndex }"
              :disabled="line.timeMs == null"
              @click="seekToLine(line)"
            >
              <strong>{{ line.text }}</strong>
              <span v-if="showTranslations && line.translation">{{ line.translation }}</span>
              <small v-if="showTranslations && line.romanization">{{ line.romanization }}</small>
            </button>
          </div>
        </div>
        <footer v-if="lyrics?.available"><span>{{ lyrics.synced ? '点击任意歌词可跳转播放进度' : '纯文本歌词' }}</span><span>歌词来源：{{ lyrics.source }}</span></footer>
      </section>
    </section>

    <section v-else class="missing-track">
      <Disc3 :size="42" />
      <h1>没有找到这首歌曲的播放信息</h1>
      <p>可能是打开了旧链接，请回到歌单后重新点击歌曲标题。</p>
      <RouterLink to="/music">返回音乐空间</RouterLink>
    </section>
  </main>
</template>

<style scoped>
.track-experience{position:relative;height:100vh;overflow:hidden;background:linear-gradient(125deg,#d9edff 0%,#e0f7f3 48%,#d9f8eb 100%);color:#20303a}.ambient-cover{position:absolute;inset:-15%;width:130%;height:130%;object-fit:cover;opacity:.16;filter:blur(70px) saturate(1.25);transform:scale(1.08)}.track-experience:after{position:absolute;inset:0;background:linear-gradient(90deg,rgba(231,244,255,.8),rgba(236,255,249,.76));content:""}.experience-topbar{position:relative;z-index:3;display:grid;height:82px;grid-template-columns:1fr auto 1fr;align-items:center;padding:0 42px}.experience-topbar>button,.experience-topbar>a{display:grid;width:42px;height:42px;place-items:center;border:0;border-radius:50%;background:rgba(255,255,255,.38);color:#37505d;backdrop-filter:blur(14px)}.experience-topbar>a{justify-self:end;text-decoration:none}.experience-topbar>a:nth-child(2){display:flex;width:auto;height:auto;align-items:center;gap:8px;background:transparent;color:#263944}.experience-topbar>a:nth-child(2) span{margin-left:4px;color:#66808d;font-size:9px;font-weight:800;letter-spacing:.16em}.experience-grid{position:relative;z-index:2;display:grid;height:calc(100vh - 82px - 126px);grid-template-columns:minmax(390px,.9fr) minmax(480px,1.1fr);align-items:center;gap:7vw;padding:18px 9vw 4px}.artwork-stage{display:grid;justify-items:center;gap:24px}.turntable{position:relative;width:min(30vw,440px);aspect-ratio:1;border:1px solid rgba(255,255,255,.7);border-radius:52px;background:linear-gradient(145deg,rgba(255,255,255,.82),rgba(237,248,247,.62));box-shadow:0 34px 75px rgba(83,120,140,.22),inset 0 1px 0 white}.record{position:absolute;top:11%;left:10%;width:73%;aspect-ratio:1;overflow:hidden;border:18px solid rgba(31,47,56,.86);border-radius:50%;background:#d8e7e8;box-shadow:0 16px 35px rgba(36,61,73,.22);animation:record-spin 18s linear infinite;animation-play-state:paused}.record.spinning{animation-play-state:running}.record:after{position:absolute;inset:22%;border:1px solid rgba(255,255,255,.7);border-radius:50%;content:""}.record img,.record>span{width:100%;height:100%;object-fit:cover}.record>span{display:grid;place-items:center;color:#6f8b98}.record-hole{position:absolute;z-index:3;top:45%;left:43.8%;width:18px;height:18px;border:6px solid rgba(244,250,249,.95);border-radius:50%;background:#4d6874}.tonearm{position:absolute;z-index:4;top:7%;right:9%;width:18%;height:68%;border-radius:24px;transform:rotate(-10deg);transform-origin:50% 12%;background:linear-gradient(90deg,#d2d9d9,#fff,#aebabc);box-shadow:4px 7px 12px rgba(51,70,78,.2)}.tonearm:before{position:absolute;top:-13%;left:-28%;width:150%;aspect-ratio:1;border:8px solid #dce4e3;border-radius:50%;background:#f8fbfa;box-shadow:0 7px 17px rgba(52,75,84,.2);content:""}.tonearm span{position:absolute;right:-25%;bottom:-3%;width:145%;height:13%;border-radius:6px;background:#d6dedf;transform:rotate(18deg)}.turntable>small{position:absolute;right:7%;bottom:5%;border-radius:999px;padding:6px 9px;background:#4f95ff;color:white;font-size:8px;font-weight:800;letter-spacing:.08em}.artwork-meta{max-width:500px;text-align:center}.artwork-meta>span{color:#4f95ff;font-size:9px;font-weight:850;letter-spacing:.18em}.artwork-meta h1{overflow:hidden;margin:8px 0 4px;text-overflow:ellipsis;white-space:nowrap;font-size:25px}.artwork-meta p{margin:0;color:#657984;font-size:12px}.lyrics-panel{display:grid;height:min(68vh,720px);grid-template-rows:auto minmax(0,1fr) auto;overflow:hidden}.lyrics-panel>header{display:flex;align-items:end;justify-content:space-between;border-bottom:1px solid rgba(63,91,105,.12);padding:0 18px 16px}.lyrics-panel>header span{color:#4f95ff;font-size:9px;font-weight:850;letter-spacing:.18em}.lyrics-panel h2{margin:7px 0 3px;font-size:24px}.lyrics-panel header p{margin:0;color:#607985;font-size:11px}.lyrics-panel>header button{display:flex;align-items:center;gap:4px;border:1px solid rgba(55,91,109,.16);border-radius:11px;padding:8px 10px;background:rgba(255,255,255,.32);color:#617884}.lyrics-panel>header button.active{border-color:rgba(79,149,255,.4);color:#317be7}.lyrics-scroller{min-height:0;overflow-y:auto;scrollbar-width:thin;scrollbar-color:rgba(83,122,140,.24) transparent;mask-image:linear-gradient(transparent,#000 8%,#000 92%,transparent)}.lyrics-list{display:grid;gap:2px;padding:42% 18px}.lyrics-list button{display:grid;justify-items:start;border:0;border-radius:15px;padding:13px 16px;background:transparent;color:rgba(47,67,77,.42);text-align:left;transition:color .2s,transform .2s,background .2s}.lyrics-list button:not(:disabled){cursor:pointer}.lyrics-list button:hover{background:rgba(255,255,255,.3);color:#536d79}.lyrics-list button strong{font-size:21px;font-weight:560;line-height:1.35}.lyrics-list button span{margin-top:5px;font-size:14px}.lyrics-list button small{margin-top:4px;color:inherit;font-size:11px}.lyrics-list button.passed{color:rgba(47,67,77,.25)}.lyrics-list button.active{background:rgba(255,255,255,.38);color:#317be7;transform:translateX(8px)}.lyrics-list button.active strong{font-size:26px;font-weight:720}.lyrics-list.plain{padding-top:40px;padding-bottom:160px}.lyrics-state,.lyrics-loading{display:grid;min-height:100%;place-items:center;align-content:center;gap:10px;color:#66808c;text-align:center}.lyrics-state strong{color:#324b57;font-size:16px}.lyrics-state p{max-width:360px;margin:0;font-size:12px}.lyrics-state button,.missing-track a{border:0;border-radius:999px;padding:9px 14px;background:#4f95ff;color:white;text-decoration:none}.lyrics-loading{justify-items:start;padding:80px 20px}.lyrics-loading i{display:block;width:70%;height:20px;border-radius:10px;background:rgba(255,255,255,.44);animation:pulse 1.4s ease-in-out infinite}.lyrics-loading i:nth-child(2n){width:48%}.lyrics-panel>footer{display:flex;justify-content:space-between;border-top:1px solid rgba(63,91,105,.1);padding:12px 18px;color:#718893;font-size:9px}.missing-track{position:relative;z-index:3;display:grid;height:calc(100vh - 120px);place-items:center;align-content:center;gap:12px;text-align:center}.missing-track h1,.missing-track p{margin:0}.missing-track p{color:#617885;font-size:12px}@keyframes record-spin{to{transform:rotate(360deg)}}@keyframes pulse{50%{opacity:.35}}@media(max-width:900px){.experience-topbar{height:68px;padding:0 18px}.experience-topbar>a:nth-child(2) span{display:none}.experience-grid{height:calc(100vh - 68px - 146px);grid-template-columns:1fr;padding:0 18px}.artwork-stage{display:none}.lyrics-panel{height:100%}.lyrics-panel>header{padding-top:8px}.lyrics-list{padding-top:45%;padding-bottom:45%}.lyrics-list button strong{font-size:18px}.lyrics-list button.active strong{font-size:22px}}
</style>
