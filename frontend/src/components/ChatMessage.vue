<script setup>
import { Bot, UserRound } from 'lucide-vue-next'
import InlineMusicResults from './InlineMusicResults.vue'
import InlinePlaylistResults from './InlinePlaylistResults.vue'
import InlineArtistResults from './InlineArtistResults.vue'

defineProps({
  role: { type: String, required: true },
  content: { type: String, required: true },
  error: { type: Boolean, default: false },
  actions: { type: Array, default: () => [] },
  conversationId: { type: String, default: '' },
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
      <InlineArtistResults v-if="role !== 'USER' && actions.length" :actions="actions" :conversation-id="conversationId" />
      <InlinePlaylistResults v-if="role !== 'USER' && actions.length" :actions="actions" />
      <InlineMusicResults v-if="role !== 'USER' && actions.length" :actions="actions" />
    </div>
  </article>
</template>
