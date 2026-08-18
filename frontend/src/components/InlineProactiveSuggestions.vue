<script setup>
import { computed } from 'vue'
import { ArrowUpRight, HeartHandshake, ListMusic } from 'lucide-vue-next'

const props = defineProps({
  actions: { type: Array, default: () => [] },
})

const emit = defineEmits(['select'])

const suggestions = computed(() => {
  for (let index = props.actions.length - 1; index >= 0; index -= 1) {
    const action = props.actions[index]
    if (action?.type === 'SHOW_PROACTIVE_SUGGESTIONS' && action.proactiveSuggestions) {
      return action.proactiveSuggestions
    }
  }
  return null
})
</script>

<template>
  <section v-if="suggestions?.items?.length" class="proactive-suggestions" aria-label="音乐陪伴后续建议">
    <header>
      <span><HeartHandshake :size="14" /> 有边界的主动建议</span>
      <strong>{{ suggestions.title }}</strong>
    </header>
    <div>
      <button
        v-for="item in suggestions.items"
        :key="`${item.capabilityId}:${item.prompt}`"
        type="button"
        @click="emit('select', item.prompt)"
      >
        <ListMusic :size="14" />
        <span>{{ item.label }}</span>
        <ArrowUpRight :size="13" />
      </button>
    </div>
  </section>
</template>

<style scoped>
.proactive-suggestions{display:grid;max-width:760px;margin-top:12px;gap:9px;border:1px solid rgba(157,139,255,.2);border-radius:16px;padding:12px;background:linear-gradient(135deg,rgba(71,55,126,.18),rgba(22,25,34,.72))}.proactive-suggestions header{display:grid;gap:3px}.proactive-suggestions header span{display:flex;align-items:center;gap:5px;color:#a99cff;font-size:8px;font-weight:800;letter-spacing:.1em;text-transform:uppercase}.proactive-suggestions header strong{color:#e7e5f4;font-size:11px}.proactive-suggestions>div{display:flex;flex-wrap:wrap;gap:7px}.proactive-suggestions button{display:flex;align-items:center;gap:6px;border:1px solid rgba(255,255,255,.09);border-radius:999px;padding:8px 10px;background:rgba(255,255,255,.04);color:#c8c7d2;font-size:9px;transition:.16s}.proactive-suggestions button:hover{border-color:rgba(169,156,255,.38);background:rgba(169,156,255,.1);color:#fff;transform:translateY(-1px)}
</style>
