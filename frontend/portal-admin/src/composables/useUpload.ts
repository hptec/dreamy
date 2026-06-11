// STORE-CAT-A05：presign 两步上传 composable（FLOW-P17：presign → PUT 直传，不经后端）
// 502501（对象存储不可用）→ 降级提示，不阻塞表单其余字段保存（决策 9）
import { ref } from 'vue'
import axios from 'axios'
import { catalogApi } from '@/api'
import { BizError } from '@/api/client'
import type { PresignScope } from '@/api/types'

/** MIME 前端白名单预检（COMP-CAT-A06） */
const IMAGE_MIME = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
const VIDEO_MIME = ['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'video/mp4', 'video/quicktime']

export class UploadError extends Error {
  degraded: boolean
  constructor(message: string, degraded = false) {
    super(message)
    this.name = 'UploadError'
    this.degraded = degraded
  }
}

export function useUpload() {
  const uploading = ref(false)
  const progress = ref(0)
  const error = ref('')

  /** presign → PUT 直传 upload_url → 返回 public_url */
  async function uploadViaPresign(
    file: File,
    scope: PresignScope = 'product',
    opts: { allowVideo?: boolean } = {},
  ): Promise<string> {
    const allow = opts.allowVideo ? VIDEO_MIME : IMAGE_MIME
    if (!allow.includes(file.type)) {
      throw new UploadError('不支持的文件类型（仅支持 JPG/PNG/WebP/GIF' + (opts.allowVideo ? '/MP4' : '') + '）')
    }
    uploading.value = true
    progress.value = 0
    error.value = ''
    try {
      const presign = await catalogApi.presignUpload({
        fileName: file.name,
        contentType: file.type,
        scope,
      })
      // PUT 直传不经后端，不带 Authorization（FLOW-P17）
      await axios.put(presign.uploadUrl, file, {
        headers: { 'Content-Type': file.type },
        onUploadProgress: (evt) => {
          if (evt.total) progress.value = Math.round((evt.loaded / evt.total) * 100)
        },
      })
      progress.value = 100
      return presign.publicUrl
    } catch (e) {
      if (e instanceof BizError && e.code === 502501) {
        const err = new UploadError('对象存储暂不可用，可先保存其他字段', true)
        error.value = err.message
        throw err
      }
      const err = e instanceof UploadError ? e : new UploadError('上传失败，请稍后重试')
      error.value = err.message
      throw err
    } finally {
      uploading.value = false
    }
  }

  return { uploading, progress, error, uploadViaPresign }
}
