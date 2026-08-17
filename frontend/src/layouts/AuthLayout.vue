<script setup>
import { Headphones, Music2, Sparkles } from 'lucide-vue-next'

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
        <span class="brand-copy"><strong>Sonora</strong><small>Music Companion</small></span>
      </RouterLink>
      <div class="auth-aside-copy">
        <span class="auth-eyebrow">PERSONALIZED MUSIC · ALWAYS ON</span>
        <h1><span v-for="line in heading" :key="line">{{ line }}</span></h1>
        <p>{{ description }}</p>
        <div class="auth-features" aria-label="功能概览">
          <span><Headphones :size="15" /> 个性化音乐推荐</span>
          <span><Music2 :size="15" /> 跨曲库搜索与播放</span>
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
