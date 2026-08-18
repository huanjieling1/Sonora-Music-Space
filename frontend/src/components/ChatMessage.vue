<script setup>
import { computed } from 'vue'
import { Bot, UserRound } from 'lucide-vue-next'
import InlineMusicResults from './InlineMusicResults.vue'
import InlinePlaylistResults from './InlinePlaylistResults.vue'
import InlineArtistResults from './InlineArtistResults.vue'
import InlineProfileStory from './InlineProfileStory.vue'
import InlineWorkflowProgress from './InlineWorkflowProgress.vue'
import InlineChartResults from './InlineChartResults.vue'
import InlineProactiveSuggestions from './InlineProactiveSuggestions.vue'

const props = defineProps({
  role: { type: String, required: true },
  content: { type: String, required: true },
  error: { type: Boolean, default: false },
  actions: { type: Array, default: () => [] },
  conversationId: { type: String, default: '' },
})

const emit = defineEmits(['quick-prompt'])

const hasProfileStory = computed(() => props.role !== 'USER' && props.actions.some(action => (
  action?.type === 'SHOW_MUSIC_PROFILE_STORY' && action.profileStory
)))
</script>

<template>
  <article class="chat-message" :class="[role.toLowerCase(), { error }]">
    <div class="message-avatar">
      <UserRound v-if="role === 'USER'" :size="15" />
      <Bot v-else :size="15" />
    </div>
    <div class="message-body">
      <div class="message-label">{{ role === 'USER' ? '你' : 'Sonora Agent' }}</div>
      <InlineWorkflowProgress v-if="role !== 'USER' && actions.length" :actions="actions" />
      <div v-if="!hasProfileStory || error" class="message-bubble">{{ content }}</div>
      <InlineProfileStory v-if="role !== 'USER' && actions.length" :actions="actions" />
      <InlineArtistResults v-if="role !== 'USER' && actions.length" :actions="actions" :conversation-id="conversationId" />
      <InlineChartResults v-if="role !== 'USER' && actions.length" :actions="actions" />
      <InlinePlaylistResults v-if="role !== 'USER' && actions.length" :actions="actions" />
      <InlineMusicResults v-if="role !== 'USER' && actions.length" :actions="actions" />
      <InlineProactiveSuggestions
        v-if="role !== 'USER' && actions.length"
        :actions="actions"
        @select="emit('quick-prompt', $event)"
      />
    </div>
  </article>
</template>
