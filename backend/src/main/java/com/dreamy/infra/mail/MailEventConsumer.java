package com.dreamy.infra.mail;

import com.dreamy.infra.mail.MailSender;
import com.dreamy.infra.mail.repository.MailRecordRepository;
import com.dreamy.infra.mq.AbstractIdempotentEventConsumer;
import com.dreamy.infra.mq.DomainEvent;
import com.dreamy.infra.mq.EventIdempotencyGuard;
import com.dreamy.infra.mq.MqProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * q.mail 邮件事件消费者（FLOW-P11，决策 16/20.5；L3 修复轮补全 FUNC-016/FUNC-019 / TC-TRD-070）。
 * 绑定拓扑（application.yml dreamy.mq.queues / RabbitMqTopologyConfig）：
 *   q.mail ← order.* / showroom.* / refund.resolved。
 * 消费链（双层幂等 + MailRecord 状态机）：
 * ① event_id 幂等闸（AbstractIdempotentEventConsumer：Redis SETNX；失败释放键允许重投重入）；
 * ② 事件 type → MailType 映射（MailType.fromEventType；无邮件语义事件如 order.cancelled → ack 跳过）；
 * ③ recipient 解析：showroom 类取 payload.email（EVT-SHR-001/002 载荷自带）；订单类取 payload.customer_id
 *    经 CustomerEmailPort（识别不到收件人 → 告警跳过，不落表）；
 * ④ MailRecord event_id 幂等落表 pending（uk_mail_record_event 兜底并发重投，已 sent|dead → 防重发跳过，bs-671）；
 * ⑤ 渲染发送：MailSender（identity stub/smtp 双模式，模板 email_template 按 type×locale 三语，
 *    MailTemplateSeedInitializer 种子，缺失回退 en）→ 成功 status=sent + sent_at；
 * ⑥ 失败：retry_count+1 → failed + 异常上抛（释放幂等键 → real 模式 dreamy.retry.q.mail 阶梯
 *    5s/30s/180s 重投）；超 dreamy.mq.max-retries=3 → status=dead 正常 ack（dlq 语义，告警人工补发，bs-670）。
 * 日志脱敏：invite_url/invite_token 不入日志，邮箱掩码由 MailSender 实现承载。
 */
@Component
public class MailEventConsumer extends AbstractIdempotentEventConsumer {

    public static final String QUEUE = "q.mail";

    /** 模板变量黑名单：结构型/非渲染字段不进 vars */
    private static final Set<String> NON_TEMPLATE_KEYS = Set.of("lines", "customer_id", "occurred_at");

    private static final String DEFAULT_LOCALE = "en";
    private static final Set<String> SUPPORTED_LOCALES = Set.of("en", "es", "fr");

    private final MailRecordRepository mailRecordRepository;
    private final CustomerEmailPort customerEmailPort;
    private final MailSender mailSender;
    private final MqProperties mqProperties;
    private final ObjectMapper objectMapper;

