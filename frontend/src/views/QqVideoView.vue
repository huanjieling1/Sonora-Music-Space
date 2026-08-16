<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowLeft, ExternalLink, LoaderCircle, Play, Video } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import { request } from '../services/api'

const route = useRoute()
const router = useRouter()
const playback = ref(null)
const loading = ref(true)
const errorMessage = ref('')
const videoId = computed(() => String(route.params.videoId || ''))
const title = computed(() => String(route.query.title || 'QQ 音乐视频'))
const cover = computed(() => String(route.query.cover || ''))
const artists = computed(() => String(route.query.artists || 'QQ 音乐'))
const publishDate = computed(() => String(route.query.publishDate || ''))

onMounted(loadVideo)
watch(videoId, loadVideo)

async function loadVideo() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await request(`/api/music/qq/videos/${encodeURIComponent(videoId.value)}`)
    playback.value = result.data
  } catch (error) {
    errorMessage.value = error.message
  } finally { loading.value = false }
}

function goBack() {
  const previous = window.history.state?.back
  if (typeof previous === 'string' && previous.startsWith('/music')) router.back()
  else router.push('/music')
}
</script>

<template>
  <main class="qq-video-page">
    <img v-if="cover" class="video-ambient" :src="cover" alt="" aria-hidden="true" />
    <header><button title="返回搜索结果" @click="goBack"><ArrowLeft :size="20" /></button><div><Video :size="18" /><strong>Sonora</strong><span>QQ MUSIC VIDEO</span></div><a v-if="playback?.externalUrl" :href="playback.externalUrl" target="_blank" rel="noreferrer" title="在 QQ 音乐查看"><ExternalLink :size="18" /></a></header>
    <section class="video-stage">
      <div v-if="loading" class="video-state"><LoaderCircle class="spin" :size="32" /><strong>正在准备高清播放地址</strong></div>
      <div v-else-if="errorMessage" class="video-state"><Video :size="42" /><strong>视频暂时无法播放</strong><p>{{ errorMessage }}</p><button @click="loadVideo">重新加载</button></div>
      <video v-else :src="playback.playbackUrl" :poster="cover || undefined" controls autoplay playsinline preload="metadata">你的浏览器不支持视频播放。</video>
    </section>
    <section class="video-info"><span>QQ MUSIC · MV</span><h1>{{ title }}</h1><p>{{ artists }}<template v-if="publishDate"> · {{ publishDate }}</template></p><small>播放地址由 QQ 音乐实时签发，不在本地保存视频文件。</small></section>
  </main>
</template>

<style scoped>
.qq-video-page{position:relative;min-height:100vh;overflow:hidden;padding-bottom:125px;background:#090a10;color:#f4f4f6}.qq-video-page:after{position:absolute;z-index:0;inset:0;background:linear-gradient(rgba(7,8,13,.72),#090a10 78%);content:""}.video-ambient{position:absolute;inset:-25%;width:150%;height:150%;object-fit:cover;opacity:.27;filter:blur(90px) saturate(1.25)}header,.video-stage,.video-info{position:relative;z-index:1}.qq-video-page>header{display:grid;height:78px;grid-template-columns:1fr auto 1fr;align-items:center;padding:0 34px}.qq-video-page header>button,.qq-video-page header>a{display:grid;width:42px;height:42px;place-items:center;border:1px solid rgba(255,255,255,.1);border-radius:50%;background:rgba(255,255,255,.05);color:#d7d9df}.qq-video-page header>a{justify-self:end}.qq-video-page header>div{display:flex;align-items:center;gap:8px}.qq-video-page header span{margin-left:5px;color:#777d8b;font-size:8px;font-weight:850;letter-spacing:.17em}.video-stage{width:min(1280px,calc(100vw - 80px));aspect-ratio:16/9;max-height:calc(100vh - 240px);margin:12px auto 0;overflow:hidden;border:1px solid rgba(255,255,255,.12);border-radius:22px;background:#030407;box-shadow:0 32px 100px rgba(0,0,0,.6)}.video-stage video{width:100%;height:100%;object-fit:contain;background:#000}.video-state{display:grid;width:100%;height:100%;place-items:center;align-content:center;gap:12px;color:#7e8492;text-align:center}.video-state strong{color:#e5e7ec;font-size:16px}.video-state p{margin:0}.video-state button{border:0;border-radius:999px;padding:9px 14px;background:#b8ff54;color:#11150d}.video-info{width:min(1280px,calc(100vw - 80px));margin:23px auto}.video-info>span{color:#b8ff54;font-size:8px;font-weight:850;letter-spacing:.18em}.video-info h1{margin:7px 0 6px;font-size:28px}.video-info p{margin:0;color:#a0a4af;font-size:12px}.video-info small{display:block;margin-top:8px;color:#555b68;font-size:9px}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:760px){.video-stage,.video-info{width:calc(100vw - 24px)}.qq-video-page>header{padding:0 12px}.qq-video-page header span{display:none}}
</style>
