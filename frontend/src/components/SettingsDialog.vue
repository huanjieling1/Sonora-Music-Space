<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  BarChart3,
  CheckCircle2,
  CircleAlert,
  Clock3,
  Disc3,
  KeyRound,
  LoaderCircle,
  Music2,
  QrCode,
  RefreshCw,
  Settings2,
  ShieldCheck,
  Smartphone,
  Tag,
  Trash2,
  X,
} from 'lucide-vue-next'
import { request } from '../services/api'
import { confirmAction } from '../services/confirm'

const props = defineProps({
  open: { type: Boolean, default: false },
})
const emit = defineEmits(['close'])
const activeSection = ref('qq')
const profile = ref(null)
const profileLoading = ref(false)
const profileError = ref('')
const settingsContent = ref(null)

const qqStatus = ref({
  enabled: false,
  bridgeAvailable: false,
  sessionConfigured: false,
  maskedAccount: '',
  message: '',
})
const qrLogin = ref(null)
const loading = ref(false)
const saving = ref(false)
const qrStarting = ref(false)
const qrChecking = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
let successTimer = null
let qrPollTimer = null

const statusLabel = computed(() => {
  if (loading.value) return '正在检查'
  if (!qqStatus.value.enabled) return '功能未启用'
  if (!qqStatus.value.bridgeAvailable) return '服务未连接'
  if (qqStatus.value.sessionConfigured) return `已连接 ${qqStatus.value.maskedAccount || ''}`.trim()
  return '等待配置'
})

const statusTone = computed(() => (
  qqStatus.value.bridgeAvailable && qqStatus.value.sessionConfigured ? 'ready' : 'pending'
))
const analytics = computed(() => profile.value?.analytics || null)
const profileProgress = computed(() => {
  const value = analytics.value
  if (!value || value.profileReady) return 100
  const plays = value.requiredPlayCount > 0 ? value.playCount / value.requiredPlayCount : 0
  const tracks = value.requiredUniqueTracks > 0 ? value.uniqueTracks / value.requiredUniqueTracks : 0
  return Math.round(Math.min(1, Math.min(plays, tracks)) * 100)
})

watch(() => props.open, value => {
  if (value) {
    refreshQqStatus()
    refreshProfile()
  }
  else closeQrLogin()
})

watch(activeSection, async () => {
  await nextTick()
  settingsContent.value?.scrollTo({ top: 0, behavior: 'auto' })
})

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  if (successTimer) window.clearTimeout(successTimer)
  stopQrPolling()
  cancelQrAttempt()
})

function handleKeydown(event) {
  if (props.open && event.key === 'Escape') emit('close')
}

function resetTransientState() {
  errorMessage.value = ''
  successMessage.value = ''
}

function closeQrLogin() {
  stopQrPolling()
  cancelQrAttempt()
  qrLogin.value = null
  resetTransientState()
}

function showSuccess(message) {
  successMessage.value = message
  if (successTimer) window.clearTimeout(successTimer)
  successTimer = window.setTimeout(() => {
    successMessage.value = ''
  }, 2600)
}