    public MailEventConsumer(EventIdempotencyGuard idempotencyGuard,
                             MailRecordRepository mailRecordRepository,
                             CustomerEmailPort customerEmailPort,
                             MailSender mailSender,
                             MqProperties mqProperties,
                             ObjectMapper objectMapper) {
        super(idempotencyGuard);
        this.mailRecordRepository = mailRecordRepository;
        this.customerEmailPort = customerEmailPort;
        this.mailSender = mailSender;
        this.mqProperties = mqProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String queue() {
        return QUEUE;
    }

    @Override
    public List<String> bindingKeys() {
        return List.of("order.*", "showroom.*", "refund.resolved");
    }

    @Override
    protected void handle(DomainEvent event) {
        // ② 事件类型 → 邮件类型映射（无映射 → ack 跳过：order.cancelled 等绑定面内非邮件事件）
        MailType type = MailType.fromEventType(event.type());
        if (type == null) {
            log.info("[MAIL] queue={} event_id={} type={} has no mail semantics, skipped",
                    QUEUE, event.eventId(), event.type());
            return;
        }
        Map<String, Object> payload = event.payload() == null ? Map.of() : event.payload();
        String locale = resolveLocale(payload.get("locale"));

        // ③ recipient 解析
        String recipient = resolveRecipient(type, payload);
        if (recipient == null || recipient.isBlank()) {
            log.warn("[MAIL] event_id={} type={} recipient unresolved, skipped (人工跟进)",
                    event.eventId(), type.getKey());
            return;
        }

        // ④ MailRecord event_id 幂等落表（已 sent/dead → 防重发空操作 ack）
        MailRecord record = loadOrInsertPending(event, type, recipient, locale, payload);
        if (record.getStatus() == MailStatus.SENT || record.getStatus() == MailStatus.DEAD) {
            log.info("[MAIL] event_id={} type={} already {} , duplicate delivery skipped (bs-671)",
                    event.eventId(), type.getKey(), record.getStatus().getKey());
            return;
        }

        // ⑤ 渲染发送 → sent；⑥ 失败 → failed(retry_count+1) / dead（超上限）
        try {
            mailSender.send(recipient, type.getKey(), locale, templateVars(payload));
        } catch (RuntimeException ex) {
            int nextRetry = (record.getRetryCount() == null ? 0 : record.getRetryCount()) + 1;
            if (nextRetry > mqProperties.getMaxRetries()) {
                mailRecordRepository.markFailure(record.getId(), MailStatus.DEAD, nextRetry);
                // dead 不再上抛：正常 ack 终止重试（FLOW-P11 dlq 分支，告警人工补发）
                log.error("[MAIL] event_id={} type={} exceeded max retries({}) -> dead (alert, manual resend)",
                        event.eventId(), type.getKey(), mqProperties.getMaxRetries(), ex);
                return;
            }
            mailRecordRepository.markFailure(record.getId(), MailStatus.FAILED, nextRetry);
            log.warn("[MAIL] event_id={} type={} send failed retry_count={} -> failed (nack retry ladder)",
                    event.eventId(), type.getKey(), nextRetry);
            throw ex;
        }
        mailRecordRepository.markSent(record.getId(), LocalDateTime.now());
        log.info("[MAIL] event_id={} type={} locale={} sent", event.eventId(), type.getKey(), locale);
    }

    /** ④ event_id 幂等落表 pending；唯一索引冲突（并发重投竞态）→ 回读既有记录 */
    private MailRecord loadOrInsertPending(DomainEvent event, MailType type, String recipient,
                                           String locale, Map<String, Object> payload) {
        MailRecord existing = mailRecordRepository.findByEventId(event.eventId());
        if (existing != null) {
            return existing;
        }
        MailRecord record = new MailRecord();
        record.setType(type);
        record.setRecipient(recipient);
        record.setLocale(locale);
        record.setPayload(toJson(payload));
        record.setStatus(MailStatus.PENDING);
        record.setRetryCount(0);
        record.setEventId(event.eventId());
        try {
            mailRecordRepository.insert(record);
            return record;
        } catch (DuplicateKeyException ex) {
            // uk_mail_record_event 兜底：并发重投竞态回读
            MailRecord raced = mailRecordRepository.findByEventId(event.eventId());
            if (raced == null) {
                throw ex;
            }
            return raced;
        }
    }

    /** ③ showroom 类 payload.email；订单类 customer_id → CustomerEmailPort */
    private String resolveRecipient(MailType type, Map<String, Object> payload) {
        if (type.isShowroom()) {
            return payload.get("email") instanceof String s ? s : null;
        }
        Long customerId = asLong(payload.get("customer_id"));
        return customerId == null ? null : customerEmailPort.getEmail(customerId);
    }

    /** locale 取 payload（en/es/fr 白名单，缺省/越界回退 en——与 identity i18n 口径一致） */
    private String resolveLocale(Object value) {
        if (value instanceof String s && SUPPORTED_LOCALES.contains(s)) {
            return s;
        }
        return DEFAULT_LOCALE;
    }

    /** 顶层标量载荷 → 模板变量 {{var}}（结构型字段剔除；email_template 渲染惯例） */
    private Map<String, String> templateVars(Map<String, Object> payload) {
        Map<String, String> vars = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            Object v = entry.getValue();
            if (v == null || NON_TEMPLATE_KEYS.contains(entry.getKey())
                    || v instanceof Map || v instanceof List) {
                continue;
            }
            vars.put(entry.getKey(), String.valueOf(v));
        }
        return vars;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            // 载荷快照仅为人工补发依据，序列化异常不阻断发送主链
            log.warn("[MAIL] payload snapshot serialize failed, stored null", ex);
            return null;
        }
    }

    private Long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
