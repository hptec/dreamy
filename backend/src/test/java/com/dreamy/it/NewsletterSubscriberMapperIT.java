package com.dreamy.it;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dreamy.domain.subscriber.consts.NewsletterSubscriberDBConst;
import com.dreamy.domain.subscriber.entity.NewsletterSubscriber;
import com.dreamy.domain.subscriber.repository.NewsletterSubscriberMapper;
import com.dreamy.enums.NewsletterSource;
import com.dreamy.enums.SubscriberStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NewsletterSubscriberMapper 真 MySQL 集成测试（2026-07-18 退订变更）。
 * 验证 upsert 复活语义 / 退订幂等 / 代际谓词的原子行为（ON DUPLICATE KEY UPDATE 赋值顺序无法用 Mock 验证）。
 */
class NewsletterSubscriberMapperIT extends AbstractIT {

    @Autowired NewsletterSubscriberMapper mapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearSubscribers() {
        jdbcTemplate.update("DELETE FROM newsletter_subscriber");
    }

    @Test
    @DisplayName("首次订阅插入 status=1；重复订阅空操作（首写胜出，source/locale/subscribed_at 保持）")
    void duplicateSubscribeIsNoOp() throws InterruptedException {
        NewsletterSubscriber first = subscribe("a@dreamy.test", NewsletterSource.FOOTER, "en");
        mapper.upsertReactivating(first);
        NewsletterSubscriber inserted = findByEmail("a@dreamy.test");
        assertThat(inserted.getStatus()).isEqualTo(SubscriberStatus.SUBSCRIBED);
        assertThat(inserted.getUnsubscribedAt()).isNull();

        Thread.sleep(20);
        NewsletterSubscriber dup = subscribe("a@dreamy.test", NewsletterSource.HOME_BLOCK, "fr");
        mapper.upsertReactivating(dup);

        NewsletterSubscriber after = findByEmail("a@dreamy.test");
        assertThat(after.getSource()).isEqualTo(NewsletterSource.FOOTER);
        assertThat(after.getLocale()).isEqualTo("en");
        assertThat(after.getSubscribedAt()).isEqualTo(inserted.getSubscribedAt());
        assertThat(after.getUpdatedAt()).isEqualTo(inserted.getUpdatedAt());
    }

    @Test
    @DisplayName("退订后再订阅复活：status 回 1、subscribed_at 更新、unsubscribed_at 清空、source/locale 取新值")
    void resubscribeReactivates() throws InterruptedException {
        mapper.upsertReactivating(subscribe("b@dreamy.test", NewsletterSource.FOOTER, "en"));
        NewsletterSubscriber subscribed = findByEmail("b@dreamy.test");
        assertThat(mapper.unsubscribeByEmail("b@dreamy.test", subscribed.getSubscribedAt())).isEqualTo(1);

        Thread.sleep(20);
        NewsletterSubscriber re = subscribe("b@dreamy.test", NewsletterSource.MODAL, "es");
        mapper.upsertReactivating(re);

        NewsletterSubscriber revived = findByEmail("b@dreamy.test");
        assertThat(revived.getStatus()).isEqualTo(SubscriberStatus.SUBSCRIBED);
        assertThat(revived.getUnsubscribedAt()).isNull();
        assertThat(revived.getSource()).isEqualTo(NewsletterSource.MODAL);
        assertThat(revived.getLocale()).isEqualTo("es");
        assertThat(revived.getSubscribedAt()).isAfter(subscribed.getSubscribedAt());
    }

    @Test
    @DisplayName("重复退订幂等：保留首次 unsubscribed_at/updated_at（Connector/J 默认 found rows，命中即 1，值不变由下行断言保证）")
    void duplicateUnsubscribeKeepsFirstTimestamp() throws InterruptedException {
        mapper.upsertReactivating(subscribe("c@dreamy.test", NewsletterSource.FOOTER, "en"));
        NewsletterSubscriber subscribed = findByEmail("c@dreamy.test");

        assertThat(mapper.unsubscribeByEmail("c@dreamy.test", subscribed.getSubscribedAt())).isEqualTo(1);
        NewsletterSubscriber once = findByEmail("c@dreamy.test");
        assertThat(once.getStatus()).isEqualTo(SubscriberStatus.UNSUBSCRIBED);
        assertThat(once.getUnsubscribedAt()).isNotNull();

        Thread.sleep(1100); // 跨秒，若误更新 unsubscribed_at 可观测
        mapper.unsubscribeByEmail("c@dreamy.test", subscribed.getSubscribedAt());
        NewsletterSubscriber twice = findByEmail("c@dreamy.test");
        assertThat(twice.getUnsubscribedAt()).isEqualTo(once.getUnsubscribedAt());
        assertThat(twice.getUpdatedAt()).isEqualTo(once.getUpdatedAt());
    }

    @Test
    @DisplayName("代际谓词：复活后旧 gen 的退订匹配 0 行，新订阅不受影响（防旧链接退订新订阅）")
    void staleGenerationDoesNotUnsubscribe() throws InterruptedException {
        mapper.upsertReactivating(subscribe("d@dreamy.test", NewsletterSource.FOOTER, "en"));
        LocalDateTime oldGen = findByEmail("d@dreamy.test").getSubscribedAt();
        mapper.unsubscribeByEmail("d@dreamy.test", oldGen);

        Thread.sleep(20);
        mapper.upsertReactivating(subscribe("d@dreamy.test", NewsletterSource.MODAL, "en"));
        NewsletterSubscriber revived = findByEmail("d@dreamy.test");
        assertThat(revived.getSubscribedAt()).isAfter(oldGen);

        // 旧 token（gen=旧 subscribed_at）→ WHERE subscribed_at = oldGen 不命中 → 0 行
        assertThat(mapper.unsubscribeByEmail("d@dreamy.test", oldGen)).isZero();
        NewsletterSubscriber after = findByEmail("d@dreamy.test");
        assertThat(after.getStatus()).isEqualTo(SubscriberStatus.SUBSCRIBED);
        assertThat(after.getUnsubscribedAt()).isNull();
    }

    @Test
    @DisplayName("代际等值：未来 gen（时钟回滚/备份恢复场景）同样匹配 0 行")
    void futureGenerationDoesNotUnsubscribe() {
        mapper.upsertReactivating(subscribe("e@dreamy.test", NewsletterSource.FOOTER, "en"));
        NewsletterSubscriber current = findByEmail("e@dreamy.test");

        assertThat(mapper.unsubscribeByEmail("e@dreamy.test", current.getSubscribedAt().plusSeconds(60))).isZero();
        NewsletterSubscriber after = findByEmail("e@dreamy.test");
        assertThat(after.getStatus()).isEqualTo(SubscriberStatus.SUBSCRIBED);
        assertThat(after.getUnsubscribedAt()).isNull();
    }

    @Test
    @DisplayName("不存在的 email 退订为 0 行幂等空操作")
    void unsubscribeUnknownEmailIsNoOp() {
        assertThat(mapper.unsubscribeByEmail("ghost@dreamy.test", LocalDateTime.now())).isZero();
    }

    private NewsletterSubscriber subscribe(String email, NewsletterSource source, String locale) {
        NewsletterSubscriber s = new NewsletterSubscriber();
        s.setEmail(email);
        s.setSource(source);
        s.setLocale(locale);
        s.setSubscribedAt(LocalDateTime.now());
        return s;
    }

    private NewsletterSubscriber findByEmail(String email) {
        return mapper.selectOne(
                new QueryWrapper<NewsletterSubscriber>().eq(NewsletterSubscriberDBConst.EMAIL, email));
    }
}
