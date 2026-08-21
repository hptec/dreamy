<script setup lang="ts">
// COMP-MKT-A10 VditorEditor：Markdown 编辑器封装（2026-08-20）
// - 懒加载 vditor（不阻塞首屏）
// - 图片上传走 useUpload().uploadViaPresign(file, 'content')
// - 支持 v-model:Markdown 字符串
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useUpload } from '@/composables/useUpload'
import { useToastStore } from '@/stores/toast'
import { UploadError } from '@/composables/useUpload'

interface VditorInstance {
  getValue(): string
  setValue(md: string): void
  destroy(): void
  insertValue(md: string): void
  focus(): void
}

interface VditorCtorOptions {
  height: number
  placeholder?: string
  mode?: 'ir' | 'sv' | 'wysiwyg'
  cache?: { enable: boolean }
  toolbar?: string[]
  upload?: {
    url?: string
    max?: number
    accept?: string
    handler?: (files: File[]) => Promise<string | null>
  }
  after?: () => void
  input?: (value: string) => void
}

const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    height?: number
    uploadScope?: 'product' | 'category' | 'banner' | 'content'
  }>(),
  { height: 500, uploadScope: 'content', placeholder: '使用 Markdown 语法撰写文章正文…' },
)
const emit = defineEmits<{
  (e: 'update:modelValue', val: string): void
  (e: 'image-uploaded', url: string): void
  (e: 'error', err: Error): void
}>()

const container = ref<HTMLDivElement | null>(null)
const toast = useToastStore()
const { uploadViaPresign } = useUpload()

let editor: VditorInstance | null = null
let cssLoaded = false

/** 动态加载 vditor CSS（异步，不阻塞首屏） */
function loadCss() {
  if (cssLoaded) return
  const link = document.createElement('link')
  link.rel = 'stylesheet'
  link.href = 'https://cdn.jsdelivr.net/npm/vditor@3.11.3/dist/index.css'
  document.head.appendChild(link)
  cssLoaded = true
}

onMounted(async () => {
  loadCss()
  const Vditor = (await import('vditor')).default as unknown as new (
    el: HTMLElement,
    options: VditorCtorOptions,
  ) => VditorInstance

  if (!container.value) return

  editor = new Vditor(container.value, {
    height: props.height,
    placeholder: props.placeholder,
    mode: 'ir', // 即时渲染（类似 Typora，婚纱内容运营友好）
    cache: { enable: false }, // 关闭 localStorage 缓存,避免跨文章串数据
    toolbar: [
      'headings', 'bold', 'italic', 'strike', '|',
      'list', 'ordered-list', 'check', 'quote', '|',
      'link', 'image', 'table', 'code', '|',
      'undo', 'redo', '|',
      'preview', 'fullscreen',
    ],
    upload: {
      max: 10 * 1024 * 1024, // 10MB
      accept: 'image/jpeg,image/png,image/webp,image/gif',
      // 自定义上传逻辑：走 presign → OSS 直传
      handler: async (files: File[]) => {
        const file = files[0]
        if (!file) return null
        try {
          const url = await uploadViaPresign(file, props.uploadScope)
          emit('image-uploaded', url)
          // 返回 null 表示由我们自行插入,Vditor 不干预
          editor?.insertValue(`![image](${url})\n`)
          return null
        } catch (e) {
          const err = e instanceof Error ? e : new Error('上传失败')
          emit('error', err)
          if (e instanceof UploadError) {
            toast.error(e.message)
          } else {
            toast.error('图片上传失败，请稍后重试')
          }
          return null
        }
      },
    },
    after: () => {
      if (props.modelValue) {
        editor?.setValue(props.modelValue)
      }
      editor?.focus()
    },
    input: (value: string) => {
      emit('update:modelValue', value)
    },
  })
})

// 外部 modelValue 变化时同步（如切换文章、强制覆盖场景）
watch(
  () => props.modelValue,
  (val) => {
    if (editor && val !== editor.getValue()) {
      editor.setValue(val ?? '')
    }
  },
)

onBeforeUnmount(() => {
  editor?.destroy()
  editor = null
})
</script>

<template>
  <div ref="container" class="vditor-container"></div>
</template>

<style scoped>
.vditor-container :deep(.vditor) {
  border: 1px solid var(--color-line, #e5e0d8);
  border-radius: 6px;
}
.vditor-container :deep(.vditor-toolbar) {
  border-bottom: 1px solid var(--color-line, #e5e0d8);
  background: var(--color-canvas-warm, #faf8f5);
}
</style>
