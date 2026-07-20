package com.dreamy.domain.subscriber.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dreamy.domain.subscriber.consts.NewsletterSubscriberDBConst;
import com.dreamy.domain.subscriber.entity.NewsletterSubscriber;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

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

    /** RM-MKT-140 订阅 upsert —— 单语句原子幂等/复活（TX-MKT-027，无显式事务编排） */
    public void upsertReactivating(NewsletterSubscriber subscriber) {
        mapper.upsertReactivating(subscriber);
    }

    /** 退订（单语句原子；WHERE 含代际谓词，返回影响行数） */
    public int unsubscribeByEmail(String email, LocalDateTime genTime) {
        return mapper.unsubscribeByEmail(email, genTime);
    }

    /** 按 email 查询（退订 0 行后区分"不存在/已退订"与"token 代际落后"） */
    public NewsletterSubscriber findByEmail(String email) {
        return mapper.selectOne(
                new QueryWrapper<NewsletterSubscriber>().eq(NewsletterSubscriberDBConst.EMAIL, email));
    }
}
