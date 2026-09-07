# 订单流程完整化 Runbook（order-flow-complete）

适用版本：commit 5534ad4 及之后。配套方案：`proposal.md`。

## 1. 配置项与开关

| 配置 | 环境变量 | 缺省 | 生产建议 | 说明 |
|---|---|---|---|---|
| `dreamy.stripe.mode` | `STRIPE_MODE` | `stub` | `real` | `stub` 时注册 `POST /api/store/orders/{id}/payment/confirm`（模拟付款）；`real` 时该端点不存在，须配 `STRIPE_SECRET_KEY`/`STRIPE_WEBHOOK_SECRET` |
| `dreamy.tracking.mode` | `TRACKING_MODE` | `stub` | `track17`（配 `TRACK17_API_KEY`） | 包裹轨迹供应商；stub 下 `/sync` 返回 204 无操作，轨迹仅手工录入 |
| `dreamy.tracking.sync-cron` | — | 每 30 分钟 | 同 | 拉取 PENDING/IN_TRANSIT/OUT_FOR_DELIVERY 且 24h 内未同步的包裹，批 200 |
| `dreamy.exchange-rate.mode` | `EXCHANGE_RATE_MODE` | `manual` | `frankfurter` | `frankfurter` 走 ECB 日频（api.frankfurter.app，超时 5s）；`manual` 下后台「刷新」返回 409905 |
| `dreamy.exchange-rate.refresh-cron` | — | 03:05 | 同 | 供应商刷新；`manual_override=1` 的币种跳过 |
| `dreamy.mq.mode` | `MQ_MODE` | `stub` | `rabbit` | 事件投递；两种模式都会先落 `event_outbox` |
| 结算配置（后台 → 设置 → 结算） | — | 见下 | — | `pending_timeout_minutes` 待支付超时（5..1440，缺省 30）、`auto_deliver_days` 发货后无签收自动送达（缺省 30）、`auto_complete_days` 签收后自动完成（缺省 7）、`exchange_rate_spread_scaled` 汇率加价 ×10000（缺省 0）、`production_days_default` 默认制作周期（缺省 21） |

## 2. 定时任务（均 Redisson 单飞锁，批 200，逐单独立事务）

| 任务 | 频率 | 锁 key | 作用 |
|---|---|---|---|
| OrderTimeoutScheduler | 每分钟 | `trading:order-timeout` | PENDING 超 `expires_at` → CANCELLED（回补库存/券，写事件） |
| OrderAutoCompleteScheduler | 每小时 | `trading:order-autocomplete` | `delivered_at + auto_complete_days` → COMPLETED；`shipped_at + auto_deliver_days` 且无签收 → DELIVERED |
| OutboxRelayScheduler | 每分钟 | `trading:outbox-relay` | 重投 PENDING outbox（退避 1m/5m/15m/1h，8 次后 DEAD） |
| ShipmentTrackingSyncScheduler | 每 30 分钟 | `trading:shipment-sync` | 供应商轨迹拉取（stub 无操作） |
| ExchangeRateRefreshScheduler | 每日 03:05 | `trading:exchange-rate-refresh` | 供应商汇率刷新 + 写 history |

## 3. 指标与告警

Micrometer 计数器（`/actuator/metrics/<name>`）：

- `dreamy.payment.confirm.total` — stub 确认次数（生产应恒为 0）
- `dreamy.webhook.mismatch.total` — webhook 金额/币种不符（>0 立即人工核对）
- `dreamy.order.autocomplete.total` — 自动送达/完成推进数
- `dreamy.outbox.dead.total` — outbox 投递死亡数（>0 需重放，见 §5）
- `dreamy.shipment.sync.failure.total` — 轨迹同步失败
- `dreamy.exchange.refresh.failure.total` — 汇率刷新失败（现值保留）

日志告警抓取关键字：`[ALERT]`（金额不符 / 迟到支付补偿 / 退款对账不收敛 / outbox DEAD / 供应商失败）。

## 4. 状态机速查

