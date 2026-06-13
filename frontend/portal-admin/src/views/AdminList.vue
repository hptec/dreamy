<script setup lang="ts">
// PAGE-A04 / COMP-A05：管理员 CRUD。超管行禁用删除/禁用（EDGE-014）；删自己禁用（EDGE-013）；409 邮箱重复
// 约束: FORM-A02 创建管理员校验；超管保护 UI 预判 + 后端二次校验
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import AppSelect from '@/components/ui/AppSelect.vue'
import { useAdminsStore } from '@/stores/admins'
import { useRolesStore } from '@/stores/roles'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { formatDateTime } from '@/utils/format'
import { type Admin, AdminStatus } from '@/api/types'
import {
  PlusIcon, MagnifyingGlassIcon, PencilSquareIcon, TrashIcon,
  ArrowPathIcon, XMarkIcon, EyeIcon, EyeSlashIcon,
} from '@heroicons/vue/24/outline'

const store = useAdminsStore()
const rolesStore = useRolesStore()
const auth = useAuthStore()
const toast = useToastStore()

const SUPER_ROLE = '超级管理员'
const search = ref('')

// 弹窗状态
const showModal = ref(false)
const showDeleteConfirm = ref(false)
const showResetPassword = ref(false)
const editingAdmin = ref<Admin | null>(null)
const submitting = ref(false)

// 表单
const form = ref<{ name: string; email: string; password: string; roleId: number | ''; status: number }>({ name: '', email: '', password: '', roleId: '', status: 1 })
const formErrors = ref<Record<string, string>>({})
const showPassword = ref(false)
const newPassword = ref('')
const newPasswordConfirm = ref('')

const roles = computed(() => rolesStore.roles)

// 客户端按姓名/邮箱过滤当前页（服务端分页 + 本地搜索）
const filteredAdmins = computed(() => {
  if (!search.value) return store.list
  const q = search.value.toLowerCase()
  return store.list.filter(
    (a) => a.name.toLowerCase().includes(q) || a.email.toLowerCase().includes(q),
  )
})

function roleNameOf(a: Admin): string {
  return a.roleName || roles.value.find((r) => r.id === a.roleId)?.name || '—'
}
function isSuperAdmin(a: Admin): boolean {
  return roleNameOf(a) === SUPER_ROLE
}
function isSelf(a: Admin): boolean {
  return !!auth.admin && a.id === auth.admin.id
}
function canDelete(a: Admin): boolean {
  return !isSelf(a) && !isSuperAdmin(a)
}
function canToggle(a: Admin): boolean {
  return !isSuperAdmin(a)
}

async function load() {
  try {
    await Promise.all([store.fetchList(), rolesStore.fetchRoles()])
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '加载管理员列表失败')
  }
}

function openAdd() {
  editingAdmin.value = null
  form.value = { name: '', email: '', password: '', roleId: '', status: 1 }
  formErrors.value = {}
  showModal.value = true
}
function openEdit(a: Admin) {
  editingAdmin.value = a
  form.value = { name: a.name, email: a.email, password: '', roleId: a.roleId || '', status: a.status }
  formErrors.value = {}
  showModal.value = true
}
function closeModal() {
  showModal.value = false
}

function validate(): boolean {
  const e: Record<string, string> = {}
  if (!form.value.name.trim()) e.name = '请输入姓名'
  if (!editingAdmin.value) {
    if (!form.value.email.trim()) e.email = '请输入邮箱'
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email.trim())) e.email = '邮箱格式不正确'
    if (!form.value.password) e.password = '请输入密码'
    else if (form.value.password.length < 6) e.password = '密码至少 6 位'
  }
  if (!form.value.roleId) e.roleId = '请选择角色'
  formErrors.value = e
  return Object.keys(e).length === 0
}

