// STORE-MKT-A06：商品选择器共享逻辑（闪购 / Real Weddings / Lookbook 三处复用）
// E-CAT-08 search 防抖 300ms + 已选 chip 集合（productIds 去重）
import { ref, watch, type Ref } from 'vue'
import { catalogApi } from '@/api'
import type { AdminProductListItem } from '@/api/types'

export interface PickedProduct {
  id: number
  name: string
  imageUrl?: string | null
}

export function useProductPicker(selectedIds: Ref<number[]>) {
  const keyword = ref('')
  const results = ref<AdminProductListItem[]>([])
  const searching = ref(false)
  /** 已选商品的展示缓存（id → name/img），由搜索结果与回显数据填充 */
  const known = ref<Map<number, PickedProduct>>(new Map())

  let timer: ReturnType<typeof setTimeout> | null = null

  async function doSearch() {
    const q = keyword.value.trim()
    searching.value = true
    try {
      const page = await catalogApi.listProducts({ page: 1, pageSize: 10, search: q || undefined })
      results.value = page.data
      for (const p of page.data) {
        known.value.set(p.id, { id: p.id, name: p.name, imageUrl: p.imageUrl })
      }
    } finally {
      searching.value = false
    }
  }

  watch(keyword, () => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      doSearch().catch(() => {
        results.value = []
      })
    }, 300)
  })

  function toggle(id: number) {
    const idx = selectedIds.value.indexOf(id)
    if (idx >= 0) selectedIds.value.splice(idx, 1)
    else selectedIds.value.push(id)
  }

  function remove(id: number) {
    const idx = selectedIds.value.indexOf(id)
    if (idx >= 0) selectedIds.value.splice(idx, 1)
  }

  function labelOf(id: number): string {
    return known.value.get(id)?.name ?? `#${id}`
  }

  /** 编辑回显：拉一页带出已选商品名称（best-effort，不阻塞） */
  async function primeKnown() {
    if (!selectedIds.value.length) return
    try {
      const page = await catalogApi.listProducts({ page: 1, pageSize: 50 })
      for (const p of page.data) {
        known.value.set(p.id, { id: p.id, name: p.name, imageUrl: p.imageUrl })
      }
    } catch {
      /* 静默：chip 回退 #id 展示 */
    }
  }

  return { keyword, results, searching, known, doSearch, toggle, remove, labelOf, primeKnown }
}
