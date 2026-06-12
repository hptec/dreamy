package com.dreamy.infra;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后回调执行器（CP-031：MQ publish / Stripe 边界外调用 / 缓存失效一律事务提交后执行；
 * TX-TRD-002/004/005/011 注记：publish 失败不回滚本地事务）。
 * 无活动事务时立即执行（幂等短路等不开事务场景）。
 */
@Component
public class TradingAfterCommitRunner {

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
