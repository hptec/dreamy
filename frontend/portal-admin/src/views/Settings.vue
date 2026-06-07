<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { adminUsers, roleMatrix, auditLogs } from '@/data/mock'
import { PlusIcon, PencilSquareIcon, CheckIcon, XMarkIcon, EyeIcon } from '@heroicons/vue/24/outline'

const tab = ref('admins')
const permIcon = { full: { c: 'text-ok', i: 'full' }, read: { c: 'text-info', i: 'read' }, none: { c: 'text-ink-faint', i: 'none' } }
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="System" title="系统设置" subtitle="管理员账号、角色权限矩阵与操作日志">
      <template #actions><button class="btn-primary"><PlusIcon class="h-4 w-4" />新增管理员</button></template>
    </PageHeader>
    <div class="mb-4 flex gap-1 border-b border-line">
      <button v-for="t in [['admins','管理员账号'],['roles','角色权限矩阵'],['logs','操作日志']]" :key="t[0]" @click="tab = t[0]" class="border-b-2 px-4 py-2.5 text-[13px] transition-colors" :class="tab === t[0] ? 'border-gold font-medium text-ink' : 'border-transparent text-ink-faint hover:text-ink'">{{ t[1] }}</button>
    </div>

    <!-- 管理员 -->
    <div v-show="tab === 'admins'" class="panel overflow-hidden">
      <table class="data-table">
        <thead><tr><th>管理员</th><th>角色</th><th>最近登录</th><th>状态</th><th class="text-right">操作</th></tr></thead>
        <tbody>
          <tr v-for="u in adminUsers" :key="u.id">
            <td><div><p class="font-medium text-ink">{{ u.name }}</p><p class="text-[11px] text-ink-faint">{{ u.email }}</p></div></td>
            <td><span class="rounded-full bg-gold/12 px-2.5 py-0.5 text-[12px] text-gold-deep">{{ u.role }}</span></td>
            <td class="text-[12px] text-ink-faint">{{ u.lastLogin }}</td>
            <td><StatusBadge :tone="u.status === 1 ? 'ok' : 'danger'" :label="u.status === 1 ? '正常' : '已禁用'" /></td>
            <td class="text-right"><button class="btn-ghost"><PencilSquareIcon class="h-4 w-4" />编辑</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 角色权限矩阵 -->
    <div v-show="tab === 'roles'" class="panel overflow-x-auto">
      <table class="data-table">
        <thead><tr><th>角色 \ 模块</th><th v-for="m in roleMatrix.modules" :key="m" class="text-center">{{ m }}</th></tr></thead>
        <tbody>
          <tr v-for="r in roleMatrix.roles" :key="r.role">
            <td class="font-medium text-ink">{{ r.role }}</td>
            <td v-for="(p, i) in r.perms" :key="i" class="text-center">
              <span v-if="p === 'full'" class="inline-flex items-center gap-0.5 text-[11px] text-ok"><CheckIcon class="h-4 w-4" /></span>
              <span v-else-if="p === 'read'" class="inline-flex items-center gap-0.5 text-[11px] text-info"><EyeIcon class="h-3.5 w-3.5" />只读</span>
              <span v-else class="text-ink-faint"><XMarkIcon class="mx-auto h-3.5 w-3.5" /></span>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="px-4 py-3 text-[12px] text-ink-faint">✓ 完全权限 · 只读 仅查看 · ✕ 无权限。点击单元格可调整（原型演示）。</p>
    </div>

    <!-- 操作日志 -->
    <div v-show="tab === 'logs'" class="panel overflow-hidden">
      <table class="data-table">
        <thead><tr><th>时间</th><th>操作人</th><th>操作</th><th>对象</th><th>IP</th></tr></thead>
        <tbody>
          <tr v-for="(l, i) in auditLogs" :key="i">
            <td class="font-mono text-[12px] text-ink-faint">{{ l.time }}</td>
            <td class="text-ink">{{ l.user }}</td>
            <td><span class="rounded bg-canvas-warm px-2 py-0.5 text-[12px] text-ink-soft">{{ l.action }}</span></td>
            <td class="text-ink-soft">{{ l.target }}</td>
            <td class="font-mono text-[12px] text-ink-faint">{{ l.ip }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
