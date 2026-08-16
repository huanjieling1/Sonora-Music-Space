import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import AgentView from '../views/AgentView.vue'
import MusicLibraryView from '../views/MusicLibraryView.vue'
import MusicTrackView from '../views/MusicTrackView.vue'
import QqVideoView from '../views/QqVideoView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/agent' },
    { path: '/login', component: LoginView, meta: { guestOnly: true } },
    { path: '/register', component: RegisterView, meta: { guestOnly: true } },
    { path: '/agent', component: AgentView, meta: { requiresAuth: true } },
    { path: '/music', name: 'music-home', component: MusicLibraryView, meta: { requiresAuth: true } },
    { path: '/music/playlists/:playlistId', name: 'music-playlist', component: MusicLibraryView, meta: { requiresAuth: true } },
    { path: '/music/qq/playlists/:qqPlaylistId', name: 'music-qq-playlist', component: MusicLibraryView, meta: { requiresAuth: true } },
    { path: '/music/qq/artists/:artistMid', name: 'music-qq-artist', component: MusicLibraryView, meta: { requiresAuth: true } },
    { path: '/music/qq/albums/:albumMid', name: 'music-qq-album', component: MusicLibraryView, meta: { requiresAuth: true } },
    { path: '/music/qq/videos/:videoId', name: 'music-qq-video', component: QqVideoView, meta: { requiresAuth: true } },
    { path: '/music/tracks/:provider/:trackId', name: 'music-track', component: MusicTrackView, meta: { requiresAuth: true } },
    { path: '/:pathMatch(.*)*', redirect: '/agent' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.initialize()
  if (to.meta.requiresAuth && !auth.user) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && auth.user) return '/agent'
})

export default router
