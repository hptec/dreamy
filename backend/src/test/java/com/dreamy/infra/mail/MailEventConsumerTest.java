package com.dreamy.infra.mail;

import com.dreamy.identity.infra.mail.MailSender;
import com.dreamy.infra.mail.repository.MailRecordRepository;
import com.dreamy.infra.mq.DomainEvent;
import com.dreamy.infra.mq.EventIdempotencyGuard;
import com.dreamy.infra.mq.MqProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * q.mail 消费单元测试（L3 修复轮，FUNC-016/FUNC-019）。
 * 覆盖面：消费幂等（闸层 + MailRecord 层防重发 bs-671）/ 事件类型映射（含 showroom.remind →
 * showroom_assign 定稿映射与无邮件语义跳过）/ 失败重试计数（failed(retry_count+1) → 超 3 次 dead）/
 * recipient 解析（订单类 CustomerEmailPort、showroom 类 payload.email）。
 * L2 TRACE: TC-TRD-070/071/072 单测面 / FLOW-P11 / acceptance mail_record 状态机场景簇。
 */
@ExtendWith(MockitoExtension.class)
class MailEventConsumerTest {

    private static final long CUSTOMER = 77L;
    private static final String EMAIL = "emma@example.com";

    @Mock
    EventIdempotencyGuard idempotencyGuard;
    @Mock
    MailRecordRepository mailRecordRepository;
    @Mock
    CustomerEmailPort customerEmailPort;
    @Mock
    MailSender mailSender;

    MailEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MailEventConsumer(idempotencyGuard, mailRecordRepository, customerEmailPort,
                mailSender, new MqProperties(), new ObjectMapper());
        lenient().when(idempotencyGuard.tryAcquire(any(), any())).thenReturn(true);
        lenient().when(customerEmailPort.getEmail(CUSTOMER)).thenReturn(EMAIL);
    }

    private DomainEvent event(String type, Map<String, Object> payload) {
        return new DomainEvent("evt-1", type, "2026-06-10T12:00:00Z", payload);
    }

    private DomainEvent orderPaid() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("order_no", "DR-1001");
        payload.put("customer_id", CUSTOMER);
        payload.put("locale", "es");
        payload.put("currency", "USD");
        payload.put("total_amount", "1899.00");
        payload.put("lines", List.of(Map.of("product_id", 11L, "qty", 1)));
        return event("order.paid", payload);
    }

    private MailRecord record(long id, MailStatus status, int retryCount) {
        MailRecord r = new MailRecord();
        r.setId(id);
        r.setType(MailType.ORDER_CONFIRMED);
        r.setRecipient(EMAIL);
        r.setLocale("es");
        r.setStatus(status);
        r.setRetryCount(retryCount);
        r.setEventId("evt-1");
        return r;
    }

    // ==================== 类型映射 ====================

    @Test
    @DisplayName("order.paid → MailRecord(type=order_confirmed, pending) → send(locale=es) → markSent")
    void orderPaidConfirmedMail() {
        when(mailRecordRepository.findByEventId("evt-1")).thenReturn(null);

        consumer.onEvent(orderPaid());

        ArgumentCaptor<MailRecord> captor = ArgumentCaptor.forClass(MailRecord.class);
        verify(mailRecordRepository).insert(captor.capture());
        MailRecord inserted = captor.getValue();
        assertThat(inserted.getType()).isEqualTo(MailType.ORDER_CONFIRMED);
        assertThat(inserted.getStatus()).isEqualTo(MailStatus.PENDING);
        assertThat(inserted.getRecipient()).isEqualTo(EMAIL);
        assertThat(inserted.getLocale()).isEqualTo("es");
        assertThat(inserted.getEventId()).isEqualTo("evt-1");
        assertThat(inserted.getRetryCount()).isZero();
        assertThat(inserted.getPayload()).contains("DR-1001");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> vars = ArgumentCaptor.forClass(Map.class);
        verify(mailSender).send(eq(EMAIL), eq("order_confirmed"), eq("es"), vars.capture());
        // 顶层标量进模板变量；结构型 lines / customer_id 剔除
        assertThat(vars.getValue()).containsEntry("order_no", "DR-1001")
                .doesNotContainKeys("lines", "customer_id");
        verify(mailRecordRepository).markSent(any(), any());
    }

    @Test
    @DisplayName("类型映射全表：order.shipped/refund.resolved/showroom.invite/showroom.remind（→showroom_assign 定稿）")
    void eventTypeMapping() {
        assertThat(MailType.fromEventType("order.paid")).isEqualTo(MailType.ORDER_CONFIRMED);
        assertThat(MailType.fromEventType("order.shipped")).isEqualTo(MailType.ORDER_SHIPPED);
        assertThat(MailType.fromEventType("refund.resolved")).isEqualTo(MailType.REFUND_RESOLVED);
        assertThat(MailType.fromEventType("showroom.invite")).isEqualTo(MailType.SHOWROOM_INVITE);
        // showroom-data-detail 161 定稿：showroom.remind → MailRecord.type=showroom_assign
        assertThat(MailType.fromEventType("showroom.remind")).isEqualTo(MailType.SHOWROOM_ASSIGN);
        assertThat(MailType.fromEventType("order.cancelled")).isNull();
    }

    @Test
    @DisplayName("showroom.invite：recipient 取 payload.email（不经 CustomerEmailPort），type=showroom_invite")
    void showroomInviteUsesPayloadEmail() {
        when(mailRecordRepository.findByEventId("evt-1")).thenReturn(null);
        consumer.onEvent(event("showroom.invite", Map.of(
                "showroom_id", 8L, "member_id", 31L, "email", "guest@example.com",
                "nickname", "Mia", "showroom_name", "Emma's Big Day",
                "product_name", "Aurelia Gown", "invite_url", "http://localhost:5173/showroom/8?invite=tk",
                "locale", "fr")));

        verify(mailSender).send(eq("guest@example.com"), eq("showroom_invite"), eq("fr"), anyMap());
        verifyNoInteractions(customerEmailPort);
        ArgumentCaptor<MailRecord> captor = ArgumentCaptor.forClass(MailRecord.class);
        verify(mailRecordRepository).insert(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(MailType.SHOWROOM_INVITE);
    }

    @Test
    @DisplayName("无邮件语义事件（order.cancelled，绑定面 order.* 内）→ ack 跳过不落表不发送")
    void nonMailEventSkipped() {
        consumer.onEvent(event("order.cancelled",
                Map.of("order_no", "DR-1", "customer_id", CUSTOMER, "cancel_reason", "timeout")));
        verifyNoInteractions(mailRecordRepository, mailSender);
    }

    @Test
    @DisplayName("订单类 recipient 解析失败（用户不存在/已匿名化）→ 告警跳过不落表")
    void unresolvedRecipientSkipped() {
        when(customerEmailPort.getEmail(CUSTOMER)).thenReturn(null);
        consumer.onEvent(orderPaid());
        verify(mailRecordRepository, never()).insert(any());
        verifyNoInteractions(mailSender);
    }

    // ==================== 消费幂等 ====================

    @Test
    @DisplayName("同 event_id 重投 → SETNX 闸拦截空操作（幂等闸层）")
    void duplicateEventIdSkippedByGuard() {
        when(idempotencyGuard.tryAcquire(MailEventConsumer.QUEUE, "evt-1")).thenReturn(false);
        consumer.onEvent(orderPaid());
        verifyNoInteractions(mailRecordRepository, mailSender);
    }

    @Test
    @DisplayName("MailRecord 已 sent → 防重发跳过（bs-671：MQ 重投同 event_id 不重发）")
    void alreadySentNotResent() {
        when(mailRecordRepository.findByEventId("evt-1")).thenReturn(record(5L, MailStatus.SENT, 0));
        consumer.onEvent(orderPaid());
        verifyNoInteractions(mailSender);
        verify(mailRecordRepository, never()).insert(any());
        verify(mailRecordRepository, never()).markSent(any(), any());
    }

    @Test
    @DisplayName("MailRecord 已 dead → 不再尝试（人工补发通道）")
    void deadRecordNotRetried() {
        when(mailRecordRepository.findByEventId("evt-1")).thenReturn(record(5L, MailStatus.DEAD, 4));
        consumer.onEvent(orderPaid());
        verifyNoInteractions(mailSender);
    }

    // ==================== 失败重试计数 ====================

    @Test
    @DisplayName("发送失败 → retry_count+1 status=failed + 异常上抛（释放幂等键走重试阶梯）")
    void sendFailureMarksFailedAndRethrows() {
        when(mailRecordRepository.findByEventId("evt-1")).thenReturn(null);
        doThrow(new RuntimeException("smtp down"))
                .when(mailSender).send(anyString(), anyString(), anyString(), anyMap());

        assertThatThrownBy(() -> consumer.onEvent(orderPaid())).isInstanceOf(RuntimeException.class);

        verify(mailRecordRepository).markFailure(any(), eq(MailStatus.FAILED), eq(1));
        verify(mailRecordRepository, never()).markSent(any(), any());
        // 基类约定：失败释放幂等键允许重投重入
        verify(idempotencyGuard).release(MailEventConsumer.QUEUE, "evt-1");
    }

    @Test
    @DisplayName("重投重入（record=failed, retry_count=2）再失败 → retry_count=3 仍 failed 上抛")
    void retryIncrementsCounter() {
        MailRecord failed = record(5L, MailStatus.FAILED, 2);
        when(mailRecordRepository.findByEventId("evt-1")).thenReturn(failed);
        doThrow(new RuntimeException("smtp down"))
                .when(mailSender).send(anyString(), anyString(), anyString(), anyMap());

        assertThatThrownBy(() -> consumer.onEvent(orderPaid())).isInstanceOf(RuntimeException.class);
        verify(mailRecordRepository).markFailure(5L, MailStatus.FAILED, 3);
    }

    @Test
    @DisplayName("超重试上限（retry_count=3 再失败）→ status=dead 正常 ack 不上抛（告警人工补发）")
    void exceededRetriesMarksDead() {
        MailRecord failed = record(5L, MailStatus.FAILED, 3);
        when(mailRecordRepository.findByEventId("evt-1")).thenReturn(failed);
        doThrow(new RuntimeException("smtp down"))
                .when(mailSender).send(anyString(), anyString(), anyString(), anyMap());

        // dead 分支吞掉异常正常返回（dlq 语义终止重试）
        consumer.onEvent(orderPaid());

        verify(mailRecordRepository).markFailure(5L, MailStatus.DEAD, 4);
        verify(mailRecordRepository, never()).markFailure(anyLong(), eq(MailStatus.FAILED), anyInt());
        verify(idempotencyGuard, never()).release(any(), any());
    }

    // ==================== 拓扑声明 ====================

    @Test
    @DisplayName("队列与绑定声明：q.mail ← order.* / showroom.* / refund.resolved（拓扑登记一致）")
    void queueTopology() {
        assertThat(consumer.queue()).isEqualTo("q.mail");
        assertThat(consumer.bindingKeys()).containsExactly("order.*", "showroom.*", "refund.resolved");
    }
}
