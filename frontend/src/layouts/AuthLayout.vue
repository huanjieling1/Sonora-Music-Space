<script setup>
import { Bot, Music2, Sparkles } from 'lucide-vue-next'

defineProps({
  heading: { type: Array, required: true },
  description: { type: String, required: true },
  footnote: { type: String, required: true },
})
</script>

<template>
  <main class="auth-shell">
    <section class="auth-aside">
      <RouterLink class="brand brand-inverse" to="/">
        <span class="brand-mark"><Sparkles :size="17" /></span>
        <span class="brand-copy"><strong>Sonora</strong><small>Agent Studio</small></span>
      </RouterLink>
      <div class="auth-aside-copy">
        <span class="auth-eyebrow">AI WORKSPACE · MUSIC FLOW</span>
        <h1><span v-for="line in heading" :key="line">{{ line }}</span></h1>
        <p>{{ description }}</p>
        <div class="auth-features" aria-label="功能概览">
          <span><Bot :size="15" /> 智能 Agent 工作流</span>
          <span><Music2 :size="15" /> 与音乐保持专注</span>
        </div>
      </div>
      <small>{{ footnote }}</small>
    </section>
    <section class="auth-main">
      <div class="auth-panel">
        <span class="auth-panel-kicker">WELCOME TO SONORA</span>
        <slot />
      </div>
    </section>
  </main>
</template>
