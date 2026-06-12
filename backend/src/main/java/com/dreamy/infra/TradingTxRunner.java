package com.dreamy.infra;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 显式事务边界执行器（TX-TRD-001~012；READ_COMMITTED 由数据源缺省承载，行级竞争一律
 * 「条件更新 CAS / 唯一索引」，不依赖间隙锁——trading-data-detail §4）。
 * 采用编程式事务而非 @Transactional：幂等短路（mergeCart/webhook 重复 event）要求「短路不开事务」，
 * guard 读取在事务外、写段落在 inTx 内原子提交（与 review ReviewTxRunner 同范式）。
 */
@Component
public class TradingTxRunner {

    private final TransactionTemplate transactionTemplate;

    public TradingTxRunner(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public <T> T inTx(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }

    public void inTx(Runnable work) {
        transactionTemplate.executeWithoutResult(status -> work.run());
    }
}
