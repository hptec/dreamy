<script setup lang="ts">
// PAGE-A05 / COMP-A06：角色权限矩阵。is_locked 只读（EDGE-019/FUNC-018）；
// FORM-A03 保存→updateRole→fetchMe 重渲菜单；FORM-A04 删角色有成员→409 40904
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { useRolesStore } from '@/stores/roles'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import type { Role } from '@/api/types'
import { PlusIcon, PencilSquareIcon, TrashIcon } from '@heroicons/vue/24/outline'

const store = useRolesStore()
const toast = useToastStore()

const selectedRoleId = ref<string | null>(null)
// 本地编辑副本：当前角色的权限 key 集合
const editingKeys = ref<string[]>([])
const hasUnsavedChanges = ref(false)
const saving = ref(false)

const showRoleModal = ref(false)
const showDeleteRoleConfirm = ref(false)
const editingRole = ref<Role | null>(null)
const roleForm = ref({ name: '' })
const roleFormError = ref('')
const deleteTarget = ref<Role | null>(null)

const roleList = computed(() => store.roles)
const selectedRole = computed(() => store.roles.find((r) => r.id === selectedRoleId.value) || null)
const permissionGroups = computed(() => store.groupedPermissions())
const allKeys = computed(() => store.permissions.map((p) => p.key))

function syncEditingKeys() {
  editingKeys.value = selectedRole.value ? [...selectedRole.value.permissionKeys] : []
  hasUnsavedChanges.value = false
}

async function load() {
  try {
    await store.fetchAll()
    if (store.roles.length) {
      selectedRoleId.value = store.roles[0].id
      syncEditingKeys()
    }
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '加载角色权限失败')
  }
}

function selectRole(id: string) {
  if (hasUnsavedChanges.value) {
    const ok = window.confirm('你有未保存的权限变更，确定放弃并切换角色？')
    if (!ok) return
  }
  selectedRoleId.value = id
  syncEditingKeys()
}

function isPermitted(key: string): boolean {
  return editingKeys.value.includes(key)
}
function togglePermission(key: string) {
  if (!selectedRole.value || selectedRole.value.isLocked) return
  hasUnsavedChanges.value = true
  const idx = editingKeys.value.indexOf(key)
  if (idx >= 0) editingKeys.value.splice(idx, 1)
  else editingKeys.value.push(key)
}
function groupState(items: { key: string }[]): 'none' | 'partial' | 'all' {
  const keys = items.map((i) => i.key)
  const granted = keys.filter((k) => editingKeys.value.includes(k)).length
  if (granted === 0) return 'none'
  if (granted === keys.length) return 'all'
  return 'partial'
}
function toggleGroup(items: { key: string }[]) {
  if (!selectedRole.value || selectedRole.value.isLocked) return
  hasUnsavedChanges.value = true
  const keys = items.map((i) => i.key)
  const allGranted = keys.every((k) => editingKeys.value.includes(k))
  if (allGranted) {
    editingKeys.value = editingKeys.value.filter((k) => !keys.includes(k))
  } else {
    for (const k of keys) if (!editingKeys.value.includes(k)) editingKeys.value.push(k)
  }
}
function toggleAll() {
  if (!selectedRole.value || selectedRole.value.isLocked) return
  hasUnsavedChanges.value = true
  const all = allKeys.value
  const allGranted = all.every((k) => editingKeys.value.includes(k))
  editingKeys.value = allGranted ? [] : [...all]
}

