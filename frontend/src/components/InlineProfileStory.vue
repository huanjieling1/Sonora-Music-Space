<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ArrowDown, Disc3, Sparkles, Waves, X } from 'lucide-vue-next'
import { conciseProfileSummary } from '../services/profileSummary'

const props = defineProps({
  actions: { type: Array, default: () => [] },
})

const open = ref(false)
const activeIndex = ref(0)
const report = ref(null)
const slides = ref([])
let observer = null
let previousOverflow = ''

const story = computed(() => {
  for (let index = props.actions.length - 1; index >= 0; index -= 1) {
    const action = props.actions[index]
    if (action?.type === 'SHOW_MUSIC_PROFILE_STORY' && action.profileStory) return action.profileStory
  }
  return null
})

const topTracks = computed(() => story.value?.topTracks || [])
const topArtists = computed(() => story.value?.topArtists || [])
const topTags = computed(() => story.value?.topTags || [])
const labels = computed(() => story.value?.labels || [])
const visualTags = computed(() => topTags.value.length ? topTags.value : topArtists.value)
const orbitItems = computed(() => {
  const values = [...topTracks.value, ...topArtists.value, ...topTags.value]
  return values.length ? values.slice(0, 12) : [{ name: '等待第一首歌', count: 0, strength: .3 }]
})
const leadArtist = computed(() => topArtists.value[0]?.name || '还未命名的旋律')
const leadTag = computed(() => topTags.value[0]?.name || labels.value[0]?.name || story.value?.stageLabel || '正在显影')
const totalMinutes = computed(() => Math.max(0, Math.round(Number(story.value?.totalPlaybackMs || 0) / 60000)))
const listeningTime = computed(() => {
  if (totalMinutes.value < 60) return `${totalMinutes.value} 分钟`
  const hours = Math.floor(totalMinutes.value / 60)
  const minutes = totalMinutes.value % 60
  return minutes ? `${hours} 小时 ${minutes} 分` : `${hours} 小时`
})
const completion = computed(() => Math.round(Number(story.value?.completionRate || 0) * 100))
const storyTitle = computed(() => story.value?.profileReady ? '你的旋律，已有了清晰的光' : '你的旋律，正在慢慢显影')
const profileSummary = computed(() => conciseProfileSummary(story.value?.narrative, storyTitle.value))
const summarySignals = computed(() => {
  const values = [leadArtist.value, leadTag.value, story.value?.stageLabel]
  return [...new Set(values.filter(Boolean))].slice(0, 3)
})
const slideCount = 6
const palette = ['#b8ff54', '#9e8cff', '#68dbb8', '#ff9c78', '#74a8ff', '#f0d884']

function openStory() {
  open.value = true
  activeIndex.value = 0
}

function closeStory() {
  open.value = false
}

function registerSlide(element, index) {
  if (element) slides.value[index] = element
}

function goTo(index) {
  slides.value[index]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function onKeydown(event) {
  if (event.key === 'Escape') closeStory()
  if (event.key === 'ArrowDown' || event.key === 'PageDown') goTo(Math.min(slideCount - 1, activeIndex.value + 1))
  if (event.key === 'ArrowUp' || event.key === 'PageUp') goTo(Math.max(0, activeIndex.value - 1))
}

function tileStyle(item, index) {
  const strength = Math.max(.18, Number(item?.strength || 0))
  return {
    '--tile-strength': strength,
    '--tile-delay': `${index * 85}ms`,
    '--tile-color': palette[index % palette.length],
    '--tile-basis': `${Math.max(25, Math.min(48, 24 + strength * 24))}%`,
  }
}

function orbitStyle(index) {
  const angle = (360 / Math.max(orbitItems.value.length, 1)) * index
  const radius = 28 + (index % 3) * 8
  return {
    '--orbit-angle': `${angle}deg`,
    '--orbit-radius': `${radius}vmin`,
    '--orbit-delay': `${index * 90}ms`,
    '--orbit-color': palette[index % palette.length],
  }
}

function wavePath(index) {
  const amplitude = Math.max(10, 34 - index * 4)
  const middle = 50
  return `M0 ${middle} C80 ${middle - amplitude}, 120 ${middle + amplitude}, 200 ${middle} S320 ${middle - amplitude}, 400 ${middle} S520 ${middle + amplitude}, 600 ${middle} S720 ${middle - amplitude}, 800 ${middle}`
}

function waveStyle(item, index) {
  return {
    '--wave-color': palette[index % palette.length],
    '--wave-opacity': Math.max(.35, Number(item?.strength || 0)),
    '--wave-delay': `${index * -420}ms`,
  }
}

watch(open, async value => {
  if (value) {
    previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onKeydown)
    await nextTick()
    observer = new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (entry.isIntersecting && entry.intersectionRatio > .55) {
          activeIndex.value = Number(entry.target.dataset.index || 0)
        }
      })
    }, { root: report.value, threshold: [.55, .75] })
    slides.value.forEach(slide => observer.observe(slide))
    report.value?.focus()
  } else {
    document.body.style.overflow = previousOverflow
    window.removeEventListener('keydown', onKeydown)
    observer?.disconnect()
    observer = null
  }
})

