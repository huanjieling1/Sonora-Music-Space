<script setup>
import { computed } from 'vue'
import { LogOut, MessageSquare, Plus, Sparkles } from 'lucide-vue-next'

const props = defineProps({
  conversations: { type: Array, required: true },
  activeId: { type: String, default: '' },
  user: { type: Object, default: null },
  creating: { type: Boolean, default: false },
  loggingOut: { type: Boolean, default: false },
})
const emit = defineEmits(['create', 'select', 'logout'])

const groups = computed(() => {
  const result = []
  const index = new Map()
  for (const conversation of props.conversations) {
    const key = dateKey(conversation.updatedAt)
    if (!index.has(key)) {
      const group = { key, label: groupLabel(conversation.updatedAt), items: [] }
      index.set(key, group)
      result.push(group)
    }
    index.get(key).items.push(conversation)
  }
  return result
})

const userName = computed(() => props.user?.username || (props.loggingOut ? '正在退出…' : '用户'))
const userEmail = computed(() => props.user?.email || '')
const userInitial = computed(() => userName.value.slice(0, 1).toUpperCase())

function dateKey(value) {
  const date = new Date(value)
  return `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`
}

function groupLabel(value) {
  const date = new Date(value)
  const today = new Date()
  const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const targetStart = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const days = Math.round((todayStart - targetStart) / 86400000)
  if (days === 0) return '最近对话'
  if (days === 1) return '昨日对话'
  return date.getFullYear() === today.getFullYear()
    ? `${date.getMonth() + 1}月${date.getDate()}日`
    : `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

function select(event, id) {
  if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return
  event.preventDefault()
  emit('select', id)
}
</script>

<template>
  <aside class="sidebar">
    <RouterLink class="brand" to="/agent">
      <span class="brand-mark"><Sparkles :size="17" /></span>
      <span class="brand-copy"><strong>Sonora</strong><small>Agent Studio</small></span>
    </RouterLink>
    <button class="conversation-create" type="button" :disabled="creating" @click="emit('create')">
      <Plus :size="17" />
      <span>{{ creating ? '正在创建...' : '新建对话' }}</span>
    </button>
    <nav class="conversation-list" aria-label="会话历史">
      <p v-if="!groups.length" class="conversation-empty">暂无对话</p>
      <section v-for="group in groups" :key="group.key" class="conversation-group">
        <h2>{{ group.label }}</h2>
        <a
          v-for="conversation in group.items"
          :key="conversation.id"
          class="conversation-item"
          :class="{ active: conversation.id === activeId }"
          :href="`/agent?conversation=${encodeURIComponent(conversation.id)}`"
          :title="conversation.title"
          @click="select($event, conversation.id)"
        ><MessageSquare :size="16" /><span>{{ conversation.title }}</span></a>
      </section>
    </nav>
    <div class="sidebar-footer">
      <span class="user-avatar">{{ userInitial }}</span>
      <div class="sidebar-user">
        <strong>{{ userName }}</strong>
        <span>{{ userEmail }}</span>
      </div>
      <button
        class="icon-command"
        type="button"
        title="退出登录"
        aria-label="退出登录"
        :disabled="loggingOut"
        @click="emit('logout')"
      >
        <LogOut :size="19" />
      </button>
    </div>
  </aside>
</template>
