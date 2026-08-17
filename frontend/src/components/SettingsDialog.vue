<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  CheckCircle2,
  CircleAlert,
  KeyRound,
  LoaderCircle,
  Music2,
  QrCode,
  RefreshCw,
  Settings2,
  ShieldCheck,
  Smartphone,
  Trash2,
  X,
} from 'lucide-vue-next'
import { request } from '../services/api'
import { confirmAction } from '../services/confirm'

const props = defineProps({
  open: { type: Boolean, default: false },
})
const emit = defineEmits(['close'])

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

watch(() => props.open, value => {
  if (value) refreshQqStatus()
  else closeQrLogin()
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
              <button class="active" type="button">
                <Music2 :size="17" />
                <span>QQ 音乐</span>
              </button>
            </nav>
            <p class="settings-future">后续账号、播放和隐私设置都会集中在这里。</p>
          </aside>

          <div class="settings-content">
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
  max-height: min(680px, calc(100dvh - 48px));
  grid-template-columns: 220px minmax(0, 1fr);
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

.settings-content { min-width: 0; overflow-y: auto; padding: 28px; }
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

.settings-fade-enter-active, .settings-fade-leave-active { transition: opacity 180ms ease; }
.settings-fade-enter-active .settings-dialog, .settings-fade-leave-active .settings-dialog { transition: transform 220ms cubic-bezier(.2,.8,.2,1), opacity 180ms ease; }
.settings-fade-enter-from, .settings-fade-leave-to { opacity: 0; }
.settings-fade-enter-from .settings-dialog, .settings-fade-leave-to .settings-dialog { opacity: 0; transform: translateY(14px) scale(0.98); }

@media (max-width: 700px) {
  .settings-backdrop { padding: 10px; }
  .settings-dialog { max-height: calc(100dvh - 20px); grid-template-columns: 1fr; border-radius: 18px; }
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
}
</style>