onBeforeUnmount(() => {
  document.body.style.overflow = previousOverflow
  window.removeEventListener('keydown', onKeydown)
  observer?.disconnect()
})
</script>

<template>
  <section v-if="story" class="profile-story-card">
    <div class="story-card-visual" aria-hidden="true">
      <i v-for="index in 7" :key="index" :style="{ '--i': index }"></i>
      <Disc3 :size="24" />
    </div>
    <div class="story-card-copy">
      <span><Sparkles :size="12" /> 你的音乐画像</span>
      <strong>{{ storyTitle }}</strong>
      <p>{{ profileSummary }}</p>
      <div class="story-card-signals">
        <small v-for="signal in summarySignals" :key="signal">{{ signal }}</small>
      </div>
    </div>
    <button type="button" @click="openStory">
      <span>查看视觉画像</span>
      <ArrowDown :size="15" />
    </button>
  </section>

  <Teleport to="body">
    <Transition name="story-shell">
      <div v-if="open && story" class="profile-story-shell" role="dialog" aria-modal="true" aria-label="我的音乐画像">
        <header class="story-toolbar">
          <div class="story-brand"><i></i><span>SONORA / MUSIC PORTRAIT</span></div>
          <div class="story-progress" aria-label="报告进度">
            <button
              v-for="index in slideCount"
              :key="index"
              type="button"
              :class="{ active: activeIndex === index - 1 }"
              :aria-label="`前往第 ${index} 幕`"
              @click="goTo(index - 1)"
            ></button>
          </div>
          <button class="story-close" type="button" aria-label="关闭音乐画像" @click="closeStory"><X :size="20" /></button>
        </header>

        <main ref="report" class="profile-story-report" tabindex="-1">
          <section :ref="el => registerSlide(el, 0)" data-index="0" class="story-scene scene-opening" :class="{ revealed: activeIndex === 0 }">
            <div class="ambient-orb orb-a"></div><div class="ambient-orb orb-b"></div>
            <div class="orbit-field" aria-hidden="true">
              <span v-for="(item, index) in orbitItems" :key="`${item.name}-${index}`" :style="orbitStyle(index)">
                <b>{{ String(index + 1).padStart(2, '0') }}</b><em>{{ item.name }}</em>
              </span>
              <i></i><i></i><i></i>
            </div>
            <div class="scene-copy opening-copy">
              <span class="scene-kicker">YOUR SONORA PORTRAIT · {{ story.stageLabel }}</span>
              <h1>{{ storyTitle }}</h1>
              <p>{{ story.profileReady ? '每一次重逢与探索，都在这里留下真实的回声。' : '先不急着定义你，让每一次播放慢慢成为坐标。' }}</p>
            </div>
            <button class="story-next" type="button" @click="goTo(1)"><span>沿着声音向下</span><ArrowDown :size="18" /></button>
          </section>

          <section :ref="el => registerSlide(el, 1)" data-index="1" class="story-scene scene-journey" :class="{ revealed: activeIndex === 1 }">
            <div class="journey-rings" aria-hidden="true"><i></i><i></i><i></i><i></i></div>
            <div class="scene-copy journey-copy">
              <span class="scene-kicker">LISTENING ORBIT</span>
              <p>你与音乐真正相遇了</p>
              <h2><strong>{{ story.playCount }}</strong><small>次</small></h2>
              <div class="journey-metrics">
                <article><strong>{{ story.uniqueTracks }}</strong><span>首不同旋律</span></article>
                <article><strong>{{ listeningTime }}</strong><span>真实收听时间</span></article>
                <article><strong>{{ completion }}%</strong><span>完整走过的比例</span></article>
              </div>
            </div>
          </section>

          <section :ref="el => registerSlide(el, 2)" data-index="2" class="story-scene scene-constellation" :class="{ revealed: activeIndex === 2 }">
            <div class="constellation-lines" aria-hidden="true"></div>
            <div class="scene-copy constellation-copy">
              <span class="scene-kicker">FAMILIAR VOICES</span>
              <p>有些声音，不只路过一次</p>
              <h2>{{ leadArtist }}</h2>
              <div class="artist-constellation">
                <article v-for="(artist, index) in topArtists.slice(0, 6)" :key="artist.name" :class="{ lead: index === 0 }" :style="{ '--delay': `${index * 110}ms` }">
                  <i>{{ artist.name.slice(0, 1) }}</i>
                  <strong>{{ artist.name }}</strong>
                  <span>{{ artist.count }} 次相遇 · {{ artist.detail }}</span>
                </article>
                <article v-if="!topArtists.length" class="lead"><i>♪</i><strong>等待熟悉的声音</strong><span>继续播放后，星座会在这里亮起</span></article>
              </div>
            </div>
          </section>

          <section :ref="el => registerSlide(el, 3)" data-index="3" class="story-scene scene-scale" :class="{ revealed: activeIndex === 3 }">
            <div class="scene-copy scale-copy">
              <span class="scene-kicker">TASTE SCALE</span>
              <h2>你的曲风音阶，<br /><em>一格一格浮现</em></h2>
              <div class="taste-tiles">
                <article
                  v-for="(tag, index) in visualTags.slice(0, 8)"
                  :key="`${tag.name}-${index}`"
                  :class="{ dominant: index === 0 }"
                  :style="tileStyle(tag, index)"
                >
                  <span>{{ index === 0 ? '主旋律' : `0${index + 1}` }}</span>
                  <strong>{{ tag.name }}</strong>
                  <small>{{ tag.detail || `${tag.count} 次回响` }}</small>
                </article>
                <article v-if="!visualTags.length" class="dominant empty-tile"><span>等待采样</span><strong>未知曲风</strong><small>新的播放会点亮这里</small></article>
              </div>
            </div>
          </section>

          <section :ref="el => registerSlide(el, 4)" data-index="4" class="story-scene scene-waves" :class="{ revealed: activeIndex === 4 }">
            <div class="scene-copy waves-copy">
              <span class="scene-kicker">TASTE IN MOTION</span>
              <h2>你的偏好不是标签，<br />而是仍在流动的声场</h2>
              <div class="wave-stack">
                <article v-for="(tag, index) in visualTags.slice(0, 5)" :key="tag.name" :class="{ dominant: index === 0 }" :style="waveStyle(tag, index)">
                  <div><strong>{{ tag.name }}</strong><span>{{ tag.count }} 次回响</span></div>
                  <svg viewBox="0 0 800 100" preserveAspectRatio="none" aria-hidden="true">
                    <path :d="wavePath(index)" />
                    <path :d="wavePath(index)" class="echo" />
                  </svg>
                </article>
                <article v-if="!visualTags.length" class="dominant waiting-wave" :style="waveStyle({ strength: .8 }, 0)">
                  <div><strong>正在采集</strong><span>等待更多播放</span></div><svg viewBox="0 0 800 100" preserveAspectRatio="none"><path :d="wavePath(0)" /></svg>
                </article>
              </div>
              <p><Waves :size="16" /> 最明亮的波形来自 {{ leadTag }}</p>
            </div>
          </section>

          <section :ref="el => registerSlide(el, 5)" data-index="5" class="story-scene scene-finale" :class="{ revealed: activeIndex === 5 }">
            <div class="finale-glow" aria-hidden="true"></div>
            <div class="scene-copy finale-copy">
              <span class="scene-kicker">A PORTRAIT, NOT A DEFINITION</span>
              <h2>音乐从不急着<br />把你定义</h2>
              <p class="narrative">{{ profileSummary }}</p>
              <div v-if="labels.length" class="story-labels">
                <span v-for="label in labels" :key="label.name">{{ label.name }}</span>
              </div>
              <p class="final-note">这里只呈现可核对的音乐行为。下一首歌，仍可以改变画像的方向。</p>
              <button type="button" @click="closeStory"><Sparkles :size="15" /> 带着这幅画像继续听</button>
            </div>
          </section>
        </main>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.profile-story-card{position:relative;display:grid;grid-template-columns:64px minmax(0,1fr) auto;align-items:center;gap:15px;overflow:hidden;margin-top:4px;border:1px solid rgba(158,140,255,.2);border-radius:21px;padding:15px;background:radial-gradient(circle at 15% 0,rgba(158,140,255,.2),transparent 34%),radial-gradient(circle at 78% 100%,rgba(104,219,184,.1),transparent 36%),#171922;box-shadow:0 18px 55px rgba(0,0,0,.22);white-space:normal}.story-card-visual{position:relative;display:grid;width:64px;height:64px;overflow:hidden;place-items:center;border:1px solid rgba(255,255,255,.12);border-radius:18px;background:#11131b;color:#dcd5ff}.story-card-visual i{position:absolute;width:46px;height:1px;background:linear-gradient(90deg,transparent,rgba(184,255,84,.65),transparent);animation:card-wave 2.6s ease-in-out infinite;animation-delay:calc(var(--i) * -180ms);transform:translateY(calc((var(--i) - 4) * 5px))}.story-card-visual svg{position:relative;z-index:1;filter:drop-shadow(0 0 12px rgba(158,140,255,.8))}.story-card-copy{display:grid;min-width:0;gap:5px}.story-card-copy>span{display:flex;align-items:center;gap:5px;color:#a99cff;font-size:9px;font-weight:850;letter-spacing:.08em}.story-card-copy strong{color:#f4f2fa;font-size:14px;line-height:1.3}.story-card-copy>p{display:-webkit-box;overflow:hidden;margin:0;color:#a8adb9;font-size:11px;line-height:1.65;-webkit-box-orient:vertical;-webkit-line-clamp:2}.story-card-signals{display:flex;flex-wrap:wrap;gap:5px}.story-card-signals small{border:1px solid rgba(255,255,255,.08);border-radius:999px;padding:3px 7px;background:rgba(255,255,255,.035);color:#858b98;font-size:8px;white-space:nowrap}.profile-story-card>button{display:flex;align-items:center;gap:7px;border:1px solid rgba(184,255,84,.22);border-radius:12px;padding:10px 12px;background:rgba(184,255,84,.1);color:#cfff89;font-size:9px;font-weight:760;transition:.2s}.profile-story-card>button:hover{background:#b8ff54;color:#11150c;transform:translateY(-1px)}
.profile-story-shell{position:fixed;z-index:1000;inset:0;overflow:hidden;background:#090a10;color:#f7f5fb;white-space:normal}.story-toolbar{position:absolute;z-index:20;top:0;right:0;left:0;display:grid;height:66px;grid-template-columns:1fr auto 1fr;align-items:center;padding:0 clamp(16px,4vw,56px);background:linear-gradient(180deg,rgba(8,9,14,.9),transparent);pointer-events:none}.story-brand{display:flex;align-items:center;gap:9px;color:rgba(255,255,255,.62);font-size:9px;font-weight:800;letter-spacing:.16em}.story-brand i{width:8px;height:8px;border-radius:50%;background:#b8ff54;box-shadow:0 0 16px #b8ff54}.story-progress{display:flex;gap:7px;pointer-events:auto}.story-progress button{width:22px;height:3px;border:0;border-radius:10px;padding:0;background:rgba(255,255,255,.16);transition:.3s}.story-progress button.active{width:38px;background:#b8ff54;box-shadow:0 0 14px rgba(184,255,84,.55)}.story-close{display:grid;width:38px;height:38px;justify-self:end;place-items:center;border:1px solid rgba(255,255,255,.12);border-radius:12px;background:rgba(10,11,17,.42);color:#dddde5;pointer-events:auto;backdrop-filter:blur(12px)}.story-close:hover{background:rgba(255,255,255,.12)}.profile-story-report{height:100%;overflow-y:auto;outline:none;scroll-behavior:smooth;scroll-snap-type:y mandatory;scrollbar-width:none}.profile-story-report::-webkit-scrollbar{display:none}.story-scene{position:relative;isolation:isolate;display:grid;min-height:100%;overflow:hidden;padding:100px clamp(24px,8vw,130px) 70px;place-items:center;scroll-snap-align:start;scroll-snap-stop:always}.scene-copy{position:relative;z-index:3;width:min(100%,1080px)}.scene-kicker{display:block;color:#b0a3ff;font-size:10px;font-weight:850;letter-spacing:.2em}.story-scene h1,.story-scene h2,.story-scene p{margin-top:0}.story-scene h1,.story-scene h2{letter-spacing:-.055em}.story-next{position:absolute;z-index:4;bottom:30px;left:50%;display:flex;align-items:center;gap:7px;border:0;background:transparent;color:rgba(255,255,255,.58);font-size:9px;transform:translateX(-50%)}.story-next svg{animation:next-bob 1.6s ease-in-out infinite}
.scene-opening{background:radial-gradient(circle at 14% 25%,rgba(158,140,255,.25),transparent 30%),radial-gradient(circle at 84% 72%,rgba(104,219,184,.17),transparent 32%),linear-gradient(145deg,#0b0b13,#12141d 60%,#0b1112)}.ambient-orb{position:absolute;border-radius:50%;filter:blur(70px);opacity:.42;animation:ambient 8s ease-in-out infinite alternate}.orb-a{top:5%;right:8%;width:32vw;height:32vw;background:#6854d8}.orb-b{bottom:3%;left:10%;width:25vw;height:25vw;background:#2a9c80;animation-delay:-3s}.opening-copy{align-self:end;padding-bottom:7vh}.opening-copy h1{max-width:780px;margin:18px 0 16px;font-size:clamp(48px,7.6vw,112px);font-weight:620;line-height:.96}.opening-copy p{max-width:560px;color:#9ca1ad;font-size:clamp(13px,1.5vw,18px);line-height:1.8}.orbit-field{position:absolute;z-index:1;top:44%;right:6%;width:min(56vw,720px);aspect-ratio:1;transform:translateY(-50%)}.orbit-field>i{position:absolute;inset:14%;border:1px solid rgba(255,255,255,.06);border-radius:50%;animation:orbit-spin 30s linear infinite}.orbit-field>i:nth-last-child(2){inset:28%;animation-direction:reverse;animation-duration:23s}.orbit-field>i:last-child{inset:41%;box-shadow:0 0 90px rgba(158,140,255,.2)}.orbit-field>span{position:absolute;z-index:2;top:50%;left:50%;display:grid;width:88px;height:88px;place-items:center;border:1px solid color-mix(in srgb,var(--orbit-color) 35%,transparent);border-radius:50%;background:color-mix(in srgb,var(--orbit-color) 10%,rgba(10,11,17,.7));opacity:0;transform:rotate(var(--orbit-angle)) translateX(var(--orbit-radius)) rotate(calc(var(--orbit-angle) * -1)) scale(.6);transition:opacity .7s var(--orbit-delay),transform .9s cubic-bezier(.2,.8,.2,1) var(--orbit-delay);backdrop-filter:blur(8px)}.revealed .orbit-field>span{opacity:1;transform:rotate(var(--orbit-angle)) translateX(var(--orbit-radius)) rotate(calc(var(--orbit-angle) * -1)) scale(1)}.orbit-field b{color:var(--orbit-color);font-size:8px}.orbit-field em{width:68px;overflow:hidden;color:#d9d9e2;font-size:9px;font-style:normal;text-align:center;text-overflow:ellipsis;white-space:nowrap}
.scene-journey{background:linear-gradient(180deg,#11121a,#0b0d13)}.journey-rings{position:absolute;top:50%;left:50%;width:82vmin;height:82vmin;transform:translate(-50%,-50%)}.journey-rings i{position:absolute;inset:calc(var(--ring,0) * 10%);border:1px solid rgba(158,140,255,.1);border-radius:50%;box-shadow:inset 0 0 70px rgba(104,219,184,.018);animation:ring-breathe 4s ease-in-out infinite}.journey-rings i:nth-child(2){--ring:1;animation-delay:-1s}.journey-rings i:nth-child(3){--ring:2;animation-delay:-2s}.journey-rings i:nth-child(4){--ring:3;animation-delay:-3s}.journey-copy{text-align:center}.journey-copy>p{margin:18px 0 2px;color:#969ca8;font-size:16px}.journey-copy h2{margin:0;background:linear-gradient(120deg,#fff,#d8d1ff 45%,#9ce9cd);background-clip:text;color:transparent;font-size:clamp(96px,17vw,240px);font-weight:560;line-height:1}.journey-copy h2 small{font-size:.14em;margin-left:10px}.journey-metrics{display:grid;width:min(100%,760px);grid-template-columns:repeat(3,1fr);gap:1px;margin:5vh auto 0;border:1px solid rgba(255,255,255,.08);border-radius:20px;background:rgba(255,255,255,.08);overflow:hidden}.journey-metrics article{display:grid;gap:7px;padding:22px;background:rgba(12,14,20,.82)}.journey-metrics strong{font-size:18px}.journey-metrics span{color:#747b87;font-size:9px;letter-spacing:.08em}
.scene-constellation{background:radial-gradient(circle at 30% 40%,rgba(71,55,130,.35),transparent 34%),radial-gradient(circle at 75% 65%,rgba(28,104,91,.2),transparent 35%),#0c0d15}.constellation-lines{position:absolute;inset:0;background-image:radial-gradient(circle,rgba(255,255,255,.45) 0 1px,transparent 1.6px);background-size:74px 74px;opacity:.12;animation:star-drift 20s linear infinite}.constellation-copy>p{margin:18px 0 6px;color:#8b919d}.constellation-copy>h2{max-width:820px;margin:0 0 8vh;font-size:clamp(54px,9vw,132px);line-height:.95}.artist-constellation{display:flex;align-items:flex-end;gap:clamp(12px,2.5vw,35px);overflow-x:auto;padding:20px 5px}.artist-constellation article{display:grid;min-width:120px;gap:7px;opacity:0;transform:translateY(32px);transition:.75s var(--delay)}.revealed .artist-constellation article{opacity:1;transform:none}.artist-constellation article.lead{min-width:190px}.artist-constellation i{display:grid;width:clamp(58px,7vw,94px);height:clamp(58px,7vw,94px);place-items:center;border:1px solid rgba(255,255,255,.13);border-radius:50%;background:linear-gradient(145deg,rgba(158,140,255,.35),rgba(104,219,184,.12));color:#eeeaff;font-size:24px;font-style:normal;box-shadow:0 0 45px rgba(158,140,255,.12)}.artist-constellation .lead i{width:clamp(88px,11vw,150px);height:clamp(88px,11vw,150px);background:linear-gradient(145deg,#9e8cff,#527b76);color:#101116;font-size:42px;box-shadow:0 0 80px rgba(158,140,255,.35)}.artist-constellation strong{font-size:13px}.artist-constellation span{max-width:180px;color:#737a86;font-size:9px;line-height:1.5}
.scene-scale{background:linear-gradient(135deg,#0c0b13,#171224 48%,#0b1513)}.scale-copy h2{margin:18px 0 7vh;font-size:clamp(44px,7vw,92px);font-weight:600;line-height:1}.scale-copy h2 em{color:#b8ff54;font-style:normal}.taste-tiles{display:flex;align-content:stretch;flex-wrap:wrap;gap:10px;perspective:900px}.taste-tiles article{display:grid;min-height:126px;min-width:150px;flex:1 1 var(--tile-basis);align-content:space-between;gap:8px;border:1px solid color-mix(in srgb,var(--tile-color) 28%,transparent);border-radius:19px;padding:17px;background:linear-gradient(145deg,color-mix(in srgb,var(--tile-color) calc(var(--tile-strength) * 24%),#15151d),rgba(15,16,22,.82));opacity:0;transform:translateY(34px) rotateX(10deg) scale(.94);transition:opacity .6s var(--tile-delay),transform .75s cubic-bezier(.2,.85,.25,1) var(--tile-delay),filter .5s}.revealed .taste-tiles article{opacity:1;transform:none}.taste-tiles article.dominant{min-height:166px;flex-basis:45%;border-color:rgba(184,255,84,.5);background:radial-gradient(circle at 90% 0,rgba(184,255,84,.27),transparent 38%),linear-gradient(145deg,#27321c,#151820);box-shadow:0 0 65px rgba(184,255,84,.12);filter:brightness(1.2)}.taste-tiles span{color:var(--tile-color,#b8ff54);font-size:8px;font-weight:850;letter-spacing:.15em}.taste-tiles strong{font-size:clamp(21px,3vw,38px);letter-spacing:-.04em}.taste-tiles small{color:#858b96;font-size:9px}.taste-tiles .dominant small{color:#b2baaa}
.scene-waves{background:radial-gradient(circle at 90% 20%,rgba(255,115,89,.14),transparent 35%),linear-gradient(180deg,#170b11,#0f0910 55%,#110d19)}.waves-copy h2{margin:18px 0 6vh;font-size:clamp(40px,6.5vw,84px);font-weight:600;line-height:1.03}.wave-stack{display:grid;gap:7px}.wave-stack article{position:relative;display:grid;min-height:74px;grid-template-columns:150px minmax(0,1fr);align-items:center;opacity:0;transform:translateX(-30px);transition:.7s}.revealed .wave-stack article{opacity:1;transform:none}.wave-stack article:nth-child(2){transition-delay:90ms}.wave-stack article:nth-child(3){transition-delay:180ms}.wave-stack article:nth-child(4){transition-delay:270ms}.wave-stack article:nth-child(5){transition-delay:360ms}.wave-stack article>div{display:grid;gap:4px}.wave-stack strong{font-size:15px}.wave-stack span{color:#7f7078;font-size:8px}.wave-stack svg{width:100%;height:74px;overflow:visible}.wave-stack path{fill:none;stroke:var(--wave-color);stroke-width:calc(1.5 + var(--wave-opacity) * 4);stroke-linecap:round;opacity:var(--wave-opacity);stroke-dasharray:26 10;animation:wave-flow 4.5s linear infinite;animation-delay:var(--wave-delay);filter:drop-shadow(0 0 7px color-mix(in srgb,var(--wave-color) 45%,transparent))}.wave-stack path.echo{stroke-width:12;opacity:calc(var(--wave-opacity) * .1);stroke-dasharray:none;animation:wave-pulse 3s ease-in-out infinite}.wave-stack article.dominant{min-height:98px}.wave-stack article.dominant strong{color:#fff2d8;font-size:20px}.wave-stack article.dominant path{stroke-width:6;opacity:1;filter:drop-shadow(0 0 13px var(--wave-color))}.waves-copy>p{display:flex;align-items:center;gap:8px;margin:5vh 0 0;color:#d7c4c7;font-size:11px}
.scene-finale{background:radial-gradient(circle at 50% 100%,rgba(104,219,184,.16),transparent 35%),radial-gradient(circle at 25% 10%,rgba(158,140,255,.22),transparent 32%),#0c0d14}.finale-glow{position:absolute;width:46vmin;height:46vmin;border-radius:50%;background:conic-gradient(from 90deg,#9e8cff,#68dbb8,#b8ff54,#ff9c78,#9e8cff);filter:blur(80px);opacity:.13;animation:orbit-spin 18s linear infinite}.finale-copy{display:grid;justify-items:center;text-align:center}.finale-copy h2{margin:18px 0 3vh;font-size:clamp(42px,6vw,76px);font-weight:580;line-height:1}.finale-copy .narrative{max-width:640px;color:#c0c2ca;font-size:clamp(13px,1.35vw,16px);line-height:1.9}.story-labels{display:flex;flex-wrap:wrap;justify-content:center;gap:7px;margin:22px 0 0}.story-labels span{border:1px solid rgba(158,140,255,.22);border-radius:999px;padding:7px 10px;background:rgba(158,140,255,.08);color:#c8c0f1;font-size:9px}.final-note{max-width:620px;margin:24px 0 18px!important;color:#646b77;font-size:9px;line-height:1.6}.finale-copy>button{display:flex;align-items:center;gap:8px;border:0;border-radius:13px;padding:12px 17px;background:#b8ff54;color:#12150e;font-size:10px;font-weight:800;box-shadow:0 12px 40px rgba(184,255,84,.13)}
.story-shell-enter-active,.story-shell-leave-active{transition:opacity .45s}.story-shell-enter-active .profile-story-report,.story-shell-leave-active .profile-story-report{transition:transform .55s cubic-bezier(.2,.75,.2,1)}.story-shell-enter-from,.story-shell-leave-to{opacity:0}.story-shell-enter-from .profile-story-report{transform:scale(1.025)}.story-shell-leave-to .profile-story-report{transform:scale(.985)}
@keyframes card-wave{0%,100%{opacity:.25;transform:translateY(calc((var(--i) - 4) * 5px)) scaleX(.7)}50%{opacity:.8;transform:translateY(calc((var(--i) - 4) * 5px)) scaleX(1.1)}}@keyframes next-bob{50%{transform:translateY(5px)}}@keyframes ambient{to{transform:translate(8%,-6%) scale(1.15)}}@keyframes orbit-spin{to{transform:rotate(360deg)}}@keyframes ring-breathe{50%{transform:scale(1.035);border-color:rgba(158,140,255,.23)}}@keyframes star-drift{to{background-position:74px 74px}}@keyframes wave-flow{to{stroke-dashoffset:-144}}@keyframes wave-pulse{50%{opacity:.2;transform:scaleY(1.15)}}
@media(max-width:720px){.profile-story-card{grid-template-columns:52px minmax(0,1fr);gap:10px}.story-card-visual{width:52px;height:52px;border-radius:15px}.story-card-copy>p{-webkit-line-clamp:3}.profile-story-card>button{grid-column:1/-1;justify-content:center}.story-toolbar{height:58px;grid-template-columns:1fr auto;padding:0 14px}.story-progress{display:none}.story-scene{padding:82px 20px 62px}.orbit-field{top:32%;right:-30%;width:110vw;opacity:.65}.opening-copy{align-self:end;padding-bottom:10vh}.opening-copy h1{font-size:50px}.journey-copy h2{font-size:96px}.journey-metrics{grid-template-columns:1fr}.journey-metrics article{grid-template-columns:1fr 1fr;align-items:center;padding:13px 16px}.journey-metrics span{text-align:right}.constellation-copy>h2{font-size:54px}.artist-constellation{align-items:flex-start}.taste-tiles article,.taste-tiles article.dominant{min-height:112px;min-width:130px;flex-basis:46%}.wave-stack article{grid-template-columns:95px minmax(0,1fr)}.wave-stack svg{height:62px}.wave-stack strong{font-size:12px}.finale-copy h2{font-size:44px}.finale-copy .narrative{font-size:12px;line-height:1.8}}
@media(min-width:721px) and (max-height:760px){.story-scene{padding-top:72px;padding-bottom:28px}.scale-copy h2,.waves-copy h2{margin-top:10px;margin-bottom:3vh}.scale-copy h2{font-size:clamp(40px,6vw,70px)}.taste-tiles{gap:8px}.taste-tiles article{min-height:92px;padding:12px 16px}.taste-tiles article.dominant{min-height:112px}.taste-tiles strong{font-size:clamp(18px,2.5vw,30px)}.wave-stack{gap:2px}.wave-stack article,.wave-stack article.dominant{min-height:58px}.wave-stack svg{height:54px}.waves-copy>p{margin-top:2vh}}
@media(prefers-reduced-motion:reduce){.profile-story-report{scroll-behavior:auto}.profile-story-card *,.profile-story-shell *{animation:none!important;transition-duration:.01ms!important}.orbit-field>span,.taste-tiles article,.artist-constellation article,.wave-stack article{opacity:1;transform:none}}
</style>
