<script setup lang="ts">
// GUARD-02 兜底：无权限访问时展示 403（前端守卫拦截，后端 40300 双保险）
import { useRouter } from 'vue-router'
import { ExclamationTriangleIcon } from '@heroicons/vue/24/outline'

const router = useRouter()
</script>

<template>
  <div class="flex min-h-[60vh] flex-col items-center justify-center text-center animate-fadeup">
    <span class="flex h-16 w-16 items-center justify-center rounded-full bg-danger/10">
      <ExclamationTriangleIcon class="h-8 w-8 text-danger" />
    </span>
    <h1 class="mt-5 font-display text-2xl font-semibold text-ink">无访问权限</h1>
    <p class="mt-2 max-w-sm text-[13px] text-ink-soft">
      你当前的角色没有访问该页面的权限。如需开通，请联系超级管理员调整角色权限。
    </p>
    <button class="btn-primary mt-6" @click="router.replace('/')">返回工作台</button>
  </div>
</template>
