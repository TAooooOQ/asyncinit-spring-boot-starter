package io.github.red.asyncinit;

import io.github.red.asyncinit.listener.AsyncTaskExecutionListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers the core beans required for async initialization.
 * Activated only when {@link io.github.red.asyncinit.annotation.EnableAsyncInit} is present
 * and the current profile matches.
 *
 * @author red
 */
@Configuration
public class AsyncInitAutoConfiguration {

    /**
     * Shared cache: beanName → async-init proxy created during early bean reference exposure.
     * Written by {@link AsyncInitBeanSmartInstantiationPostProcessor},
     * read by {@link AsyncInitBeanPostProcessor}.
     */
    private final Map<String, Object> cachedProxiedBeanReferences = new ConcurrentHashMap<>();

    /**
     * Shared cache: beanName → original bean saved during postProcessBeforeInitialization.
     * Written and read by {@link AsyncInitBeanPostProcessor} to handle circular dependency.
     */
    private final Map<String, Object> cachedOriginalBeanReferences = new ConcurrentHashMap<>();

    @Bean(name = "asyncInitBeanPostProcessor")
    @ConditionalOnMissingBean
    public AsyncInitBeanPostProcessor asyncInitBeanPostProcessor() {
        return new AsyncInitBeanPostProcessor(cachedProxiedBeanReferences, cachedOriginalBeanReferences);
    }

    @Bean(name = "asyncInitBeanSmartInstantiationPostProcessor")
    @ConditionalOnMissingBean
    public AsyncInitBeanSmartInstantiationPostProcessor asyncInitBeanSmartInstantiationPostProcessor() {
        return new AsyncInitBeanSmartInstantiationPostProcessor(cachedProxiedBeanReferences, cachedOriginalBeanReferences);
    }

    @Bean(name = "asyncTaskExecutionListener")
    @ConditionalOnMissingBean
    public AsyncTaskExecutionListener asyncTaskExecutionListener() {
        return new AsyncTaskExecutionListener();
    }
}
