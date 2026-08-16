<script setup>
import { Bot, UserRound } from 'lucide-vue-next'

defineProps({
  role: { type: String, required: true },
  content: { type: String, required: true },
  error: { type: Boolean, default: false },
})
</script>

<template>
  <article class="chat-message" :class="[role.toLowerCase(), { error }]">
    <div class="message-avatar">
      <UserRound v-if="role === 'USER'" :size="15" />
      <Bot v-else :size="15" />
    </div>
    <div class="message-body">
      <div class="message-label">{{ role === 'USER' ? '你' : 'Sonora Agent' }}</div>
      <div class="message-bubble">{{ content }}</div>
    </div>
  </article>
</template>
