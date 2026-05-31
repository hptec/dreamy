<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { customerDetail, ORDER_STATUS } from '@/data/mock'
import {
  ArrowLeftIcon, ArrowRightOnRectangleIcon, TagIcon, MapPinIcon,
  KeyIcon, ComputerDesktopIcon, ShieldCheckIcon, CheckBadgeIcon
} from '@heroicons/vue/24/outline'

const router = useRouter()
const c = customerDetail
const stats = [
  { label: '订单数', value: c.stats.orders }, { label: '累计消费', value: '$' + c.stats.spent.toLocaleString() },
  { label: '收藏', value: c.stats.wishlist }, { label: '评价', value: c.stats.reviews }
]

const sessions = ref([...c.activeSessions])
const showForceLogout = ref(false)

const PROVIDER_LABEL = { email: '邮箱', google: 'Google', apple: 'Apple' }
function providerLabel(p) { return PROVIDER_LABEL[p] || p }

function doForceLogout() {
  sessions.value = []
  showForceLogout.value = false
}
function revokeSession(id) {
  sessions.value = sessions.value.filter((s) => s.id !== id)
}
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader :eyebrow="c.id" title="用户详情">
      <template #actions>
        <button class="btn-ghost" @click="router.push('/customers')"><ArrowLeftIcon class="h-4 w-4" />返回</button>
        <button class="btn-outline"><TagIcon class="h-4 w-4" />打标签</button>
        <button class="btn-outline" @click="showForceLogout = true"><ArrowRightOnRectangleIcon class="h-4 w-4" />强制下线</button>
        <button class="btn-danger-ghost"><KeyIcon class="h-4 w-4" />禁用账户</button>
      </template>
    </PageHeader>

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-[320px_1fr]">
      <!-- 左：资料卡 + 登录方式 + 地址 -->
      <div class="space-y-6">
        <div class="panel p-6 text-center">
          <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-ink text-xl font-medium text-canvas">{{ c.avatar }}</span>
          <p class="mt-3 font-display text-xl font-semibold text-ink">{{ c.name }}</p>
          <p class="text-[12px] text-ink-soft">{{ c.email }}</p>
          <div class="mt-2 flex justify-center gap-2">
            <StatusBadge tone="warn" :label="c.tier" :dot="false" />
            <StatusBadge :tone="c.status === 'active' ? 'ok' : 'danger'" :label="c.status === 'active' ? '正常' : '已禁用'" />
          </div>
          <div class="mt-4 grid grid-cols-2 gap-3 border-t border-line pt-4">
            <div v-for="s in stats" :key="s.label"><p class="font-display text-lg font-semibold text-gold-deep">{{ s.value }}</p><p class="text-[11px] text-ink-faint">{{ s.label }}</p></div>
          </div>
          <p class="mt-4 text-[11px] text-ink-faint">注册于 {{ c.joined }} · {{ c.phone }}</p>
        </div>

        <!-- 登录方式 -->
        <div class="panel p-5">
          <h3 class="mb-3 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><ShieldCheckIcon class="h-4 w-4 text-gold-deep" />登录方式</h3>
          <div class="space-y-2.5">
            <div v-for="m in c.loginMethods" :key="m.provider" class="rounded-luxe border border-line p-3">
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <span
                    class="flex h-6 w-6 items-center justify-center rounded-full text-[11px] font-semibold"
                    :class="m.provider === 'google' ? 'bg-info/12 text-info' : m.provider === 'apple' ? 'bg-ink/8 text-ink' : 'bg-gold/12 text-gold-deep'"
                  >{{ providerLabel(m.provider).charAt(0) }}</span>
                  <span class="text-[13px] font-medium text-ink">{{ m.label }}</span>
                  <span v-if="m.primary" class="rounded-full bg-ink/8 px-1.5 py-0.5 text-[10px] text-ink-soft">主</span>
                  <CheckBadgeIcon v-if="m.verified" class="h-4 w-4 text-ok" title="已验证" />
                </div>
                <span class="text-[11px]" :class="m.connected ? 'text-ok' : 'text-ink-faint'">{{ m.connected ? '已绑定' : '未绑定' }}</span>
              </div>
              <p class="mt-1 truncate text-[11px] text-ink-soft">{{ m.identifier }}</p>
              <p v-if="m.hiddenEmail" class="mt-0.5 text-[10px] text-ink-faint">relay: {{ m.relay }}（按 Apple sub 标识）</p>
              <div class="mt-1 flex justify-between text-[10px] text-ink-faint">
                <span>绑定于 {{ m.boundAt }}</span>
                <span>最近登录 {{ m.lastLogin }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="panel p-5">
          <h3 class="mb-3 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><MapPinIcon class="h-4 w-4 text-gold-deep" />收货地址</h3>
          <div v-for="a in c.addresses" :key="a.line" class="rounded-luxe border border-line p-3 text-[12px] text-ink-soft">
            <p class="mb-1 text-[11px] font-medium text-gold-deep">{{ a.label }}</p>{{ a.line }}<br />{{ a.country }}
          </div>
        </div>
      </div>

      <!-- 右：订单 + 登录记录 + 会话 + Wishlist + 行为 -->
      <div class="space-y-6">
        <div class="panel">
          <div class="border-b border-line px-6 py-4"><h3 class="font-display text-lg font-semibold text-ink">最近订单</h3></div>
          <table class="data-table">
            <thead><tr><th>订单号</th><th class="text-right">金额</th><th>状态</th><th>时间</th></tr></thead>
            <tbody>
              <tr v-for="o in c.recentOrders" :key="o.id">
                <td class="font-mono text-[12px] text-gold-deep">{{ o.id }}</td>
                <td class="text-right font-medium text-ink">${{ o.total.toLocaleString() }}</td>
                <td><StatusBadge :tone="ORDER_STATUS[o.status].tone" :label="ORDER_STATUS[o.status].label" /></td>
                <td class="text-[12px] text-ink-faint">{{ o.date }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 登录记录 -->
        <div class="panel">
          <div class="border-b border-line px-6 py-4"><h3 class="font-display text-lg font-semibold text-ink">登录记录</h3></div>
          <table class="data-table">
            <thead><tr><th>时间</th><th>方式</th><th>IP</th><th>设备</th><th>位置</th><th>结果</th></tr></thead>
            <tbody>
              <tr v-for="(h, i) in c.loginHistory" :key="i">
                <td class="font-mono text-[12px] text-ink-faint whitespace-nowrap">{{ h.time }}</td>
                <td><span class="rounded bg-canvas-warm px-2 py-0.5 text-[12px] text-ink-soft">{{ providerLabel(h.method) }}</span></td>
                <td class="font-mono text-[12px] text-ink-faint">{{ h.ip }}</td>
                <td class="text-[12px] text-ink-soft">{{ h.device }}</td>
                <td class="text-[12px] text-ink-faint">{{ h.location }}</td>
                <td><StatusBadge :tone="h.result === 'success' ? 'ok' : 'danger'" :label="h.result === 'success' ? '成功' : '失败'" /></td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 活跃会话 -->
        <div class="panel p-6">
          <div class="mb-4 flex items-center justify-between">
            <h3 class="flex items-center gap-1.5 font-display text-lg font-semibold text-ink"><ComputerDesktopIcon class="h-5 w-5 text-gold-deep" />活跃会话</h3>
            <button v-if="sessions.length" class="btn-ghost text-danger hover:bg-danger/8" @click="showForceLogout = true">
              <ArrowRightOnRectangleIcon class="h-4 w-4" />强制下线全部
            </button>
          </div>
          <div v-if="sessions.length" class="space-y-2">
            <div v-for="s in sessions" :key="s.id" class="flex items-center justify-between rounded-luxe border border-line p-3">
              <div>
                <p class="text-[13px] text-ink">{{ s.device }} <span class="text-[11px] text-ink-faint">· {{ providerLabel(s.method) }}</span></p>
                <p class="text-[11px] text-ink-faint">{{ s.ip }} · {{ s.location }} · {{ s.lastActive }}</p>
              </div>
              <button class="btn-ghost text-[12px]" @click="revokeSession(s.id)">下线</button>
            </div>
          </div>
          <p v-else class="rounded-luxe border border-dashed border-line py-6 text-center text-[13px] text-ink-faint">该用户当前无活跃会话</p>
        </div>

        <div class="panel p-6">
          <h3 class="mb-4 font-display text-lg font-semibold text-ink">Wishlist 收藏</h3>
          <div class="grid grid-cols-4 gap-3">
            <div v-for="p in c.wishlist" :key="p.id"><img :src="p.img" class="aspect-[3/4] w-full rounded-luxe object-cover" /><p class="mt-1 truncate text-[11px] text-ink-soft">{{ p.name }}</p></div>
          </div>
        </div>
        <div class="panel p-6">
          <h3 class="mb-4 font-display text-lg font-semibold text-ink">行为时间线</h3>
          <div class="space-y-3">
            <div v-for="(a, i) in c.activity" :key="i" class="relative pl-5">
              <span class="absolute left-0 top-1.5 h-2 w-2 rounded-full bg-gold"></span>
              <span v-if="i < c.activity.length - 1" class="absolute left-[3px] top-3.5 h-full w-px bg-line"></span>
              <p class="text-[13px] text-ink">{{ a.label }}</p>
              <p class="text-[11px] text-ink-faint">{{ a.time }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 强制下线确认 -->
    <Teleport to="body">
      <div v-if="showForceLogout" class="fixed inset-0 z-50 flex items-center justify-center bg-ink/40" @click.self="showForceLogout = false">
        <div class="mx-4 w-full max-w-sm rounded-2xl bg-white p-6 shadow-2xl">
          <h3 class="font-display text-lg font-semibold text-ink">强制下线全部会话</h3>
          <p class="mt-2 text-[13px] text-ink-soft">将使 <strong>{{ c.name }}</strong> 在所有设备上退出登录，下次需重新通过验证码 / Google / Apple 登录。该用户为 passwordless 账户，无需重置密码。</p>
          <div class="mt-6 flex justify-end gap-3">
            <button class="btn-ghost" @click="showForceLogout = false">取消</button>
            <button class="rounded-luxe bg-danger px-4 py-2 text-[13px] font-medium text-white hover:bg-danger/90" @click="doForceLogout">确认下线</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
