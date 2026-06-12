package com.dreamy.infra;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后回调执行器（CP-031：写操作事务提交后失效缓存 + MQ publish，禁止脏读；
 * TX-REV-002~009 备注 / EC-REV-001/002：缓存失效与 MQ 失败不回滚 DB，TTL 兜底）。
 * 无活动事务时立即执行（幂等短路等不开事务场景）。
 */
@Component("reviewAfterCommitRunner")
public class ReviewAfterCommitRunner {

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
