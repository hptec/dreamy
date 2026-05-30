<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { orderDetail, ORDER_STATUS } from '@/data/mock'
import { ArrowLeftIcon, TruckIcon, ArrowUturnLeftIcon, CheckIcon, MapPinIcon, CreditCardIcon } from '@heroicons/vue/24/outline'

const router = useRouter()
const o = orderDetail
const subtotal = o.lines.reduce((s, l) => s + l.price * l.qty, 0)
const showShip = ref(false)
const carrier = ref('FedEx International Priority')
const tracking = ref('')
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader :eyebrow="o.id" title="订单详情">
      <template #actions>
        <button class="btn-ghost" @click="router.push('/orders')"><ArrowLeftIcon class="h-4 w-4" />返回</button>
        <button class="btn-outline"><ArrowUturnLeftIcon class="h-4 w-4" />发起退款</button>
        <button class="btn-primary" @click="showShip = !showShip"><TruckIcon class="h-4 w-4" />标记发货</button>
      </template>
    </PageHeader>

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_340px]">
      <!-- 左：明细 + 时间线 -->
      <div class="space-y-6">
        <!-- 发货面板 -->
        <div v-if="showShip" class="panel border-gold/40 p-5">
          <h3 class="mb-3 font-display text-lg font-semibold text-ink">填写物流信息</h3>
          <div class="grid grid-cols-2 gap-4">
            <div><label class="field-label">承运方</label><select v-model="carrier" class="field"><option>FedEx International Priority</option><option>UPS Worldwide Express</option><option>DHL Express</option></select></div>
            <div><label class="field-label">运单号</label><input v-model="tracking" class="field" placeholder="输入 tracking number" /></div>
          </div>
          <div class="mt-4 flex justify-end gap-2"><button class="btn-ghost" @click="showShip = false">取消</button><button class="btn-gold"><CheckIcon class="h-4 w-4" />确认发货</button></div>
        </div>

        <!-- 商品明细 -->
        <div class="panel">
          <div class="border-b border-line px-6 py-4"><h3 class="font-display text-lg font-semibold text-ink">商品明细</h3></div>
          <table class="data-table">
            <thead><tr><th>商品</th><th>SKU</th><th class="text-right">单价</th><th class="text-right">数量</th><th class="text-right">小计</th></tr></thead>
            <tbody>
              <tr v-for="l in o.lines" :key="l.sku">
                <td><div class="flex items-center gap-3"><img :src="l.img" class="h-12 w-10 rounded-luxe object-cover" /><div><p class="font-medium text-ink">{{ l.name }}</p><p class="text-[11px] text-ink-faint">{{ l.color }} · {{ l.size }}</p></div></div></td>
                <td class="font-mono text-[12px] text-ink-soft">{{ l.sku }}</td>
                <td class="text-right">${{ l.price.toLocaleString() }}</td>
                <td class="text-right">{{ l.qty }}</td>
                <td class="text-right font-medium text-ink">${{ (l.price * l.qty).toLocaleString() }}</td>
              </tr>
            </tbody>
          </table>
          <div class="border-t border-line px-6 py-4">
            <div class="ml-auto max-w-xs space-y-1.5 text-[13px]">
              <div class="flex justify-between text-ink-soft"><span>小计</span><span>${{ subtotal.toLocaleString() }}</span></div>
              <div class="flex justify-between text-ink-soft"><span>运费</span><span>免邮</span></div>
              <div class="flex justify-between border-t border-line pt-2 font-display text-lg font-semibold text-ink"><span>合计</span><span>${{ o.payment.paid.toLocaleString() }}</span></div>
            </div>
          </div>
        </div>

        <!-- 状态时间线 -->
        <div class="panel p-6">
          <h3 class="mb-5 font-display text-lg font-semibold text-ink">订单状态</h3>
          <div class="flex items-center justify-between">
            <template v-for="(t, i) in o.timeline" :key="i">
              <div class="flex flex-col items-center text-center">
                <span class="flex h-9 w-9 items-center justify-center rounded-full border-2" :class="t.done ? 'border-gold bg-gold text-white' : 'border-line text-ink-faint'">
                  <CheckIcon v-if="t.done" class="h-4 w-4" /><span v-else>{{ i + 1 }}</span>
                </span>
                <p class="mt-2 text-[12px]" :class="t.done ? 'font-medium text-ink' : 'text-ink-faint'">{{ t.label }}</p>
                <p class="text-[10px] text-ink-faint">{{ t.time }}</p>
              </div>
              <div v-if="i < o.timeline.length - 1" class="mx-2 h-px flex-1" :class="o.timeline[i+1].done ? 'bg-gold' : 'bg-line'"></div>
            </template>
          </div>
        </div>
      </div>

      <!-- 右：客户 + 地址 + 支付 -->
      <div class="space-y-6">
        <div class="panel p-5">
          <div class="mb-3 flex items-center justify-between"><h3 class="font-display text-base font-semibold text-ink">订单状态</h3><StatusBadge :tone="ORDER_STATUS[o.status].tone" :label="ORDER_STATUS[o.status].label" /></div>
        </div>
        <div class="panel p-5">
          <h3 class="mb-3 font-display text-base font-semibold text-ink">客户信息</h3>
          <p class="text-[13px] font-medium text-ink">{{ o.customer.name }}</p>
          <p class="text-[12px] text-ink-soft">{{ o.customer.email }}</p>
          <p class="text-[12px] text-ink-soft">{{ o.customer.phone }}</p>
        </div>
        <div class="panel p-5">
          <h3 class="mb-3 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><MapPinIcon class="h-4 w-4 text-gold-deep" />收货地址</h3>
          <p class="text-[13px] text-ink-soft">{{ o.address.line }}<br />{{ o.address.city }}, {{ o.address.state }} {{ o.address.zip }}<br />{{ o.address.country }}</p>
        </div>
        <div class="panel p-5">
          <h3 class="mb-3 flex items-center gap-1.5 font-display text-base font-semibold text-ink"><CreditCardIcon class="h-4 w-4 text-gold-deep" />支付信息</h3>
          <p class="text-[13px] text-ink-soft">{{ o.payment.method }}</p>
          <p class="text-[12px] text-ink-faint">支付时间 {{ o.payment.time }}</p>
        </div>
      </div>
    </div>
  </div>
</template>
