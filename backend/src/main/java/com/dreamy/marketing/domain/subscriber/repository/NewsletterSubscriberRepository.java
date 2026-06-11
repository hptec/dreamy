package com.dreamy.marketing.domain.subscriber.repository;

import com.dreamy.marketing.domain.subscriber.entity.NewsletterSubscriber;
import org.springframework.stereotype.Repository;

/**
 * Newsletter 订阅仓储（RM-MKT-140）。
 * L2 TRACE: marketing-data-detail §2 NewsletterSubscriberRepository / TX-MKT-027。
 */
@Repository
public class NewsletterSubscriberRepository {

    private final NewsletterSubscriberMapper mapper;

    public NewsletterSubscriberRepository(NewsletterSubscriberMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-MKT-140 insertIgnoreDuplicate —— 单语句原子幂等（TX-MKT-027，无显式事务编排） */
    public void insertIgnoreDuplicate(NewsletterSubscriber subscriber) {
        mapper.insertIgnoreDuplicate(subscriber);
    }
}
