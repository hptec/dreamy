// STORE-003 useAiTranslateStore：可用模型来源（启用 AI 网关的 model_list）+ 翻译请求（FUNC-008~013）
// 决策 4：两级模型选择——默认全局模型，弹窗可下拉切换
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { gatewayApi, aiTranslateApi } from '@/api'
import { GatewayType } from '@/api/types'
import type { GatewayModel, TranslateRequest, TranslateResponse } from '@/api/types'

export const useAiTranslateStore = defineStore('aiTranslate', () => {
  const availableModels = ref<GatewayModel[]>([])
  const defaultModel = ref<string>('')
  /** 是否已配置启用的 AI 网关（无则弹窗提示去配置，EDGE-001） */
  const hasEnabledGateway = ref(false)
  const modelsLoaded = ref(false)
  const translating = ref(false)

  /** 读取启用 AI 网关的 model_list 与 default_model（取最新启用的一条，与后端代理选网关口径一致） */
  async function fetchAvailableModels(force = false) {
    if (modelsLoaded.value && !force) return
    const res = await gatewayApi.listConfigs({ gatewayType: GatewayType.AI, page: 1, pageSize: 100 })
    const enabled = res.data.filter((c) => c.enabled)
    const primary = enabled[0]
    // 后端暂时返回字符串数组，前端容错包装成 {id, name} 对象数组
    const rawModels = primary?.modelList ?? []
    availableModels.value = rawModels.map((m) => (typeof m === 'string' ? { id: m, name: m } : m))
    defaultModel.value = primary?.defaultModel || availableModels.value[0]?.id || ''
    hasEnabledGateway.value = !!primary
    modelsLoaded.value = true
  }

  /** FUNC-008~010（决策 10）：翻译请求；失败抛 BizError 由调用方 toast，不阻塞 */
  async function translate(req: TranslateRequest): Promise<TranslateResponse> {
    translating.value = true
    try {
      return await aiTranslateApi.translate(req)
    } finally {
      translating.value = false
    }
  }

  return {
    availableModels,
    defaultModel,
    hasEnabledGateway,
    modelsLoaded,
    translating,
    fetchAvailableModels,
    translate,
  }
})
