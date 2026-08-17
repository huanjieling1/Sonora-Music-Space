<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import MusicPlayerDock from './components/MusicPlayerDock.vue'
import ConfirmDialog from './components/ConfirmDialog.vue'

const route = useRoute()
const showGlobalPlayer = computed(() => Boolean(route.meta.requiresAuth))
const isTrackExperience = computed(() => route.name === 'music-track')
</script>

<template>
  <RouterView />
  <MusicPlayerDock v-if="showGlobalPlayer" :immersive="isTrackExperience" />
  <ConfirmDialog />
</template>

<style>
::view-transition-group(sonora-player-experience) {
  z-index: 120;
  overflow: hidden;
  border-radius: 18px;
  animation-duration: 560ms;
  animation-timing-function: cubic-bezier(0.2, 0.82, 0.22, 1);
}

::view-transition-image-pair(sonora-player-experience) {
  isolation: auto;
}

html[data-music-experience-transition]::view-transition-old(root) {
  animation: music-root-fade-out 420ms ease both;
}

html[data-music-experience-transition]::view-transition-new(root) {
  animation: music-root-fade-in 520ms ease both;
}

html[data-music-experience-transition='enter']:not([data-music-experience-native]) .track-experience {
  transform-origin: var(--music-transition-origin-x) var(--music-transition-origin-y);
  animation: music-experience-expand 520ms cubic-bezier(0.2, 0.82, 0.22, 1) both;
}

html[data-music-experience-transition='exit']:not([data-music-experience-native]) .track-experience {
  transform-origin: var(--music-transition-origin-x) var(--music-transition-origin-y);
  animation: music-experience-collapse 360ms cubic-bezier(0.4, 0, 0.7, 0.2) both;
}

@keyframes music-root-fade-out {
  to { opacity: 0.2; }
}

@keyframes music-root-fade-in {
  from { opacity: 0.2; }
}

@keyframes music-experience-expand {
  from {
    opacity: 0;
    border-radius: 24px;
    transform: scale(0.08);
    filter: saturate(0.8) blur(2px);
  }
  58% { opacity: 1; }
  to {
    border-radius: 0;
    transform: scale(1);
    filter: none;
  }
}

@keyframes music-experience-collapse {
  from {
    opacity: 1;
    border-radius: 0;
    transform: scale(1);
    filter: none;
  }
  to {
    opacity: 0;
    border-radius: 24px;
    transform: scale(0.08);
    filter: saturate(0.8) blur(2px);
  }
}

@media (prefers-reduced-motion: reduce) {
  ::view-transition-group(sonora-player-experience),
  ::view-transition-old(root),
  ::view-transition-new(root),
  .track-experience {
    animation-duration: 0.01ms !important;
  }
}
</style>
