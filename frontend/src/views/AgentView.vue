<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Disc3, PanelRightClose, PanelRightOpen, Send, Sparkles } from 'lucide-vue-next'
import ChatMessage from '../components/ChatMessage.vue'
import ConversationSidebar from '../components/ConversationSidebar.vue'
import MusicPanel from '../components/MusicPanel.vue'
import { ApiError, request } from '../services/api'
import { useAuthStore } from '../stores/auth'
import { useMusicStore } from '../stores/music'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const music = useMusicStore()
const conversations = ref([])
const activeId = ref('')
const messages = ref([])
const input = ref('')
const creating = ref(false)
const sending = ref(false)
const loadingHistory = ref(false)
const loggingOut = ref(false)
const messageContainer = ref(null)
const showMusic = ref(true)
let initialized = false

const isFreshConversation = computed(() => (
  messages.value.length === 1
  && messages.value[0]?.role === 'ASSISTANT'
  && !messages.value[0]?.id
  && !messages.value[0]?.error
))

const suggestions = [
  { label: '为此刻配乐', prompt: '我现在想听一些适合专注工作的音乐，请直接为我推荐。' },
  { label: '设计一个 Agent', prompt: '帮我设计一个结构清晰、可以落地的 LangChain4j Agent。' },
  { label: '排查一个问题', prompt: '帮我系统排查这个 Agent 问题，并给出最小可验证修复方案：' },
]

onMounted(initialize)

watch(() => route.query.conversation, async id => {
  if (!initialized || typeof id !== 'string' || id === activeId.value) return
  if (conversations.value.some(item => item.id === id)) await selectConversation(id, false)
})

async function initialize() {
  try {
    await refreshConversations()
    const requested = typeof route.query.conversation === 'string' ? route.query.conversation : ''
    const forceNew = route.query.new === '1'
    if (forceNew || conversations.value.length === 0) {
      await createConversation()
    } else {
      const selected = conversations.value.some(item => item.id === requested)
        ? requested
        : conversations.value[0].id
      await selectConversation(selected)
    }
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      await router.replace('/login')
      return
    }
    messages.value = [{ role: 'ASSISTANT', content: error.message, error: true }]
  } finally {
    initialized = true
  }
}

async function refreshConversations() {
  const result = await request('/api/agent/conversations')
  conversations.value = result.data
}

async function createConversation() {
  if (creating.value) return
  creating.value = true
  try {
    const result = await request('/api/agent/conversations', { method: 'POST' })
    conversations.value.unshift(result.data)
    await selectConversation(result.data.id)
  } finally {
    creating.value = false
  }
}

async function selectConversation(id, updateRoute = true) {
  activeId.value = id
  loadingHistory.value = true
  if (updateRoute) {
    await router.replace({ path: '/agent', query: { conversation: id } })
  }
  try {
    const result = await request(`/api/agent/conversations/${encodeURIComponent(id)}/messages`)
    messages.value = result.data.length
      ? result.data.map(item => ({ ...item, error: false }))
      : [{ role: 'ASSISTANT', content: '你好，我是 Sonora Agent。我可以帮你开发和调试 Agent，也可以直接搜索、推荐并播放音乐。', error: false }]
    await scrollToBottom()
  } finally {
    loadingHistory.value = false
  }
}

async function sendMessage() {
  const text = input.value.trim()
  if (!text || sending.value || !activeId.value) return
  input.value = ''
  sending.value = true
  messages.value.push({ role: 'USER', content: text, error: false })
  const pending = { role: 'ASSISTANT', content: '正在思考...', error: false }
  messages.value.push(pending)
  await scrollToBottom()
  try {
    const result = await request('/api/agent/chat', {
      method: 'POST',
      body: JSON.stringify({ conversationId: activeId.value, message: text }),
    })
    pending.content = result.data.answer
    if (result.data.actions?.length) {
      showMusic.value = true
      music.publish(result.data.actions)
    }
    await refreshConversations()
  } catch (error) {
    pending.content = error.message
    pending.error = true
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    // auth.logout() always clears the local session. A transient server error
    // must not leave the user stranded on a protected page afterwards.
    music.clearQueue()
    await auth.logout().catch(() => undefined)
    await router.replace('/login')
  } finally {
    loggingOut.value = false
  }
}

function useSuggestion(prompt) {
  input.value = prompt
}

async function scrollToBottom() {
  await nextTick()
  if (messageContainer.value) messageContainer.value.scrollTop = messageContainer.value.scrollHeight
}
</script>

<template>
  <main class="app-shell" :class="{ 'music-open': showMusic }">
    <ConversationSidebar
      :conversations="conversations"
      :active-id="activeId"
      :user="auth.user"
      :creating="creating"
      :logging-out="loggingOut"
      @create="createConversation"
      @select="selectConversation"
      @logout="logout"
    />
    <section class="workspace">
      <header class="workspace-header">
        <div>
          <span class="workspace-kicker">AGENT WORKSPACE</span>
          <h1>智能工作台</h1>
        </div>
        <div class="workspace-tools">
          <span class="session-label">会话 {{ activeId ? activeId.slice(0, 8) : '准备中' }}</span>
          <RouterLink class="toolbar-button" to="/music" title="打开音乐工作台">
            <Disc3 :size="18" />
            <span>音乐库</span>
          </RouterLink>
          <button class="toolbar-button" type="button" :title="showMusic ? '收起音乐面板' : '展开音乐面板'" @click="showMusic = !showMusic">
            <PanelRightClose v-if="showMusic" :size="18" />
            <PanelRightOpen v-else :size="18" />
            <span>音乐</span>
          </button>
        </div>
      </header>
      <div ref="messageContainer" class="messages" aria-live="polite" :aria-busy="loadingHistory">
        <section v-if="isFreshConversation" class="chat-empty">
          <div class="agent-orb"><Sparkles :size="26" /></div>
          <span class="workspace-kicker">LANGCHAIN4J COPILOT</span>
          <h2>今天想一起完成什么？</h2>
          <p>从一个想法、问题或现有实现开始。我会帮你拆解、实现并验证。</p>
          <div class="suggestion-grid">
            <button v-for="item in suggestions" :key="item.label" type="button" @click="useSuggestion(item.prompt)">
              <strong>{{ item.label }}</strong>
              <span>{{ item.prompt }}</span>
            </button>
          </div>
        </section>
        <template v-else>
          <ChatMessage
            v-for="(message, index) in messages"
            :key="message.id || `${message.role}-${index}`"
            :role="message.role"
            :content="message.content"
            :error="message.error"
          />
        </template>
      </div>
      <footer class="composer">
        <form @submit.prevent="sendMessage">
          <div class="composer-shell">
            <textarea
              v-model="input"
              rows="1"
              maxlength="10000"
              placeholder="向 Agent 描述你的目标…"
              :disabled="sending || !activeId"
              required
              @keydown.enter.exact.prevent="sendMessage"
            ></textarea>
            <button class="send-button" type="submit" :disabled="sending || !input.trim() || !activeId" title="发送消息" aria-label="发送消息">
              <Send :size="18" />
            </button>
          </div>
          <div class="composer-meta">
            <span><Sparkles :size="12" /> Agent 可以协助开发，也能搜索与播放音乐</span>
            <span>Enter 发送 · Shift + Enter 换行</span>
          </div>
        </form>
      </footer>
    </section>
    <MusicPanel v-if="showMusic" :conversation-id="activeId" />
  </main>
</template>
