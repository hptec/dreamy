<script setup lang="ts">
// COMP-002 GatewayConfigForm（FORM-001 载体）：网关配置创建/编辑抽屉
// FUNC-005/006/007/021：字段表单 + API Key 掩码处理 + 测试连接 + 模型刷新
import { computed, ref, watch } from 'vue'
import DrawerShell from '@/components/DrawerShell.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import Toggle from '@/components/Toggle.vue'
import { useGatewayStore } from '@/stores/gateway'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { extractFieldErrors, validateGatewayForm, type FieldErrors } from '@/utils/validators'
import { GatewayType, GatewayProtocol, ModelRefreshStrategy, GatewayErrorCode } from '@/api/types'
import type { GatewayConfigDetail, GatewayTestResult } from '@/api/types'
import { SparklesIcon, ArrowPathIcon, SignalIcon } from '@heroicons/vue/24/outline'

const props = defineProps<{ open: boolean; editing: GatewayConfigDetail | null }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved'): void }>()

const store = useGatewayStore()
const toast = useToastStore()

type FormState = {
  gatewayType: GatewayType
  name: string
  protocol: GatewayProtocol
  baseUrl: string
  apiKey: string
  defaultModel: string
  modelRefreshStrategy: ModelRefreshStrategy
  modelRefreshIntervalMin: number | null
  enabled: boolean
}

function emptyForm(): FormState {
  return {
    gatewayType: GatewayType.AI,
    name: '',
    protocol: GatewayProtocol.OPENAI,
    baseUrl: '',
    apiKey: '',
    defaultModel: '',
    modelRefreshStrategy: ModelRefreshStrategy.MANUAL,
    modelRefreshIntervalMin: 60,
    enabled: true,
  }
}

const form = ref<FormState>(emptyForm())
const errors = ref<FieldErrors>({})
const saving = ref(false)
const testing = ref(false)
const syncing = ref(false)
const testResult = ref<GatewayTestResult | null>(null)
// API Key 掩码态：编辑时初始展示掩码，聚焦清空（FORM-001 交互 2）
const apiKeyDirty = ref(false)

const isAi = computed(() => form.value.gatewayType === GatewayType.AI)
const isEdit = computed(() => props.editing != null)
const savedConfig = ref<GatewayConfigDetail | null>(null)
const modelList = computed(() => {
  const raw = savedConfig.value?.modelList ?? props.editing?.modelList ?? []
  // 后端暂时返回字符串数组，前端容错包装成 {id, name} 对象数组
  return raw.map((m) => (typeof m === 'string' ? { id: m, name: m } : m))
})

watch(
  () => props.open,
  (open) => {
    if (!open) return
    errors.value = {}
    testResult.value = null
    apiKeyDirty.value = false
    savedConfig.value = props.editing ? { ...props.editing } : null
    if (props.editing) {
      const e = props.editing
      form.value = {
        gatewayType: e.gatewayType,
        name: e.name,
        protocol: e.protocol,
        baseUrl: e.baseUrl,
        // 编辑态：apiKey 初始填掩码占位，未聚焦修改则提交掩码（后端保持原密文）
        apiKey: e.apiKeyMasked || '',
        defaultModel: e.defaultModel || '',
        modelRefreshStrategy: e.modelRefreshStrategy ?? ModelRefreshStrategy.MANUAL,
        modelRefreshIntervalMin: e.modelRefreshIntervalMin ?? 60,
        enabled: e.enabled,
      }
    } else {
      form.value = emptyForm()
    }
  },
)

/** FORM-001 交互 2：API Key 聚焦清空掩码，便于输入明文新 Key */
function onApiKeyFocus() {
  if (isEdit.value && !apiKeyDirty.value) {
    form.value.apiKey = ''
    apiKeyDirty.value = true
  }
}
function onApiKeyBlur() {
  // 失焦未输入 → 编辑态恢复掩码（提交掩码=保持原密文）
  if (isEdit.value && !form.value.apiKey.trim()) {
    form.value.apiKey = props.editing?.apiKeyMasked || ''
    apiKeyDirty.value = false
  }
}

