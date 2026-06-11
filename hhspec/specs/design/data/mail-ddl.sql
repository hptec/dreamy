-- =====================================================================
-- 邮件基建 DDL（portal-api-integration / L3 修复轮补全，FUNC-016/FUNC-019 / 决策 16/20.5）
-- MySQL 8.0, utf8mb4_0900_ai_ci, InnoDB；与 huihao-mysql 实体注解建表等价
-- （运行期由 @EnableMysql(auto=update) 按 com.dreamy.infra.mail.MailRecord 实体自动管理，本文件为审阅基准）。
-- 实体：MailRecord（FLOW-P11 邮件消费者落表）；Long 自增主键（决策 12）；无物理 FK（CP-010）。
-- 备注：① 幂等键 event_id 唯一（showroom-data-detail 161 定稿：showroom 类每次 assign/remind 均为
--        独立业务发送；订单类事件 event_id 同样每事件唯一，统一口径；MQ 重投同 event_id 不重发 bs-671）；
--      ② 状态机 pending→sent / pending→failed(retry_count+1)→dead（超 dreamy.mq.max-retries=3，
--        FLOW-P11 dlq 语义，告警人工补发 bs-670）；
--      ③ 三语模板复用 identity email_template 表（type×locale 18 行种子，MailTemplateSeedInitializer，
--        缺失回退 en——EmailTemplateRenderer RM-120 口径），不另建模板表；
--      ④ 消费拓扑：q.mail ← order.* / showroom.* / refund.resolved（application.yml dreamy.mq.queues）。
-- =====================================================================

-- 1. mail_record 邮件发送记录（决策 16 幂等防重发 + 失败重试；acceptance mail_record 场景簇）
CREATE TABLE mail_record (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  type        VARCHAR(32)  NOT NULL COMMENT '邮件类型 order_confirmed|order_shipped|refund_resolved|showroom_invite|showroom_assign|showroom_remind（决策 20.5 扩展枚举）',
  recipient   VARCHAR(254) NOT NULL COMMENT '收件邮箱（订单类经 CustomerEmailPort 解析；showroom 类取 payload.email）',
  locale      VARCHAR(8)   NOT NULL DEFAULT 'en' COMMENT '渲染语言 en/es/fr（取事件 payload.locale，缺省 en）',
  payload     JSON         NULL COMMENT '事件载荷 JSON 快照（dead 人工补发渲染依据）',
  status      VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending|sent|failed|dead（MailStatus 状态机）',
  retry_count INT          NOT NULL DEFAULT 0 COMMENT '失败重试计数（超 dreamy.mq.max-retries=3 → dead）',
  event_id    VARCHAR(64)  NOT NULL COMMENT '领域事件 event_id（UUID，消费幂等键）',
  sent_at     DATETIME(3)  NULL COMMENT '发送成功时间（status=sent 同写）',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_mail_record_event (event_id),
  KEY idx_mail_record_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件发送记录（MQ 消费幂等防重发 + 失败重试，FLOW-P11）';
