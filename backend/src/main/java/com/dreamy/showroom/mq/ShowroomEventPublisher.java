package com.dreamy.showroom.mq;

import com.dreamy.infra.mq.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * showroom 域邮件事件发布器（showroom-data-detail §8.1，决策 20.5 / FLOW-P11 消费）。
 * - EVT-SHR-001 `showroom.invite`：TX-SHR-010 提交后（E-SHR-12，仅当本次提供 email 且为新值/首填，防骚扰）
 *   → q.mail 消费 INSERT MailRecord(type=showroom_invite)。
 * - EVT-SHR-002 `showroom.remind`：TX-SHR-011 提交后（E-SHR-13）→ MailRecord(type=showroom_assign)。
 * - 幂等键：showroom 两类型按 event_id 唯一（每次 assign 换 email/remind 重发均为独立业务发送，
 *   区别于订单类 orderId+type 键——本域定稿向 q.mail 分册登记）；event_id/occurred_at 由发布器信封承载。
 * - invite_url 构造：{store-base-url}/showroom/{showroom_id}?invite={invite_token}
 *   （与 showroom-frontend-detail 路由约定一致）；payload 含 token 属业务必要，
 *   **日志侧 invite_url/token 一律 [REDACTED]**（error-strategy 脱敏规则）。
 * - locale：取触发请求 locale（owner 操作语言——收件访客语言未知，以邀请语境渲染，设计定稿）。
 * - 生产侧可靠性：事务提交后发布（调用方经 ShowroomAfterCommitRunner）；失败不回滚（EC-SHR-002，
 *   DomainEventPublisher 契约不抛出）。
 * L2 TRACE: SHR-IMPL-MQ-PUBLISHER / TC-SHR-007/008/023。
 */
@Component
public class ShowroomEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ShowroomEventPublisher.class);

    public static final String RK_INVITE = "showroom.invite";
    public static final String RK_REMIND = "showroom.remind";

    private final DomainEventPublisher eventPublisher;
    private final String storeBaseUrl;

    public ShowroomEventPublisher(DomainEventPublisher eventPublisher,
                                  @Value("${dreamy.showroom.store-base-url:http://localhost:5173}")
                                  String storeBaseUrl) {
        this.eventPublisher = eventPublisher;
        this.storeBaseUrl = storeBaseUrl.endsWith("/")
                ? storeBaseUrl.substring(0, storeBaseUrl.length() - 1) : storeBaseUrl;
    }

    /** EVT-SHR-001 邀请/指派通知（E-SHR-12 STEP-SHR-04 触发条件由调用方裁决） */
    public void publishInvite(MailEventPayload payload) {
        publish(RK_INVITE, payload);
    }

    /** EVT-SHR-002 下单提醒（E-SHR-13 STEP-SHR-04） */
    public void publishRemind(MailEventPayload payload) {
        publish(RK_REMIND, payload);
    }

    private void publish(String routingKey, MailEventPayload p) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("showroom_id", p.showroomId());
        payload.put("member_id", p.memberId());
        payload.put("email", p.email());
        payload.put("nickname", p.nickname());
        payload.put("showroom_name", p.showroomName());
        if (p.weddingDate() != null) {
            payload.put("wedding_date", p.weddingDate().toString());
        }
        payload.put("product_name", p.productName());
        if (p.color() != null && !p.color().isEmpty()) {
            payload.put("color", p.color());
        }
        payload.put("invite_url", inviteUrl(p.showroomId(), p.inviteToken()));
        payload.put("locale", p.locale());
        payload.put("occurred_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        String eventId = eventPublisher.publish(routingKey, payload);
        // 日志脱敏：invite_url/token 一律 [REDACTED]，仅记 showroom_id + member_id
        log.info("[SHOWROOM] published {} event_id={} showroom_id={} member_id={} (invite_url [REDACTED])",
                routingKey, eventId, p.showroomId(), p.memberId());
    }

    /** invite_url 拼装（showroom-data-detail §8.1 口径） */
    String inviteUrl(Long showroomId, String inviteToken) {
        return storeBaseUrl + "/showroom/" + showroomId + "?invite=" + inviteToken;
    }

    /** EVT-SHR-001/002 业务载荷（event_id/occurred_at 信封承载） */
    public record MailEventPayload(Long showroomId, Long memberId, String email, String nickname,
                                   String showroomName, LocalDate weddingDate, String productName,
                                   String color, String inviteToken, String locale) {
    }
}
