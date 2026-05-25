package io.github.red.asyncinit.annotation;

import io.github.red.asyncinit.selector.AsyncInitConfigurationSelector;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enable async initialization for Spring beans.
 *
 * <p>Add this annotation to a {@code @SpringBootApplication} or {@code @Configuration} class
 * to enable async initialization of bean init methods ({@code @PostConstruct},
 * {@code afterPropertiesSet}, and custom {@code init-method}).</p>
 *
 * <p>Example — always enabled:</p>
 * <pre>{@code
 * @SpringBootApplication
 * @EnableAsyncInit
 * public class Application { ... }
 * }</pre>
 *
 * <p>Example — restricted to specific Spring profiles:</p>
 * <pre>{@code
 * @SpringBootApplication
 * @EnableAsyncInit(enabledProfiles = {"dev", "staging"})
 * public class Application { ... }
 * }</pre>
 *
 * @author red
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(AsyncInitConfigurationSelector.class)
public @interface EnableAsyncInit {

    /**
     * Spring profiles in which async init is active.
     * Empty array (default) means active in all profiles.
     */
    String[] enabledProfiles() default {};

    /**
     * Fully-qualified class names to exclude from async initialization.
     */
    String[] excludeClasses() default {};
}
