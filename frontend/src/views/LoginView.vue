<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Eye, EyeOff } from 'lucide-vue-next'
import AuthLayout from '../layouts/AuthLayout.vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const rememberedAccountKey = 'sonora.rememberedAccount'
const rememberedAccount = readRememberedAccount()
const account = ref(rememberedAccount)
const password = ref('')
const rememberMe = ref(Boolean(rememberedAccount))
const showPassword = ref(false)
const submitting = ref(false)
const error = ref('')
const notice = computed(() => route.query.registered === '1' ? '注册成功，请使用新账号登录' : '')

function readRememberedAccount() {
  try {
    return window.localStorage.getItem(rememberedAccountKey) || ''
  } catch {
    return ''
  }
}

function storeRememberedAccount(value) {
  try {
    if (rememberMe.value) window.localStorage.setItem(rememberedAccountKey, value)
    else window.localStorage.removeItem(rememberedAccountKey)
  } catch {
    // 浏览器禁用本地存储时，服务端的安全登录凭证仍然有效。
  }
}

async function submit() {
  if (submitting.value) return
  error.value = ''
  submitting.value = true
  try {
    const normalizedAccount = account.value.trim()
    await auth.login(normalizedAccount, password.value, rememberMe.value)
    storeRememberedAccount(normalizedAccount)
    const target = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : '/agent'
    await router.replace(target)
  } catch (requestError) {
    error.value = requestError.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout
    :heading="['让思考与节奏，', '自然流动。']"
    description="在同一个安静的空间里，与 Agent 协作、完成代码，并让音乐陪伴专注。"
    footnote="账号支持用户名、邮箱或手机号登录"
  >
    <h2>欢迎回来</h2>
    <p>登录并继续上一次的工作。</p>
    <form class="form-grid" @submit.prevent="submit">
      <div class="field">
        <label for="account">账号</label>
        <input id="account" v-model="account" autocomplete="username" placeholder="用户名、邮箱或手机号" required />
      </div>
      <div class="field">
        <label for="login-password">密码</label>
        <div class="password-input">
          <input
            id="login-password"
            v-model="password"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="current-password"
            placeholder="输入密码"
            required
          />
          <button type="button" :title="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">
            <EyeOff v-if="showPassword" :size="18" />
            <Eye v-else :size="18" />
          </button>
        </div>
      </div>
      <label class="remember-option" for="remember-me">
        <input id="remember-me" v-model="rememberMe" type="checkbox" />
        <span>
          <strong>记住我</strong>
          <small>30 天内自动登录，不保存密码</small>
        </span>
      </label>
      <p class="form-message" :class="{ success: notice && !error }" aria-live="polite">{{ error || notice }}</p>
      <button class="button wide" type="submit" :disabled="submitting">
        {{ submitting ? '正在登录...' : '进入工作台' }}
      </button>
    </form>
    <div class="auth-switch">还没有账号？ <RouterLink to="/register">创建账号</RouterLink></div>
  </AuthLayout>
</template>
