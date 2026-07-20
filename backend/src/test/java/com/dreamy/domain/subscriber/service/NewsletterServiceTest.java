package com.dreamy.domain.subscriber.service;

import com.dreamy.domain.subscriber.entity.NewsletterSubscriber;
import com.dreamy.domain.subscriber.repository.NewsletterSubscriberRepository;
import com.dreamy.enums.NewsletterSource;
import com.dreamy.enums.SubscriberStatus;
import com.dreamy.error.MarketingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NewsletterService 单元测试（2026-07-18 退订变更）。
 * 订阅校验/复活编排 + 退订各路径（SQL 赋值语义见 NewsletterSubscriberMapperIT 真 MySQL 集成测试）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NewsletterService 单元测试")
class NewsletterServiceTest {

    @Mock
    private NewsletterSubscriberRepository repository;
    @Mock
    private UnsubscribeTokenService tokenService;
    @InjectMocks
    private NewsletterService service;

    // ---------- subscribe ----------

    @Test
    @DisplayName("合法订阅：email trim+小写归一后落 upsert")
    void subscribeNormalizesEmail() {
        service.subscribe("  User@Dreamy.TEST ", 1, "en");
        ArgumentCaptor<NewsletterSubscriber> captor = ArgumentCaptor.forClass(NewsletterSubscriber.class);
        verify(repository).upsertReactivating(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("user@dreamy.test");
        assertThat(captor.getValue().getSource()).isEqualTo(NewsletterSource.FOOTER);
        assertThat(captor.getValue().getSubscribedAt()).isNotNull();
    }

    @Test
    @DisplayName("非法订阅参数 → 422704 字段错误（email/source/locale）")
    void subscribeValidation() {
        assertThatThrownBy(() -> service.subscribe("not-an-email", 1, "en"))
                .isInstanceOf(MarketingException.class);
        assertThatThrownBy(() -> service.subscribe(null, 1, "en"))
                .isInstanceOf(MarketingException.class);
        assertThatThrownBy(() -> service.subscribe("a@b.com", 99, "en"))
                .isInstanceOf(MarketingException.class);
        assertThatThrownBy(() -> service.subscribe("a@b.com", 1, "de"))
                .isInstanceOf(MarketingException.class);
        verify(repository, never()).upsertReactivating(any());
    }

    // ---------- unsubscribe ----------

    @Test
    @DisplayName("token 解析失败（畸形/过期/签名不符）→ 422704 field=token")
    void unsubscribeInvalidToken() {
        when(tokenService.parse("bad")).thenThrow(new UnsubscribeTokenService.InvalidTokenException());
        assertThatThrownBy(() -> service.unsubscribe("bad"))
                .isInstanceOf(MarketingException.class)
                .satisfies(e -> assertThat(((MarketingException) e).getDetails().toString())
                        .contains("token"));
        verify(repository, never()).unsubscribeByEmail(anyString(), any());
    }

    @Test
    @DisplayName("退订命中 1 行 → 正常返回")
    void unsubscribeUpdated() {
        mockParse("user@dreamy.test", 1000L);
        when(repository.unsubscribeByEmail(eq("user@dreamy.test"), any())).thenReturn(1);
        assertThatCode(() -> service.unsubscribe("tok")).doesNotThrowAnyException();
        verify(repository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("退订 0 行 + email 不存在 → 幂等正常返回（不泄露存在性）")
    void unsubscribeUnknownEmailIdempotent() {
        mockParse("ghost@dreamy.test", 1000L);
        when(repository.unsubscribeByEmail(anyString(), any())).thenReturn(0);
        when(repository.findByEmail("ghost@dreamy.test")).thenReturn(null);
        assertThatCode(() -> service.unsubscribe("tok")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("退订 0 行 + 已退订 → 幂等正常返回")
    void unsubscribeAlreadyUnsubscribedIdempotent() {
        mockParse("user@dreamy.test", 1000L);
        when(repository.unsubscribeByEmail(anyString(), any())).thenReturn(0);
        NewsletterSubscriber existing = subscriber("user@dreamy.test", SubscriberStatus.UNSUBSCRIBED,
                LocalDateTime.of(2026, 7, 1, 0, 0));
        when(repository.findByEmail("user@dreamy.test")).thenReturn(existing);
        assertThatCode(() -> service.unsubscribe("tok")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("退订 0 行 + 仍订阅且 subscribed_at 晚于 gen（复活后旧链接）→ 422704 field=token")
    void unsubscribeStaleGenerationRejected() {
        long gen = UnsubscribeTokenService.toEpochMillis(LocalDateTime.of(2026, 7, 1, 0, 0));
        mockParse("user@dreamy.test", gen);
        when(repository.unsubscribeByEmail(anyString(), any())).thenReturn(0);
        NewsletterSubscriber existing = subscriber("user@dreamy.test", SubscriberStatus.SUBSCRIBED,
                LocalDateTime.of(2026, 7, 10, 0, 0));
        when(repository.findByEmail("user@dreamy.test")).thenReturn(existing);
        assertThatThrownBy(() -> service.unsubscribe("tok"))
                .isInstanceOf(MarketingException.class);
    }

    @Test
    @DisplayName("退订 0 行 + 仍订阅且 subscribed_at 早于 gen（未来代际/时钟回滚）→ 422704 field=token")
    void unsubscribeFutureGenerationRejected() {
        long futureGen = UnsubscribeTokenService.toEpochMillis(LocalDateTime.of(2026, 7, 10, 0, 0));
        mockParse("user@dreamy.test", futureGen);
        when(repository.unsubscribeByEmail(anyString(), any())).thenReturn(0);
        NewsletterSubscriber existing = subscriber("user@dreamy.test", SubscriberStatus.SUBSCRIBED,
                LocalDateTime.of(2026, 7, 1, 0, 0));
        when(repository.findByEmail("user@dreamy.test")).thenReturn(existing);
        assertThatThrownBy(() -> service.unsubscribe("tok"))
                .isInstanceOf(MarketingException.class);
    }

    @Test
    @DisplayName("退订 0 行 + 仍订阅但 subscribed_at 等于 gen（并发边缘）→ 幂等正常返回")
    void unsubscribeConcurrentEdgeIdempotent() {
        LocalDateTime subscribedAt = LocalDateTime.of(2026, 7, 1, 0, 0);
        mockParse("user@dreamy.test", UnsubscribeTokenService.toEpochMillis(subscribedAt));
        when(repository.unsubscribeByEmail(anyString(), any())).thenReturn(0);
        NewsletterSubscriber existing = subscriber("user@dreamy.test", SubscriberStatus.SUBSCRIBED, subscribedAt);
        when(repository.findByEmail("user@dreamy.test")).thenReturn(existing);
        assertThatCode(() -> service.unsubscribe("tok")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("token 中的 email 统一小写后落 SQL")
    void unsubscribeLowercasesEmail() {
        mockParse("User@Dreamy.TEST", 1000L);
        when(repository.unsubscribeByEmail(anyString(), any())).thenReturn(1);
        service.unsubscribe("tok");
        verify(repository).unsubscribeByEmail(eq("user@dreamy.test"), any());
    }

    // ---------- generateUnsubscribeToken ----------

    @Test
    @DisplayName("生成退订 token：gen 取持久化 subscribed_at；不存在 → null")
    void generateTokenBindsPersistedGeneration() {
        LocalDateTime subscribedAt = LocalDateTime.of(2026, 7, 1, 12, 0, 0, 123_000_000);
        NewsletterSubscriber existing = subscriber("user@dreamy.test", SubscriberStatus.SUBSCRIBED, subscribedAt);
        when(repository.findByEmail("user@dreamy.test")).thenReturn(existing);
        when(tokenService.generate(eq("user@dreamy.test"),
                eq(UnsubscribeTokenService.toEpochMillis(subscribedAt)))).thenReturn("signed-token");

        assertThat(service.generateUnsubscribeToken("  User@Dreamy.TEST ")).isEqualTo("signed-token");

        when(repository.findByEmail("ghost@dreamy.test")).thenReturn(null);
        assertThat(service.generateUnsubscribeToken("ghost@dreamy.test")).isNull();
    }

    @Test
    @DisplayName("已退订记录不生成 token（防未来邮件发送方误用）")
    void generateTokenSkippedForUnsubscribed() {
        NewsletterSubscriber existing = subscriber("user@dreamy.test", SubscriberStatus.UNSUBSCRIBED,
                LocalDateTime.of(2026, 7, 1, 0, 0));
        when(repository.findByEmail("user@dreamy.test")).thenReturn(existing);
        assertThat(service.generateUnsubscribeToken("user@dreamy.test")).isNull();
        verify(tokenService, never()).generate(anyString(), anyLong());
    }

    private void mockParse(String email, long gen) {
        when(tokenService.parse("tok"))
                .thenReturn(new UnsubscribeTokenService.ParsedToken(email, Long.MAX_VALUE, gen));
    }

    private NewsletterSubscriber subscriber(String email, SubscriberStatus status, LocalDateTime subscribedAt) {
        NewsletterSubscriber s = new NewsletterSubscriber();
        s.setEmail(email);
        s.setStatus(status);
        s.setSubscribedAt(subscribedAt);
        return s;
    }
}
