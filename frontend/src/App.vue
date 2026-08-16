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
