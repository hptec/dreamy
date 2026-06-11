package com.dreamy.shipping.infra;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后回调执行器（CP-031：写操作事务提交后失效缓存，禁止脏读窗口；
 * TX-SHP-001~007「提交后失效」落点；回滚则不执行——旧缓存值仍正确）。
 * 无活动事务时立即执行（幂等短路等不开事务场景）。
 */
@Component
public class ShippingAfterCommitRunner {

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
