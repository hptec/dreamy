<script setup>
import { ref, computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { roles, menuPermissionKeys, adminUsers } from '@/data/mock'
import {
  PlusIcon, PencilSquareIcon, TrashIcon, XMarkIcon
} from '@heroicons/vue/24/outline'

// 可编辑的角色列表
const roleList = ref(roles.map(r => ({ ...r, permissions: [...r.permissions] })))

const selectedRoleId = ref(roles[0]?.id || null)
const showRoleModal = ref(false)
const showDeleteRoleConfirm = ref(false)
const editingRole = ref(null)
const roleForm = ref({ name: '' })
const roleFormError = ref('')
const hasUnsavedChanges = ref(false)

const selectedRole = computed(() => roleList.value.find(r => r.id === selectedRoleId.value))

// 按分组聚合权限
const permissionGroups = computed(() => {
  const map = {}
  for (const p of menuPermissionKeys) {
    if (!map[p.group]) map[p.group] = { group: p.group, items: [] }
    map[p.group].items.push(p)
  }
  return Object.values(map)
})

function isPermitted(key) {
  if (!selectedRole.value) return false
  return selectedRole.value.permissions.includes(key)
}
function togglePermission(key) {
  if (!selectedRole.value || selectedRole.value.isLocked) return
  hasUnsavedChanges.value = true
  const perms = selectedRole.value.permissions
  const idx = perms.indexOf(key)
  if (idx >= 0) perms.splice(idx, 1)
  else perms.push(key)
}
function toggleGroup(groupItems) {
  if (!selectedRole.value || selectedRole.value.isLocked) return
  hasUnsavedChanges.value = true
  const perms = selectedRole.value.permissions
  const keys = groupItems.map(i => i.key)
  const allGranted = keys.every(k => perms.includes(k))
  if (allGranted) {
    for (const k of keys) {
      const idx = perms.indexOf(k)
      if (idx >= 0) perms.splice(idx, 1)
    }
  } else {
    for (const k of keys) {
      if (!perms.includes(k)) perms.push(k)
    }
  }
}
function toggleAll() {
  if (!selectedRole.value || selectedRole.value.isLocked) return
  hasUnsavedChanges.value = true
  const allKeys = menuPermissionKeys.map(p => p.key)
  const allGranted = allKeys.every(k => selectedRole.value.permissions.includes(k))
  if (allGranted) {
    selectedRole.value.permissions = []
  } else {
    selectedRole.value.permissions = [...allKeys]
  }
}

function groupState(groupItems) {
  if (!selectedRole.value) return 'none'
  const perms = selectedRole.value.permissions
  const keys = groupItems.map(i => i.key)
  const granted = keys.filter(k => perms.includes(k)).length
  if (granted === 0) return 'none'
  if (granted === keys.length) return 'all'
  return 'partial'
}

function savePermissions() {
  hasUnsavedChanges.value = false
}

function selectRole(id) {
  if (hasUnsavedChanges.value) {
    const ok = confirm('你有未保存的权限变更，确定放弃并切换角色？')
    if (!ok) return
  }
  hasUnsavedChanges.value = false
  selectedRoleId.value = id
}

function adminCountForRole(roleName) {
  return adminUsers.filter(a => a.role === roleName).length
}

function openAddRole() {
  editingRole.value = null
  roleForm.value = { name: '' }
  roleFormError.value = ''
  showRoleModal.value = true
}
function openEditRole(role) {
  if (role.isLocked) return
  editingRole.value = role
  roleForm.value = { name: role.name }
  roleFormError.value = ''
  showRoleModal.value = true
}
function submitRole() {
  const name = roleForm.value.name.trim()
  if (!name) { roleFormError.value = '请输入角色名称'; return }
  if (!editingRole.value && roleList.value.some(r => r.name === name)) {
    roleFormError.value = '角色名称已存在'; return
  }
  if (editingRole.value) {
    editingRole.value.name = name
  } else {
    const newId = 'r-custom-' + Date.now()
    roleList.value.push({
      id: newId,
      name,
      type: 'custom',
      isLocked: false,
      adminCount: 0,
      permissions: []
    })
    selectedRoleId.value = newId
    hasUnsavedChanges.value = false
  }
  showRoleModal.value = false
}
function confirmDeleteRole(role) {
  if (role.isLocked || role.type === 'preset') return
  editingRole.value = role
  showDeleteRoleConfirm.value = true
}
function doDeleteRole() {
  const count = adminCountForRole(editingRole.value.name)
  if (count > 0) {
    alert(`该角色下有 ${count} 个管理员，请先将他们迁移到其他角色后再删除。`)
    showDeleteRoleConfirm.value = false
    return
  }
  roleList.value = roleList.value.filter(r => r.id !== editingRole.value.id)
  if (selectedRoleId.value === editingRole.value.id) {
    selectedRoleId.value = roleList.value[0]?.id || null
  }
  showDeleteRoleConfirm.value = false
}

function permCount(role) {
  return role.permissions.length
}
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="System" title="角色权限" subtitle="管理后台角色及其菜单访问权限">
      <template #actions>
        <button class="btn-primary" @click="openAddRole"><PlusIcon class="h-4 w-4" />新增角色</button>
      </template>
    </PageHeader>

    <div class="flex gap-6">
      <!-- 左侧：角色列表 -->
      <div class="w-56 shrink-0">
        <div class="panel overflow-hidden">
          <div class="border-b border-line px-4 py-3">
            <p class="text-[12px] font-medium uppercase tracking-wide text-ink-faint">角色列表</p>
          </div>
          <div class="divide-y divide-line">
            <button
              v-for="r in roleList"
              :key="r.id"
              class="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-canvas-warm"
              :class="selectedRoleId === r.id ? 'bg-gold/8 border-r-2 border-gold' : ''"
              @click="selectRole(r.id)"
            >
              <div class="flex-1">
                <p class="text-[13px] font-medium text-ink">{{ r.name }}</p>
                <p class="text-[11px] text-ink-faint">
                  {{ r.type === 'preset' ? '系统预设' : '自定义' }}
                  · {{ permCount(r) }} 项权限
                </p>
              </div>
              <span v-if="r.isLocked" class="text-[10px] text-ink-faint">🔒</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 右侧：权限矩阵 -->
      <div class="flex-1 min-w-0">
        <div v-if="selectedRole" class="panel overflow-hidden">
          <!-- 头部 -->
          <div class="flex items-center justify-between border-b border-line px-5 py-4">
            <div class="flex items-center gap-3">
              <h3 class="font-display text-base font-semibold text-ink">{{ selectedRole.name }}</h3>
              <span class="rounded-full bg-canvas-warm px-2 py-0.5 text-[11px] text-ink-faint">
                {{ selectedRole.type === 'preset' ? '系统预设' : '自定义' }}
              </span>
              <span v-if="selectedRole.isLocked" class="text-[11px] text-ink-faint">· 权限不可编辑</span>
            </div>
            <div class="flex items-center gap-2">
              <button
                class="rounded-luxe px-3 py-1.5 text-[12px] font-medium text-ink-soft hover:bg-canvas-warm"
                :disabled="selectedRole.isLocked"
                @click="toggleAll"
              >{{ menuPermissionKeys.every(k => selectedRole.permissions.includes(k.key)) ? '取消全选' : '全选全部' }}</button>
              <button
                v-if="!selectedRole.isLocked && selectedRole.type === 'preset'"
                class="btn-ghost"
                @click="openEditRole(selectedRole)"
              ><PencilSquareIcon class="h-4 w-4" />改名</button>
              <button
                v-if="!selectedRole.isLocked && selectedRole.type === 'custom'"
                class="btn-ghost"
                @click="openEditRole(selectedRole)"
              ><PencilSquareIcon class="h-4 w-4" />编辑</button>
              <button
                v-if="!selectedRole.isLocked && selectedRole.type === 'custom'"
                class="btn-ghost text-danger"
                @click="confirmDeleteRole(selectedRole)"
              ><TrashIcon class="h-4 w-4" />删除</button>
            </div>
          </div>

          <!-- 提示条 -->
          <div class="border-b border-line bg-gold/5 px-5 py-2.5 text-[12px] text-ink-soft">
            勾选的菜单项对该角色可见且可访问。分组标题前复选框可批量勾选全组。
          </div>

          <!-- 权限网格 -->
          <div class="divide-y divide-line">
            <div v-for="g in permissionGroups" :key="g.group" class="px-5 py-3">
              <label
                class="flex cursor-pointer items-center gap-2 text-[13px] font-medium"
                :class="selectedRole.isLocked ? 'cursor-not-allowed' : ''"
              >
                <input
                  type="checkbox"
                  class="h-4 w-4 rounded accent-gold"
                  :checked="groupState(g.items) === 'all'"
                  :indeterminate.prop="groupState(g.items) === 'partial'"
                  :disabled="selectedRole.isLocked"
                  @change="toggleGroup(g.items)"
                />
                {{ g.group }}
              </label>
              <div class="mt-2 ml-6 grid grid-cols-1 gap-1.5 sm:grid-cols-2 lg:grid-cols-3">
                <label
                  v-for="item in g.items"
                  :key="item.key"
                  class="flex cursor-pointer items-center gap-2 text-[12.5px] text-ink-soft transition-colors hover:text-ink"
                  :class="selectedRole.isLocked ? 'cursor-not-allowed' : ''"
                >
                  <input
                    type="checkbox"
                    class="h-3.5 w-3.5 rounded accent-gold"
                    :checked="isPermitted(item.key)"
                    :disabled="selectedRole.isLocked"
                    @change="togglePermission(item.key)"
                  />
                  {{ item.label }}
                </label>
              </div>
            </div>
          </div>

          <!-- 保存按钮 -->
          <div v-if="!selectedRole.isLocked" class="flex items-center justify-end gap-3 border-t border-line px-5 py-4">
            <span v-if="hasUnsavedChanges" class="text-[12px] text-warn">有未保存的变更</span>
            <button
              class="btn-primary"
              :class="!hasUnsavedChanges ? 'opacity-50 cursor-not-allowed' : ''"
              :disabled="!hasUnsavedChanges"
              @click="savePermissions"
            >保存权限</button>
          </div>
        </div>

        <!-- 未选择角色 -->
        <div v-else class="flex items-center justify-center rounded-2xl border-2 border-dashed border-line py-20 text-ink-faint">
          请从左侧选择一个角色以编辑其权限
        </div>
      </div>
    </div>

    <!-- 新增/编辑角色弹窗 -->
    <Teleport to="body">
      <div v-if="showRoleModal" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" @click.self="showRoleModal = false">
        <div class="mx-4 w-full max-w-sm rounded-2xl bg-white p-6 shadow-2xl">
          <h3 class="font-display text-lg font-semibold text-ink">{{ editingRole ? '编辑角色' : '新增角色' }}</h3>
          <div class="mt-4">
            <label class="mb-1 block text-[13px] font-medium text-ink">角色名称</label>
            <input v-model="roleForm.name" class="field w-full" placeholder="输入角色名称" @keyup.enter="submitRole" />
            <p v-if="roleFormError" class="mt-1 text-[12px] text-danger">{{ roleFormError }}</p>
          </div>
          <div class="mt-6 flex justify-end gap-3">
            <button class="btn-ghost" @click="showRoleModal = false">取消</button>
            <button class="btn-primary" @click="submitRole">{{ editingRole ? '保存' : '创建' }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 删除角色确认 -->
    <Teleport to="body">
      <div v-if="showDeleteRoleConfirm" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" @click.self="showDeleteRoleConfirm = false">
        <div class="mx-4 w-full max-w-sm rounded-2xl bg-white p-6 shadow-2xl">
          <h3 class="font-display text-lg font-semibold text-ink">确认删除角色</h3>
          <p class="mt-2 text-[13px] text-ink-soft">
            确定删除角色 <strong>{{ editingRole?.name }}</strong>？若该角色下有管理员，需先将他们迁移到其他角色。
          </p>
          <div class="mt-6 flex justify-end gap-3">
            <button class="btn-ghost" @click="showDeleteRoleConfirm = false">取消</button>
            <button class="rounded-luxe bg-danger px-4 py-2 text-[13px] font-medium text-white hover:bg-danger/90" @click="doDeleteRole">确认删除</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
