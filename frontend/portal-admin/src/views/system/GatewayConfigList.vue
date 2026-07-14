<script setup lang="ts">
// PAGE-001 GatewayConfigList：外部网关配置管理（FUNC-004~007/021；EDGE-008 权限守卫由 router meta 控制）
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import Pagination from '@/components/Pagination.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import GatewayConfigFormDrawer from '@/components/drawers/GatewayConfigFormDrawer.vue'
import SelectMenu from '@/components/ui/SelectMenu.vue'
import { useGatewayStore } from '@/stores/gateway'
import { useToastStore } from '@/stores/toast'
import { BizError } from '@/api/client'
import { GatewayType } from '@/api/types'
import type { GatewayConfigDetail } from '@/api/types'
import { PlusIcon, PencilSquareIcon, TrashIcon } from '@heroicons/vue/24/outline'
import { normalizeEnumFilter } from '@/utils/validators'

const store = useGatewayStore()
const toast = useToastStore()

const drawer = ref(false)
const editing = ref<GatewayConfigDetail | null>(null)
const typeFilter = ref<GatewayType | 'all'>('all')

const confirm = ref<{ id: number; message: string } | null>(null)
const confirmBusy = ref(false)

const GATEWAY_TYPE_LABEL: Record<number, string> = {
  [GatewayType.AI]: 'AI 网关',
  [GatewayType.LOGISTICS]: '物流网关',
  [GatewayType.PAYMENT]: '支付网关',
}

function load(page?: number) {
  store
    .fetchConfigs({ gatewayType: normalizeEnumFilter(typeFilter.value) as GatewayType | undefined, page })
    .catch((e) => toast.error(e instanceof BizError ? e.message : '加载网关配置失败'))
}

onMounted(() => load(1))

function open(c?: GatewayConfigDetail) {
  editing.value = c ?? null
  drawer.value = true
}

function onSaved() {
  // 保存后刷新列表（掩码 Key / 模型列表以服务端为准）
  load(store.page)
}

function askDelete(c: GatewayConfigDetail) {
  confirm.value = { id: c.id, message: `确认删除网关配置「${c.name}」？` }
}

async function doConfirm() {
  if (!confirm.value) return
  confirmBusy.value = true
  try {
    await store.deleteConfig(confirm.value.id)
    toast.success('已删除')
    confirm.value = null
  } catch (e) {
    toast.error(e instanceof BizError ? e.message : '删除失败')
  } finally {
    confirmBusy.value = false
  }
}

const hasConfigs = computed(() => store.configs.length > 0)
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="System" title="外部网关配置" subtitle="管理 AI / 物流 / 支付网关的连接配置、模型列表与连通性测试">
      <template #actions>
        <button class="btn-gold" @click="open()"><PlusIcon class="h-4 w-4" />新增配置</button>
      </template>
    </PageHeader>

    <div class="panel">
      <!-- 类型筛选 -->
      <div class="flex items-center gap-2 border-b border-line px-4 py-3">
        <span class="text-[12px] text-ink-faint">类型：</span>
        <SelectMenu
          v-model="typeFilter"
          class="w-40"
          :options="[
            { value: 'all', label: '全部' },
            { value: GatewayType.AI, label: 'AI 网关' },
            { value: GatewayType.LOGISTICS, label: '物流网关' },
            { value: GatewayType.PAYMENT, label: '支付网关' },
          ]"
          @change="load(1)"
        />
      </div>

      <template v-if="store.loading">
        <div class="space-y-2 p-4">
          <div v-for="i in 4" :key="i" class="h-14 animate-pulse rounded-luxe bg-canvas-warm/50"></div>
        </div>
      </template>
      <EmptyState v-else-if="!hasConfigs" title="尚未配置任何网关" hint="新增 AI 网关后即可在翻译弹窗中使用其模型。" />
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>类型</th>
            <th>地址</th>
            <th>API Key</th>
            <th>默认模型</th>
            <th class="text-center">模型数</th>
            <th class="text-center">状态</th>
            <th class="text-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in store.configs" :key="c.id">
            <td class="font-medium text-ink">{{ c.name }}</td>
            <td class="text-ink-soft">{{ GATEWAY_TYPE_LABEL[c.gatewayType] || '—' }}</td>
            <td class="max-w-[220px] truncate text-ink-faint" :title="c.baseUrl">{{ c.baseUrl }}</td>
            <td class="font-mono text-[12px] text-ink-faint">{{ c.apiKeyMasked || '—' }}</td>
            <td class="text-ink-soft">{{ c.defaultModel || '—' }}</td>
            <td class="text-center text-ink-soft">{{ c.modelList?.length ?? 0 }}</td>
            <td class="text-center">
              <StatusBadge :tone="c.enabled ? 'ok' : 'neutral'" :label="c.enabled ? '启用' : '停用'" />
            </td>
            <td>
              <div class="flex items-center justify-end gap-1">
                <button class="btn-ghost" title="编辑" @click="open(c)"><PencilSquareIcon class="h-4 w-4" /></button>
                <button class="btn-danger-ghost" title="删除" @click="askDelete(c)"><TrashIcon class="h-4 w-4" /></button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <Pagination
        v-if="hasConfigs"
        :total="store.total"
        :page="store.page"
        :per-page="store.pageSize"
        @change="load($event)"
      />
    </div>

    <GatewayConfigFormDrawer :open="drawer" :editing="editing" @close="drawer = false" @saved="onSaved" />

    <ConfirmDialog
      :open="!!confirm"
      title="删除确认"
      :message="confirm?.message || ''"
      confirm-text="删除"
      danger
      :busy="confirmBusy"
      @confirm="doConfirm"
      @cancel="confirm = null"
    />
  </div>
</template>