```
PENDING(1) → PAID(2) | CANCELLED(5)
PAID(2)    → SHIPPED(3) | REFUNDING(6) | CANCELLED(5，后台，自动全额退款)
SHIPPED(3) → DELIVERED(8) | COMPLETED(4) | REFUNDING(6)
DELIVERED(8) → COMPLETED(4) | REFUNDING(6)
REFUNDING(6) → REFUNDED(7) | 还原 from_status
```

- `production_stage`（1 待审核 2 制作中 3 质检中 4 待发货）仅 PAID 态非空；发货后置空。
- 部分退款：`refunded_amount` 累计，未达 `total_amount` 时订单回到 `from_status`；Payment 为 PARTIALLY_REFUNDED(6)。
- 包裹（`shipment`）：1 待揽收 2 运输中 3 派送中 4 已签收 5 异常 6 作废；全部行已分配 → 订单 SHIPPED；全部包裹签收 → 订单 DELIVERED。

## 5. 常见故障处置

| 现象 | 排查 | 处置 |
|---|---|---|
| 顾客付款后订单仍 PENDING（real） | Stripe Dashboard webhook 投递记录；`processed_event` 是否有该 event_id；日志 401601（验签失败）/`[ALERT] mismatch` | 验签失败核对 `STRIPE_WEBHOOK_SECRET`；金额不符核对订单 `total_amount` 与 PI 金额后人工处理；Stripe 会自动重投 |
| 邮件/销量/showroom 未触发 | `SELECT * FROM event_outbox WHERE status<>2` | broker 恢复后 relay 自动重投；DEAD 行 `UPDATE event_outbox SET status=1, attempts=0, next_attempt_at=NOW() WHERE status=3` 重放 |
| 发货报 422906 | 行已分配数量 `SELECT order_line_id, SUM(qty) FROM shipment_line ... GROUP BY` | 作废错误包裹（仅无供应商事件且未签收）后重建 |
| 发货报 409906 | 同一订单并发发货 | 重试 |
| 订单 SHIPPED 但长期无签收 | 供应商同步失败计数；`shipment.last_event_at` | 后台手工「标记签收」或等 `auto_deliver_days` 自动送达 |
| 汇率异常 | `exchange_rate.source/synced_at`；`exchange_rate_history` | 后台手工改值并勾选「手工锁定」；供应商恢复后取消锁定 |
| 税费为 0 但应收税 | `tax_destination_policy`（DDU 不计入）与 `tax_rule.enabled/生效窗口`；地址 `country_code/region_code` 是否为空 | 后台补政策/规则；存量地址由启动回填器补码，无法解析的需顾客重选 |
| 游客查单 429 | `trading:track:{ip}` Redis 计数 | 10 次/小时；确需放行删 key |

## 6. 迁移与回滚

- 本版本仅 expand：新表 `order_event`/`event_outbox`/`shipment`/`shipment_line`/`shipment_event`/`shipping_option`/`tax_rule`/`tax_destination_policy`/`exchange_rate_history`；存量表只增可空/带默认值列（`shipping_rate` 原样保留）。
- 启动期初始化器（幂等）：`LogisticsMigrationInitializer`（carrier.code 回填、shipping_rate → shipping_option）、`TaxSeedInitializer`、`AddressCountryCodeBackfillInitializer`、`MailTemplateSeedInitializer`。
- 回滚：部署上一版本 jar 即可，新表/新列对旧代码不可见；回滚窗口内新建的包裹/税费数据在再次升级后仍有效。
- contract（删 `shipping_rate`、删 `orders.carrier/tracking_no` 冗余）留待下一版本。

## 7. 本地开发

- 启动：`bash scripts/backend-api.sh`（需 `.env.backend.local`）、`bash scripts/frontend-portal-store.sh`、`bash scripts/frontend-portal-admin.sh`。
- 冒烟：`bash tests/api-integration/order-flow-smoke.sh`、`bash tests/api-integration/logistics-smoke.sh`（OTP 频控 5 次/小时，勿连续重跑）。
- 模拟付款：消费端支付面板 Continue 即调用 stub 确认端点；订单立刻 PAID 并进入「待审核」阶段。
