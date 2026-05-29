import { defineStore } from 'pinia'

function load() {
  try {
    return JSON.parse(localStorage.getItem('me_wishlist') || '[]')
  } catch (e) {
    return []
  }
}

export const useWishlistStore = defineStore('wishlist', {
  state: () => ({
    ids: load(),
  }),
  getters: {
    count: (s) => s.ids.length,
    has: (s) => (id) => s.ids.includes(id),
  },
  actions: {
    persist() {
      if (typeof localStorage !== 'undefined') localStorage.setItem('me_wishlist', JSON.stringify(this.ids))
    },
    toggle(id) {
      if (this.ids.includes(id)) this.ids = this.ids.filter((x) => x !== id)
      else this.ids.push(id)
      this.persist()
      return this.ids.includes(id)
    },
    remove(id) {
      this.ids = this.ids.filter((x) => x !== id)
      this.persist()
    },
  },
})
