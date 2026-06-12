package com.dreamy.infra;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后回调执行器（CP-031：MQ publish 在事务提交后；publish 失败不回滚——EC-SHR-002）。
 * 无活动事务时立即执行（ReviewAfterCommitRunner 同型）。
 */
@Component("showroomAfterCommitRunner")
public class ShowroomAfterCommitRunner {

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
