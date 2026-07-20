package com.dreamy.domain.subscriber.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.subscriber.entity.NewsletterSubscriber;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** NewsletterSubscriberMapper。表 newsletter_subscriber（RM-MKT-140 由 Repository 封装）。 */
@Mapper
public interface NewsletterSubscriberMapper extends BaseMapper<NewsletterSubscriber> {

    /**
     * RM-MKT-140 原生 SQL：订阅 upsert（uk_newsletter_email）。
     * 旧 status=1（已订阅）→ 全字段保持，空操作；旧 status=2（已退订）→ 复活：
     * source/locale/subscribed_at 取新值，unsubscribed_at 清空，status 回 1。
     * MySQL ON DUPLICATE KEY UPDATE 从左到右求值、列引用取当前（含已赋值）值，
     * 故条件赋值全部在前、status 最后赋值。8.0.20+ 用 AS new 别名（VALUES() 已废弃）；
     * 有别名时旧行列引用必须带表名限定，否则 "Column in field list is ambiguous"（真 MySQL 验证）。
     */
    @Insert("INSERT INTO newsletter_subscriber(email, source, locale, status, subscribed_at, created_at, updated_at) "
            + "VALUES(#{email}, #{source.key}, #{locale}, 1, #{subscribedAt}, NOW(3), NOW(3)) AS new "
            + "ON DUPLICATE KEY UPDATE "
            + "source = IF(newsletter_subscriber.status = 2, new.source, newsletter_subscriber.source), "
            + "locale = IF(newsletter_subscriber.status = 2, new.locale, newsletter_subscriber.locale), "
            + "subscribed_at = IF(newsletter_subscriber.status = 2, new.subscribed_at, newsletter_subscriber.subscribed_at), "
            + "unsubscribed_at = IF(newsletter_subscriber.status = 2, NULL, newsletter_subscriber.unsubscribed_at), "
            + "updated_at = IF(newsletter_subscriber.status = 2, NOW(3), newsletter_subscriber.updated_at), "
            + "status = IF(newsletter_subscriber.status = 2, 1, newsletter_subscriber.status)")
    int upsertReactivating(NewsletterSubscriber subscriber);

    /**
     * 退订（幂等，重复退订保留首次 unsubscribed_at；status 同样最后赋值）。
     * WHERE 原子含代际等值谓词 subscribed_at = gen：token 生成时 gen 取持久化 subscribed_at，
     * 任何更新（复活/时钟回滚/备份恢复产生的代际漂移）都使旧 token 匹配 0 行——
     * 防"旧链接退订新订阅"并发竞态（不在 Java 侧先查后改）。
     * 返回匹配行数（Connector/J 默认 CLIENT_FOUND_ROWS：WHERE 命中即 1，含已退订无变更场景）；
     * 0 = email 不存在 / 代际不一致，由 Service 再 SELECT 区分。
     */
    @Update("UPDATE newsletter_subscriber SET "
            + "unsubscribed_at = IF(status = 2, unsubscribed_at, NOW(3)), "
            + "updated_at = IF(status = 2, updated_at, NOW(3)), "
            + "status = 2 "
            + "WHERE email = #{email} AND subscribed_at = #{genTime}")
    int unsubscribeByEmail(@Param("email") String email, @Param("genTime") LocalDateTime genTime);
}
