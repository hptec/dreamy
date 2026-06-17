<script setup lang="ts">
// AiTranslateModal：AI 翻译弹窗（瘦版，仅源文本 + 模型 + 自定义要求 + 译文）
// 注意：不使用 Headless UI Dialog（规避根组件无 as 崩溃陷阱），沿用 DrawerShell 同型 Teleport overlay
import { computed, ref, watch } from 'vue'
import { XMarkIcon, SparklesIcon } from '@heroicons/vue/24/outline'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import { useAiTranslateStore } from '@/stores/aiTranslate'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { AiTranslateErrorCode } from '@/api/types'

const props = withDefaults(
  defineProps<{
    open: boolean
    /** EN 主字段内容（源文本，只读） */
    sourceText: string
    /** 目标语言 es/fr（源语言固定 en） */
    targetLang: 'es' | 'fr'
    /** 业务来源类型（product/category/tag/banner…，溯源用） */
    bizType: string
    /** 业务来源标识（如 product_id） */
    bizRef?: string | null
    /** 字段名（name/description…，用于弹窗标题展示） */
    fieldLabel?: string
  }>(),
  { bizRef: null, fieldLabel: '' },
)

const emit = defineEmits<{
  (e: 'confirm', translatedText: string): void
  (e: 'close'): void
}>()

const store = useAiTranslateStore()
const toast = useToastStore()

const model = ref('')
const customRequirement = ref('')
const result = ref('')

const langLabel = computed(() => (props.targetLang === 'es' ? '西班牙语 (ES)' : '法语 (FR)'))

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    // 打开前重置 + 加载可用模型（决策 4 默认全局模型）
    result.value = ''
    customRequirement.value = ''
    try {
      await store.fetchAvailableModels()
    } catch {
      // 模型列表拉取失败不阻塞弹窗，翻译时后端仍可用 default_model 兜底
    }
    model.value = store.defaultModel
  },
)

async function doTranslate() {
  // EDGE-002：源文本为空不应触发（外层按钮已 disable，双重保险）
  if (!props.sourceText.trim()) {
    toast.error('请先填写 EN 主字段内容')
    return
  }
  try {
    const res = await store.translate({
      sourceLang: 'en',
      targetLang: props.targetLang,
      sourceText: props.sourceText,
      customRequirement: customRequirement.value.trim() || null,
      model: model.value || null,
      bizType: props.bizType,
      bizRef: props.bizRef,
    })
    result.value = res.translatedText
    toast.success(`翻译完成（${res.model}，${res.latencyMs}ms）`)
  } catch (e) {
    // 决策 10 / FUNC-013：失败 toast 提示但不阻塞，弹窗保持打开允许重试或手动填
    if (e instanceof BizError) {
      if (e.code === AiTranslateErrorCode.NO_ENABLED_GATEWAY) {
        toast.error('尚未配置启用的 AI 网关，请先在「系统管理 › 外部网关」中配置')
      } else {
        toast.error(e.message)
      }
    } else {
      toast.error('翻译失败，请稍后重试')
    }
  }
}

function confirm() {
  if (!result.value.trim()) {
    toast.error('暂无可回填的译文')
    return
  }
  emit('confirm', result.value.trim())
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-[60] flex items-center justify-center bg-ink/40 p-4"
      @click.self="emit('close')"
    >
      <div class="flex max-h-[88vh] w-full max-w-lg flex-col rounded-luxe border border-line bg-canvas shadow-2xl">
        <!-- 头部 -->
        <div class="flex items-center justify-between border-b border-line px-6 py-4">
          <div>
            <p class="flex items-center gap-1.5 text-[11px] text-gold-deep">
              <SparklesIcon class="h-3.5 w-3.5" />AI 翻译
            </p>
            <h3 class="font-display text-lg font-semibold text-ink">
              EN → {{ langLabel }}<span v-if="fieldLabel" class="text-[13px] font-normal text-ink-faint"> · {{ fieldLabel }}</span>
            </h3>
          </div>
          <button class="rounded-lg p-1 text-ink-faint hover:bg-canvas-warm" @click="emit('close')">
            <XMarkIcon class="h-5 w-5" />
          </button>
        </div>

        <!-- 主体 -->
        <div class="flex-1 space-y-4 overflow-y-auto px-6 py-5">
          <div>
            <label class="field-label">源文本（EN，只读）</label>
            <div class="max-h-32 overflow-y-auto rounded-luxe border border-line bg-canvas-warm/60 px-3 py-2 text-[13px] text-ink-soft">
              {{ sourceText || '（空）' }}
            </div>
          </div>

          <div>
            <label class="field-label">模型</label>
            <SelectMenu
              v-model="model"
              :options="store.availableModels.length
                ? store.availableModels.map((m) => ({ value: m.id, label: m.name }))
                : [{ value: '', label: '（使用网关默认模型）' }]"
            />
            <p class="mt-1 text-[11px] text-ink-faint">默认使用全局模型，可切换为特定模型。</p>
          </div>

          <div>
            <label class="field-label">自定义要求（可选，≤500）</label>
            <textarea
              v-model="customRequirement"
              rows="2"
              maxlength="500"
              class="field resize-none"
              placeholder="如：语气更正式、突出奢华感、简洁些…"
            ></textarea>
            <p class="mt-1 text-right text-[11px] text-ink-faint">{{ customRequirement.length }} / 500</p>
          </div>

          <div>
            <div class="mb-1.5 flex items-center justify-between">
              <label class="field-label mb-0">翻译结果（可二次编辑）</label>
            </div>
            <textarea
              v-model="result"
              rows="4"
              class="field resize-none"
              placeholder="点击下方「翻译」生成译文，可手动修改后回填…"
            ></textarea>
          </div>
        </div>

        <!-- 底部 -->
        <div class="flex items-center justify-between gap-2 border-t border-line px-6 py-4">
          <button class="btn-outline" @click="emit('close')">取消</button>
          <div class="flex items-center gap-2">
            <button class="btn-ghost" :disabled="store.translating || !sourceText.trim()" @click="doTranslate">
              <SparklesIcon class="h-4 w-4" />{{ store.translating ? '翻译中…' : '翻译' }}
            </button>
            <button class="btn-gold" :disabled="!result.trim()" @click="confirm">确认回填</button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
