# Refunds L2 前端详设（admin-prototype-alignment）

> 覆盖缺口：ALIGN-024、ALIGN-025
> 原型 ground truth：`hhspec/prototype/portal-admin/src/views/Refunds.vue`（48 行，行内 同意/拒绝 按钮，非 pending 显示「已处理」）
> 实现现状：`frontend/portal-admin/src/views/Refunds.vue`（197 行，弹窗审批：同意确认弹窗/拒绝原因弹窗/登记退货单号弹窗——决策 24/31 旧方案）
> 决策依据：决策 7（恢复行内审批，拒绝行内展开原因输入，后端拒绝原因必填约束不变）、契约 AdminRefund（stripe_refund_id / return_tracking_no 字段已有）

## 0. 缺口处置总表

| 缺口 | 处置 | 说明 |
|---|---|---|
| ALIGN-024 | CHANGE | 弹窗审批 → 行内同意/拒绝；拒绝在行内展开原因输入框（决策 7 推翻旧弹窗方案） |
| ALIGN-025 | CHANGE | 已处理状态行展示退款单号（stripe_refund_id）/ 退货单号（return_tracking_no） |

## 1. 行内审批交互（FORM-TRD-R01，ALIGN-024 核心）

操作列状态机（替换现 openModal 三弹窗流）：

```
组件局部状态：
  rejectingId: number | null      // 当前展开拒绝输入的工单 id（同一时刻仅一行展开）
  reasonDraft: string
  busyId: number | null

r.status === 'pending' 且 rejectingId !== r.id：
  [✓ 同意 btn-ghost text-ok] [✗ 拒绝 btn-danger-ghost]      // 1:1 原型 L37-40
  同意点击 → approve(r)：
    await store.approve(r.id)                                // 同意不弹窗（决策 7：恢复原型观感）
    toast.success('已同意退款，Stripe 退款已发起')
    // 退货物流单号改为事后登记：已同意行保留「登记退货单号」入口（见 §3）
  拒绝点击 → rejectingId = r.id; reasonDraft = ''

rejectingId === r.id（行内展开态，按钮位置原地替换为输入区）：
  <input v-model=reasonDraft maxlength=255 placeholder='拒绝原因（必填，将向买家展示）'
         autofocus @keyup.enter=confirmReject @keyup.escape=cancelReject />
  [确认拒绝 btn-danger-ghost :disabled='!reasonDraft.trim() || busy'] [取消 btn-ghost]
  confirmReject():
    if !reasonDraft.trim(): inlineError='拒绝原因必填'; return   // 前端预判
    await store.reject(r.id, reasonDraft.trim())                 // 后端必填约束不变（422 兜底）
    toast.success('已拒绝该工单'); rejectingId = null

r.status !== 'pending'：见 §2 已处理单元格
```

布局约束：展开输入区占据操作单元格（`flex items-center justify-end gap-1`，input `w-44`），不破坏表格行高骤变——允许行高自然增高，不用弹层。

## 2. 已处理状态展示（COMP-TRD-R01，ALIGN-025）

```
<td v-else>   // approved / rejected
  <div class="text-right">
    <span class="text-[12px] text-ink-faint">已处理</span>                      // 原型 L42 文案保留
    <p v-if="r.status==='approved' && r.stripeRefundId" class="font-mono text-[11px] text-ink-faint">
      退款单号 {{ r.stripeRefundId }}</p>
    <p v-if="r.returnTrackingNo" class="font-mono text-[11px] text-ink-faint">
      退货单号 {{ r.returnTrackingNo }}</p>
  </div>
</td>
// 字段来源：AdminRefund.stripe_refund_id（审核通过后写入）/ return_tracking_no（登记，决策 31）
// api/types.ts 确认两字段已映射（deepSnakeize→camel）
```

## 3. 登记退货单号入口保留（决策 31 兼容）

现 impl「登记退货单号」弹窗入口（已同意未登记行）**保留**——属实现增强（决策 9），但入口样式降级为行内 `btn-ghost text-[12px]`「登记退货单号」，点击仍走现有登记弹窗（单字段表单弹窗可保留，不属于审批二次确认范畴）。

## 4. 状态管理（STORE-TRD-R01）

`stores/refunds.ts`：
- `approve(id)` 签名调整：return_tracking_no 不再随同意提交（approve 请求体允许省略该字段，契约为选填）
- `reject(id, reason)` / `registerReturnNo(id, no)` 不变

## 5. 豁免与验证

- Tab 结构（全部/待审批/已同意/已拒绝）与列结构 impl 已对齐原型，仅验证
- 退款工单表本身为实现增强（决策 9 保留项），原型仅为简表
