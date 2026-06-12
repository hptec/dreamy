package com.dreamy.testsupport;

import com.dreamy.infra.ReviewTxRunner;

import java.util.function.Supplier;

/** 单测用即时事务执行器（绕过 TransactionTemplate，直接执行工作单元——TX 边界由 IT 层验证） */
public class ReviewImmediateTxRunner extends ReviewTxRunner {

    public ReviewImmediateTxRunner() {
        super(null);
    }

    @Override
    public <T> T inTx(Supplier<T> work) {
        return work.get();
    }

    @Override
    public void inTx(Runnable work) {
        work.run();
    }
}
