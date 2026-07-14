package com.dreamy.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Serializes Catalog admin writes that maintain logical references without database foreign keys.
 *
 * <p>Every operation that creates, changes, or removes a category, attribute, collection, or product
 * reference must use this annotation. The corresponding aspect holds one distributed, reentrant lock
 * until the intercepted transaction has committed or rolled back.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CatalogAdminWrite {
}