async function submit() {
  if (!validate()) return
  submitting.value = true
  try {
    if (editingAdmin.value) {
      await store.update(editingAdmin.value.id, { name: form.value.name.trim(), roleId: Number(form.value.roleId) })
      toast.success('管理员已更新')
    } else {
      await store.create({
        name: form.value.name.trim(),
        email: form.value.email.trim(),
        password: form.value.password,
        roleId: Number(form.value.roleId),
      })
      toast.success('管理员已创建')
    }
    closeModal()
  } catch (err) {
    if (err instanceof BizError) {
      // 409 40901 EMAIL_EXISTS → 字段级回显
      if (err.code === 40901) formErrors.value = { ...formErrors.value, email: err.message }
      else toast.error(err.message)
    } else {
      toast.error('操作失败')
    }
  } finally {
    submitting.value = false
  }
}

function confirmDelete(a: Admin) {
  editingAdmin.value = a
  showDeleteConfirm.value = true
}
async function doDelete() {
  if (!editingAdmin.value) return
  submitting.value = true
  try {
    await store.remove(editingAdmin.value.id)
    toast.success('管理员已删除')
    showDeleteConfirm.value = false
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '删除失败')
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(a: Admin) {
  if (!canToggle(a)) return
  try {
    await store.toggleStatus(a.id, a.status === AdminStatus.ACTIVE ? AdminStatus.DISABLED : AdminStatus.ACTIVE)
    toast.success(a.status === AdminStatus.ACTIVE ? '已禁用' : '已启用')
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '操作失败')
  }
}

