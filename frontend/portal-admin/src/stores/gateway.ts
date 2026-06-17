// STORE-001 useGatewayStore：网关配置 CRUD + 测试连接 + 模型同步（FUNC-004~007/021）
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { gatewayApi } from '@/api'
import type { GatewayConfigDetail, GatewayConfigUpsert, GatewayTestResult, GatewayType } from '@/api/types'

export const useGatewayStore = defineStore('gateway', () => {
  const configs = ref<GatewayConfigDetail[]>([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)
  const loading = ref(false)
  const saving = ref(false)

  /** FUNC-004：拉取配置列表（可按 gateway_type 筛选） */
  async function fetchConfigs(params: { gatewayType?: GatewayType; page?: number } = {}) {
    loading.value = true
    if (params.page) page.value = params.page
    try {
      const res = await gatewayApi.listConfigs({
        gatewayType: params.gatewayType,
        page: page.value,
        pageSize: pageSize.value,
      })
      configs.value = res.data
      total.value = res.totalElements
    } finally {
      loading.value = false
    }
  }

  /** FUNC-005：创建/更新配置（id 缺省=创建） */
  async function saveConfig(body: GatewayConfigUpsert, id?: number): Promise<GatewayConfigDetail> {
    saving.value = true
    try {
      const saved = id == null ? await gatewayApi.createConfig(body) : await gatewayApi.updateConfig(id, body)
      const idx = configs.value.findIndex((c) => c.id === saved.id)
      if (idx >= 0) configs.value[idx] = saved
      else configs.value.unshift(saved)
      return saved
    } finally {
      saving.value = false
    }
  }

  async function deleteConfig(id: number) {
    await gatewayApi.deleteConfig(id)
    configs.value = configs.value.filter((c) => c.id !== id)
  }

  /** FUNC-021：测试连接（结果不落库，仅返回连通状态） */
  function testConnection(id: number): Promise<GatewayTestResult> {
    return gatewayApi.testConnection(id)
  }

  /** FUNC-006：手动同步模型列表（返回更新后的配置） */
  async function syncModels(id: number): Promise<GatewayConfigDetail> {
    const updated = await gatewayApi.syncModels(id)
    const idx = configs.value.findIndex((c) => c.id === updated.id)
    if (idx >= 0) configs.value[idx] = updated
    return updated
  }

  return {
    configs,
    total,
    page,
    pageSize,
    loading,
    saving,
    fetchConfigs,
    saveConfig,
    deleteConfig,
    testConnection,
    syncModels,
  }
})
