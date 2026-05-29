import { defineStore } from 'pinia'

function load() {
  try {
    return JSON.parse(localStorage.getItem('me_user') || 'null')
  } catch (e) {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: load(),
  }),
  getters: {
    isAuthenticated: (s) => !!s.user,
    initials: (s) => {
      if (!s.user) return ''
      return ((s.user.firstName?.[0] || '') + (s.user.lastName?.[0] || '')).toUpperCase()
    },
  },
  actions: {
    persist() {
      if (typeof localStorage !== 'undefined') localStorage.setItem('me_user', JSON.stringify(this.user))
    },
    signIn(email) {
      // prototype: accept any credentials
      this.user = {
        id: 'u-1',
        email: email || 'isabella@example.com',
        firstName: 'Isabella',
        lastName: 'Moreau',
        newsletterOptIn: true,
      }
      this.persist()
    },
    signUp(payload) {
      this.user = {
        id: 'u-1',
        email: payload.email,
        firstName: payload.firstName || 'Isabella',
        lastName: payload.lastName || 'Moreau',
        newsletterOptIn: !!payload.newsletter,
      }
      this.persist()
    },
    updateProfile(patch) {
      this.user = { ...this.user, ...patch }
      this.persist()
    },
    signOut() {
      this.user = null
      if (typeof localStorage !== 'undefined') localStorage.removeItem('me_user')
    },
  },
})
