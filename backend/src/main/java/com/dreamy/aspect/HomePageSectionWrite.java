package com.dreamy.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Serializes homepage section writes across application instances.
 *
 * <p>The lock protects invariants that span multiple rows, including the single Hero section rule.
 * The corresponding aspect runs outside Spring's transaction interceptor, so the lock remains held
 * until the intercepted transaction has committed or rolled back.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HomePageSectionWrite {
}
