package com.dreamy.domain.subscriber.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.subscriber.entity.NewsletterSubscriber;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** NewsletterSubscriberMapper。表 newsletter_subscriber（RM-MKT-140 由 Repository 封装）。 */
@Mapper
public interface NewsletterSubscriberMapper extends BaseMapper<NewsletterSubscriber> {

    /**
     * RM-MKT-140 原生 SQL：`INSERT ... ON DUPLICATE KEY UPDATE id=id`（uk_newsletter_email；
     * 重复订阅为空操作、首写胜出，不更新 source/locale——E-MKT-11 STEP-MKT-02）。
     */
    @Insert("INSERT INTO newsletter_subscriber(email, source, locale, subscribed_at, created_at, updated_at) "
            + "VALUES(#{email}, #{source.key}, #{locale}, #{subscribedAt}, NOW(3), NOW(3)) "
            + "ON DUPLICATE KEY UPDATE id = id")
    int insertIgnoreDuplicate(NewsletterSubscriber subscriber);
}
