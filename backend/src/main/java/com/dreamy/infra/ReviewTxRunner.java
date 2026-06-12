package com.dreamy.infra;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 显式事务边界执行器（TX-REV-001~010）。
 * 采用编程式事务而非 @Transactional：E-REV-08/11/12/15 的幂等短路要求「短路不开事务」
 * （TX-REV-003/006/007/009），guard 读取在事务外、写段落在 inTx 内原子提交。
 */
@Component
public class ReviewTxRunner {

    private final TransactionTemplate transactionTemplate;

    public ReviewTxRunner(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public <T> T inTx(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }

    public void inTx(Runnable work) {
        transactionTemplate.executeWithoutResult(status -> work.run());
    }
}