async function submit() {
  errors.value = validateGatewayForm(form.value)
  if (Object.keys(errors.value).length) return
  saving.value = true
  try {
    // 首次保存成功后 props.editing 仍为 null，但 savedConfig 已持有新 id；
    // 以 savedConfig.id 优先兜底，避免第二次保存被当成新增而报「名称已存在」
    const targetId = savedConfig.value?.id ?? props.editing?.id
    const saved = await store.saveConfig(
      {
        gatewayType: form.value.gatewayType,
        name: form.value.name.trim(),
        protocol: form.value.protocol,
        baseUrl: form.value.baseUrl.trim(),
        apiKey: form.value.apiKey.trim(),
        defaultModel: form.value.defaultModel.trim() || null,
        modelRefreshStrategy: form.value.modelRefreshStrategy,
        modelRefreshIntervalMin:
          form.value.modelRefreshStrategy === ModelRefreshStrategy.SCHEDULED ? form.value.modelRefreshIntervalMin : null,
        enabled: form.value.enabled,
      },
      targetId,
    )
    savedConfig.value = saved
    // EDGE-014：AI 网关保存后模型列表为空 → 提示可手动刷新
    if (saved.gatewayType === GatewayType.AI && !saved.modelList.length) {
      toast.warn('模型列表获取失败，可点击「刷新模型」手动重试')
    } else {
      toast.success('已保存')
    }
    emit('saved')
  } catch (e) {
    if (e instanceof BizError) {
      if (e.code === GatewayErrorCode.NAME_EXISTS) {
        errors.value = { name: '配置名称已存在' }
      } else if (e.code === GatewayErrorCode.FIELD_VALIDATION_FAILED) {
        errors.value = extractFieldErrors(e)
        if (!Object.keys(errors.value).length) toast.error(e.message)
      } else {
        toast.error(e.message)
      }
    } else {
      toast.error('保存失败')
    }
  } finally {
    saving.value = false
  }
}

/** FUNC-021：测试连接（需已保存，用 savedConfig.id）EDGE-023 具体错误展示 */
async function test() {
  const id = savedConfig.value?.id ?? props.editing?.id
  if (id == null) {
    toast.info('请先保存配置后再测试连接')
    return
  }
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await store.testConnection(id)
  } catch (e) {
    if (e instanceof BizError && e.code === 501) {
      toast.info('该网关类型暂不支持测试连接')
    } else {
      toast.error(e instanceof BizError ? e.message : '测试连接失败')
    }
  } finally {
    testing.value = false
  }
}

/** FUNC-006：手动刷新模型列表（仅 AI 网关，需已保存） */
async function refreshModels() {
  const id = savedConfig.value?.id ?? props.editing?.id
  if (id == null) {
    toast.info('请先保存配置后再刷新模型')
    return
  }
  syncing.value = true
  try {
    const updated = await store.syncModels(id)
    savedConfig.value = updated
    toast.success(`模型已刷新（${updated.modelList.length} 个）`)
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '模型刷新失败')
  } finally {
    syncing.value = false
  }
}
</script>

