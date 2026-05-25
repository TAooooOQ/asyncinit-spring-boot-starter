package io.github.red.asyncinit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a bean class to be excluded from async initialization.
 * The bean's init methods will still be executed, but synchronously on the main thread.
 *
 * <p>Use this annotation when a bean's init method calls
 * {@code applicationContext.getBean()} or has other thread-safety concerns.</p>
 *
 * @author red
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AsyncInitExclude {
}
