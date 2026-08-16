import { defineStore } from 'pinia'
import { ApiError, clearCsrf, loadCsrf, request } from '../services/api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,
    initialized: false,
  }),
  actions: {
    async initialize() {
      if (this.initialized) return
      try {
        const result = await request('/api/auth/me')
        this.user = result.data
      } catch (error) {
        if (!(error instanceof ApiError) || error.status !== 401) throw error
        this.user = null
      } finally {
        this.initialized = true
      }
    },
    async login(account, password, rememberMe = false) {
      await loadCsrf()
      const result = await request('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ account, password, rememberMe }),
      })
      this.user = result.data
      this.initialized = true
      await loadCsrf(true)
    },
    async logout() {
      try {
        await request('/api/auth/logout', { method: 'POST' })
      } finally {
        this.user = null
        this.initialized = true
        clearCsrf()
      }
    },
  },
})