<template>
  <DrawerShell
    :open="open"
    eyebrow="Gateway"
    :title="editing ? '编辑网关配置' : '新增网关配置'"
    width="max-w-lg"
    @close="emit('close')"
  >
    <div class="space-y-4">
      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="field-label">网关类型 *</label>
          <SelectMenu
            v-model="form.gatewayType"
            :options="[
              { value: GatewayType.AI, label: 'AI 网关' },
              { value: GatewayType.LOGISTICS, label: '物流网关' },
              { value: GatewayType.PAYMENT, label: '支付网关' },
            ]"
          />
          <p v-if="errors.gatewayType" class="mt-1 text-[11px] text-danger">{{ errors.gatewayType }}</p>
        </div>
        <div>
          <label class="field-label">协议 *</label>
          <SelectMenu
            v-model="form.protocol"
            :options="[{ value: GatewayProtocol.OPENAI, label: 'OpenAI-compatible' }]"
          />
        </div>
      </div>

      <div>
        <label class="field-label">配置名称 *</label>
        <input v-model="form.name" class="field" placeholder="如 OpenRouter 主网关" maxlength="64" />
        <p v-if="errors.name" class="mt-1 text-[11px] text-danger">{{ errors.name }}</p>
      </div>

      <div>
        <label class="field-label">网关地址 (Base URL) *</label>
        <input v-model="form.baseUrl" class="field" placeholder="https://openrouter.ai/api/v1" maxlength="255" />
        <p v-if="errors.baseUrl" class="mt-1 text-[11px] text-danger">{{ errors.baseUrl }}</p>
      </div>

      <div>
        <label class="field-label">API Key *</label>
        <input
          v-model="form.apiKey"
          type="text"
          class="field font-mono text-[12px]"
          :placeholder="isEdit ? '点击修改（留空保持原 Key）' : 'sk-or-v1-…'"
          autocomplete="off"
          @focus="onApiKeyFocus"
          @blur="onApiKeyBlur"
        />
        <p v-if="errors.apiKey" class="mt-1 text-[11px] text-danger">{{ errors.apiKey }}</p>
        <p v-else-if="isEdit && !apiKeyDirty" class="mt-1 text-[11px] text-ink-faint">
          当前为掩码展示，聚焦输入框可填写新 Key；不修改则保持原密文。
        </p>
      </div>

      <!-- AI 网关专属：默认模型 + 刷新策略 + 模型列表 -->
      <template v-if="isAi">
        <div>
          <label class="field-label">全局默认模型</label>
          <SelectMenu
            v-model="form.defaultModel"
            :options="[
              { value: '', label: '未设置' },
              ...modelList.map((m) => ({ value: m.id, label: m.name })),
            ]"
          />
          <p v-if="!modelList.length" class="mt-1 text-[11px] text-ink-faint">
            保存后自动拉取模型列表；若为空可点击下方「刷新模型」。
          </p>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="field-label">模型刷新策略</label>
            <SelectMenu
              v-model="form.modelRefreshStrategy"
              :options="[
                { value: ModelRefreshStrategy.MANUAL, label: '手动刷新' },
                { value: ModelRefreshStrategy.SCHEDULED, label: '定时刷新' },
              ]"
            />
          </div>
          <div v-if="form.modelRefreshStrategy === ModelRefreshStrategy.SCHEDULED">
            <label class="field-label">刷新间隔（分钟）</label>
            <input v-model.number="form.modelRefreshIntervalMin" type="number" min="5" max="1440" class="field" />
            <p v-if="errors.modelRefreshIntervalMin" class="mt-1 text-[11px] text-danger">
              {{ errors.modelRefreshIntervalMin }}
            </p>
          </div>
        </div>

        <div v-if="isEdit || savedConfig" class="rounded-luxe border border-line bg-canvas-warm/40 p-3">
          <div class="mb-2 flex items-center justify-between">
            <p class="flex items-center gap-1.5 text-[12px] font-medium text-ink">
              <SparklesIcon class="h-3.5 w-3.5 text-gold-deep" />可用模型（{{ modelList.length }}）
            </p>
            <button class="btn-ghost text-[12px]" :disabled="syncing" @click="refreshModels">
              <ArrowPathIcon class="h-3.5 w-3.5" :class="syncing && 'animate-spin'" />{{ syncing ? '刷新中…' : '刷新模型' }}
            </button>
          </div>
          <div v-if="modelList.length" class="flex flex-wrap gap-1.5">
            <span
              v-for="m in modelList"
              :key="m.id"
              class="rounded-full border border-line bg-canvas px-2 py-0.5 text-[11px] text-ink-soft"
            >{{ m.name }}</span>
          </div>
          <p v-else class="text-[11.5px] text-ink-faint">暂无模型，点击「刷新模型」从网关拉取。</p>
        </div>
      </template>

      <div class="flex items-center justify-between rounded-luxe border border-line px-4 py-3">
        <div>
          <p class="text-[13px] font-medium text-ink">启用该配置</p>
          <p class="text-[12px] text-ink-faint">AI 网关启用后，翻译弹窗将使用其模型列表</p>
        </div>
        <Toggle v-model="form.enabled" />
      </div>

      <!-- FUNC-021 测试连接结果（EDGE-023） -->
      <div v-if="testResult" class="rounded-luxe border px-4 py-3 text-[12.5px]" :class="testResult.reachable ? 'border-ok/40 bg-ok/8' : 'border-danger/40 bg-danger/8'">
        <p class="flex items-center gap-1.5 font-medium" :class="testResult.reachable ? 'text-ok' : 'text-danger'">
          <SignalIcon class="h-4 w-4" />
          {{ testResult.reachable ? '连接成功' : '连接失败' }}
          <span v-if="testResult.latencyMs != null" class="font-normal text-ink-faint">· {{ testResult.latencyMs }}ms</span>
        </p>
        <p v-if="testResult.reachable && testResult.availableModelsCount != null" class="mt-1 text-ink-soft">
          检测到 {{ testResult.availableModelsCount }} 个可用模型
        </p>
        <p v-if="!testResult.reachable && testResult.errorMessage" class="mt-1 text-danger">
          {{ testResult.errorMessage }}
        </p>
      </div>
    </div>

    <template #footer>
      <button class="btn-outline" @click="emit('close')">关闭</button>
      <button
        class="btn-ghost"
        :disabled="testing"
        :title="!savedConfig && !editing ? '请先保存配置后再测试连接' : ''"
        @click="test"
      >
        <SignalIcon class="h-4 w-4" />{{ testing ? '测试中…' : '测试连接' }}
      </button>
      <button class="btn-gold" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
    </template>
  </DrawerShell>
</template>