function openResetPassword(a: Admin) {
  editingAdmin.value = a
  newPassword.value = ''
  newPasswordConfirm.value = ''
  showResetPassword.value = true
}
async function doResetPassword() {
  if (!editingAdmin.value) return
  if (!newPassword.value || newPassword.value.length < 6) return
  if (newPassword.value !== newPasswordConfirm.value) return
  submitting.value = true
  try {
    await store.resetPassword(editingAdmin.value.id, newPassword.value)
    toast.success('密码已重置')
    showResetPassword.value = false
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '重置失败')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="System" title="管理员管理" subtitle="管理系统后台登录账号，分配角色权限">
      <template #actions>
        <button class="btn-primary" @click="openAdd"><PlusIcon class="h-4 w-4" />新增管理员</button>
      </template>
    </PageHeader>

    <!-- 筛选栏 -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <div class="relative flex-1" style="max-width:280px">
        <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
        <input v-model="search" class="field w-full pl-9" placeholder="搜索姓名 / 邮箱…" />
      </div>
      <select v-model="store.filterRoleId" class="field w-40 shrink-0" @change="store.applyFilters()">
        <option value="">全部角色</option>
        <option v-for="r in roles" :key="r.id" :value="r.id">{{ r.name }}</option>
      </select>
      <select v-model="store.filterStatus" class="field w-36 shrink-0" @change="store.applyFilters()">
        <option value="all">全部状态</option>
        <option :value="AdminStatus.ACTIVE">正常</option>
        <option :value="AdminStatus.DISABLED">已禁用</option>
      </select>
      <span class="ml-auto text-[12px] text-ink-faint">共 {{ store.total }} 人</span>
    </div>

    <!-- 表格 -->
    <div class="panel overflow-hidden">
      <table class="data-table">
        <thead>
          <tr>
            <th>管理员</th>
            <th>角色</th>
            <th>最近登录</th>
            <th>状态</th>
            <th class="text-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="store.loading">
            <td colspan="5" class="py-12 text-center text-ink-faint">加载中…</td>
          </tr>
          <tr v-else-if="filteredAdmins.length === 0">
            <td colspan="5" class="py-12 text-center text-ink-faint">暂无匹配的管理员</td>
          </tr>
          <tr v-for="a in filteredAdmins" v-else :key="a.id">
            <td>
              <div class="flex items-center gap-3">
                <span class="flex h-8 w-8 items-center justify-center rounded-full bg-ink text-[12px] font-medium text-canvas">{{ a.name.charAt(0).toUpperCase() }}</span>
                <div>
                  <p class="font-medium text-ink">{{ a.name }}</p>
                  <p class="text-[11px] text-ink-faint">{{ a.email }}</p>
                </div>
              </div>
            </td>
            <td>
              <span class="rounded-full bg-gold/12 px-2.5 py-0.5 text-[12px] text-gold-deep">{{ roleNameOf(a) }}</span>
            </td>
            <td class="text-[12px] text-ink-faint">{{ formatDateTime(a.lastLoginAt) }}</td>
            <td>
              <button
                v-if="canToggle(a)"
                class="relative inline-flex h-6 w-10 shrink-0 cursor-pointer items-center rounded-full transition-colors"
                :class="a.status === AdminStatus.ACTIVE ? 'bg-ok' : 'bg-ink-faint'"
                :title="a.status === AdminStatus.ACTIVE ? '点击禁用' : '点击启用'"
                @click="toggleStatus(a)"
              >
                <span class="inline-block h-4 w-4 rounded-full bg-white shadow-sm transition-transform" :class="a.status === AdminStatus.ACTIVE ? 'translate-x-5' : 'translate-x-1'" />
              </button>
              <span v-else class="inline-flex items-center gap-0.5 text-[11px] text-ink-faint" title="超级管理员不可被禁用">
                <span class="h-2 w-2 rounded-full bg-ok" /> 正常
              </span>
            </td>
            <td class="text-right">
              <div class="flex items-center justify-end gap-1">
                <button
                  class="btn-ghost"
                  :disabled="isSuperAdmin(a) && !isSelf(a)"
                  :class="isSuperAdmin(a) && !isSelf(a) ? 'cursor-not-allowed opacity-40' : ''"
                  :title="isSuperAdmin(a) && !isSelf(a) ? '超级管理员不可降权' : ''"
                  @click="openEdit(a)"
                ><PencilSquareIcon class="h-4 w-4" />编辑</button>
                <button class="btn-ghost" @click="openResetPassword(a)"><ArrowPathIcon class="h-4 w-4" />重置密码</button>
                <button
                  v-if="canDelete(a)"
                  class="btn-ghost text-danger hover:bg-danger/8"
                  @click="confirmDelete(a)"
                ><TrashIcon class="h-4 w-4" />删除</button>
                <span
                  v-else
                  class="inline-block cursor-not-allowed px-2 py-1 text-[12px] text-ink-faint"
                  :title="isSelf(a) ? '不可删除当前登录账号' : '超级管理员不可删除'"
                >—</span>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 新增/编辑弹窗 -->
    <Teleport to="body">
      <div v-if="showModal" class="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto pt-[10vh]" v-dismiss="closeModal">
        <div class="mx-4 w-full max-w-md rounded-2xl bg-white shadow-2xl" role="dialog" aria-modal="true">
          <div class="flex items-center justify-between border-b border-line px-6 py-4">
            <h3 class="font-display text-lg font-semibold text-ink">{{ editingAdmin ? '编辑管理员' : '新增管理员' }}</h3>
            <button class="rounded-lg p-1 text-ink-faint hover:bg-canvas-warm" @click="closeModal"><XMarkIcon class="h-5 w-5" /></button>
          </div>
          <div class="space-y-4 px-6 py-5">
            <div>
              <label class="mb-1 block text-[13px] font-medium text-ink">姓名</label>
              <input v-model="form.name" class="field w-full" placeholder="管理员姓名" />
              <p v-if="formErrors.name" class="mt-1 text-[12px] text-danger">{{ formErrors.name }}</p>
            </div>
            <div>
              <label class="mb-1 block text-[13px] font-medium text-ink">邮箱</label>
              <input
                v-model="form.email"
                class="field w-full"
                :class="editingAdmin ? 'cursor-not-allowed bg-canvas-warm text-ink-faint' : ''"
                :disabled="!!editingAdmin"
                placeholder="admin@dreamy.com"
              />
              <p v-if="formErrors.email" class="mt-1 text-[12px] text-danger">{{ formErrors.email }}</p>
              <p v-if="editingAdmin" class="mt-1 text-[11px] text-ink-faint">邮箱不可修改</p>
            </div>
            <div v-if="!editingAdmin">
              <label class="mb-1 block text-[13px] font-medium text-ink">密码</label>
              <div class="relative">
                <input v-model="form.password" :type="showPassword ? 'text' : 'password'" class="field w-full pr-10" placeholder="至少 6 位" />
                <button type="button" class="absolute right-3 top-1/2 -translate-y-1/2 text-ink-faint" @click="showPassword = !showPassword">
                  <EyeSlashIcon v-if="showPassword" class="h-4 w-4" />
                  <EyeIcon v-else class="h-4 w-4" />
                </button>
              </div>
              <p v-if="formErrors.password" class="mt-1 text-[12px] text-danger">{{ formErrors.password }}</p>
            </div>
            <div>
              <label class="mb-1 block text-[13px] font-medium text-ink">角色</label>
              <AppSelect
                :model-value="form.roleId"
                :options="roles.map(r => ({ value: r.id, label: r.name + (r.type === 1 ? '（系统预设）' : '') }))"
                placeholder="请选择角色"
                @update:model-value="form.roleId = $event as typeof form.roleId"
              />
              <p v-if="formErrors.roleId" class="mt-1 text-[12px] text-danger">{{ formErrors.roleId }}</p>
            </div>
          </div>
          <div class="flex items-center justify-end gap-3 border-t border-line px-6 py-4">
            <button class="btn-ghost" :disabled="submitting" @click="closeModal">取消</button>
            <button class="btn-primary" :disabled="submitting" @click="submit">{{ submitting ? '提交中…' : (editingAdmin ? '保存' : '创建') }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 删除确认 -->
    <Teleport to="body">
      <div v-if="showDeleteConfirm" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" v-dismiss="() => (showDeleteConfirm = false)">
        <div class="mx-4 w-full max-w-sm rounded-2xl bg-white p-6 shadow-2xl" role="dialog" aria-modal="true">
          <h3 class="font-display text-lg font-semibold text-ink">确认删除</h3>
          <p class="mt-2 text-[13px] text-ink-soft">确定删除管理员 <strong>{{ editingAdmin?.name }}</strong> 吗？删除后不可恢复。</p>
          <div class="mt-6 flex justify-end gap-3">
            <button class="btn-ghost" :disabled="submitting" @click="showDeleteConfirm = false">取消</button>
            <button class="rounded-luxe bg-danger px-4 py-2 text-[13px] font-medium text-white hover:bg-danger/90 disabled:opacity-50" :disabled="submitting" @click="doDelete">{{ submitting ? '删除中…' : '确认删除' }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 重置密码 -->
    <Teleport to="body">
      <div v-if="showResetPassword" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" v-dismiss="() => (showResetPassword = false)">
        <div class="mx-4 w-full max-w-sm rounded-2xl bg-white p-6 shadow-2xl" role="dialog" aria-modal="true">
          <h3 class="font-display text-lg font-semibold text-ink">重置密码</h3>
          <p class="mt-1 text-[13px] text-ink-faint">为 <strong>{{ editingAdmin?.name }}</strong> 设置新密码</p>
          <div class="mt-4 space-y-3">
            <div>
              <label class="mb-1 block text-[13px] font-medium text-ink">新密码</label>
              <input v-model="newPassword" type="password" class="field w-full" placeholder="至少 6 位" />
            </div>
            <div>
              <label class="mb-1 block text-[13px] font-medium text-ink">确认新密码</label>
              <input v-model="newPasswordConfirm" type="password" class="field w-full" placeholder="再次输入" />
              <p v-if="newPasswordConfirm && newPassword !== newPasswordConfirm" class="mt-1 text-[12px] text-danger">两次密码不一致</p>
            </div>
          </div>
          <div class="mt-6 flex justify-end gap-3">
            <button class="btn-ghost" :disabled="submitting" @click="showResetPassword = false">取消</button>
            <button
              class="btn-primary"
              :disabled="submitting || !newPassword || newPassword.length < 6 || newPassword !== newPasswordConfirm"
              @click="doResetPassword"
            >{{ submitting ? '重置中…' : '确认重置' }}</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
