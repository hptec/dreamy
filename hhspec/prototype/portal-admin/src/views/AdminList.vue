<script setup>
import { ref, computed, watch } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { adminUsers, roles } from '@/data/mock'
import { useAuth } from '@/composables/useAuth'
import {
  PlusIcon, MagnifyingGlassIcon, PencilSquareIcon, TrashIcon,
  ArrowPathIcon, XMarkIcon, EyeIcon, EyeSlashIcon
} from '@heroicons/vue/24/outline'

const { user: currentUser } = useAuth()

// 数据
const admins = ref([...adminUsers])
const search = ref('')
const roleFilter = ref('')
const statusFilter = ref('')

// 弹窗
const showModal = ref(false)
const showDeleteConfirm = ref(false)
const showResetPassword = ref(false)
const editingAdmin = ref(null)
const formErrors = ref({})
const submitting = ref(false)

// 表单
const form = ref({ name: '', email: '', password: '', role: '', status: 'active' })
const showPassword = ref(false)
const newPassword = ref('')
const newPasswordConfirm = ref('')

const filteredAdmins = computed(() => {
  let list = admins.value
  if (search.value) {
    const q = search.value.toLowerCase()
    list = list.filter(a => a.name.toLowerCase().includes(q) || a.email.toLowerCase().includes(q))
  }
  if (roleFilter.value) list = list.filter(a => a.role === roleFilter.value)
  if (statusFilter.value) list = list.filter(a => a.status === statusFilter.value)
  return list
})

function isSuperAdmin(admin) {
  return admin.role === '超级管理员'
}
function isSelf(admin) {
  return currentUser.value && admin.email === currentUser.value.email
}
function canDelete(admin) {
  return !isSelf(admin) && !isSuperAdmin(admin)
}
function canToggle(admin) {
  return !isSuperAdmin(admin)
}

function openAdd() {
  editingAdmin.value = null
  form.value = { name: '', email: '', password: '', role: roles[1]?.name || '', status: 'active' }
  formErrors.value = {}
  showModal.value = true
}
function openEdit(admin) {
  editingAdmin.value = admin
  form.value = { name: admin.name, email: admin.email, password: '', role: admin.role, status: admin.status }
  formErrors.value = {}
  showModal.value = true
}
function closeModal() {
  showModal.value = false
}

function validate() {
  const e = {}
  if (!form.value.name.trim()) e.name = '请输入姓名'
  if (!editingAdmin.value) {
    if (!form.value.email.trim()) e.email = '请输入邮箱'
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email.trim())) e.email = '邮箱格式不正确'
    else if (admins.value.some(a => a.email === form.value.email.trim())) e.email = '该邮箱已存在'
    if (!form.value.password) e.password = '请输入密码'
    else if (form.value.password.length < 6) e.password = '密码至少6位'
  }
  if (!form.value.role) e.role = '请选择角色'
  formErrors.value = e
  return Object.keys(e).length === 0
}

function submit() {
  if (!validate()) return
  if (editingAdmin.value) {
    const idx = admins.value.findIndex(a => a.id === editingAdmin.value.id)
    if (idx >= 0) {
      admins.value[idx] = {
        ...admins.value[idx],
        name: form.value.name.trim(),
        role: form.value.role,
        status: form.value.status
      }
    }
  } else {
    admins.value.push({
      id: 'a-' + Date.now(),
      name: form.value.name.trim(),
      email: form.value.email.trim(),
      role: form.value.role,
      lastLogin: '—',
      status: form.value.status,
      createdAt: new Date().toISOString().slice(0, 10)
    })
  }
  closeModal()
}

function confirmDelete(admin) {
  editingAdmin.value = admin
  showDeleteConfirm.value = true
}
function doDelete() {
  admins.value = admins.value.filter(a => a.id !== editingAdmin.value.id)
  showDeleteConfirm.value = false
}

function toggleStatus(admin) {
  if (!canToggle(admin)) return
  admin.status = admin.status === 'active' ? 'disabled' : 'active'
}

