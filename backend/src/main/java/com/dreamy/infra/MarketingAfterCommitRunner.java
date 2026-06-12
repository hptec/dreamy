package com.dreamy.infra;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后回调执行器（CP-031：写操作事务提交后失效缓存 + MQ publish，禁止脏读；
 * TX-MKT-004~026 备注：MQ 失败不回滚，TTL 兜底——EC-MKT-002）。
 * 无活动事务时立即执行（幂等短路等不开事务场景，TX-MKT-007~010 备注）。
 */
@Component
public class MarketingAfterCommitRunner {

    public void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
