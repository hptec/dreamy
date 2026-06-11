package com.dreamy.showroom.infra;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 显式事务边界执行器（TX-SHR-001~012）。
 * 编程式事务：guest-session 的 JWT 签发在事务提交后（TX-SHR-005）、MQ publish 在事务提交后
 * （TX-SHR-010/011，CP-031）——写段落在 inTx 内原子提交，副作用段在事务外。
 */
@Component
public class ShowroomTxRunner {

    private final TransactionTemplate transactionTemplate;

    public ShowroomTxRunner(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public <T> T inTx(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }

    public void inTx(Runnable work) {
        transactionTemplate.executeWithoutResult(status -> work.run());
    }
}
