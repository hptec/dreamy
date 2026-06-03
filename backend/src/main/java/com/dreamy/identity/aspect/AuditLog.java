package com.dreamy.identity.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注需要写 operation_log 的 admin 写操作（FLOW-17）。
 * action 值 ∈ ck_oplog_action 15 种枚举。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    String action();
    String target() default "";
}
