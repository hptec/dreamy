<script setup>
import { ref, computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { pendingChanges, publishHistory } from '@/data/mock'
import {
  RocketLaunchIcon, CheckCircleIcon, ArrowPathIcon, DocumentTextIcon,
  ClockIcon, ArrowUturnLeftIcon, CodeBracketIcon, GlobeAltIcon
} from '@heroicons/vue/24/outline'

const changes = ref(pendingChanges.map((c) => ({ ...c, included: true })))
const phase = ref('idle') // idle | building | done
const buildSteps = ref([])
const progress = ref(0)
const affectedPages = ref([])

const selectedCount = computed(() => changes.value.filter((c) => c.included).length)
const allAffected = computed(() => {
  const set = new Set()
  changes.value.filter((c) => c.included).forEach((c) => c.affects.forEach((a) => set.add(a)))
  return [...set]
})

const typeTone = { '首页装修': 'info', '商品': 'ok', 'Banner': 'warn', '内容': 'info', '导航': 'neutral' }

async function publish() {
  if (phase.value === 'building') return
  phase.value = 'building'
  progress.value = 0
  buildSteps.value = []
  affectedPages.value = []
  const seq = [
    { label: '收集待发布改动', ms: 500 },
    { label: '校验内容完整性', ms: 600 },
    { label: 'next build · 编译页面', ms: 1200 },
    { label: '生成静态 HTML（output: export）', ms: 1400 },
    { label: '写出 out/ 目录并优化资源', ms: 800 },
    { label: '部署到 CDN', ms: 700 }
  ]
  const total = seq.length
  for (let i = 0; i < total; i++) {
    buildSteps.value.push({ ...seq[i], status: 'running' })
    await new Promise((r) => setTimeout(r, seq[i].ms))
    buildSteps.value[i].status = 'done'
    progress.value = Math.round(((i + 1) / total) * 100)
  }
  // 逐个列出受影响页面
  affectedPages.value = allAffected.value.map((p) => ({ path: p, status: 'generated' }))
  phase.value = 'done'
}
function reset() { phase.value = 'idle'; progress.value = 0; buildSteps.value = [] }
</script>

<template>
  <div class="animate-fadeup">
    <PageHeader eyebrow="Static Publishing" title="发布中心" subtitle="将后台改动编译为静态 HTML 站点 · output: export">
      <template #actions>
        <span class="badge bg-warn/14 text-warn"><span class="h-1.5 w-1.5 rounded-full bg-current"></span>{{ selectedCount }} 项待发布</span>
      </template>
    </PageHeader>

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_360px]">
      <!-- 左：待发布 diff + 构建过程 -->
      <div class="space-y-6">
        <!-- 待发布改动 -->
        <div class="panel">
          <div class="flex items-center justify-between border-b border-line px-6 py-4">
            <h3 class="font-display text-lg font-semibold text-ink">待发布改动</h3>
            <span class="text-[12px] text-ink-faint">勾选要纳入本次发布的改动</span>
          </div>
          <div class="divide-y divide-line">
            <label v-for="c in changes" :key="c.id" class="flex cursor-pointer items-start gap-3 px-6 py-4 hover:bg-canvas-warm/40">
              <input type="checkbox" v-model="c.included" class="mt-1 h-4 w-4 rounded border-line accent-gold" />
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2">
                  <StatusBadge :tone="typeTone[c.type] || 'neutral'" :label="c.type" :dot="false" />
                  <span class="text-[13px] font-medium text-ink">{{ c.summary }}</span>
                </div>
                <p class="mt-1 text-[11px] text-ink-faint">{{ c.author }} · {{ c.time }}</p>
                <div class="mt-2 flex flex-wrap gap-1.5">
                  <span v-for="a in c.affects" :key="a" class="rounded bg-canvas-warm px-2 py-0.5 font-mono text-[10px] text-ink-soft">{{ a }}</span>
                </div>
              </div>
            </label>
          </div>
        </div>

        <!-- 构建过程 -->
        <div v-if="phase !== 'idle'" class="panel p-6">
          <div class="mb-4 flex items-center justify-between">
            <h3 class="flex items-center gap-2 font-display text-lg font-semibold text-ink">
              <CodeBracketIcon class="h-5 w-5 text-gold-deep" />构建日志
            </h3>
            <span class="font-mono text-[13px]" :class="phase === 'done' ? 'text-ok' : 'text-gold-deep'">{{ progress }}%</span>
          </div>
          <!-- 进度条 -->
          <div class="mb-5 h-1.5 overflow-hidden rounded-full bg-line">
            <div class="h-full rounded-full bg-gold transition-all duration-300" :style="{ width: progress + '%' }"></div>
          </div>
          <!-- 步骤 -->
          <div class="space-y-2.5 font-mono text-[12.5px]">
            <div v-for="(s, i) in buildSteps" :key="i" class="flex items-center gap-2.5">
              <CheckCircleIcon v-if="s.status === 'done'" class="h-4 w-4 shrink-0 text-ok" />
              <ArrowPathIcon v-else class="h-4 w-4 shrink-0 animate-spin text-gold-deep" />
              <span :class="s.status === 'done' ? 'text-ink-soft' : 'text-ink'">{{ s.label }}</span>
              <span v-if="s.status === 'done'" class="ml-auto text-ink-faint">✓</span>
            </div>
          </div>

          <!-- 受影响页面清单 -->
          <div v-if="phase === 'done'" class="mt-6 rounded-luxe border border-ok/30 bg-ok/6 p-4">
            <p class="mb-3 flex items-center gap-2 text-[13px] font-medium text-ok">
              <CheckCircleIcon class="h-5 w-5" />发布成功 · 已生成 {{ affectedPages.length }} 个静态页面
            </p>
            <div class="grid grid-cols-1 gap-1.5 sm:grid-cols-2">
              <div v-for="p in affectedPages" :key="p.path" class="flex items-center gap-2 rounded bg-white/60 px-3 py-1.5 text-[12px]">
                <GlobeAltIcon class="h-3.5 w-3.5 text-ink-faint" />
                <span class="font-mono text-ink-soft">{{ p.path }}</span>
                <span class="ml-auto text-[10px] text-ok">已生成</span>
              </div>
            </div>
            <button class="btn-ghost mt-3" @click="reset">完成</button>
          </div>
        </div>
      </div>

      <!-- 右：发布操作 + 历史 -->
      <div class="space-y-6">
        <!-- 发布卡 -->
        <div class="panel p-6">
          <p class="eyebrow">Ready to Publish</p>
          <h3 class="mt-1 font-display text-xl font-semibold text-ink">发布到生产环境</h3>
          <p class="mt-2 text-[13px] text-ink-soft">本次将重新生成 <span class="font-medium text-gold-deep">{{ allAffected.length }}</span> 个受影响页面。</p>
          <ul class="mt-4 space-y-1.5 text-[12px] text-ink-faint">
            <li v-for="p in allAffected.slice(0, 6)" :key="p" class="flex items-center gap-1.5"><span class="h-1 w-1 rounded-full bg-gold"></span>{{ p }}</li>
            <li v-if="allAffected.length > 6" class="text-ink-faint">… 等 {{ allAffected.length }} 个页面</li>
          </ul>
          <button class="btn-gold mt-5 w-full py-2.5" :disabled="phase === 'building' || selectedCount === 0" @click="publish">
            <RocketLaunchIcon class="h-4 w-4" />
            {{ phase === 'building' ? '正在生成静态站点…' : '一键发布 · 生成 HTML' }}
          </button>
          <p class="mt-3 text-center text-[11px] text-ink-faint">原型演示：模拟 next build 静态导出流程</p>
        </div>

        <!-- 发布历史 -->
        <div class="panel p-6">
          <h3 class="mb-4 flex items-center gap-2 font-display text-lg font-semibold text-ink"><ClockIcon class="h-5 w-5 text-gold-deep" />发布历史</h3>
          <div class="space-y-4">
            <div v-for="(h, i) in publishHistory" :key="h.id" class="relative pl-5">
              <span class="absolute left-0 top-1.5 h-2 w-2 rounded-full bg-ok"></span>
              <span v-if="i < publishHistory.length - 1" class="absolute left-[3px] top-3.5 h-full w-px bg-line"></span>
              <div class="flex items-start justify-between gap-2">
                <div class="min-w-0">
                  <p class="truncate text-[13px] text-ink">{{ h.note }}</p>
                  <p class="text-[11px] text-ink-faint">{{ h.time }} · {{ h.author }}</p>
                  <p class="text-[11px] text-ink-faint">{{ h.pages }} 页 · 耗时 {{ h.duration }} · <span class="font-mono">{{ h.id }}</span></p>
                </div>
                <button class="btn-ghost shrink-0 text-[11px]"><ArrowUturnLeftIcon class="h-3.5 w-3.5" />回滚</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