async function refreshQqStatus() {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await request('/api/music/qq/status')
    qqStatus.value = result.data
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

async function refreshProfile() {
  if (profileLoading.value) return
  profileLoading.value = true
  profileError.value = ''
  try {
    const result = await request('/api/music/profile')
    profile.value = result.data
  } catch (error) {
    profileError.value = error.message
  } finally {
    profileLoading.value = false
  }
}

function selectSection(section) {
  activeSection.value = section
  if (section === 'profile' && !profile.value) refreshProfile()
}

function minutes(milliseconds) {
  return Math.round(Math.max(0, Number(milliseconds) || 0) / 60000)
}

function percent(value) {
  return `${Math.round(Math.max(0, Math.min(1, Number(value) || 0)) * 100)}%`
}

async function startQrLogin() {
  if (qrStarting.value || !qqStatus.value.bridgeAvailable) return
  stopQrPolling()
  await cancelQrAttempt()
  qrStarting.value = true
  errorMessage.value = ''
  try {
    const result = await request('/api/music/qq/login/qr', { method: 'POST' })
    qrLogin.value = result.data
    scheduleQrPoll(900)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    qrStarting.value = false
  }
}

function scheduleQrPoll(delay = 1600) {
  stopQrPolling()
  qrPollTimer = window.setTimeout(pollQrLogin, delay)
}

function stopQrPolling() {
  if (qrPollTimer) window.clearTimeout(qrPollTimer)
  qrPollTimer = null
}

async function pollQrLogin() {
  const loginId = qrLogin.value?.loginId
  if (!props.open || !loginId || qrChecking.value) return
  qrChecking.value = true
  try {
    const result = await request(`/api/music/qq/login/qr/${encodeURIComponent(loginId)}`)
    qrLogin.value = {
      ...qrLogin.value,
      ...result.data,
      qrImage: result.data.qrImage || qrLogin.value?.qrImage || null,
    }
    if (result.data.status === 'SUCCESS') {
      stopQrPolling()
      if (result.data.connection) qqStatus.value = result.data.connection
      showSuccess('QQ 音乐已通过官方登录窗口连接')
      return
    }
    if (['EXPIRED', 'FAILED'].includes(result.data.status)) {
      stopQrPolling()
      if (result.data.status === 'FAILED') {
        errorMessage.value = result.data.message || 'QQ 音乐登录确认失败，请重新打开登录窗口'
      }
      return
    }
    scheduleQrPoll()
  } catch (error) {
    stopQrPolling()
    errorMessage.value = error.message
  } finally {
    qrChecking.value = false
  }
}

async function cancelQrAttempt() {
  const loginId = qrLogin.value?.loginId
  if (!loginId || ['SUCCESS', 'EXPIRED', 'FAILED'].includes(qrLogin.value?.status)) return
  try {
    await request(`/api/music/qq/login/qr/${encodeURIComponent(loginId)}`, { method: 'DELETE' })
  } catch {
    // The bridge may already have expired the one-time QR attempt.
  }
}

async function clearQqSession() {
  if (saving.value) return
  const accepted = await confirmAction({
    eyebrow: 'QQ 音乐设置',
    title: '清除本机 QQ 音乐登录态吗？',
    message: '清除后，部分需要登录的 QQ 音乐内容可能暂时无法播放，之后可以随时重新配置。',
    subject: qqStatus.value.maskedAccount || '当前 QQ 音乐账号',
    hint: '不会注销 QQ 账号，也不会删除 QQ 音乐中的线上数据',
    confirmText: '清除登录态',
    cancelText: '保留登录态',
  })
  if (!accepted) return

  saving.value = true
  errorMessage.value = ''
  try {
    const result = await request('/api/music/qq/session', { method: 'DELETE' })
    qqStatus.value = result.data
    showSuccess('本机 QQ 音乐登录态已清除')
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="settings-fade">
      <div v-if="open" class="settings-backdrop" role="presentation" @mousedown.self="emit('close')">
        <section
          class="settings-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="settings-title"
          @mousedown.stop
        >
          <aside class="settings-nav">
            <div class="settings-brand">
              <span><Settings2 :size="19" /></span>
              <div>
                <strong id="settings-title">设置</strong>
                <small>Sonora</small>
              </div>
            </div>
            <nav aria-label="设置分类">
              <button :class="{ active: activeSection === 'qq' }" type="button" @click="selectSection('qq')">
                <Music2 :size="17" />
                <span>QQ 音乐</span>
              </button>
              <button :class="{ active: activeSection === 'profile' }" type="button" @click="selectSection('profile')">
                <BarChart3 :size="17" />
                <span>我的画像</span>
              </button>
            </nav>
            <p class="settings-future">播放统计与画像只属于当前登录用户，并可解释每个标签的生成依据。</p>
          </aside>

          <div ref="settingsContent" class="settings-content" tabindex="0" aria-label="设置内容">
            <template v-if="activeSection === 'qq'">
            <header class="settings-header">
              <div>
                <span class="settings-eyebrow">MUSIC CONNECTION</span>
                <h2>QQ 音乐接入</h2>
                <p>管理本机 QQ 音乐登录态，用于搜索、歌单、歌词和可播放链接。</p>
              </div>
              <button class="settings-close" type="button" title="关闭设置" aria-label="关闭设置" @click="emit('close')">
                <X :size="20" />
              </button>
            </header>

            <div class="connection-card">
              <div class="connection-icon"><KeyRound :size="22" /></div>
              <div class="connection-copy">
                <strong>QQ 音乐服务</strong>
                <span>{{ qqStatus.message || '正在读取本机连接状态…' }}</span>
              </div>
              <span class="connection-status" :class="statusTone">
                <LoaderCircle v-if="loading" class="spin" :size="14" />
                <CheckCircle2 v-else-if="statusTone === 'ready'" :size="14" />
                <CircleAlert v-else :size="14" />
                {{ statusLabel }}
              </span>
            </div>

            <section class="settings-section">
              <div class="section-heading">
                <div>
                  <h3>{{ qqStatus.sessionConfigured ? '更新 QQ 音乐登录' : '官方网页登录 QQ 音乐' }}</h3>
                  <p>打开独立 Edge 窗口后，在 QQ 官方页面扫码或登录。成功后，本机服务会自动提取必要凭据并加密保存。</p>
                </div>
                <span class="qr-private"><ShieldCheck :size="14" /> 登录凭据仅由本机服务加密保存</span>
              </div>

              <div v-if="qrLogin?.loginMode === 'BROWSER' && qrLogin.status !== 'SUCCESS'" class="qr-login-card browser-login-card">
                <div class="browser-login-icon"><KeyRound :size="40" /></div>
                <div class="qr-instructions">
                  <span class="qr-step"><ShieldCheck :size="15" /> 独立的 Microsoft Edge 登录窗口</span>
                  <strong>{{ qrLogin.message }}</strong>
                  <p>请只在已打开的 QQ 官方页面操作。窗口使用独立配置，不读取你的日常 Edge 浏览记录或其他网站 Cookie。</p>
                  <span class="qr-state" :class="qrLogin.status.toLowerCase()">
                    <LoaderCircle v-if="qrChecking && !['EXPIRED', 'FAILED'].includes(qrLogin.status)" class="spin" :size="14" />
                    <CircleAlert v-else-if="['EXPIRED', 'FAILED'].includes(qrLogin.status)" :size="14" />
                    <KeyRound v-else :size="14" />
                    {{ qrLogin.status === 'EXPIRED' ? '登录窗口已失效' : qrLogin.status === 'FAILED' ? '网页登录失败' : '等待完成网页登录' }}
                  </span>
                </div>
              </div>

              <div v-else-if="qrLogin?.qrImage && qrLogin.status !== 'SUCCESS'" class="qr-login-card">
                <div class="qr-image-wrap">
                  <img :src="qrLogin.qrImage" alt="QQ 音乐登录二维码" />
                  <span v-if="qrLogin.status === 'EXPIRED'" class="qr-expired">已过期</span>
                </div>
                <div class="qr-instructions">
                  <span class="qr-step"><Smartphone :size="15" /> 手机打开 QQ 或 QQ 音乐</span>
                  <strong>{{ qrLogin.message }}</strong>
                  <p>二维码仅用于本次登录，约 3 分钟后自动失效。请勿将二维码发送给他人。</p>
                  <span class="qr-state" :class="qrLogin.status.toLowerCase()">
                    <LoaderCircle v-if="qrChecking && !['EXPIRED', 'SUCCESS'].includes(qrLogin.status)" class="spin" :size="14" />
                    <CheckCircle2 v-else-if="qrLogin.status === 'WAITING_CONFIRM'" :size="14" />
                    <CircleAlert v-else-if="['EXPIRED', 'FAILED'].includes(qrLogin.status)" :size="14" />
                    <QrCode v-else :size="14" />
                    {{ qrLogin.status === 'WAITING_CONFIRM' ? '等待手机确认' : qrLogin.status === 'EXPIRED' ? '二维码已失效' : qrLogin.status === 'FAILED' ? '登录确认失败' : '等待扫码' }}
                  </span>
                </div>
              </div>

              <div v-else-if="qrLogin?.status === 'SUCCESS'" class="qr-success-card">
                <CheckCircle2 :size="22" />
                <div><strong>连接成功</strong><span>{{ qqStatus.maskedAccount || 'QQ 音乐账号' }} 已安全接入本机服务</span></div>
              </div>

              <div v-else class="qr-empty-card">
                <span><KeyRound :size="24" /></span>
                <div><strong>官方网页登录采集</strong><p>点击下方按钮打开独立 Edge，登录成功后自动保存必要 Cookie。</p></div>
              </div>

              <div class="settings-actions">
                <span class="security-note"><ShieldCheck :size="15" /> 登录态仅加密保存在当前设备</span>
                <button
                  class="settings-secondary"
                  type="button"
                  :disabled="loading || saving || qrStarting"
                  @click="refreshQqStatus"
                >
                  <RefreshCw :class="{ spin: loading }" :size="15" /> 刷新状态
                </button>
                <button
                  class="settings-primary"
                  type="button"
                  :disabled="saving || qrStarting || !qqStatus.bridgeAvailable"
                  @click="startQrLogin"
                >
                  <LoaderCircle v-if="qrStarting" class="spin" :size="15" />
                  <KeyRound v-else :size="15" />
                  {{ ['EXPIRED', 'FAILED'].includes(qrLogin?.status) ? '重新打开登录窗口' : qqStatus.sessionConfigured ? '打开窗口更新登录' : '打开 QQ 登录窗口' }}
                </button>
              </div>

              <p v-if="errorMessage" class="settings-message error"><CircleAlert :size="15" /> {{ errorMessage }}</p>
              <p v-if="successMessage" class="settings-message success"><CheckCircle2 :size="15" /> {{ successMessage }}</p>
            </section>

            <section v-if="qqStatus.sessionConfigured" class="danger-row">
              <div>
                <strong>清除本机登录态</strong>
                <span>移除 Sonora 保存的 QQ 音乐登录凭据，不会影响 QQ 账号本身。</span>
              </div>
              <button type="button" :disabled="saving" @click="clearQqSession">
                <Trash2 :size="15" /> 清除
              </button>
            </section>
            </template>

            <template v-else>
              <header class="settings-header">
                <div>
                  <span class="settings-eyebrow">LISTENING PROFILE</span>
                  <h2>我的音乐画像</h2>
                  <p>根据实际播放、完播、跳过、循环以及带来源的歌曲标签，生成可核对的收听画像。</p>
                </div>
                <button class="settings-close" type="button" title="关闭设置" aria-label="关闭设置" @click="emit('close')">
                  <X :size="20" />
                </button>
              </header>

              <div v-if="profileLoading && !profile" class="profile-loading">
                <LoaderCircle class="spin" :size="24" /> 正在汇总收听记录…
              </div>
              <p v-else-if="profileError" class="settings-message error">
                <CircleAlert :size="15" /> {{ profileError }}
                <button type="button" @click="refreshProfile">重试</button>
              </p>
              <template v-else-if="analytics">
                <section class="profile-hero" :class="{ ready: analytics.profileReady }">
                  <div>
                    <span>{{ profile?.summary?.stageLabel || '收听画像' }}</span>
                    <h3>{{ analytics.profileReady ? '你的音乐偏好标签已开始形成' : '正在积累可靠的收听证据' }}</h3>
                    <p v-if="analytics.profileReady">所有标签都由歌曲、歌手和播放行为统计生成，不推断无关的个人属性。</p>
                    <p v-else>需要 {{ analytics.requiredPlayCount }} 次有效播放和 {{ analytics.requiredUniqueTracks }} 首不同歌曲；当前为 {{ analytics.playCount }} 次、{{ analytics.uniqueTracks }} 首。</p>
                  </div>
                  <strong>{{ analytics.profileReady ? '画像可用' : `${profileProgress}%` }}</strong>
                  <div class="profile-progress"><i :style="{ width: `${profileProgress}%` }"></i></div>
                </section>

                <section class="profile-metrics">
                  <article><Disc3 :size="17" /><span>有效播放</span><strong>{{ analytics.playCount }}</strong></article>
                  <article><Music2 :size="17" /><span>不同歌曲</span><strong>{{ analytics.uniqueTracks }}</strong></article>
                  <article><CheckCircle2 :size="17" /><span>完播率</span><strong>{{ percent(analytics.completionRate) }}</strong></article>
                  <article><Clock3 :size="17" /><span>实际收听</span><strong>{{ minutes(analytics.totalPlaybackMs) }} 分钟</strong></article>
                </section>

                <section class="settings-section profile-section">
                  <div class="section-heading"><div><h3>用户标签</h3><p>达到数据门槛后生成，每个标签都附带统计依据。</p></div><Tag :size="18" /></div>
                  <div v-if="analytics.labels?.length" class="profile-labels">
                    <article v-for="label in analytics.labels" :key="label.code">
                      <strong>{{ label.name }}</strong><span>{{ label.basis }}</span><small>可信度 {{ percent(label.confidence) }}</small>
                    </article>
                  </div>
                  <p v-else class="profile-empty-copy">{{ analytics.profileReady ? '数据量已经足够，但当前还没有达到任一偏好特征的显著阈值。' : '数据量尚未达到标签生成门槛，系统不会提前猜测你的偏好。' }}</p>
                </section>

                <section class="profile-rankings">
                  <article>
                    <header><h3>最常听歌曲</h3><span>TOP TRACKS</span></header>
                    <ol><li v-for="track in analytics.topTracks?.slice(0, 5)" :key="track.trackKey"><div><strong>{{ track.title }}</strong><span>{{ track.artist || '未知艺人' }}</span></div><b>{{ track.playCount }} 次</b></li></ol>
                    <p v-if="!analytics.topTracks?.length">还没有歌曲统计。</p>
                  </article>
                  <article>
                    <header><h3>最常听歌手</h3><span>TOP ARTISTS</span></header>
                    <ol><li v-for="artist in analytics.topArtists?.slice(0, 5)" :key="artist.name"><div><strong>{{ artist.name }}</strong><span>{{ artist.uniqueTracks }} 首歌曲</span></div><b>{{ artist.playCount }} 次</b></li></ol>
                    <p v-if="!analytics.topArtists?.length">还没有歌手统计。</p>
                  </article>
                </section>

                <section class="settings-section profile-section">
                  <div class="section-heading"><div><h3>偏好标签</h3><p>标签保留 QQ 专辑、公开歌单等来源可信度后再参与统计。</p></div></div>
                  <div class="profile-tags"><span v-for="tagItem in analytics.topTags?.slice(0, 10)" :key="`${tagItem.type}:${tagItem.value}`"><b>{{ tagItem.value }}</b><small>{{ tagItem.playCount }} 次 · {{ percent(tagItem.confidence) }} 可信</small></span></div>
                  <p v-if="!analytics.topTags?.length" class="profile-empty-copy">播放带有曲风、语种或歌单标签的 QQ 音乐后，这里会逐步形成偏好。</p>
                </section>

                <div class="profile-refresh"><button class="settings-secondary" type="button" :disabled="profileLoading" @click="refreshProfile"><RefreshCw :class="{ spin: profileLoading }" :size="15" />刷新画像</button></div>
              </template>
              <section v-else class="profile-unavailable">
                <CircleAlert :size="20" />
                <div>
                  <h3>画像数据暂不可用</h3>
                  <p>当前服务尚未返回播放画像统计，请确认后端已更新并重启后再刷新。</p>
                </div>
                <button type="button" class="settings-secondary" @click="refreshProfile">
                  <RefreshCw :size="14" />刷新画像
                </button>
              </section>
            </template>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.settings-backdrop {
  position: fixed;
  z-index: 180;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(4, 6, 10, 0.72);
  backdrop-filter: blur(14px);
}

.settings-dialog {
  display: grid;
  width: min(880px, 100%);
  height: min(680px, calc(100dvh - 48px));
  min-height: 0;
  max-height: min(680px, calc(100dvh - 48px));
  grid-template-columns: 220px minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 24px;
  background: #111419;
  box-shadow: 0 34px 100px rgba(0, 0, 0, 0.58);
  color: #eef0f4;
}

.settings-nav {
  display: flex;
  min-height: 520px;
  flex-direction: column;
  padding: 22px 16px;
  border-right: 1px solid rgba(255, 255, 255, 0.075);
  background:
    radial-gradient(circle at 16% 10%, rgba(158, 140, 255, 0.12), transparent 34%),
    #0c0f13;
}

.settings-brand { display: flex; align-items: center; gap: 11px; padding: 2px 8px 22px; }
.settings-brand > span { display: grid; width: 38px; height: 38px; place-items: center; border: 1px solid rgba(158, 140, 255, 0.25); border-radius: 12px; background: rgba(158, 140, 255, 0.12); color: #cfc6ff; }
.settings-brand div { display: grid; gap: 2px; }
.settings-brand strong { font-size: 16px; }
.settings-brand small { color: #6f7682; font-size: 10px; letter-spacing: 0.16em; text-transform: uppercase; }
.settings-nav nav { display: grid; gap: 6px; }
.settings-nav nav button { display: flex; width: 100%; align-items: center; gap: 10px; border: 0; border-radius: 11px; padding: 11px 12px; background: transparent; color: #969da8; font-size: 12px; text-align: left; }
.settings-nav nav button.active { background: rgba(158, 140, 255, 0.13); color: #e4dfff; }
.settings-future { margin: auto 8px 0; color: #626975; font-size: 10px; line-height: 1.7; }

.settings-content {
  min-width: 0;
  min-height: 0;
  height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 28px;
  outline: none;
  scrollbar-color: rgba(158, 140, 255, 0.42) rgba(255, 255, 255, 0.025);
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  touch-action: pan-y;
}
.settings-content:focus-visible { box-shadow: inset 0 0 0 1px rgba(158, 140, 255, 0.2); }
.settings-content::-webkit-scrollbar { width: 8px; }
.settings-content::-webkit-scrollbar-track { background: rgba(255, 255, 255, 0.025); }
.settings-content::-webkit-scrollbar-thumb { border: 2px solid #111419; border-radius: 999px; background: rgba(158, 140, 255, 0.42); }
.settings-content::-webkit-scrollbar-thumb:hover { background: rgba(158, 140, 255, 0.65); }
.settings-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; }
.settings-eyebrow { color: #9f91e6; font-size: 10px; font-weight: 760; letter-spacing: 0.16em; }
.settings-header h2 { margin: 7px 0 6px; font-size: 25px; letter-spacing: -0.035em; }
.settings-header p { max-width: 560px; margin: 0; color: #858c98; font-size: 12px; line-height: 1.65; }
.settings-close { display: grid; width: 38px; height: 38px; flex: 0 0 38px; place-items: center; border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 12px; background: rgba(255, 255, 255, 0.035); color: #9ba1ab; }
.settings-close:hover { background: rgba(255, 255, 255, 0.07); color: #fff; }

.connection-card { display: grid; grid-template-columns: 44px minmax(0, 1fr) auto; align-items: center; gap: 13px; margin-top: 28px; border: 1px solid rgba(255, 255, 255, 0.075); border-radius: 17px; padding: 16px; background: rgba(255, 255, 255, 0.027); }
.connection-icon { display: grid; width: 44px; height: 44px; place-items: center; border-radius: 13px; background: linear-gradient(145deg, rgba(158, 140, 255, 0.2), rgba(104, 219, 184, 0.1)); color: #cfc6ff; }
.connection-copy { display: grid; min-width: 0; gap: 4px; }
.connection-copy strong { font-size: 13px; }
.connection-copy span { overflow: hidden; color: #747c88; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.connection-status { display: inline-flex; align-items: center; gap: 6px; border-radius: 999px; padding: 7px 10px; font-size: 10px; white-space: nowrap; }
.connection-status.ready { background: rgba(104, 219, 184, 0.1); color: #8be0c1; }
.connection-status.pending { background: rgba(239, 185, 110, 0.1); color: #dfb57b; }

.settings-section { margin-top: 18px; border: 1px solid rgba(255, 255, 255, 0.075); border-radius: 17px; padding: 18px; background: rgba(255, 255, 255, 0.02); }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; }
.section-heading h3 { margin: 0 0 5px; font-size: 14px; }
.section-heading p { max-width: 500px; margin: 0; color: #737b87; font-size: 10px; line-height: 1.65; }
.qr-private { display: inline-flex; flex: 0 0 auto; align-items: center; gap: 5px; color: #8be0c1; font-size: 9px; }
.qr-login-card { display: grid; grid-template-columns: 142px minmax(0, 1fr); gap: 18px; margin-top: 16px; border: 1px solid rgba(158, 140, 255, 0.14); border-radius: 15px; padding: 15px; background: linear-gradient(135deg, rgba(158, 140, 255, 0.07), rgba(104, 219, 184, 0.035)); }
.qr-image-wrap { position: relative; display: grid; width: 142px; height: 142px; place-items: center; overflow: hidden; border-radius: 13px; background: #fff; box-shadow: 0 14px 32px rgba(0, 0, 0, 0.26); }
.qr-image-wrap img { width: 132px; height: 132px; object-fit: contain; image-rendering: crisp-edges; }
.browser-login-icon { display: grid; width: 142px; height: 142px; place-items: center; border-radius: 13px; background: rgba(158, 140, 255, 0.11); color: #b8acf1; }
.qr-expired { position: absolute; inset: 0; display: grid; place-items: center; background: rgba(10, 12, 17, 0.82); color: #fff; font-size: 13px; font-weight: 750; backdrop-filter: blur(3px); }
.qr-instructions { display: flex; min-width: 0; flex-direction: column; justify-content: center; align-items: flex-start; }
.qr-step { display: inline-flex; align-items: center; gap: 7px; color: #9f91e6; font-size: 10px; font-weight: 700; }
.qr-instructions strong { margin-top: 12px; font-size: 15px; }
.qr-instructions p { max-width: 400px; margin: 7px 0 13px; color: #717986; font-size: 10px; line-height: 1.65; }
.qr-state { display: inline-flex; align-items: center; gap: 6px; border-radius: 999px; padding: 7px 10px; background: rgba(255, 255, 255, 0.045); color: #aeb4bd; font-size: 9px; }
.qr-state.waiting_confirm { background: rgba(104, 219, 184, 0.1); color: #8be0c1; }
.qr-state.expired { background: rgba(238, 111, 116, 0.1); color: #eaa3a6; }
.qr-state.failed { background: rgba(238, 111, 116, 0.1); color: #eaa3a6; }
.qr-empty-card, .qr-success-card { display: flex; min-height: 104px; align-items: center; gap: 14px; margin-top: 16px; border: 1px dashed rgba(255, 255, 255, 0.1); border-radius: 14px; padding: 17px; background: rgba(5, 7, 11, 0.24); }
.qr-empty-card > span { display: grid; width: 48px; height: 48px; flex: 0 0 48px; place-items: center; border-radius: 14px; background: rgba(158, 140, 255, 0.1); color: #b8acf1; }
.qr-empty-card div, .qr-success-card div { display: grid; gap: 5px; }
.qr-empty-card strong, .qr-success-card strong { font-size: 13px; }
.qr-empty-card p, .qr-success-card span { margin: 0; color: #737b87; font-size: 10px; }
.qr-success-card { border-style: solid; border-color: rgba(104, 219, 184, 0.16); background: rgba(104, 219, 184, 0.045); color: #82dbb9; }

.settings-actions { display: flex; align-items: center; justify-content: flex-end; gap: 9px; margin-top: 13px; }
.security-note { display: inline-flex; min-width: 0; align-items: center; gap: 6px; margin-right: auto; color: #68717d; font-size: 9px; }
.settings-actions button, .danger-row button { display: inline-flex; min-height: 34px; align-items: center; justify-content: center; gap: 6px; border-radius: 10px; padding: 0 12px; font-size: 10px; font-weight: 680; }
.settings-secondary { border: 1px solid rgba(255, 255, 255, 0.09); background: rgba(255, 255, 255, 0.035); color: #aeb4bd; }
.settings-primary { border: 1px solid rgba(167, 255, 69, 0.28); background: #a7ff45; color: #10140d; }
.settings-actions button:disabled, .danger-row button:disabled { cursor: default; opacity: 0.4; }
.settings-message { display: flex; align-items: center; gap: 7px; margin: 13px 0 0; font-size: 10px; }
.settings-message.error { color: #f0a4a7; }
.settings-message.success { color: #82dbb9; }

.danger-row { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-top: 18px; border: 1px solid rgba(238, 111, 116, 0.12); border-radius: 17px; padding: 16px 18px; background: rgba(238, 111, 116, 0.035); }
.danger-row > div { display: grid; gap: 4px; }
.danger-row strong { font-size: 12px; }
.danger-row span { color: #737b87; font-size: 10px; }
.danger-row button { border: 1px solid rgba(238, 111, 116, 0.22); background: rgba(238, 111, 116, 0.08); color: #eaa3a6; }

.profile-loading { display: flex; min-height: 320px; align-items: center; justify-content: center; gap: 9px; color: #858c98; font-size: 11px; }
.profile-unavailable { display: grid; grid-template-columns: auto minmax(0,1fr) auto; align-items: center; gap: 13px; margin-top: 28px; border: 1px solid rgba(238,111,116,.14); border-radius: 17px; padding: 18px; background: rgba(238,111,116,.04); color: #eaa3a6; }
.profile-unavailable h3 { margin: 0 0 5px; color: #e6e8ed; font-size: 13px; }
.profile-unavailable p { margin: 0; color: #858c98; font-size: 10px; line-height: 1.6; }
.profile-unavailable button { display: inline-flex; align-items: center; gap: 6px; border-radius: 10px; padding: 8px 11px; font-size: 9px; white-space: nowrap; }
.profile-hero { position: relative; display: grid; grid-template-columns:minmax(0,1fr) auto; gap: 12px; margin-top: 28px; overflow: hidden; border: 1px solid rgba(158,140,255,.16); border-radius: 18px; padding: 20px; background: linear-gradient(135deg,rgba(158,140,255,.11),rgba(104,219,184,.045)); }
.profile-hero.ready { border-color: rgba(104,219,184,.2); }
.profile-hero > div:first-child { display: grid; gap: 5px; }
.profile-hero span { color: #a99cec; font-size: 9px; font-weight: 750; letter-spacing: .08em; }
.profile-hero h3 { margin: 0; font-size: 17px; }
.profile-hero p { max-width: 520px; margin: 0; color: #7f8793; font-size: 10px; line-height: 1.65; }
.profile-hero > strong { align-self: center; color: #9f91e6; font-size: 15px; }
.profile-hero.ready > strong { color: #82dbb9; }
.profile-progress { position: absolute; right: 0; bottom: 0; left: 0; height: 3px; background: rgba(255,255,255,.05); }
.profile-progress i { display: block; height: 100%; background: linear-gradient(90deg,#9f91e6,#82dbb9); transition: width .3s ease; }
.profile-metrics { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 9px; margin-top: 12px; }
.profile-metrics article { display: grid; min-width: 0; gap: 5px; border: 1px solid rgba(255,255,255,.07); border-radius: 13px; padding: 13px; background: rgba(255,255,255,.025); color: #9f91e6; }
.profile-metrics span { color: #737b87; font-size: 9px; }
.profile-metrics strong { overflow: hidden; color: #e6e8ed; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.profile-section .section-heading > svg { color: #9f91e6; }
.profile-labels { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 9px; margin-top: 14px; }
.profile-labels article { display: grid; gap: 4px; border: 1px solid rgba(158,140,255,.13); border-radius: 12px; padding: 12px; background: rgba(158,140,255,.055); }
.profile-labels strong { color: #e5e0ff; font-size: 12px; }
.profile-labels span { color: #858c98; font-size: 9px; line-height: 1.5; }
.profile-labels small { color: #77718f; font-size: 8px; }
.profile-empty-copy { margin: 14px 0 0; color: #747c88; font-size: 10px; line-height: 1.6; }
.profile-rankings { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12px; margin-top: 18px; }
.profile-rankings > article { min-width: 0; border: 1px solid rgba(255,255,255,.075); border-radius: 17px; padding: 16px; background: rgba(255,255,255,.02); }
.profile-rankings header { display: flex; align-items: center; justify-content: space-between; }
.profile-rankings h3 { margin: 0; font-size: 13px; }
.profile-rankings header span { color: #696f7a; font-size: 8px; letter-spacing: .12em; }
.profile-rankings ol { display: grid; gap: 2px; margin: 11px 0 0; padding: 0; list-style: none; counter-reset: rank; }
.profile-rankings li { display: grid; grid-template-columns:minmax(0,1fr) auto; align-items: center; gap: 8px; border-radius: 9px; padding: 8px; counter-increment: rank; }
.profile-rankings li:hover { background: rgba(255,255,255,.025); }
.profile-rankings li div { display: grid; min-width: 0; gap: 3px; }
.profile-rankings li strong { overflow: hidden; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.profile-rankings li span,.profile-rankings > article > p { margin: 0; color: #717986; font-size: 8px; }
.profile-rankings li b { color: #9f91e6; font-size: 9px; font-weight: 650; }
.profile-tags { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 14px; }
.profile-tags > span { display: grid; gap: 2px; border: 1px solid rgba(104,219,184,.12); border-radius: 999px; padding: 7px 10px; background: rgba(104,219,184,.045); }
.profile-tags b { color: #a8dfca; font-size: 9px; }
.profile-tags small { color: #687b75; font-size: 7px; }
.profile-refresh { display: flex; justify-content: flex-end; margin-top: 14px; }
.profile-refresh button,.settings-message button { display: inline-flex; align-items: center; gap: 6px; border-radius: 10px; padding: 8px 11px; font-size: 9px; }

.settings-fade-enter-active, .settings-fade-leave-active { transition: opacity 180ms ease; }
.settings-fade-enter-active .settings-dialog, .settings-fade-leave-active .settings-dialog { transition: transform 220ms cubic-bezier(.2,.8,.2,1), opacity 180ms ease; }
.settings-fade-enter-from, .settings-fade-leave-to { opacity: 0; }
.settings-fade-enter-from .settings-dialog, .settings-fade-leave-to .settings-dialog { opacity: 0; transform: translateY(14px) scale(0.98); }

@media (max-width: 700px) {
  .settings-backdrop { padding: 10px; }
  .settings-dialog { height: calc(100dvh - 20px); max-height: calc(100dvh - 20px); grid-template-columns: 1fr; grid-template-rows: auto minmax(0, 1fr); border-radius: 18px; }
  .settings-nav { min-height: auto; flex-direction: row; align-items: center; gap: 12px; padding: 12px; border-right: 0; border-bottom: 1px solid rgba(255, 255, 255, 0.075); }
  .settings-brand { padding: 0; }
  .settings-brand div, .settings-future { display: none; }
  .settings-nav nav { flex: 1; }
  .settings-nav nav button { justify-content: center; }
  .settings-content { padding: 20px 16px; }
  .connection-card { grid-template-columns: 40px minmax(0, 1fr); }
  .connection-status { grid-column: 1 / -1; justify-self: start; }
  .section-heading { display: grid; }
  .qr-login-card { grid-template-columns: 1fr; justify-items: center; }
  .qr-instructions { align-items: center; text-align: center; }
  .settings-actions { flex-wrap: wrap; }
  .security-note { width: 100%; margin: 0; }
  .profile-metrics { grid-template-columns: repeat(2,minmax(0,1fr)); }
  .profile-labels,.profile-rankings { grid-template-columns: 1fr; }
  .profile-unavailable { grid-template-columns: auto minmax(0,1fr); }
  .profile-unavailable button { grid-column: 1 / -1; justify-self: end; }
}
</style>
