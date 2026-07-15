<script setup lang="ts">
// COMP-CAT-A06 MediaUploadCard：MIME 预检 → presign → PUT 直传（进度条）→ 预览 public_url；
// 502501 → 卡片错误态「稍后重试」，不阻塞表单其余字段保存（决策 9 降级）
import { ref } from 'vue'
import { ArrowUpTrayIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { useUpload, UploadError } from '@/composables/useUpload'
import type { PresignScope } from '@/api/types'

const props = withDefaults(
  defineProps<{
    modelValue?: string | null
    fallbackValue?: string | null
    fallbackLabel?: string
    scope?: PresignScope
    label?: string
    aspect?: string
    allowVideo?: boolean
  }>(),
  { modelValue: '', fallbackValue: '', fallbackLabel: '继承默认图片', scope: 'product', label: '点击上传', aspect: 'aspect-[3/4]', allowVideo: false },
)

const emit = defineEmits<{ (e: 'update:modelValue', url: string): void }>()

const { uploading, progress, error, uploadViaPresign } = useUpload()
const inputRef = ref<HTMLInputElement | null>(null)

async function onFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (inputRef.value) inputRef.value.value = ''
  if (!file) return
  try {
    const url = await uploadViaPresign(file, props.scope, { allowVideo: props.allowVideo })
    emit('update:modelValue', url)
  } catch (err) {
    if (!(err instanceof UploadError)) error.value = '上传失败，请稍后重试'
  }
}

function clearImage() {
  emit('update:modelValue', '')
}
</script>

<template>
  <div>
    <label
      class="group relative flex w-full cursor-pointer flex-col items-center justify-center overflow-hidden rounded-luxe border-2 border-dashed text-ink-faint transition-colors hover:border-gold"
      :class="[aspect, error ? 'border-danger/60' : 'border-line']"
    >
      <img v-if="modelValue || fallbackValue" :src="modelValue || fallbackValue || ''" class="absolute inset-0 h-full w-full object-cover" />
      <template v-else>
        <ArrowUpTrayIcon class="h-6 w-6" />
        <span class="mt-1 px-2 text-center text-[11px]">{{ label }}</span>
      </template>
      <span
        v-if="(modelValue || fallbackValue) && !uploading"
        class="absolute inset-0 flex items-center justify-center bg-ink/40 text-[11px] text-white opacity-0 transition-opacity group-hover:opacity-100"
      >{{ modelValue ? '更换' : '上传独立图片' }}</span>
      <span v-if="!modelValue && fallbackValue" class="absolute left-2 top-2 rounded bg-ink/70 px-2 py-1 text-[10px] text-white">
        {{ fallbackLabel }}
      </span>
      <!-- 进度条 -->
      <div v-if="uploading" class="absolute inset-x-0 bottom-0 bg-ink/60 px-2 py-1">
        <div class="h-1 overflow-hidden rounded-full bg-white/30">
          <div class="h-full rounded-full bg-gold transition-all" :style="{ width: progress + '%' }"></div>
        </div>
        <p class="mt-0.5 text-center text-[10px] text-white">{{ progress }}%</p>
      </div>
      <input ref="inputRef" type="file" :accept="allowVideo ? 'image/*,video/mp4' : 'image/*'" class="hidden" @change="onFile" />
    </label>
    <div class="mt-1 flex items-center justify-between">
      <p v-if="error" class="text-[11px] text-danger">{{ error }}</p>
      <button v-if="modelValue" type="button" class="ml-auto inline-flex items-center gap-0.5 text-[11px] text-ink-faint hover:text-danger" @click="clearImage">
        <XMarkIcon class="h-3 w-3" />移除
      </button>
    </div>
  </div>
</template>