async function savePermissions() {
  if (!selectedRole.value || selectedRole.value.isLocked || !hasUnsavedChanges.value) return
  saving.value = true
  try {
    await store.update(selectedRole.value.id, {
      name: selectedRole.value.name,
      permissionKeys: [...editingKeys.value],
    })
    hasUnsavedChanges.value = false
    toast.success('权限已保存，菜单已刷新')
    syncEditingKeys()
  } catch (e) {
    if (e instanceof BizError && e.code === 40308) toast.error(e.message) // ROLE_LOCKED
    else toast.error(e instanceof BizError ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

// ---- 新增 / 改名 ----
function openAddRole() {
  editingRole.value = null
  roleForm.value = { name: '' }
  roleFormError.value = ''
  showRoleModal.value = true
}
function openEditRole(role: Role) {
  if (role.isLocked) return
  editingRole.value = role
  roleForm.value = { name: role.name }
  roleFormError.value = ''
  showRoleModal.value = true
}
async function submitRole() {
  const name = roleForm.value.name.trim()
  if (!name) {
    roleFormError.value = '请输入角色名称'
    return
  }
  if (!editingRole.value && roleList.value.some((r) => r.name === name)) {
    roleFormError.value = '角色名称已存在'
    return
  }
  saving.value = true
  try {
    if (editingRole.value) {
      await store.update(editingRole.value.id, { name, permissionKeys: editingRole.value.permissionKeys })
      toast.success('角色已更新')
    } else {
      const created = await store.create(name)
      selectedRoleId.value = created.id
      syncEditingKeys()
      toast.success('角色已创建')
    }
    showRoleModal.value = false
  } catch (e) {
    roleFormError.value = e instanceof BizError ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}

// ---- 删除 ----
function confirmDeleteRole(role: Role) {
  if (role.isLocked || role.type === 'preset') return
  deleteTarget.value = role
  showDeleteRoleConfirm.value = true
}
async function doDeleteRole() {
  if (!deleteTarget.value) return
  saving.value = true
  try {
    await store.remove(deleteTarget.value.id)
    toast.success('角色已删除')
    if (selectedRoleId.value === deleteTarget.value.id) {
      selectedRoleId.value = store.roles[0]?.id || null
      syncEditingKeys()
    }
    showDeleteRoleConfirm.value = false
  } catch (e) {
    // 409 40904 ROLE_IN_USE → 提示先迁移成员
    toast.error(e instanceof BizError ? e.message : '删除失败')
    showDeleteRoleConfirm.value = false
  } finally {
    saving.value = false
  }
}

function permCount(role: Role): number {
  return role.permissionKeys.length
}

onMounted(load)
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
          <div v-if="store.loading" class="px-4 py-6 text-center text-[12px] text-ink-faint">加载中…</div>
          <div v-else class="divide-y divide-line">
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
                  · {{ permCount(r) }} 项权限 · {{ r.memberCount }} 人
                </p>
              </div>
              <span v-if="r.isLocked" class="text-[10px] text-ink-faint" title="权限锁定">🔒</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 右侧：权限矩阵 -->
      <div class="flex-1 min-w-0">
        <div v-if="selectedRole" class="panel overflow-hidden">
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
                class="rounded-luxe px-3 py-1.5 text-[12px] font-medium text-ink-soft hover:bg-canvas-warm disabled:cursor-not-allowed disabled:opacity-50"
                :disabled="selectedRole.isLocked"
                @click="toggleAll"
              >{{ allKeys.every((k) => editingKeys.includes(k)) ? '取消全选' : '全选全部' }}</button>
              <button
                v-if="!selectedRole.isLocked"
                class="btn-ghost"
                @click="openEditRole(selectedRole)"
              ><PencilSquareIcon class="h-4 w-4" />改名</button>
              <button
                v-if="!selectedRole.isLocked && selectedRole.type === 'custom'"
                class="btn-ghost text-danger"
                @click="confirmDeleteRole(selectedRole)"
              ><TrashIcon class="h-4 w-4" />删除</button>
            </div>
          </div>

          <div class="border-b border-line bg-gold/5 px-5 py-2.5 text-[12px] text-ink-soft">
            勾选的菜单项对该角色可见且可访问。分组标题前复选框可批量勾选全组。
          </div>

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

          <div v-if="!selectedRole.isLocked" class="flex items-center justify-end gap-3 border-t border-line px-5 py-4">
            <span v-if="hasUnsavedChanges" class="text-[12px] text-warn">有未保存的变更</span>
            <button
              class="btn-primary"
              :class="!hasUnsavedChanges ? 'opacity-50 cursor-not-allowed' : ''"
              :disabled="!hasUnsavedChanges || saving"
              @click="savePermissions"
            >{{ saving ? '保存中…' : '保存权限' }}</button>
          </div>
        </div>

        <div v-else class="flex items-center justify-center rounded-2xl border-2 border-dashed border-line py-20 text-ink-faint">
          请从左侧选择一个角色以编辑其权限
        </div>
      </div>
    </div>

    <!-- 新增/编辑角色弹窗 -->
    <Teleport to="body">
      <div v-if="showRoleModal" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" @click.self="showRoleModal = false">
        <div class="mx-4 w-full max-w-sm rounded-2xl bg-white p-6 shadow-2xl" role="dialog" aria-modal="true">
          <h3 class="font-display text-lg font-semibold text-ink">{{ editingRole ? '编辑角色' : '新增角色' }}</h3>
          <div class="mt-4">
            <label class="mb-1 block text-[13px] font-medium text-ink">角色名称</label>
            <input v-model="roleForm.name" class="field w-full" placeholder="输入角色名称" @keyup.enter="submitRole" />
            <p v-if="roleFormError" class="mt-1 text-[12px] text-danger">{{ roleFormError }}</p>
          </div>
          <div class="mt-6 flex justify-end gap-3">
            <button class="btn-ghost" :disabled="saving" @click="showRoleModal = false">取消</button>
            <button class="btn-primary" :disabled="saving" @click="submitRole">{{ editingRole ? '保存' : '创建' }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 删除角色确认 -->
    <Teleport to="body">
      <div v-if="showDeleteRoleConfirm" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" @click.self="showDeleteRoleConfirm = false">
        <div class="mx-4 w-full max-w-sm rounded-2xl bg-white p-6 shadow-2xl" role="dialog" aria-modal="true">
          <h3 class="font-display text-lg font-semibold text-ink">确认删除角色</h3>
          <p class="mt-2 text-[13px] text-ink-soft">
            确定删除角色 <strong>{{ deleteTarget?.name }}</strong>？若该角色下有管理员，需先将他们迁移到其他角色。
          </p>
          <div class="mt-6 flex justify-end gap-3">
            <button class="btn-ghost" :disabled="saving" @click="showDeleteRoleConfirm = false">取消</button>
            <button class="rounded-luxe bg-danger px-4 py-2 text-[13px] font-medium text-white hover:bg-danger/90 disabled:opacity-50" :disabled="saving" @click="doDeleteRole">确认删除</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
