<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Disc3, Send, Sparkles } from 'lucide-vue-next'
import ChatMessage from '../components/ChatMessage.vue'
import ConversationSidebar from '../components/ConversationSidebar.vue'
import SettingsDialog from '../components/SettingsDialog.vue'
import { restoreHistoryMessage } from '../services/agentMessages'
import { ApiError, request } from '../services/api'
import { confirmAction } from '../services/confirm'
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
const deletingId = ref('')
const sending = ref(false)
const loadingHistory = ref(false)
const loggingOut = ref(false)
const messageContainer = ref(null)
const settingsOpen = ref(false)
let initialized = false

const isFreshConversation = computed(() => (
  messages.value.length === 1
  && messages.value[0]?.role === 'ASSISTANT'
  && !messages.value[0]?.id
  && !messages.value[0]?.error
))

const suggestions = [
  { label: '为此刻配乐', prompt: '我现在想听一些适合专注工作的音乐，请直接为我推荐。' },
  { label: '总结音乐偏好', prompt: '分析并总结我的音乐画像，告诉我目前有哪些可靠的偏好。' },
  { label: '随机播放歌单', prompt: '随机选择一个 QQ 音乐公开歌单并开始播放。' },
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
      ? result.data.map(restoreHistoryMessage)
      : [{ role: 'ASSISTANT', content: '你好，我是 Sonora 音乐 Agent。告诉我你想听的歌曲、歌手、曲风、情绪或场景，我会为你查找和推荐真实音乐。', actions: [], error: false }]
    await scrollToBottom()
  } finally {
    loadingHistory.value = false
  }
}

async function deleteConversation(conversation) {
  if (!conversation?.id || deletingId.value) return
  const accepted = await confirmAction({
    eyebrow: '删除对话',
    title: '删除这段音乐对话？',
    message: '这段对话及其中的音乐结果将从侧栏和历史记录中移除。',
    subject: conversation.title,
    hint: '删除后无法在界面中恢复',
    confirmText: '删除对话',
    cancelText: '保留对话',
  })
  if (!accepted) return

  deletingId.value = conversation.id
  try {
    const removedIndex = conversations.value.findIndex(item => item.id === conversation.id)
    await request(`/api/agent/conversations/${encodeURIComponent(conversation.id)}`, { method: 'DELETE' })
    conversations.value = conversations.value.filter(item => item.id !== conversation.id)
    if (activeId.value !== conversation.id) return

    const replacement = conversations.value[Math.min(removedIndex, conversations.value.length - 1)]
    if (replacement) {
      await selectConversation(replacement.id)
    } else {
      activeId.value = ''
      messages.value = []
      await createConversation()
    }
  } finally {
    deletingId.value = ''
  }
}

async function sendMessage() {
  const text = input.value.trim()
  if (!text || sending.value || !activeId.value) return
  input.value = ''
  sending.value = true
  messages.value.push({ role: 'USER', content: text, actions: [], error: false })
  const pending = { role: 'ASSISTANT', content: '正在为你寻找合适的音乐...', actions: [], error: false }
  messages.value.push(pending)
  await scrollToBottom()
  try {
    const result = await request('/api/agent/chat', {
      method: 'POST',
      body: JSON.stringify({ conversationId: activeId.value, message: text }),
    })
    pending.content = result.data.answer
    pending.actions = result.data.actions || []
    music.applyAgentActions(result.data.actions)
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
  <main class="app-shell">
    <ConversationSidebar
      :conversations="conversations"
      :active-id="activeId"
      :user="auth.user"
      :creating="creating"
      :deleting-id="deletingId"
      :logging-out="loggingOut"
      @create="createConversation"
      @select="selectConversation"
      @delete="deleteConversation"
      @settings="settingsOpen = true"
      @logout="logout"
    />
    <section class="workspace">
      <header class="workspace-header">
        <div>
          <span class="workspace-kicker">SONORA MUSIC COMPANION</span>
          <h1>音乐陪伴空间</h1>
        </div>
        <div class="workspace-tools">
          <span class="session-label">会话 {{ activeId ? activeId.slice(0, 8) : '准备中' }}</span>
          <RouterLink class="toolbar-button" to="/music" title="打开音乐工作台">
            <Disc3 :size="18" />
            <span>音乐库</span>
          </RouterLink>
        </div>
      </header>
      <div ref="messageContainer" class="messages" aria-live="polite" :aria-busy="loadingHistory">
        <section v-if="isFreshConversation" class="chat-empty">
          <div class="agent-orb"><Sparkles :size="26" /></div>
          <span class="workspace-kicker">SONORA MUSIC AGENT</span>
          <h2>现在想听什么？</h2>
          <p>描述一首歌、一位歌手、一种曲风，或者你此刻的情绪与场景。</p>
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
            :actions="message.actions || []"
            :conversation-id="activeId"
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
              placeholder="告诉我你想听的音乐、情绪或场景…"
              :disabled="sending || !activeId"
              required
              @keydown.enter.exact.prevent="sendMessage"
            ></textarea>
            <button class="send-button" type="submit" :disabled="sending || !input.trim() || !activeId" title="发送消息" aria-label="发送消息">
              <Send :size="18" />
            </button>
          </div>
          <div class="composer-meta">
            <span><Sparkles :size="12" /> 搜索、推荐、画像分析与播放真实音乐</span>
            <span>Enter 发送 · Shift + Enter 换行</span>
          </div>
        </form>
      </footer>
    </section>
    <SettingsDialog :open="settingsOpen" @close="settingsOpen = false" />
  </main>
</template>