function openResetPassword(admin) {
  editingAdmin.value = admin
  newPassword.value = ''
  newPasswordConfirm.value = ''
  showResetPassword.value = true
}
function doResetPassword() {
  if (!newPassword.value || newPassword.value.length < 6) return
  if (newPassword.value !== newPasswordConfirm.value) return
  showResetPassword.value = false
}
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
      <select v-model="roleFilter" class="field w-40 shrink-0">
        <option value="">全部角色</option>
        <option v-for="r in roles" :key="r.id" :value="r.name">{{ r.name }}</option>
      </select>
      <select v-model="statusFilter" class="field w-36 shrink-0">
        <option value="">全部状态</option>
        <option value="active">正常</option>
        <option value="disabled">已禁用</option>
      </select>
      <span class="ml-auto text-[12px] text-ink-faint">共 {{ filteredAdmins.length }} 人</span>
    </div>

    <!-- 表格 -->
    <div class="panel overflow-hidden">
      <table class="data-table">
        <thead>
          <tr>
            <th>管理员</th>
            <th>角色</th>
            <th>创建时间</th>
            <th>最近登录</th>
            <th>状态</th>
            <th class="text-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="filteredAdmins.length === 0">
            <td colspan="6" class="py-12 text-center text-ink-faint">暂无匹配的管理员</td>
          </tr>
          <tr v-for="a in filteredAdmins" :key="a.id">
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
              <span class="rounded-full bg-gold/12 px-2.5 py-0.5 text-[12px] text-gold-deep">{{ a.role }}</span>
            </td>
            <td class="text-[12px] text-ink-faint">{{ a.createdAt || '—' }}</td>
            <td class="text-[12px] text-ink-faint">{{ a.lastLogin }}</td>
            <td>
              <button
                v-if="canToggle(a)"
                class="relative inline-flex h-6 w-10 shrink-0 cursor-pointer items-center rounded-full transition-colors"
                :class="a.status === 'active' ? 'bg-ok' : 'bg-ink-faint'"
                @click="toggleStatus(a)"
              >
                <span class="inline-block h-4 w-4 rounded-full bg-white shadow-sm transition-transform" :class="a.status === 'active' ? 'translate-x-5' : 'translate-x-1'" />
              </button>
              <span v-else class="inline-flex items-center gap-0.5 text-[11px] text-ink-faint" :title="'超级管理员不可被禁用'">
                <span class="h-2 w-2 rounded-full bg-ok" /> 正常
              </span>
            </td>
            <td class="text-right">
              <div class="flex items-center justify-end gap-1">
                <button class="btn-ghost" @click="openEdit(a)"><PencilSquareIcon class="h-4 w-4" />编辑</button>
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
      <div v-if="showModal" class="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto pt-[10vh]" @click.self="closeModal">
        <div class="mx-4 w-full max-w-md rounded-2xl bg-white shadow-2xl">
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
                <input v-model="form.password" :type="showPassword ? 'text' : 'password'" class="field w-full pr-10" placeholder="至少6位" />
                <button class="absolute right-3 top-1/2 -translate-y-1/2 text-ink-faint" @click="showPassword = !showPassword">
                  <EyeSlashIcon v-if="showPassword" class="h-4 w-4" />
                  <EyeIcon v-else class="h-4 w-4" />
                </button>
              </div>
              <p v-if="formErrors.password" class="mt-1 text-[12px] text-danger">{{ formErrors.password }}</p>
            </div>
            <div>
              <label class="mb-1 block text-[13px] font-medium text-ink">角色</label>
              <select v-model="form.role" class="field w-full">
                <option value="" disabled>请选择角色</option>
                <option v-for="r in roles" :key="r.id" :value="r.name">{{ r.name }}{{ r.type === 'preset' ? '（系统预设）' : '' }}</option>
              </select>
              <p v-if="formErrors.role" class="mt-1 text-[12px] text-danger">{{ formErrors.role }}</p>
            </div>
            <div v-if="editingAdmin">
              <label class="mb-1 block text-[13px] font-medium text-ink">状态</label>
              <div class="flex items-center gap-3">
                <label class="flex items-center gap-2 text-[13px] cursor-pointer">
                  <input v-model="form.status" type="radio" value="active" class="accent-ink" /> 正常
                </label>
                <label class="flex items-center gap-2 text-[13px] cursor-pointer">
                  <input v-model="form.status" type="radio" value="disabled" class="accent-ink" /> 已禁用
                </label>
              </div>
            </div>
          </div>
          <div class="flex items-center justify-end gap-3 border-t border-line px-6 py-4">
            <button class="btn-ghost" @click="closeModal">取消</button>
            <button class="btn-primary" @click="submit">{{ editingAdmin ? '保存' : '创建' }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 删除确认 -->
    <Teleport to="body">
      <div v-if="showDeleteConfirm" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" @click.self="showDeleteConfirm = false">
        <div class="mx-4 w-full max-w-sm rounded-2xl bg-white p-6 shadow-2xl">
          <h3 class="font-display text-lg font-semibold text-ink">确认删除</h3>
          <p class="mt-2 text-[13px] text-ink-soft">确定删除管理员 <strong>{{ editingAdmin?.name }}</strong> 吗？删除后不可恢复。</p>
          <div class="mt-6 flex justify-end gap-3">
            <button class="btn-ghost" @click="showDeleteConfirm = false">取消</button>
            <button class="rounded-luxe bg-danger px-4 py-2 text-[13px] font-medium text-white hover:bg-danger/90" @click="doDelete">确认删除</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 重置密码 -->
    <Teleport to="body">
      <div v-if="showResetPassword" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" @click.self="showResetPassword = false">
        <div class="mx-4 w-full max-w-sm rounded-2xl bg-white p-6 shadow-2xl">
          <h3 class="font-display text-lg font-semibold text-ink">重置密码</h3>
          <p class="mt-1 text-[13px] text-ink-faint">为 <strong>{{ editingAdmin?.name }}</strong> 设置新密码</p>
          <div class="mt-4 space-y-3">
            <div>
              <label class="mb-1 block text-[13px] font-medium text-ink">新密码</label>
              <input v-model="newPassword" type="password" class="field w-full" placeholder="至少6位" />
            </div>
            <div>
              <label class="mb-1 block text-[13px] font-medium text-ink">确认新密码</label>
              <input v-model="newPasswordConfirm" type="password" class="field w-full" placeholder="再次输入" />
              <p v-if="newPasswordConfirm && newPassword !== newPasswordConfirm" class="mt-1 text-[12px] text-danger">两次密码不一致</p>
            </div>
          </div>
          <div class="mt-6 flex justify-end gap-3">
            <button class="btn-ghost" @click="showResetPassword = false">取消</button>
            <button
              class="btn-primary"
              :disabled="!newPassword || newPassword.length < 6 || newPassword !== newPasswordConfirm"
              @click="doResetPassword"
            >确认重置</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
