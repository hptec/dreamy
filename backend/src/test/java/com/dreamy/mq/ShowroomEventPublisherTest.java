package com.dreamy.mq;

import com.dreamy.infra.mq.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 邮件事件 payload 构造单元测试。
 * L2 TRACE: TC-SHR-007 [P1]（EVT-SHR-001/002 字段齐全 + invite_url 拼装 + locale 透传 + occurred_at；
 * event_id 由发布器信封承载）。
 */
@ExtendWith(MockitoExtension.class)
class ShowroomEventPublisherTest {

    @Mock
    DomainEventPublisher domainEventPublisher;

    ShowroomEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ShowroomEventPublisher(domainEventPublisher, "http://localhost:5173/", "stub");
        lenient().when(domainEventPublisher.publish(any(), any())).thenReturn("evt-1");
    }

    private ShowroomEventPublisher.MailEventPayload payload() {
        return new ShowroomEventPublisher.MailEventPayload(8L, 77L, "emma@example.com", "Emma",
                "Sarah's Bridal Party", LocalDate.parse("2026-09-19"),
                "Meadow Sage Bridesmaid Dress", "Sage", "tok-1", "en");
    }

    @Test
    @DisplayName("showroom.invite payload 字段齐全 + invite_url={base}/showroom/{id}?invite={token}")
    @SuppressWarnings("unchecked")
    void invitePayloadComplete() {
        publisher.publishInvite(payload());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(domainEventPublisher).publish(eq(ShowroomEventPublisher.RK_INVITE), captor.capture());
        Map<String, Object> p = (Map<String, Object>) captor.getValue();
        assertThat(p).containsEntry("showroom_id", 8L)
                .containsEntry("member_id", 77L)
                .containsEntry("email", "emma@example.com")
                .containsEntry("nickname", "Emma")
                .containsEntry("showroom_name", "Sarah's Bridal Party")
                .containsEntry("wedding_date", "2026-09-19")
                .containsEntry("product_name", "Meadow Sage Bridesmaid Dress")
                .containsEntry("color", "Sage")
                .containsEntry("invite_url", "http://localhost:5173/showroom/8?invite=tok-1")
                .containsEntry("locale", "en")
                .containsKey("occurred_at");
    }

    @Test
    @DisplayName("showroom.remind 走同构 payload；wedding_date/color 可选缺省省略")
    @SuppressWarnings("unchecked")
    void remindOptionalFieldsOmitted() {
        publisher.publishRemind(new ShowroomEventPublisher.MailEventPayload(8L, 77L,
                "emma@example.com", "Emma", "Room", null, "Dress", "", "tok-1", "fr"));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(domainEventPublisher).publish(eq(ShowroomEventPublisher.RK_REMIND), captor.capture());
        Map<String, Object> p = (Map<String, Object>) captor.getValue();
        assertThat(p).doesNotContainKey("wedding_date").doesNotContainKey("color")
                .containsEntry("locale", "fr");
    }

    @Test
    @DisplayName("invite_url 拼装：基址尾斜杠归一化")
    void inviteUrlNormalized() {
        assertThat(publisher.inviteUrl(8L, "tok-1"))
                .isEqualTo("http://localhost:5173/showroom/8?invite=tok-1");
    }

    @Test
    @DisplayName("启动 fail-fast：smtp 发信模式 + 回环基址拒绝启动（覆盖协议/大小写/IPv6 变体）")
    void rejectsLoopbackBaseWhenSmtp() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new ShowroomEventPublisher(domainEventPublisher, "http://localhost:5173", "smtp"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new ShowroomEventPublisher(domainEventPublisher, "http://127.0.0.1:5173", "smtp"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new ShowroomEventPublisher(domainEventPublisher, "https://localhost:5173", "smtp"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new ShowroomEventPublisher(domainEventPublisher, "http://LOCALHOST:5173", "smtp"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new ShowroomEventPublisher(domainEventPublisher, "http://[::1]:5173", "smtp"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new ShowroomEventPublisher(domainEventPublisher, "not a url", "smtp"));
        // smtp + 公网地址 / stub + localhost 均放行
        new ShowroomEventPublisher(domainEventPublisher, "http://localhost:5173", "stub");
        new ShowroomEventPublisher(domainEventPublisher, "https://dreamy.com", "smtp");
    }
}
