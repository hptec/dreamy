package com.dreamy.testsupport;

import com.dreamy.infra.ShowroomAfterCommitRunner;

/** 单测用即时提交后执行器（无事务环境下基类本就立即执行；显式子类便于语义自明） */
public class ImmediateAfterCommitRunner extends ShowroomAfterCommitRunner {

    @Override
    public void run(Runnable action) {
        action.run();
    }
}
