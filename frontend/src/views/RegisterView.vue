<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { RefreshCw } from 'lucide-vue-next'
import AuthLayout from '../layouts/AuthLayout.vue'
import { apiUrl, request } from '../services/api'

const router = useRouter()
const form = ref({ username: '', email: '', phone: '', password: '', confirmPassword: '', imageCaptcha: '' })
const captchaSeed = ref(Date.now())
const submitting = ref(false)
const error = ref('')
const captchaSrc = computed(() => `${apiUrl('/api/auth/captcha')}?t=${captchaSeed.value}`)

function refreshCaptcha() {
  captchaSeed.value = Date.now()
  form.value.imageCaptcha = ''
}

async function submit() {
  error.value = ''
  if (form.value.password !== form.value.confirmPassword) {
    error.value = '两次输入的密码不一致'
    return
  }
  submitting.value = true
  try {
    await request('/api/auth/register', { method: 'POST', body: JSON.stringify(form.value) })
    await router.replace({ path: '/login', query: { registered: '1' } })
  } catch (requestError) {
    error.value = requestError.message
    refreshCaptcha()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout
    :heading="['建立属于你的', '音乐空间。']"
    description="保存歌单、播放记录和音乐偏好，让推荐随着每一次聆听更懂你。"
    footnote="图形验证码 2 分钟有效且只能使用一次"
  >
    <h2>创建音乐账号</h2>
    <p>只需一分钟，即可开始发现音乐。</p>
    <form class="form-grid" @submit.prevent="submit">
      <div class="form-row">
        <div class="field">
          <label for="username">用户名</label>
          <input id="username" v-model="form.username" autocomplete="username" placeholder="3-32 位" required />
        </div>
        <div class="field">
          <label for="phone">手机号</label>
          <input id="phone" v-model="form.phone" inputmode="tel" autocomplete="tel" placeholder="11 位大陆手机号" required />
        </div>
      </div>
      <div class="field">
        <label for="email">邮箱</label>
        <input id="email" v-model="form.email" type="email" autocomplete="email" placeholder="name@example.com" required />
      </div>
      <div class="form-row">
        <div class="field">
          <label for="password">密码</label>
          <input id="password" v-model="form.password" type="password" autocomplete="new-password" placeholder="至少 8 位，含字母和数字" required />
        </div>
        <div class="field">
          <label for="confirm-password">确认密码</label>
          <input id="confirm-password" v-model="form.confirmPassword" type="password" autocomplete="new-password" placeholder="再次输入密码" required />
        </div>
      </div>
      <div class="field">
        <label for="image-captcha">图形验证码</label>
        <div class="captcha-control">
          <input id="image-captcha" v-model="form.imageCaptcha" maxlength="5" autocomplete="off" placeholder="输入图中字符" required />
          <img :src="captchaSrc" alt="图形验证码" title="点击刷新验证码" @click="refreshCaptcha" />
          <button type="button" title="刷新验证码" aria-label="刷新验证码" @click="refreshCaptcha">
            <RefreshCw :size="18" />
          </button>
        </div>
      </div>
      <p class="form-message" aria-live="polite">{{ error }}</p>
      <button class="button wide" type="submit" :disabled="submitting">
        {{ submitting ? '正在注册...' : '完成注册' }}
      </button>
    </form>
    <div class="auth-switch">已有账号？ <RouterLink to="/login">返回登录</RouterLink></div>
  </AuthLayout>
</template>
